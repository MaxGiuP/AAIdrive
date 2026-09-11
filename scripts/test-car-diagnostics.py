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
            self.assertEqual([tag + ":V" for tag in diagnostics.LOG_TAGS] + ["*:S"], args[8:])
            self.assertIn("CarDebugging:V", args)
            return ("I/Unrelated( 1): PRIVATE\n"
                    "I/CarDebugging( 1): Received notification of USB state, connected usb profiles: {connected=true, mtp=true, adb=true}\n"
                    "I/CarDebugging( 1): UsbAccessory manufacturer=BMW serial=PRIVATE_SERIAL\n")

        report = diagnostics.collect("unused-adb", "PRIVATE_SERIAL", fake_adb)
        self.assertEqual(len(diagnostics.PACKAGES) + 3, len(calls))
        self.assertEqual(35, report["android_sdk"])
        self.assertEqual([{"connected": True, "mtp": True, "adb": True}],
                         report["log_observations"]["usb_profile_samples"])
        with tempfile.TemporaryDirectory() as directory:
            path = diagnostics.save_report(report, Path(directory))
            self.assertNotIn("PRIVATE", path.read_text())
            self.assertEqual([path], list(path.parent.iterdir()))
            if os.name == "posix":
                self.assertEqual(0o600, path.stat().st_mode & 0o777)
                self.assertEqual(0o700, path.parent.stat().st_mode & 0o777)

    def test_projection_samples_are_bounded_numeric_and_reject_trailing_text(self):
        sample = ("I/ScreenMirroring( 12): ProjectionPerf window_ms=5000 width=800 height=480 "
                  "interval_ms=250 quality=25 frames=20 sent=10 unchanged=8 jpeg_duplicates=2 "
                  "bytes=100000 sends_per_s=2.00 avg_bytes=10000.0 copy_ms=1.00 encode_ms=5.00 send_ms=190.00")
        result = diagnostics.summarize_logs("\n".join([sample] * 15 + [sample + " PRIVATE_TOKEN"]))
        self.assertEqual(12, len(result["projection_samples"]))
        self.assertEqual(190.0, result["projection_samples"][0]["send_ms"])
        self.assertEqual(8, result["projection_samples"][0]["unchanged"])
        self.assertNotIn("PRIVATE", json.dumps(result))
        self.assertEqual([], diagnostics.summarize_logs(sample + " PRIVATE_TOKEN")["projection_samples"])

    def test_usb_broadcasts_preserve_mode_changes_without_inventing_bmw_connection(self):
        prefix = "I/CarDebugging( 12): " + diagnostics.USB_STATE_PREFIX
        raw = "\n".join(prefix + profiles for profiles in (
            "{connected=true, configured=false}",
            "{connected=true, configured=true, mtp=true, adb=false}",
            "{connected=true, accessory=true, adb=true}",
            "{connected=false, accessory=false}",
            "{}",
        ))
        result = diagnostics.summarize_logs(raw)
        self.assertEqual([
            {"connected": True, "configured": False},
            {"connected": True, "configured": True, "mtp": True, "adb": False},
            {"connected": True, "accessory": True, "adb": True},
            {"connected": False, "accessory": False},
            {},
        ], result["usb_profile_samples"])
        self.assertEqual(5, result["event_counts"]["usb_profile_broadcast_observed"])
        self.assertIsNone(result["last_connection_observed"])
        self.assertEqual("not_logged_by_app", result["bmw_usb_accessory_status"])
        self.assertIn("not live state", result["observation_scope"])
        self.assertIn("possibly from a computer", result["usb_observation_scope"])
        self.assertIn("Missing profiles are unknown", result["usb_observation_scope"])

    def test_usb_profile_parser_accepts_only_known_boolean_keys(self):
        expected = {key: index % 2 == 0 for index, key in enumerate(diagnostics.USB_PROFILE_FIELDS)}
        message = diagnostics.USB_STATE_PREFIX + "{" + ", ".join(
            key + "=" + str(value).lower() for key, value in expected.items()) + "}"
        self.assertEqual(expected, diagnostics.parse_usb_profiles(message))
        self.assertIsNone(diagnostics.parse_usb_profiles("unrelated"))

    def test_usb_samples_are_bounded_and_require_the_expected_log_tag(self):
        prefix = diagnostics.USB_STATE_PREFIX
        raw = "\n".join(["I/CarDebugging( 12): " + prefix + "{mtp=true}"] * 15
                        + ["I/CarDebugging( 12): " + prefix + "{mtp=false}",
                           "I/MainService( 12): " + prefix + "{mtp=true}"])
        result = diagnostics.summarize_logs(raw)
        self.assertEqual(12, len(result["usb_profile_samples"]))
        self.assertEqual({"mtp": False}, result["usb_profile_samples"][-1])
        self.assertEqual(16, result["event_counts"]["usb_profile_broadcast_observed"])

    def test_usb_rejects_extra_identifiers_malformed_values_and_duplicate_keys(self):
        prefix = "I/CarDebugging( 12): " + diagnostics.USB_STATE_PREFIX
        malformed = (
            "{connected=true, serial=PRIVATE_SERIAL}",
            "{connected=true, PRIVATE_SECRET=true}",
            "{connected=PRIVATE_SECRET}",
            "{connected=true} PRIVATE_SECRET",
            "{connected=true, mtp=true, token=https://private.example/SECRET}",
            "{connected=true, manufacturer=BMW, serial=PRIVATE_SERIAL}",
            "{connected=true, connected=false}",
            "{connected=1}",
            "{connected=True}",
            "{connected=true,mtp=true}",
            "{connected=true, }",
        )
        raw = "\n".join(prefix + profiles for profiles in malformed)
        raw += "\nI/CarDebugging( 12): UsbAccessory manufacturer=BMW serial=PRIVATE_SERIAL"
        raw += "\nI/CarDebugging( 12): VIN=WBA123456789ABCDE0 address=PRIVATE_HOME token=SECRET"
        result = diagnostics.summarize_logs(raw)
        self.assertEqual([], result["usb_profile_samples"])
        self.assertNotIn("usb_profile_broadcast_observed", result["event_counts"])
        for private in ("PRIVATE", "SECRET", "https://", "WBA", "serial", "manufacturer"):
            self.assertNotIn(private, json.dumps(result))


if __name__ == "__main__":
    unittest.main()
