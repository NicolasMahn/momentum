import fcntl
import hashlib
import io
import json
import os
from pathlib import Path
import shlex
import subprocess
import sys
import tarfile
import tempfile
import time
import unittest

SCRIPT = Path(__file__).resolve().parents[1] / "emulator.py"
PACKAGE = "com.example.workouttracker.test"

# An emulator with one app: it keeps the app's files, build and permission in a JSON file, answers
# the commands the script sends, logs each one, and refuses anything else.
FAKE_ADB = r'''#!/usr/bin/env python3
import hashlib, io, json, os, shlex, sys, tarfile, time
from pathlib import Path
root = Path(os.environ["FAKE_ROOT"])
state_file = root / "device.json"
state = json.loads(state_file.read_text())
args = sys.argv[1:]
with open(root / "calls.jsonl", "a") as log:
    log.write(json.dumps(args) + "\n")
kind, rest = args[2], args[3:]
PKG = "com.example.workouttracker.test"
files = state["files"]

def under(dirs):
    return {path: content for path, content in files.items() if path.split("/")[0] in dirs}

def tar_of(selected):
    buffer = io.BytesIO()
    with tarfile.open(fileobj=buffer, mode="w") as tar:
        for path, content in sorted(selected.items()):
            item = tarfile.TarInfo(path)
            item.size = len(content.encode())
            tar.addfile(item, io.BytesIO(content.encode()))
    return buffer.getvalue()

def done(output=""):
    state_file.write_text(json.dumps(state))
    sys.stdout.write(output)
    sys.exit(0)

if kind == "get-state":
    done("device\n")
if kind == "shell" and rest[0].startswith("getprop ro.kernel.qemu"):
    done(os.environ["FAKE_QEMU"] + "\n")
if kind == "pull":
    Path(rest[1]).write_bytes(bytes.fromhex(state["build"]))
    done()
if kind == "install":
    state["build"] = Path(rest[-1]).read_bytes().hex()
    state["installed"] = True
    done("Success\n")
argv = shlex.split(rest[0])
writes = not os.environ.get("FAKE_LOSES_WRITES")
DATA_DIRS = {"shared_prefs", "files", "no_backup", "databases"}
if kind == "exec-out" and argv[:5] == ["run-as", PKG, "tar", "-cf", "-"] and set(argv[5:]) <= DATA_DIRS:
    time.sleep(float(os.environ.get("FAKE_SLOW", 0)))
    selected = under(argv[5:])
    if os.environ.get("FAKE_TRUNCATES"):
        selected = dict(sorted(selected.items())[:1])
    sys.stdout.buffer.write(tar_of(selected))
    sys.exit(0)
if kind == "exec-in" and argv == ["run-as", PKG, "tar", "-xf", "-"]:
    if writes:
        with tarfile.open(fileobj=io.BytesIO(sys.stdin.buffer.read())) as tar:
            files.update({item.name: tar.extractfile(item).read().decode() for item in tar if item.isfile()})
    done()
if kind == "shell":
    if argv == ["pm", "list", "packages", PKG]:
        if os.environ.get("FAKE_PM_FAILS"):
            sys.stderr.write("Failure calling service package\n")
            sys.exit(1)
        done(f"package:{PKG}\n" if state["installed"] else "")
    if argv == ["pm", "path", PKG] and state["installed"]:
        done("package:/data/app/base.apk\n")
    if argv == ["sha256sum", "/data/app/base.apk"]:
        done(hashlib.sha256(bytes.fromhex(state["build"])).hexdigest() + "  /data/app/base.apk\n")
    if argv == ["run-as", PKG, "ls"]:
        done("\n".join(sorted({path.split("/")[0] for path in files} | {"cache"})) + "\n")
    if argv[:3] == ["run-as", PKG, "find"] and argv[-6:] == ["-type", "f", "-exec", "sha256sum", "{}", "+"] \
            and set(argv[3:-6]) <= DATA_DIRS:
        if os.environ.get("FAKE_LISTING_FAILS"):
            sys.exit(1)
        dirs = argv[3:-6]
        done("".join(f"{hashlib.sha256(c.encode()).hexdigest()}  {p}\n" for p, c in sorted(under(dirs).items())))
    if argv[:4] == ["run-as", PKG, "rm", "-rf"]:
        if writes:
            for path in under(argv[4:]):
                del files[path]
        done()
    if argv[:3] == ["run-as", PKG, "cat"]:
        done(files.get(argv[3], ""))
    if argv == ["am", "force-stop", PKG]:
        done()
    if argv == ["pm", "clear", PKG]:
        files.clear()
        state["notifications"] = False
        done()
    if argv[:2] in (["pm", "grant"], ["pm", "revoke"]) and argv[2] == PKG:
        state["notifications"] = argv[1] == "grant"
        done()
    if argv == ["appops", "get", PKG, "SCHEDULE_EXACT_ALARM"]:
        mode = state["exact_alarms"]
        done("No operations.\nDefault mode: default\n" if mode == "default" else f"Uid mode: SCHEDULE_EXACT_ALARM: {mode}\n")
    if argv[:5] == ["appops", "set", "--uid", PKG, "SCHEDULE_EXACT_ALARM"]:
        state["exact_alarms"] = argv[5]
        done()
    if argv == ["dumpsys", "package", PKG]:
        done(f"android.permission.POST_NOTIFICATIONS: granted={str(state['notifications']).lower()}\n")
sys.stderr.write(f"unexpected: {args}\n")
sys.exit(1)
'''

FAKE_AAPT2 = r'''#!/usr/bin/env python3
import os, sys
assert sys.argv[1:3] == ["dump", "badging"]
print(f"package: name='{os.environ.get('FAKE_APK_PACKAGE', 'com.example.workouttracker.test')}' versionCode='5'")
if not os.environ.get("FAKE_APK_RELEASE"):
    print("application-debuggable")
'''

# A worktree whose instrumented tests replace the app's build and data, as Gradle's do, and fail.
FAKE_GRADLEW = r'''#!/usr/bin/env python3
import json, os
from pathlib import Path
state_file = Path(os.environ["FAKE_ROOT"]) / "device.json"
state = json.loads(state_file.read_text())
state.update(files={"shared_prefs/workout_tracker.xml": "<map>from the tests</map>"}, build=b"test build".hex())
state_file.write_text(json.dumps(state))
raise SystemExit(1)
'''


class EmulatorTest(unittest.TestCase):
    def setUp(self):
        temp = tempfile.TemporaryDirectory()
        self.addCleanup(temp.cleanup)
        self.root = Path(temp.name)
        for name, source in (("adb", FAKE_ADB), ("aapt2", FAKE_AAPT2)):
            tool = self.root / name
            tool.write_text(source)
            tool.chmod(0o700)
        self.set_device(files={"shared_prefs/workout_tracker.xml": "<map>records</map>"}, build=b"build one", notifications=True)
        self.env = {
            **os.environ,
            "MOMENTUM_ADB": str(self.root / "adb"),
            "MOMENTUM_AAPT2": str(self.root / "aapt2"),
            "MOMENTUM_EMULATOR_HOME": str(self.root / "home"),
            "MOMENTUM_EMULATOR_LOCK": str(self.root / "emulator.lock"),
            "MOMENTUM_EMULATOR_WAIT": "2",
            "FAKE_ROOT": str(self.root),
            "FAKE_QEMU": "1\n1",
        }

    def set_device(self, files, build, notifications, exact_alarms="default"):
        (self.root / "device.json").write_text(json.dumps({"files": files, "build": build.hex(), "installed": True,
                                                           "notifications": notifications, "exact_alarms": exact_alarms}))

    def device(self):
        return json.loads((self.root / "device.json").read_text())

    def run_script(self, *args, **env):
        return subprocess.run([sys.executable, str(SCRIPT), *args], env={**self.env, **env},
                              capture_output=True, text=True, timeout=180)

    def calls(self):
        log = self.root / "calls.jsonl"
        return [json.loads(line) for line in log.read_text().splitlines()] if log.exists() else []

    def commands(self):
        """Each call to the chosen device, with its command line split into words."""
        return [call[2:3] + (shlex.split(call[3]) if call[2] in ("shell", "exec-out", "exec-in") and len(call) == 4
                             and not call[3].startswith("getprop") else call[3:])
                for call in self.calls() if call[:1] == ["-s"]]

    def test_a_device_that_is_not_an_emulator_is_never_changed(self):
        result = self.run_script("fresh", FAKE_QEMU="0\n")

        self.assertNotEqual(result.returncode, 0)
        self.assertIn("not an emulator", result.stderr)
        self.assertEqual(len(self.commands()), 1)

    def test_clearing_the_app_keeps_its_data_and_build_first_and_reaches_only_the_debug_package(self):
        result = self.run_script("fresh")

        self.assertEqual(result.returncode, 0, result.stderr)
        commands = self.commands()
        named = [word for command in commands for word in command if "com.example.workouttracker" in word]
        self.assertTrue(named and all(word.startswith(PACKAGE) for word in named), named)
        copied = commands.index(["exec-out", "run-as", PACKAGE, "tar", "-cf", "-", "shared_prefs"])
        self.assertLess(copied, commands.index(["shell", "pm", "clear", PACKAGE]))
        [safety] = (self.root / "home/saves").glob("auto-*.json")
        self.assertEqual(json.loads(safety.read_text())["files"],
                         {"shared_prefs/workout_tracker.xml": hashlib.sha256(b"<map>records</map>").hexdigest()})
        self.assertEqual(self.device()["files"], {})

    def test_a_restore_puts_back_build_data_and_permission_exactly(self):
        for allowed in (True, False):
            with self.subTest(allowed=allowed):
                self.set_device(files={"shared_prefs/workout_tracker.xml": "<map>records</map>"}, build=b"build one",
                                notifications=allowed, exact_alarms="allow" if allowed else "default")
                self.assertEqual(self.run_script("save", f"before-{allowed}").returncode, 0)
                self.set_device(files={"shared_prefs/workout_tracker.xml": "<map>converted</map>", "files/extra": "new"},
                                build=b"build two", notifications=not allowed, exact_alarms="default" if allowed else "allow")

                restored = self.run_script("restore", f"before-{allowed}")

                self.assertEqual(restored.returncode, 0, restored.stderr)
                self.assertEqual(self.device()["files"], {"shared_prefs/workout_tracker.xml": "<map>records</map>"})
                self.assertEqual(bytes.fromhex(self.device()["build"]), b"build one")
                self.assertEqual(self.device()["notifications"], allowed)
                self.assertEqual(self.device()["exact_alarms"], "allow" if allowed else "default")

    def test_a_restore_that_did_not_take_says_so(self):
        self.assertEqual(self.run_script("save", "before").returncode, 0)
        self.set_device(files={"shared_prefs/workout_tracker.xml": "<map>changed</map>"}, build=b"build one",
                        notifications=True)

        lost = self.run_script("restore", "before", FAKE_LOSES_WRITES="1")

        self.assertNotEqual(lost.returncode, 0)
        self.assertIn("differs from the save in: shared_prefs/workout_tracker.xml", lost.stderr)

    def test_a_cut_off_copy_is_never_kept_as_a_save(self):
        self.set_device(files={"files/a": "first", "shared_prefs/workout_tracker.xml": "<map>records</map>"},
                        build=b"build one", notifications=True)

        result = self.run_script("save", "before", FAKE_TRUNCATES="1")

        self.assertNotEqual(result.returncode, 0)
        self.assertIn("incomplete", result.stderr)
        self.assertFalse((self.root / "home/saves/before.tar").exists())

    def test_a_save_never_replaces_an_earlier_one(self):
        self.assertEqual(self.run_script("save", "before").returncode, 0)
        self.set_device(files={"shared_prefs/workout_tracker.xml": "<map>changed</map>"}, build=b"build one",
                        notifications=True)

        again = self.run_script("save", "before")

        self.assertNotEqual(again.returncode, 0)
        self.assertIn(b"records", (self.root / "home/saves/before.tar").read_bytes())

    def test_only_a_debuggable_debug_build_is_installed(self):
        apk = self.root / "other.apk"
        apk.write_bytes(b"other build")

        for refused in ({"FAKE_APK_PACKAGE": "com.example.workouttracker"}, {"FAKE_APK_RELEASE": "1"}):
            with self.subTest(refused):
                result = self.run_script("install", str(apk), **refused)

                self.assertNotEqual(result.returncode, 0)
                self.assertNotIn("install", [command[0] for command in self.commands()])

    def test_only_the_store_build_goes_in_as_a_store_build(self):
        apk = self.root / "other.apk"
        apk.write_bytes(b"other build")

        # The owner's real package must never pass, whatever else is wrong with the APK.
        result = self.run_script("store-install", str(apk), FAKE_APK_PACKAGE="com.example.workouttracker")

        self.assertNotEqual(result.returncode, 0)
        self.assertNotIn("install", [command[0] for command in self.commands()])

    def test_removing_the_store_build_names_nothing_else(self):
        self.run_script("store-remove")

        removals = [command for command in self.commands() if "uninstall" in command]
        self.assertEqual(removals, [["shell", "pm", "uninstall", "de.nicolasmahn.momentum"]])

    def test_installing_keeps_the_data_and_build_first(self):
        apk = self.root / "new.apk"
        apk.write_bytes(b"build two")

        result = self.run_script("install", str(apk))

        self.assertEqual(result.returncode, 0, result.stderr)
        kinds = [command[0] for command in self.commands()]
        self.assertLess(kinds.index("exec-out"), kinds.index("install"))
        [safety] = (self.root / "home/saves").glob("auto-*.json")
        self.assertEqual(json.loads(safety.read_text())["build"], hashlib.sha256(b"build one").hexdigest())
        self.assertEqual(bytes.fromhex(self.device()["build"]), b"build two")

    def test_a_failing_package_service_stops_an_install(self):
        apk = self.root / "new.apk"
        apk.write_bytes(b"build two")

        result = self.run_script("install", str(apk), FAKE_PM_FAILS="1")

        self.assertNotEqual(result.returncode, 0)
        self.assertNotIn("install", [command[0] for command in self.commands()])

    def test_a_failed_file_list_stops_clearing_the_app(self):
        result = self.run_script("fresh", FAKE_LISTING_FAILS="1")

        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(self.device()["files"], {"shared_prefs/workout_tracker.xml": "<map>records</map>"})

    def test_a_failed_suite_puts_build_and_data_back_and_fails(self):
        tree = self.root / "worktree"
        tree.mkdir()
        (tree / "gradlew").write_text(FAKE_GRADLEW)
        (tree / "gradlew").chmod(0o700)

        result = self.run_script("suite", str(tree))

        self.assertNotEqual(result.returncode, 0)
        self.assertIn("The suite failed in: worktree", result.stderr)
        self.assertEqual(self.device()["files"], {"shared_prefs/workout_tracker.xml": "<map>records</map>"})
        self.assertEqual(bytes.fromhex(self.device()["build"]), b"build one")

    def test_an_argument_never_runs_as_a_command_on_the_emulator(self):
        self.run_script("prefs", "x; pm clear com.example.workouttracker; #")

        [line] = [call[3] for call in self.calls() if len(call) > 3 and " cat " in call[3]]
        # Split as the emulator's shell splits a command line, operators included.
        words = list(shlex.shlex(line, posix=True, punctuation_chars=True))
        self.assertEqual(words, ["run-as", PACKAGE, "cat", "shared_prefs/x; pm clear com.example.workouttracker; #.xml"])

    def test_a_held_emulator_answers_only_its_run(self):
        self.assertEqual(self.run_script("hold", "mine").returncode, 0)
        self.addCleanup(self.run_script, "--run", "mine", "release")

        other = self.run_script("--run", "theirs", "fresh")
        stolen = self.run_script("--run", "theirs", "release")
        own = self.run_script("--run", "mine", "save", "before")

        self.assertIn("held for run `mine`", other.stderr)
        self.assertEqual(self.device()["files"], {"shared_prefs/workout_tracker.xml": "<map>records</map>"})
        self.assertIn("only that run releases it", stolen.stderr)
        self.assertEqual(own.returncode, 0, own.stderr)

    def test_a_command_of_the_holding_run_keeps_the_lock_when_the_hold_ends(self):
        self.assertEqual(self.run_script("hold", "mine").returncode, 0)
        slow = subprocess.Popen([sys.executable, str(SCRIPT), "--run", "mine", "save", "slow"],
                                env={**self.env, "FAKE_SLOW": "4"}, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        time.sleep(1.5)
        self.assertEqual(self.run_script("--run", "mine", "release").returncode, 0)

        with open(self.root / "emulator.lock", "w") as lock:
            with self.assertRaises(BlockingIOError):
                fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
            _, errors = slow.communicate(timeout=120)
            self.assertEqual(slow.returncode, 0, errors)
            fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)


if __name__ == "__main__":
    unittest.main()
