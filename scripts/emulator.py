#!/usr/bin/env python3
"""Use Momentum's debug package on the shared test emulator.

Agents and scripts reach the emulator through this file only. It talks to `emulator-5554` when it
runs, else to a phone that `~/.config/momentum/test_phones.txt` lists as kept for testing
(`MOMENTUM_SERIAL` picks one). It refuses any other device and any phone with the personal build,
and touches nothing but the disposable debug package, so it cannot reach the owner's phone or the
personal installation. The `store-*` commands try the shrunken store build too, under its own
package: only a release build shows what shrinking broke.

Other sessions share the emulator (Mo's renders, the instrumented suite) and take
`/tmp/mo-emulator.lock` around every install and run; every command here takes it too. `hold RUN`
keeps it for a whole exploration, so nothing reinstalls the app between two steps. While you hold
it, pass `--run RUN` to every command; anyone else waits until you `release`.

    emulator.py start                                   boot the emulator unless it runs
    emulator.py hold first-look                         take the emulator for a run
    emulator.py --run first-look save start             keep the data and the installed build
    emulator.py --run first-look look --locale de-DE    switch language, font or width
    emulator.py --run first-look launch                 start the app cold
    emulator.py --run first-look gallery                open the debug gallery of specimens
    emulator.py --run first-look screen                 list what is on screen
    emulator.py --run first-look tap Settings           tap an element by its label
    emulator.py --run first-look shot settings          screenshot into the run's folder
    emulator.py --run first-look restore start          put build and data back, checked
    emulator.py --run first-look release
    emulator.py --help                                  every command
"""
import argparse
from contextlib import contextmanager
from datetime import datetime
import fcntl
from functools import cache
import hashlib
import io
import json
import os
from pathlib import Path
import re
import secrets
import shlex
import signal
import socket
import subprocess
import sys
import tarfile
import time
import xml.etree.ElementTree as ET

EMULATOR = "emulator-5554"
PERSONAL_PACKAGE = "com.example.workouttracker"
AVD = "momentum-recovery-test"
PACKAGE = "com.example.workouttracker.test"
STORE_PACKAGE = "de.nicolasmahn.momentum"
LAUNCHER = f"{PACKAGE}/com.example.workouttracker.MainActivity"
GALLERY = f"{PACKAGE}/com.example.workouttracker.gallery.GalleryActivity"
NOTIFICATIONS = "android.permission.POST_NOTIFICATIONS"
EXACT_ALARMS = "SCHEDULE_EXACT_ALARM"
SDK = Path.home() / "Android/Sdk"
ADB = os.environ.get("MOMENTUM_ADB", str(SDK / "platform-tools/adb"))
LOCK = Path(os.environ.get("MOMENTUM_EMULATOR_LOCK", "/tmp/mo-emulator.lock"))
HOLD = LOCK.with_suffix(".hold")
# Where a hold hands its run the locked descriptor.
HOLD_SOCKET = LOCK.with_suffix(".sock")
WAIT_SECONDS = float(os.environ.get("MOMENTUM_EMULATOR_WAIT", 30 * 60))
IDLE_MINUTES = 30
# Not /tmp, which this machine empties at every reboot.
HOME = Path(os.environ.get("MOMENTUM_EMULATOR_HOME", Path.home() / ".local/share/momentum-dev/emulator"))
SAVES = HOME / "saves"
BUILDS = SAVES / "builds"
RUNS = HOME / "runs"
# Everything the app stores; caches are rebuilt.
DATA_DIRS = ("shared_prefs", "files", "no_backup", "databases")
DUMP = "/sdcard/momentum-ui.xml"


# Choosing the device

def test_phones():
    """The phones kept for testing only, by serial: one adb serial per line, then a name.

    The list is each developer's own, so it lives outside the repository; without it there are none.
    Never list a phone someone uses.
    """
    listed = Path.home() / ".config/momentum/test_phones.txt"
    lines = listed.read_text().splitlines() if listed.is_file() else []
    listed_phones = (line.split(maxsplit=1) for line in lines if line.strip() and not line.startswith("#"))
    return {serial: name[0] if name else serial for serial, *name in listed_phones}


TEST_PHONES = test_phones()


def choose_serial():
    """The emulator when it runs, else an attached test phone; `MOMENTUM_SERIAL` overrides both."""
    if chosen := os.environ.get("MOMENTUM_SERIAL"):
        return chosen
    try:
        listing = subprocess.run([ADB, "devices"], capture_output=True, timeout=30).stdout.decode()
    except (OSError, subprocess.TimeoutExpired):
        return EMULATOR
    attached = {line.split()[0] for line in listing.splitlines()[1:] if line.endswith("\tdevice")}
    if EMULATOR in attached:
        return EMULATOR
    return next((serial for serial in TEST_PHONES if serial in attached), EMULATOR)


SERIAL = choose_serial()


# Talking to the device

def run_adb(*args, timeout=60, check=True, stdin=None):
    try:
        result = subprocess.run([ADB, "-s", SERIAL, *args], input=stdin, capture_output=True, timeout=timeout)
    except subprocess.TimeoutExpired:
        raise SystemExit(f"The emulator did not answer `{' '.join(args)[:60]}` within {timeout} s.")
    if check and result.returncode:
        raise SystemExit(f"`adb {' '.join(args)[:60]}` failed: {result.stderr.decode(errors='replace').strip()}")
    return result.stdout


@cache
def require_test_device():
    """Stop before anything reaches a device that is neither the test emulator nor a test phone."""
    if SERIAL in TEST_PHONES:
        # A phone listed by mistake would be someone's real one: the personal build gives it away.
        if f"package:{PERSONAL_PACKAGE}" in run_adb("shell", "pm", "list", "packages", PERSONAL_PACKAGE).decode().split():
            raise SystemExit(f"{SERIAL} has the personal build installed. Refusing to touch it.")
        keep_phone_awake()
        return
    qemu = run_adb("shell", "getprop ro.kernel.qemu; getprop ro.boot.qemu", timeout=15).decode().split()
    if "1" not in qemu:
        raise SystemExit(f"{SERIAL} is not an emulator or a test phone. Refusing to touch it.")


def keep_phone_awake():
    """A test phone has a PIN, so a run must never let it lock: its screen stays on while plugged in."""
    run_adb("shell", "settings put global stay_on_while_plugged_in 7; input keyevent KEYCODE_WAKEUP; wm dismiss-keyguard")
    if "isKeyguardShowing=true" in run_adb("shell", "dumpsys window").decode():
        raise SystemExit(f"{TEST_PHONES[SERIAL]} is locked. Ask the owner to unlock it once; it then stays awake.")


def adb(*args, timeout=60, check=True, stdin=None):
    require_test_device()
    return run_adb(*args, timeout=timeout, check=check, stdin=stdin)


def remote(kind, *argv, timeout=60, check=True, stdin=None):
    # adb joins its arguments into one command line for the emulator's shell, so each is quoted here.
    return adb(kind, shlex.join(argv), timeout=timeout, check=check, stdin=stdin)


def shell(*argv, timeout=60, check=True):
    return remote("shell", *argv, timeout=timeout, check=check).decode(errors="replace")


def sha256(content):
    return hashlib.sha256(content).hexdigest()


# The lock

def current_hold():
    try:
        hold = json.loads(HOLD.read_text())
        alive = hold["token"].encode() in Path(f"/proc/{hold['pid']}/cmdline").read_bytes()
    except (OSError, ValueError, KeyError):
        return None
    return hold if alive else None


_locked_here = False


def borrow_hold(run):
    """A descriptor sharing the hold's lock if `run` holds the emulator, else None.

    flock belongs to the open file, not the process: while this copy is open the lock stays
    taken, even if the holder ends meanwhile.
    """
    try:
        with socket.socket(socket.AF_UNIX, socket.SOCK_STREAM) as client:
            client.settimeout(10)
            client.connect(str(HOLD_SOCKET))
            client.sendall(run.encode())
            answer, descriptors, _, _ = socket.recv_fds(client, 64, 1)
    except OSError:
        return None
    return descriptors[0] if answer == b"yours" and descriptors else None


@contextmanager
def emulator_lock(run):
    global _locked_here
    # A second flock on a new descriptor would wait for this process's own lock.
    if _locked_here:
        yield
        return
    borrowed = borrow_hold(run) if run else None
    if borrowed is None and run and not current_hold():
        print(f"Run `{run}` holds the emulator no longer: it was released or idle for {IDLE_MINUTES} minutes. "
              "Another session may have used it since.", file=sys.stderr)
    with (os.fdopen(borrowed, "w") if borrowed is not None else open(LOCK, "w")) as lock:
        if borrowed is None:
            deadline = time.monotonic() + WAIT_SECONDS
            while True:
                try:
                    fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
                    break
                except BlockingIOError:
                    if time.monotonic() > deadline:
                        holder = current_hold()
                        raise SystemExit(f"The emulator is held for run `{holder['run']}`." if holder else
                                         "Another session has had the emulator for too long. Try again later.")
                    time.sleep(2)
        _locked_here = True
        try:
            yield
        finally:
            _locked_here = False


def hold(run):
    if held := current_hold():
        raise SystemExit(f"The emulator is held for run `{held['run']}`. Wait for its release.")
    token = secrets.token_hex(8)
    folder = run_folder(run, None)
    holder = subprocess.Popen(
        [sys.executable, __file__, "_hold", run, str(folder), token],
        start_new_session=True, stdin=subprocess.DEVNULL, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
    )
    started = time.monotonic()
    told = False
    while (current_hold() or {}).get("token") != token:
        if holder.poll() is not None:
            raise SystemExit("Could not take the emulator.")
        if time.monotonic() - started > WAIT_SECONDS:
            holder.terminate()
            raise SystemExit("Another session has had the emulator for too long. Try again later.")
        if not told and time.monotonic() - started > 3:
            print("Waiting for another session to finish with the emulator…", flush=True)
            told = True
        time.sleep(1)
    print(f"Held for run `{run}`. Pass `--run {run}` to every command; the hold ends with `release` "
          f"or after {IDLE_MINUTES} idle minutes. Run folder: {folder}")


def hold_in_background(run, folder, token):
    lock = open(LOCK, "w")
    fcntl.flock(lock, fcntl.LOCK_EX)
    signal.signal(signal.SIGTERM, lambda *_: sys.exit(0))
    HOLD_SOCKET.unlink(missing_ok=True)
    server = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
    server.bind(str(HOLD_SOCKET))
    server.listen()
    server.settimeout(10)
    HOLD.write_text(json.dumps({"pid": os.getpid(), "token": token, "run": run, "folder": folder}))
    last_used = time.monotonic()
    try:
        while time.monotonic() - last_used < IDLE_MINUTES * 60:
            try:
                connection, _ = server.accept()
            except TimeoutError:
                continue
            with connection:
                connection.settimeout(10)
                if connection.recv(256).decode(errors="replace") == run:
                    socket.send_fds(connection, [b"yours"], [lock.fileno()])
                    last_used = time.monotonic()
                else:
                    connection.sendall(b"not yours")
    finally:
        server.close()
        if (current_hold() or {}).get("token") == token:
            HOLD.unlink(missing_ok=True)
            HOLD_SOCKET.unlink(missing_ok=True)


def release(run):
    held = current_hold()
    if not held:
        print("Nothing was held.")
    elif held["run"] != run:
        raise SystemExit(f"The emulator is held for run `{held['run']}`; only that run releases it.")
    else:
        os.kill(held["pid"], signal.SIGTERM)
        print(f"Released run `{run}`.")


def run_folder(run, held):
    if held and held["run"] == run:
        folder = Path(held["folder"])
    else:
        folder = RUNS / (f"{datetime.now():%Y-%m-%d}-{run}" if run else "unheld")
    folder.mkdir(parents=True, exist_ok=True)
    return folder


# Starting

def start():
    if SERIAL in TEST_PHONES:
        if run_adb("get-state", check=False, timeout=15).strip() != b"device":
            raise SystemExit(f"{TEST_PHONES[SERIAL]} is not attached, or it waits for USB debugging to be allowed.")
        require_test_device()
        print(f"{TEST_PHONES[SERIAL]} is ready.")
        return
    if run_adb("get-state", check=False, timeout=15).strip() == b"device":
        require_test_device()
        print("The emulator is running.")
        return
    # Headless mode crashes on this machine, so the window opens. Popen passes the child no
    # descriptor: an emulator holding the lock's would hold the lock for its whole life.
    env = {**os.environ, "DISPLAY": os.environ.get("DISPLAY", ":0")}
    subprocess.Popen(
        [str(SDK / "emulator/emulator"), "-avd", AVD, "-no-snapshot-save", "-no-boot-anim", "-no-audio"],
        env=env, start_new_session=True, stdin=subprocess.DEVNULL, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
    )
    deadline = time.monotonic() + 300
    while time.monotonic() < deadline:
        state = run_adb("get-state", check=False, timeout=15).strip()
        if state == b"device" and run_adb("shell", "getprop", "sys.boot_completed", check=False).strip() == b"1":
            require_test_device()
            print("The emulator is running.")
            return
        time.sleep(3)
    raise SystemExit("The emulator did not finish booting within five minutes.")


# The installed build and its data

def installed_apk():
    """Where the installed debug build lies on the emulator, or None when it is not installed."""
    if f"package:{PACKAGE}" not in shell("pm", "list", "packages", PACKAGE).split():
        return None
    return shell("pm", "path", PACKAGE).strip().removeprefix("package:")


def keep_build(path):
    """Keep a copy of the installed build, once per distinct build, and return its digest."""
    digest = shell("sha256sum", path).split()[0]
    kept = BUILDS / f"{digest}.apk"
    if not (kept.exists() and sha256(kept.read_bytes()) == digest):
        BUILDS.mkdir(parents=True, exist_ok=True)
        partial = kept.with_suffix(".part")
        adb("pull", path, str(partial), timeout=300)
        if sha256(partial.read_bytes()) != digest:
            raise SystemExit("The copy of the installed build is incomplete.")
        partial.rename(kept)
    return digest


def notifications_allowed():
    return f"{NOTIFICATIONS}: granted=true" in shell("dumpsys", "package", PACKAGE)


def exact_alarms_mode():
    """Whether the app may set precise alarms ("Alarms & reminders"), as Android's mode name."""
    answer = shell("appops", "get", PACKAGE, EXACT_ALARMS)
    # An app nobody changed answers "No operations." and its default mode.
    if found := re.search(EXACT_ALARMS + r": (\w+)", answer) or re.search(r"Default mode: (\w+)", answer):
        return found.group(1)
    raise SystemExit(f"Could not read whether the app may set precise alarms: {answer.strip()}")


def files_of(payload):
    try:
        with tarfile.open(fileobj=io.BytesIO(payload), mode="r:") as archive:
            return {item.name: archive.extractfile(item).read() for item in archive if item.isfile()}
    except tarfile.TarError as error:
        raise SystemExit(f"A copy of the app's data is broken: {error}")


def read_data():
    """The app's stored files, checked against the emulator's own list so a cut-off copy never passes."""
    present = shell("run-as", PACKAGE, "ls").split()
    dirs = [name for name in DATA_DIRS if name in present]
    if not dirs:
        buffer = io.BytesIO()
        tarfile.open(fileobj=buffer, mode="w").close()
        return buffer.getvalue(), {}
    # exec-out passes bytes unchanged but drops the remote exit status; the list below makes up for it.
    payload = remote("exec-out", "run-as", PACKAGE, "tar", "-cf", "-", *dirs, timeout=120)
    files = files_of(payload)
    listing = shell("run-as", PACKAGE, "find", *dirs, "-type", "f", "-exec", "sha256sum", "{}", "+")
    listed = {path: digest for digest, path in (line.split(maxsplit=1) for line in listing.splitlines())}
    if {name: sha256(content) for name, content in files.items()} != listed:
        raise SystemExit("The copy of the app's data is incomplete. Nothing was changed.")
    return payload, files


def save(name):
    data, meta = SAVES / f"{name}.tar", SAVES / f"{name}.json"
    if data.exists():
        raise SystemExit(f"A save named `{name}` exists. Choose another name; saves are never replaced.")
    apk = installed_apk()
    if apk is None:
        raise SystemExit("The debug package is not installed. There is nothing to save.")
    shell("am", "force-stop", PACKAGE)
    payload, files = read_data()
    record = {
        "build": keep_build(apk),
        "notifications": notifications_allowed(),
        "exact_alarms": exact_alarms_mode(),
        "files": {path: sha256(content) for path, content in files.items()},
    }
    SAVES.mkdir(parents=True, exist_ok=True)
    try:
        with open(data, "xb") as out:
            out.write(payload)
    except FileExistsError:
        raise SystemExit(f"A save named `{name}` exists. Choose another name; saves are never replaced.")
    meta.write_text(json.dumps(record, indent=2))
    print(f"Saved {len(files)} files and the installed build as `{name}`.")
    return name


def safety_save():
    """Keep the data before a command replaces it, so no state is lost to a mistake."""
    if installed_apk() is None:
        return None
    name = base = f"auto-{datetime.now():%Y-%m-%dT%H-%M-%S}"
    counter = 2
    while (SAVES / f"{name}.tar").exists():
        name, counter = f"{base}-{counter}", counter + 1
    return save(name)


def restore(name):
    data, meta = SAVES / f"{name}.tar", SAVES / f"{name}.json"
    if not (data.exists() and meta.exists()):
        raise SystemExit(f"No save named `{name}` in {SAVES}.")
    record = json.loads(meta.read_text())
    payload = data.read_bytes()
    wanted = files_of(payload)
    if {path: sha256(content) for path, content in wanted.items()} != record["files"]:
        raise SystemExit(f"The save `{name}` is damaged.")
    safety_save()
    apk = installed_apk()
    if apk is None or shell("sha256sum", apk).split()[0] != record["build"]:
        # The data belongs to the build it was saved with; a newer one may have converted it already.
        build = BUILDS / f"{record['build']}.apk"
        if not (build.exists() and sha256(build.read_bytes()) == record["build"]):
            raise SystemExit(f"The build saved with `{name}` is missing or damaged in {BUILDS}. Nothing was changed.")
        install_file(build, downgrade=True)
    shell("am", "force-stop", PACKAGE)
    shell("run-as", PACKAGE, "rm", "-rf", *DATA_DIRS)
    if wanted:
        remote("exec-in", "run-as", PACKAGE, "tar", "-xf", "-", stdin=payload)
    # A broadcast may have started the app while its data was missing.
    shell("am", "force-stop", PACKAGE)
    shell("pm", "grant" if record["notifications"] else "revoke", PACKAGE, NOTIFICATIONS)
    shell("appops", "set", "--uid", PACKAGE, EXACT_ALARMS, record["exact_alarms"])
    _, present = read_data()
    differing = sorted(path for path in wanted.keys() | present.keys() if wanted.get(path) != present.get(path))
    if differing:
        raise SystemExit("The data on the emulator differs from the save in: " + ", ".join(differing))
    print(f"Restored the build, both permissions and {len(wanted)} files exactly.")


def fresh():
    """Clear the app as a new installation finds it, without the notification permission."""
    if safety_save() is None:
        raise SystemExit("The debug package is not installed.")
    shell("am", "force-stop", PACKAGE)
    shell("pm", "clear", PACKAGE)
    print("The app is as new. `launch` starts it.")


def badging(apk):
    """What the APK says about itself: its package and whether it is debuggable."""
    tools = sorted(SDK.glob("build-tools/*/aapt2"), key=lambda path: [int(part) for part in re.findall(r"\d+", path.parent.name)])
    aapt2 = os.environ.get("MOMENTUM_AAPT2") or (str(tools[-1]) if tools else None)
    if not aapt2:
        raise SystemExit("aapt2 is missing from the Android SDK's build tools.")
    return subprocess.run([aapt2, "dump", "badging", str(apk)], capture_output=True, text=True).stdout


def is_debug_build(apk):
    """Whether the APK is the debug package, built debuggable so its data can be saved."""
    described = badging(apk)
    return f"package: name='{PACKAGE}'" in described and "application-debuggable" in described.splitlines()


def install(apk, downgrade):
    path = Path(apk)
    if not path.is_file():
        raise SystemExit(f"No APK at {apk}.")
    if not is_debug_build(path):
        raise SystemExit(f"{apk} is not a debug build of `{PACKAGE}`. Only debug builds go on the emulator.")
    safety_save()
    install_file(path, downgrade)


def install_file(path, downgrade):
    # An update keeps the data and tells the app it was replaced, as a phone update does.
    output = adb("install", "-r", *(["-d"] if downgrade else []), str(path), timeout=300).decode().strip()
    print(output.splitlines()[-1] if output else "Installed.")


# The store build

def store_install(apk):
    """The signed store build goes in beside the debug app; it has no data anyone needs."""
    if f"package: name='{STORE_PACKAGE}'" not in badging(apk):
        raise SystemExit(f"{apk} is not a store build of `{STORE_PACKAGE}`.")
    print(adb("install", "-r", str(apk), timeout=300).decode().strip().splitlines()[-1])


def store_launch():
    shell("am", "force-stop", STORE_PACKAGE)
    shell("logcat", "-b", "crash", "-c", check=False)
    shell("monkey", "-p", STORE_PACKAGE, "-c", "android.intent.category.LAUNCHER", "1")
    time.sleep(4)


def store_crashes():
    log = shell("logcat", "-d", "-b", "crash", check=False)
    reports = [report for report in re.split(r"\n(?=.*FATAL EXCEPTION)", log) if f"Process: {STORE_PACKAGE}," in report]
    print("\n".join(reports).strip() or "No crash of the store build since its last launch.")


def store_remove():
    shell("pm", "uninstall", STORE_PACKAGE, check=False)
    print("The store build is gone.")


# Using the app

def launch(activity=LAUNCHER):
    shell("am", "force-stop", PACKAGE)
    shell("logcat", "-b", "crash", "-c", check=False)
    shell("am", "start", "-W", "-n", activity)
    time.sleep(2)


def screen_size():
    # The last size is the override, when a session left one.
    return tuple(map(int, re.findall(r"(\d+)x(\d+)", shell("wm", "size"))[-1]))


def look(locale, font, width, reset):
    if reset:
        locale, font = "system", 1.0
    if locale:
        shell("cmd", "locale", "set-app-locales", PACKAGE, "--locales", "" if locale == "system" else locale)
    if font:
        shell("settings", "put", "system", "font_scale", str(font))
    if reset:
        shell("wm", "size", "reset")
        shell("wm", "density", "reset")
    elif width:
        shell("wm", "density", str(round(screen_size()[0] * 160 / width)))
    language = re.search(r"\[(.*)\]", shell("cmd", "locale", "get-app-locales", PACKAGE)).group(1) or "the phone's"
    scale = shell("settings", "get", "system", "font_scale").strip()
    density = int(re.findall(r"density: (\d+)", shell("wm", "density"))[-1])
    print(f"language {language}, font {scale}, width {round(screen_size()[0] * 160 / density)} dp")


def ui_nodes():
    """Every element on screen, each marked with whether it or an element around it can be tapped."""
    # A dump that fails would leave the last one in place, with another screen's positions.
    shell("rm", "-f", DUMP)
    shell("uiautomator", "dump", DUMP, check=False, timeout=30)
    try:
        root = ET.fromstring(remote("exec-out", "cat", DUMP))
    except ET.ParseError:
        raise SystemExit("Could not read the screen. Is an animation running? Try again.")
    nodes = []

    def walk(node, tappable, field, viewport):
        # Compose often gives an element's words to an element inside it: a tile's label, a
        # field's floating label.
        tappable = tappable or node.get("clickable") == "true"
        field = node if node.get("class") == "android.widget.EditText" else field
        if node.tag == "node":
            node.set("tappable", str(tappable).lower())
            node.set("viewport", viewport)
            if field is not None:
                node.set("field-bounds", field.get("bounds"))
                node.set("field-text", field.get("text") or "")
            nodes.append(node)
        for child in node:
            walk(child, tappable, field, node.get("bounds") if node.get("scrollable") == "true" else viewport)

    walk(root, False, None, "[0,0][{0},{1}]".format(*screen_size()))
    return nodes


def bounds(node, key="bounds"):
    return tuple(map(int, re.findall(r"\d+", node.get(key))))


def fully_shown(node):
    """Whether the element lies inside the list or page that scrolls it."""
    x1, y1, x2, y2 = bounds(node)
    left, top, right, bottom = bounds(node, "viewport")
    return left <= x1 and top <= y1 and x2 <= right and y2 <= bottom


def label(node):
    return node.get("text") or node.get("content-desc") or ""


def screen():
    for node in ui_nodes():
        text, desc, tag = node.get("text"), node.get("content-desc"), node.get("resource-id")
        checkable = node.get("checkable") == "true"
        if not (text or desc or tag or checkable):
            continue
        x1, y1, x2, y2 = bounds(node)
        marks = [mark for mark, flag in (("tap", "tappable"), ("scroll", "scrollable")) if node.get(flag) == "true"]
        marks += [("on" if node.get("checked") == "true" else "off")] if checkable else []
        marks += ["field"] if node.get("field-bounds") else []
        words = " | ".join(repr(value) for value in (text, desc) if value)
        print(f"({(x1 + x2) // 2},{(y1 + y2) // 2}) {words or '-'}{' #' + tag if tag else ''}{' [' + ','.join(marks) + ']' if marks else ''}")


def fits(node, wanted, tag, contains):
    if tag is not None:
        return node.get("resource-id") == tag
    values = [value for value in (node.get("text"), node.get("content-desc")) if value]
    return any(wanted in value if contains else wanted == value for value in values)


def tap_center(node):
    x1, y1, x2, y2 = bounds(node)
    shell("input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))
    time.sleep(0.8)


def find(wanted, tag, contains, index, scrolling):
    if wanted is None and tag is None:
        raise SystemExit("Name the element by its label or --tag.")
    seen = None
    for _ in range(25 if scrolling else 1):
        nodes = ui_nodes()
        found = [node for node in nodes if fits(node, wanted, tag, contains)]
        if scrolling:
            found = [node for node in found if fully_shown(node)]
        # A label is often both a row's text and the switch's description; the tappable one acts.
        found = [node for node in found if node.get("tappable") == "true"] or found
        if len(found) > index:
            return found[index]
        shown = [(label(node), node.get("resource-id"), node.get("bounds")) for node in nodes]
        if not scrolling or shown == seen:
            break
        seen = shown
        scroll("down")
    labels = sorted({label(node) for node in ui_nodes() if label(node)})
    shown = ", ".join(repr(text[:40]) for text in labels[:30]) + (", …" if len(labels) > 30 else "")
    raise SystemExit(f"Not on screen{'' if contains else ' (--contains matches part of a label)'}. What is: {shown}")


def scroll(direction):
    """Show what is further down, or further up, by a third of the screen."""
    width, height = screen_size()
    low, high = str(height * 2 // 3), str(height // 3)
    start_y, end_y = (low, high) if direction == "down" else (high, low)
    # Slow enough to drag without a fling, so the page moves by the distance dragged.
    shell("input", "swipe", str(width // 2), start_y, str(width // 2), end_y, "1200")
    time.sleep(0.6)


def type_text(value, into):
    if into is not None:
        fields = [node for node in ui_nodes() if fits(node, into, None, True) and node.get("field-bounds")]
        if not fields:
            raise SystemExit(f"No field is labelled {into!r}.")
        x1, y1, x2, y2 = map(int, re.findall(r"\d+", fields[0].get("field-bounds")))
        shell("input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))
        time.sleep(0.5)
        shell("input", "keyevent", "KEYCODE_MOVE_END")
        shell("input", "keyevent", *(["KEYCODE_DEL"] * max(len(fields[0].get("field-text")), 1)))
    shell("input", "text", value)
    time.sleep(0.5)


def key(name):
    shell("input", "keyevent", f"KEYCODE_{name.upper()}")
    time.sleep(0.8)


def shade(state):
    shell("cmd", "statusbar", "expand-notifications" if state == "open" else "collapse")
    time.sleep(1)


def shot(name, run):
    folder = run_folder(run, current_hold())
    path = folder / f"{name}.png"
    counter = 2
    while path.exists():
        path = folder / f"{name}-{counter}.png"
        counter += 1
    path.write_bytes(remote("exec-out", "screencap", "-p"))
    print(path)


# What the app left behind

def alarms(package=PACKAGE):
    blocks = re.split(r"\n(?=\s+\w+ #\d+: Alarm\{)", shell("dumpsys", "alarm"))
    found = [block for block in blocks if re.match(r"\s+\w+ #\d+: Alarm\{[^}]* " + re.escape(package) + r"\}", block)]
    for block in found:
        tag = re.search(r"tag=(\S+)", block)
        when = re.search(r"origWhen=(\d{4}-\d\d-\d\d \S+|\S+)", block)
        print(f"{when.group(1) if when else '?'}  {tag.group(1) if tag else '?'}")
    print(f"{len(found)} alarms")


def notifications():
    # Only the list on show; Android also lists notifications still being posted.
    shown = re.search(r"^  Notification List:\n(.*?)^  \S", shell("dumpsys", "notification", "--noredact"), re.M | re.S)
    records = re.split(r"\n(?=\s+NotificationRecord\()", shown.group(1) if shown else "")
    found = [record for record in records
             if f"pkg={PACKAGE} " in record.split("\n", 1)[0] and "GROUP_SUMMARY" not in record.split("\n", 1)[0]]
    for record in found:
        title = re.search(r"android\.title=\w+ \((.*)\)", record)
        text = re.search(r"android\.text=\w+ \((.*)\)", record)
        actions = re.findall(r'^\s+\[\d+\] "(.*)" ->', record, re.M)
        print(f"{title.group(1) if title else '-'} / {text.group(1) if text else '-'}" + (f"  actions: {actions}" if actions else ""))
    print(f"{len(found)} notifications")


def crashes():
    """The app's crashes since the last launch; `launch` clears the log, so read it before."""
    log = shell("logcat", "-d", "-b", "crash", check=False)
    reports = [report for report in re.split(r"\n(?=.*FATAL EXCEPTION)", log) if f"Process: {PACKAGE}," in report]
    print("\n".join(reports).strip() or "No crash of the app since the last launch.")


def prefs(name):
    if name:
        print(shell("run-as", PACKAGE, "cat", f"shared_prefs/{name}.xml"))
    else:
        print(shell("run-as", PACKAGE, "ls", "-l", "shared_prefs", check=False))


# The instrumented suite

def suite(worktrees):
    trees = [Path(tree) for tree in worktrees]
    if missing := [str(tree) for tree in trees if not (tree / "gradlew").is_file()]:
        raise SystemExit(f"Not a worktree: {', '.join(missing)}")
    before = safety_save()
    if before is None:
        raise SystemExit("Install the debug package first: the suite's data is put back from a save.")
    logs = HOME / "suite"
    logs.mkdir(parents=True, exist_ok=True)
    failed, interrupted = [], None
    try:
        for tree in trees:
            log = logs / f"{datetime.now():%Y-%m-%dT%H-%M-%S}-{tree.name}.log"
            with open(log, "w") as out:
                # ANDROID_SERIAL keeps Gradle's connected tests on this device if another is attached too.
                code = subprocess.run(["./gradlew", ":app:connectedDebugAndroidTest"], cwd=tree, stdout=out,
                                      stderr=subprocess.STDOUT, env={**os.environ, "ANDROID_SERIAL": SERIAL}).returncode
            summary = [line for line in log.read_text().splitlines() if re.search(r"Tests? on|tests completed|BUILD", line)]
            print(f"{tree.name}: exit {code}. {' '.join(summary[-2:])} Log: {log}")
            if code:
                failed.append(tree.name)
    except BaseException as error:
        interrupted = error
        raise
    finally:
        # The tests install their own build and may uninstall it; put back what was there.
        try:
            restore(before)
        except SystemExit as recovery:
            if interrupted is None:
                raise
            print(f"Putting the emulator back failed too: {recovery} The save `{before}` holds what was there.",
                  file=sys.stderr)
    if failed:
        raise SystemExit(f"The suite failed in: {', '.join(failed)}")


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--run", help="the run holding the emulator; pass it to every command while you hold it")
    commands = parser.add_subparsers(dest="command", required=True)
    commands.add_parser("start", help="boot the emulator unless it runs")
    holding = commands.add_parser("hold", help="take the emulator for a run")
    holding.add_argument("name", help="a short name; the run's folder is named after it")
    commands.add_parser("release", help="give the emulator back")
    for name, text in (("save", "keep the app's data and installed build under a new name"),
                       ("restore", "put a save's build and data back, and check them")):
        command = commands.add_parser(name, help=text)
        command.add_argument("name")
    commands.add_parser("fresh", help="clear the app as a new installation finds it")
    installing = commands.add_parser("install", help="install a debug APK over the app, keeping its data")
    installing.add_argument("apk")
    installing.add_argument("--downgrade", action="store_true")
    commands.add_parser("launch", help="start the app cold")
    commands.add_parser("gallery", help="open the debug gallery of specimens, cold")
    store_installing = commands.add_parser("store-install", help="install the signed store build beside the debug app")
    store_installing.add_argument("apk")
    commands.add_parser("store-launch", help="start the store build cold")
    commands.add_parser("store-crashes", help="the store build's crashes since its last launch")
    commands.add_parser("store-alarms", help="the store build's pending alarms")
    commands.add_parser("store-remove", help="uninstall the store build, the one package it may remove")
    looking = commands.add_parser("look", help="switch language, font size or width; prints the result")
    looking.add_argument("--locale", help="a language tag such as de-DE, or `system`")
    looking.add_argument("--font", type=float, help="font scale, 1.0 is normal")
    looking.add_argument("--width", type=int, help="screen width in dp; the emulator's own is 411")
    looking.add_argument("--reset", action="store_true", help="the phone's language, normal font and width")
    commands.add_parser("screen", help="list what is on screen, with centers and what can be tapped")
    for name, text in (("tap", "tap an element"), ("find", "print where an element is")):
        command = commands.add_parser(name, help=text)
        command.add_argument("label", nargs="?", help="its text or content description, as `screen` lists it")
        command.add_argument("--tag", help="its test tag instead")
        command.add_argument("--contains", action="store_true", help="match part of the label")
        command.add_argument("--index", type=int, default=0)
        command.add_argument("--scroll", action="store_true", help="scroll down until it shows")
    typing = commands.add_parser("type", help="type ASCII text into the focused field, or into the one --into names; Android reads %%s as a space")
    typing.add_argument("value")
    typing.add_argument("--into", help="replace the content of the field with this label or text")
    pressing = commands.add_parser("key", help="press a key: back, enter, home")
    pressing.add_argument("name")
    scrolling = commands.add_parser("scroll", help="show what is further down or up")
    scrolling.add_argument("direction", choices=["down", "up"])
    pulling = commands.add_parser("shade", help="open or close the notification shade")
    pulling.add_argument("state", choices=["open", "close"])
    shooting = commands.add_parser("shot", help="screenshot into the run's folder")
    shooting.add_argument("name")
    commands.add_parser("alarms", help="the app's pending alarms")
    commands.add_parser("notifications", help="the app's notifications on show")
    commands.add_parser("crashes", help="the app's crashes since the last launch")
    reading = commands.add_parser("prefs", help="list the stored preference files, or print one")
    reading.add_argument("name", nargs="?")
    testing = commands.add_parser("suite", help="run the instrumented suite of each worktree, then put everything back")
    testing.add_argument("worktrees", nargs="+")
    internal = commands.add_parser("_hold")
    for name in ("run", "folder", "token"):
        internal.add_argument(name)

    args = parser.parse_args()
    match args.command:
        case "hold": hold(args.name)
        case "_hold": hold_in_background(args.run, args.folder, args.token)
        case "release": release(args.run)
        case _:
            with emulator_lock(args.run):
                dispatch(args)


def dispatch(args):
    match args.command:
        case "start": start()
        case "save": save(args.name)
        case "restore": restore(args.name)
        case "fresh": fresh()
        case "install": install(args.apk, args.downgrade)
        case "launch": launch()
        case "gallery": launch(GALLERY)
        case "store-install": store_install(args.apk)
        case "store-launch": store_launch()
        case "store-crashes": store_crashes()
        case "store-alarms": alarms(STORE_PACKAGE)
        case "store-remove": store_remove()
        case "look": look(args.locale, args.font, args.width, args.reset)
        case "screen": screen()
        case "tap": tap_center(find(args.label, args.tag, args.contains, args.index, args.scroll))
        case "find":
            x1, y1, x2, y2 = bounds(find(args.label, args.tag, args.contains, args.index, args.scroll))
            print(f"({(x1 + x2) // 2},{(y1 + y2) // 2})")
        case "type": type_text(args.value, args.into)
        case "key": key(args.name)
        case "scroll": scroll(args.direction)
        case "shade": shade(args.state)
        case "shot": shot(args.name, args.run)
        case "alarms": alarms()
        case "notifications": notifications()
        case "crashes": crashes()
        case "prefs": prefs(args.name)
        case "suite": suite(args.worktrees)


if __name__ == "__main__":
    main()
