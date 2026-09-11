#!/usr/bin/env python3
"""Parser and privacy regressions, using synthetic data only; never runs adb."""
import importlib.util
import json
import os
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location("car_diagnostics", Path(__file__).with_name("collect-car-diagnostics.py"))
diagnostics = importlib.util.module_from_spec(spec)
spec.loader.exec_module(diagnostics)
PACKAGE = "app.revanced.android.apps.youtube.music"


def session(package=PACKAGE, state="3", size=43, actions=4148):
    return f"""  Session SECRET_SESSION_TOKEN
    ownerPid=123, ownerUid=456, userId=0
    package={package}
    active=true
    state=PlaybackState {{state={state}, position=0, buffered position=0, speed=1.0, updated=123, actions={actions}, custom actions=[Action:mName='PRIVATE_ACTION, mExtras=TOKEN], active item id=17, error=PRIVATE_ERROR}}
    metadata: PRIVATE_TITLE, PRIVATE_ARTIST, https://private.example/token
    queueTitle=PRIVATE_PLAYLIST, size={size}
"""


class DiagnosticsTest(unittest.TestCase):
    def test_device_selection_requires_single_authorized_or_explicit_selection(self):
        devices = "List of devices attached\nphone device\nother unauthorized\n"
        self.assertEqual("phone", diagnostics.select_device(devices))
        devices += "emulator device\n"
        with self.assertRaises(diagnostics.CollectionError):
            diagnostics.select_device(devices)
        self.assertEqual("phone", diagnostics.select_device(devices, "phone"))
        for serial in ("other", "absent"):
            with self.assertRaises(diagnostics.CollectionError):
                diagnostics.select_device(devices, serial)
        with self.assertRaises(diagnostics.CollectionError):
            diagnostics.select_device("List of devices attached\nphone offline\n")

    def test_old_and_new_android_state_formats_keep_only_capabilities(self):
        for state in ("3", "PLAYING(3)"):
            result = diagnostics.parse_media_sessions(session(state=state))[0]
            self.assertEqual("PLAYING", result["state"])
            self.assertEqual(43, result["queue_length"])
            self.assertEqual(17, result["active_queue_item_id"])
            self.assertIn("SKIP_TO_QUEUE_ITEM", result["actions"])
            self.assertIsNone(result["queue_item_ids"])
            serialized = json.dumps(result)
            for private in ("PRIVATE", "SECRET", "https://", "TOKEN"):
                self.assertNotIn(private, serialized)

    def test_empty_queue_differs_from_unknown_queue_and_sessions_do_not_mix(self):
        raw = session(size=0, actions=48) + session("com.private.app", size=800)
        raw += "  Other session\n    package=com.audible.application\n    active=false\n    state=null\n"
        result = diagnostics.parse_media_sessions(raw)
        self.assertEqual(2, len(result))
        self.assertEqual(0, result[0]["queue_length"])
        self.assertEqual(["SKIP_TO_PREVIOUS", "SKIP_TO_NEXT"], result[0]["actions"])
        self.assertIsNone(result[1]["queue_length"])
        self.assertIsNone(result[1]["actions"])
        self.assertFalse(result[1]["active"])
        self.assertEqual([], diagnostics.parse_media_sessions("Permission Denial: PRIVATE"))

    def test_queue_title_embedded_size_cannot_override_final_count(self):
        raw = session().replace("queueTitle=PRIVATE_PLAYLIST, size=43", "queueTitle=PRIVATE, size=900, size=7")
        self.assertEqual(7, diagnostics.parse_media_sessions(raw)[0]["queue_length"])

    def test_package_versions_ignore_resolver_data_and_old_system_package(self):
        raw = f"""Resolver table:
  versionCode=999
Packages:
  Package [{PACKAGE}] (private):
    codePath=/data/app/PRIVATE_PATH
    versionCode=123 minSdk=23 targetSdk=35
    versionName=8.34.51-revanced
    installerPackageName=PRIVATE
Hidden system packages:
  Package [{PACKAGE}] (private):
    versionCode=42 minSdk=21 targetSdk=28
    versionName=1.2.3
"""
        result = diagnostics.parse_package(raw, PACKAGE)
        self.assertEqual(123, result["version_code"])
        self.assertEqual(35, result["targetSdk"])
        self.assertEqual("8.34.51-revanced", result["version_name"])
        self.assertNotIn("PRIVATE", json.dumps(result))
        missing = diagnostics.parse_package("Unable to find package: PRIVATE", PACKAGE)
        self.assertEqual("not_found_or_unavailable", missing["status"])

    def test_log_allowlist_discards_titles_vin_addresses_tokens_and_unrelated_apps(self):
        raw = f"""I/CarProber( 12): Successfully detected BMW connection at port 192.168.1.2:4007
I/MusicSessions( 12): Found mediaSession for {PACKAGE}
I/MusicSessions( 12): Found mediaSession for com.private.app
I/MusicMetadata( 12): Playback state: queueId:-1
I/MusicMetadata( 12): Parsing MediaMetadata PRIVATE_TITLE VIN=WBA123456789ABCDE0 address=PRIVATE_HOME token=SECRET
I/MusicAppDiscovery( 12): Found music app PRIVATE_LABEL
I/MusicBrowser( 12): Successful MediaBrowser connection to PRIVATE_LABEL
I/Unrelated( 12): PRIVATE_TEXT
W/GenericMusicController( 12): Received DeadObjectException from MediaController SECRET
I/CarProber( 12): carCapabilities hmi.type=BMW location=PRIVATE_HOME
"""
        result = diagnostics.summarize_logs(raw)
        self.assertEqual("Bluetooth", result["last_connection_observed"]["inferred_transport"])
        self.assertEqual(-1, result["last_active_queue_id_observed_unattributed"])
        self.assertEqual(1, result["event_counts"]["browser_connected"])
        self.assertEqual([PACKAGE], result["known_session_packages_observed"])
        for private in ("PRIVATE", "SECRET", "192.168", "WBA", "com.private"):
            self.assertNotIn(private, json.dumps(result))

    def test_collection_uses_only_read_allowlist_and_saves_only_sanitized_report(self):
        calls = []

        def fake_adb(adb, args):
            calls.append(args)
            self.assertEqual(["-s", "PRIVATE_SERIAL"], args[:2])
            if args[2:5] == ["shell", "dumpsys", "package"]:
                self.assertIn(args[5], diagnostics.PACKAGES)
                return "Unable to find package: PRIVATE"
            if args[2:] == ["shell", "dumpsys", "media_session"]:
                return session()
            if args[2:] == ["shell", "getprop", "ro.build.version.sdk"]:
                return "35\n"
            self.assertEqual(["logcat", "-d", "-t", "1500", "-v", "brief"], args[2:8])
            self.assertEqual("*:S", args[-1])
            return "I/Unrelated( 1): PRIVATE\n"

        report = diagnostics.collect("unused-adb", "PRIVATE_SERIAL", fake_adb)
        self.assertEqual(len(diagnostics.PACKAGES) + 3, len(calls))
        self.assertEqual(35, report["android_sdk"])
        with tempfile.TemporaryDirectory() as directory:
            path = diagnostics.save_report(report, Path(directory))
            self.assertNotIn("PRIVATE", path.read_text())
            self.assertEqual([path], list(path.parent.iterdir()))
            if os.name == "posix":
                self.assertEqual(0o600, path.stat().st_mode & 0o777)
                self.assertEqual(0o700, path.parent.stat().st_mode & 0o777)


if __name__ == "__main__":
    unittest.main()
