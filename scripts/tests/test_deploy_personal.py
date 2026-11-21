import io
import json
import os
from pathlib import Path
import subprocess
import sys
import tarfile
import tempfile
import unittest

SCRIPT = Path(__file__).resolve().parents[1] / "deploy_personal.py"


class DeploymentTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.apk = self.root / "personal.apk"
        self.apk.write_bytes(b"fake apk")
        self.analyzer = self.root / "apkanalyzer"
        self.analyzer.write_text('#!/usr/bin/env python3\nimport os\nprint(os.environ.get("APK_PACKAGE", "com.example.workouttracker"))\n')
        self.analyzer.chmod(0o700)
        self.adb = self.root / "adb"
        self.adb.write_text('''#!/usr/bin/env python3
import os, sys, json
from pathlib import Path
with open(os.environ["CALLS"], "a") as out: out.write(json.dumps(sys.argv[1:]) + "\\n")
a = sys.argv[3:]
if a == ["get-state"]: print("device")
elif a[:4] == ["shell", "pm", "list", "packages"]:
    if not os.environ.get("ABSENT"): print("package:com.example.workouttracker")
elif a[:3] == ["shell", "am", "force-stop"]: pass
elif a[:2] == ["exec-out", "run-as"] and a[3] == "find":
    if os.environ.get("JOURNAL"): print("./no_backup/restore-journal.json" + os.environ["JOURNAL"].replace("base", ""))
    else: print("./shared_prefs/tracker.xml")
    if os.environ.get("DATABASE"): print("./databases/momentum.db")
elif a[:2] == ["exec-out", "run-as"]:
    if os.environ.get("FAIL_BACKUP"): sys.exit(1)
    sys.stdout.buffer.write(Path(os.environ["PAYLOAD"]).read_bytes())
elif a[:2] == ["install", "-r"]: print("Success")
else: sys.exit(20)
''')
        self.adb.chmod(0o700)
        self.payload = self.root / "payload.tar"
        self.write_archive()
        self.env = dict(os.environ, CALLS=str(self.root / "calls"), PAYLOAD=str(self.payload))

    def write_archive(self, bad=False, database=None):
        with tarfile.open(self.payload, "w") as archive:
            for name in ("tracker", "reminders", "visibility"):
                content = b'<map><string name="secret">private value</string></map>'
                if bad and name == "reminders": content = b"<map>"
                entry = tarfile.TarInfo(f"shared_prefs/{name}.xml")
                entry.size = len(content)
                archive.addfile(entry, io.BytesIO(content))
            for name, content in (database or {}).items():
                entry = tarfile.TarInfo(f"databases/{name}")
                entry.size = len(content)
                archive.addfile(entry, io.BytesIO(content))

    def records_database(self):
        """A records database as the app leaves it while stopped: its last commit still in the write-ahead log."""
        import sqlite3
        directory = Path(tempfile.mkdtemp(dir=self.root))
        connection = sqlite3.connect(directory / "momentum.db")
        connection.execute("PRAGMA journal_mode=WAL")
        connection.execute("PRAGMA wal_autocheckpoint=0")
        connection.execute("CREATE TABLE entity (kind TEXT, id TEXT, body TEXT, position INTEGER, changed TEXT, deleted INTEGER, PRIMARY KEY (kind, id))")
        connection.execute("CREATE TABLE meta (key TEXT PRIMARY KEY, value TEXT)")
        connection.commit()
        connection.execute("INSERT INTO entity VALUES ('measurementLogs', 'a', '{}', 0, '1', 0)")
        connection.commit()
        files = {path.name: path.read_bytes() for path in directory.iterdir()}
        connection.close()
        return files

    def deploy(self, *extra, **env):
        result = subprocess.run([sys.executable, str(SCRIPT), "--serial", "explicit-device", "--apk", str(self.apk), "--archive-dir", str(self.root / "archives"), "--adb", str(self.adb), "--apkanalyzer", str(self.analyzer), *extra], env=dict(self.env, **env), capture_output=True, text=True)
        log = self.root / "calls"
        calls = [json.loads(line) for line in log.read_text().splitlines()] if log.exists() else []
        for call in calls:
            self.assertEqual(call[:2], ["-s", "explicit-device"])
            self.assertNotIn("uninstall", call)
            self.assertNotIn("clear", call)
        self.assertNotIn("private value", result.stdout + result.stderr)
        return result, calls

    def test_complete_archive_precedes_update(self):
        result, calls = self.deploy()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(calls[-1][2:4], ["install", "-r"])
        archives = list((self.root / "archives").glob("*/shared-prefs.tar"))
        self.assertEqual(len(archives), 1)
        self.assertEqual(archives[0].read_bytes(), self.payload.read_bytes())
        manifest = json.loads(archives[0].with_name("manifest.json").read_text())
        self.assertEqual(len(manifest["files"]), 3)
        import hashlib
        self.assertEqual(manifest["sha256"], hashlib.sha256(self.payload.read_bytes()).hexdigest())
        self.assertEqual(archives[0].stat().st_mode & 0o777, 0o600)

    def assert_blocked(self, **env):
        result, calls = self.deploy(**env)
        self.assertNotEqual(result.returncode, 0)
        self.assertFalse(any(call[2] == "install" for call in calls))

    def test_pending_restore_journal_blocks_install(self):
        for suffix in ("base", ".bak", ".new"):
            with self.subTest(suffix=suffix):
                result, calls = self.deploy(JOURNAL=suffix)
                self.assertNotEqual(result.returncode, 0)
                self.assertIn("open Momentum to finish recovery", result.stderr)
                self.assertFalse(any(call[2] == "install" for call in calls))
                self.assertFalse(any("tar" in call for call in calls))

    def test_failed_backup_blocks_install(self): self.assert_blocked(FAIL_BACKUP="1")

    def test_the_records_database_is_archived_with_its_log_once_it_exists(self):
        database = self.records_database()
        self.assertIn("momentum.db-wal", database)
        self.write_archive(database=database)
        result, calls = self.deploy(DATABASE="1")
        self.assertEqual(result.returncode, 0, result.stderr)
        tar = next(call for call in calls if "tar" in call)
        self.assertEqual(tar[-2:], ["shared_prefs", "databases"])
        manifest = json.loads(next((self.root / "archives").glob("*/manifest.json")).read_text())
        self.assertTrue({"databases/momentum.db", "databases/momentum.db-wal"} <= set(manifest["files"]))

    def test_without_a_database_only_preferences_are_asked_for(self):
        result, calls = self.deploy()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(next(call for call in calls if "tar" in call)[-1], "shared_prefs")

    def test_a_damaged_database_blocks_install(self):
        # Without a log to read its pages from, a damaged file is all there is.
        self.write_archive(database={"momentum.db": b"not a database" * 100})
        self.assert_blocked(DATABASE="1")
    def test_absent_package_requires_explicit_first_install(self): self.assert_blocked(ABSENT="1")
    def test_wrong_apk_blocks_all_device_commands(self):
        self.assert_blocked(APK_PACKAGE="com.example.workouttracker.test")
        self.assertFalse((self.root / "calls").exists())
    def test_malformed_xml_blocks_install(self):
        self.write_archive(bad=True)
        self.assert_blocked()
    def test_truncated_tar_blocks_install(self):
        self.payload.write_bytes(self.payload.read_bytes()[:1500])
        self.assert_blocked()
    def test_unwritable_archive_blocks_install(self):
        (self.root / "archives").write_text("not a directory")
        self.assert_blocked()
    def test_explicit_first_install(self):
        result, calls = self.deploy("--first-install", ABSENT="1")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(calls[-1][2:4], ["install", "-r"])
    def test_existing_package_rejects_first_install_override(self):
        result, calls = self.deploy("--first-install")
        self.assertNotEqual(result.returncode, 0)
        self.assertFalse(any(call[2] == "install" for call in calls))


if __name__ == "__main__": unittest.main()
