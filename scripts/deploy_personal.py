#!/usr/bin/env python3
"""Archive personal SharedPreferences and the records database before a data-preserving APK update."""
import argparse
import hashlib
import io
import json
import os
from pathlib import Path
import sqlite3
import subprocess
import tarfile
import tempfile
from datetime import datetime, timezone
import xml.etree.ElementTree as ET

PACKAGE = "com.example.workouttracker"
# The records database and the files SQLite keeps beside it. With write-ahead logging, committed
# records can still be in the -wal file, so the archive takes all of them together while the app is
# stopped.
DATABASE_FILES = {"momentum.db", "momentum.db-wal", "momentum.db-shm", "momentum.db-journal"}


def run(args):
    result = subprocess.run(args, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    if result.returncode:
        raise RuntimeError(f"Command failed: {Path(args[0]).name} (exit {result.returncode}); installation stopped")
    return result.stdout


def validate_archive(payload):
    # tarfile alone accepts some archives missing their end marker.
    if len(payload) < 1024 or len(payload) % 512 or payload[-1024:] != bytes(1024):
        raise ValueError("Incomplete archive")
    names = set()
    database = {}
    with tarfile.open(fileobj=io.BytesIO(payload), mode="r:") as archive:
        for item in archive:
            if item.isdir() and item.name.rstrip("/") in ("shared_prefs", "databases"):
                continue
            path = Path(item.name)
            if item.isfile() and path.parent == Path("databases") and path.name in DATABASE_FILES and item.name not in names:
                content = archive.extractfile(item).read()
                if len(content) != item.size:
                    raise ValueError("Invalid database payload")
                database[path.name] = content
                names.add(item.name)
                continue
            if not item.isfile() or path.parent != Path("shared_prefs") or item.name in names:
                raise ValueError("Unexpected archive entry")
            if not (path.name.endswith(".xml") or path.name.endswith(".xml.bak")):
                raise ValueError("Unexpected preferences file")
            content = archive.extractfile(item).read()
            if len(content) != item.size or b"<!DOCTYPE" in content or b"<!ENTITY" in content:
                raise ValueError("Invalid preferences payload")
            if ET.fromstring(content).tag != "map":
                raise ValueError("Invalid preferences XML root")
            names.add(item.name)
    if not any(name.endswith(".xml") for name in names):
        raise ValueError("No preferences found; refusing unprotected update")
    if database:
        validate_database(database)
    return sorted(names)


def validate_database(files):
    """Opens a copy of the archived database, with its log, and checks it is whole and holds the records table."""
    if "momentum.db" not in files:
        raise ValueError("Database log without its database")
    with tempfile.TemporaryDirectory() as directory:
        for name, content in files.items():
            Path(directory, name).write_bytes(content)
        connection = sqlite3.connect(Path(directory, "momentum.db"))
        try:
            if connection.execute("PRAGMA integrity_check").fetchone() != ("ok",):
                raise ValueError("Archived database is damaged")
            tables = {row[0] for row in connection.execute("SELECT name FROM sqlite_master WHERE type = 'table'")}
            if not {"entity", "meta"} <= tables:
                raise ValueError("Archived database has no records table")
            connection.execute("SELECT count(*) FROM entity WHERE deleted = 0").fetchone()
        except sqlite3.DatabaseError as error:
            raise ValueError("Archived database cannot be read") from error
        finally:
            connection.close()


def persist_archive(root, payload, serial, apk):
    root.mkdir(mode=0o700, parents=True, exist_ok=True)
    stamp = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H-%M-%S.%fZ")
    destination = Path(tempfile.mkdtemp(prefix=stamp + "-", dir=root))
    archive = destination / "shared-prefs.tar"
    with archive.open("xb") as output:
        os.chmod(archive, 0o600)
        output.write(payload)
        output.flush()
        os.fsync(output.fileno())
    files = validate_archive(archive.read_bytes())
    digest = hashlib.sha256(payload).hexdigest()
    if hashlib.sha256(archive.read_bytes()).hexdigest() != digest:
        raise ValueError("Archive readback failed")
    metadata = {"formatVersion": 1, "createdAt": datetime.now(timezone.utc).isoformat(),
                "package": PACKAGE, "serial": serial, "files": files,
                "sha256": digest, "apkSha256": hashlib.sha256(apk.read_bytes()).hexdigest()}
    with (destination / "manifest.json").open("x") as output:
        os.chmod(output.name, 0o600)
        json.dump(metadata, output, indent=2)
        output.flush()
        os.fsync(output.fileno())
    # Persist directory entries before permitting installation.
    for directory in (destination, root):
        fd = os.open(directory, os.O_RDONLY | os.O_DIRECTORY)
        try:
            os.fsync(fd)
        finally:
            os.close(fd)
    return destination


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--apk", required=True, type=Path)
    parser.add_argument("--archive-dir", required=True, type=Path)
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--apkanalyzer", required=True)
    parser.add_argument("--first-install", action="store_true", help="Explicitly accept that no existing app can be archived")
    args = parser.parse_args()
    os.umask(0o077)
    apk = args.apk.resolve(strict=True)
    if run([args.apkanalyzer, "manifest", "application-id", str(apk)]).decode().strip() != PACKAGE:
        raise ValueError("APK is not the personal application")
    adb = [args.adb, "-s", args.serial]
    if run(adb + ["get-state"]).strip() != b"device":
        raise ValueError("Device is not available")
    installed = run(adb + ["shell", "pm", "list", "packages", PACKAGE]).decode().splitlines()
    exists = f"package:{PACKAGE}" in installed
    if not exists and not args.first_install:
        raise ValueError("Personal package is absent; use --first-install only after reviewing recovery options")
    if exists and args.first_install:
        raise ValueError("First-install flag conflicts with an existing personal package")
    if exists:
        run(adb + ["shell", "am", "force-stop", PACKAGE])
        # Android AtomicFile may retain its base, legacy backup, or staging file after interruption.
        # An absent no_backup directory is normal for older versions; listing the app root still works.
        private_paths = run(adb + ["exec-out", "run-as", PACKAGE, "find", ".", "-type", "f"]).decode().splitlines()
        journal_paths = {"./no_backup/restore-journal.json" + suffix for suffix in ("", ".bak", ".new")}
        if journal_paths.intersection(private_paths):
            raise ValueError("Pending restore journal found; open Momentum to finish recovery, then retry the update")
        # The database exists once a build that keeps records as rows has run.
        folders = ["shared_prefs"] + (["databases"] if any(path.startswith("./databases/") for path in private_paths) else [])
        payload = run(adb + ["exec-out", "run-as", PACKAGE, "tar", "-cf", "-", *folders])
        validate_archive(payload)
        destination = persist_archive(args.archive_dir.resolve(), payload, args.serial, apk)
        print(f"Verified local archive: {destination}", flush=True)
    else:
        print("Explicit first install: no existing personal data was archived.", flush=True)
    result = run(adb + ["install", "-r", str(apk)])
    if b"Success" not in result.splitlines():
        raise RuntimeError("Install did not report success; any archive remains preserved")
    print("Personal APK installed successfully. Archive does not prove application-level restoration.")


if __name__ == "__main__":
    try:
        main()
    except (OSError, ValueError, RuntimeError, tarfile.TarError, ET.ParseError) as error:
        # XML parse errors include positions, never the private values themselves.
        raise SystemExit(f"Preflight/deployment failed: {error}")
