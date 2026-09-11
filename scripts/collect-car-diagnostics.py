#!/usr/bin/env python3
"""Collect a local, allowlisted AAIdrive report; never save raw adb output."""

import argparse
from collections import Counter
from datetime import datetime, timezone
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile


OWN_PACKAGES = (
    "me.hufman.androidautoidrive",
    "io.github.maxgiup.aaidrive.projection",
    "io.github.maxgiup.aaidrive.setup",
)
MEDIA_PACKAGES = (
    "app.revanced.android.apps.youtube.music",
    "app.rvx.android.apps.youtube.music",
    "com.google.android.apps.youtube.music",
    "app.revanced.android.youtube",
    "app.rvx.android.youtube",
    "com.google.android.youtube",
    "com.vanced.android.youtube",
    "com.audible.application",
    "com.rumble.battles",
    "com.spotify.music",
)
PACKAGES = OWN_PACKAGES + MEDIA_PACKAGES + (
    "com.andrerinas.headunitrevived",
    "com.google.android.projection.gearhead",
)

# Android PlaybackState action bits. Keep unknown bits in the numeric mask.
ACTION_BITS = {
    1: "STOP", 2: "PAUSE", 4: "PLAY", 8: "REWIND",
    16: "SKIP_TO_PREVIOUS", 32: "SKIP_TO_NEXT", 64: "FAST_FORWARD",
    128: "SET_RATING", 256: "SEEK_TO", 512: "PLAY_PAUSE",
    1024: "PLAY_FROM_MEDIA_ID", 2048: "PLAY_FROM_SEARCH",
    4096: "SKIP_TO_QUEUE_ITEM", 8192: "PLAY_FROM_URI",
    16384: "PREPARE", 32768: "PREPARE_FROM_MEDIA_ID",
    65536: "PREPARE_FROM_SEARCH", 131072: "PREPARE_FROM_URI",
    4194304: "SET_PLAYBACK_SPEED",
}
STATE_NAMES = (
    "NONE", "STOPPED", "PAUSED", "PLAYING", "FAST_FORWARDING", "REWINDING",
    "BUFFERING", "ERROR", "CONNECTING", "SKIPPING_TO_PREVIOUS",
    "SKIPPING_TO_NEXT", "SKIPPING_TO_QUEUE_ITEM",
)
LOG_TAGS = (
    "CarProber", "CarDebugging", "MainService", "MusicSessions", "MusicAppDiscovery",
    "MusicBrowser", "MusicMetadata", "GenericMusicController", "ScreenMirroring",
)
# UsbStatus.KNOWN_PROFILES. Never preserve arbitrary USB extras or device strings.
USB_PROFILE_FIELDS = (
    "connected", "host_connected", "configured", "unlocked", "none", "adb",
    "rndis", "mtp", "ptp", "audio_source", "midi", "accessory", "ncm",
)
USB_STATE_PREFIX = "Received notification of USB state, connected usb profiles: "
PROJECTION_FIELDS = (
    "window_ms", "width", "height", "interval_ms", "quality", "frames", "sent",
    "unchanged", "jpeg_duplicates", "bytes", "sends_per_s", "avg_bytes",
    "copy_ms", "encode_ms", "send_ms",
)
PROJECTION_INTEGERS = set(PROJECTION_FIELDS[:10])
PROJECTION_PATTERN = re.compile("ProjectionPerf " + " ".join(
    field + (r"=(\d+)" if field in PROJECTION_INTEGERS else r"=(\d+(?:\.\d+)?)")
    for field in PROJECTION_FIELDS))


class CollectionError(Exception):
    """Messages are static; never include raw adb errors or device identifiers."""


def select_device(output, requested=None):
    devices = {}
    for line in output.splitlines():
        fields = line.split()
        if len(fields) >= 2 and fields[1] in ("device", "unauthorized", "offline"):
            devices[fields[0]] = fields[1]
    if requested is not None:
        if devices.get(requested) != "device":
            raise CollectionError("Selected device is absent, offline, or not authorized. Check adb devices and the phone's USB debugging prompt.")
        return requested
    authorized = [serial for serial, state in devices.items() if state == "device"]
    if len(authorized) != 1:
        raise CollectionError("Expected exactly one authorized device. Run adb devices, then select the phone with --serial SERIAL.")
    return authorized[0]


def parse_package(output, package):
    """Only the active Package section, never filters, permissions, or old APKs."""
    result = {"package": package, "status": "not_found_or_unavailable"}
    in_packages = False
    in_target = False
    for line in output.splitlines():
        stripped = line.strip()
        if stripped == "Packages:":
            in_packages = True
            continue
        if in_packages and line and not line[0].isspace():
            break
        match = re.match(r"Package \[([^\]]+)\]", stripped)
        if in_packages and match:
            if in_target:
                break
            in_target = match[1] == package
        if not in_target:
            continue
        version = re.fullmatch(r"versionCode=(\d+)(?:\s+.*)?", stripped)
        if version:
            result.update(status="found", version_code=int(version[1]))
            for field in ("minSdk", "targetSdk"):
                value = re.search(r"(?:^|\s)" + field + r"=(\d+)(?:\s|$)", stripped)
                if value:
                    result[field] = int(value[1])
        name = re.fullmatch(r"versionName=([0-9][0-9A-Za-z.+_()-]{0,79})", stripped)
        if name:
            result["version_name"] = name[1]
    return result


def parse_media_sessions(output):
    """AOSP dump fields only. Null means unavailable, not an empty queue."""
    sessions = []
    current = None
    field_indent = None
    for line in output.splitlines():
        stripped = line.strip()
        indent = len(line) - len(line.lstrip())
        package = re.fullmatch(r"package=([a-zA-Z0-9_.]+)", stripped)
        if package:
            current = None
            field_indent = indent
            if package[1] in MEDIA_PACKAGES:
                current = {"package": package[1], "active": None,
                           "state": None, "actions_mask": None, "actions": None,
                           "queue_length": None, "active_queue_item_id": None,
                           "queue_item_ids": None}
                sessions.append(current)
            continue
        if current is None:
            continue
        if stripped and indent < field_indent:
            current = None
            continue
        if indent != field_indent:
            continue
        active = re.fullmatch(r"active=(true|false)", stripped)
        if active:
            current["active"] = active[1] == "true"
        # New Android dumps use PLAYING(3); older releases use just 3.
        state = re.match(r"state=PlaybackState \{state=(?:[A-Z_]+\()?(\d+)\)?, "
                         r"position=-?\d+, buffered position=-?\d+, speed=[^,]+, "
                         r"updated=\d+, actions=(\d+),", stripped)
        if state:
            code, actions = int(state[1]), int(state[2])
            current["state"] = STATE_NAMES[code] if code < len(STATE_NAMES) else "UNKNOWN"
            current["actions_mask"] = actions
            current["actions"] = [name for bit, name in ACTION_BITS.items() if actions & bit]
            active_id = re.search(r"\], active item id=(-?\d+), error=", stripped)
            if active_id:
                current["active_queue_item_id"] = int(active_id[1])
        if stripped.startswith("queueTitle="):
            size = re.search(r", size=(\d+)$", stripped)
            if size:
                current["queue_length"] = int(size[1])
    return sessions


def parse_usb_profiles(message):
    """Accept only the exact boolean map logged by UsbStatus; missing stays unknown."""
    match = re.fullmatch(re.escape(USB_STATE_PREFIX) + r"\{(.*)\}", message)
    if not match:
        return None
    profiles = {}
    if not match[1]:
        return profiles
    for entry in match[1].split(", "):
        field = re.fullmatch(r"([a-z_]+)=(true|false)", entry)
        if not field or field[1] not in USB_PROFILE_FIELDS or field[1] in profiles:
            return None
        profiles[field[1]] = field[2] == "true"
    return profiles


def summarize_logs(output):
    """Turn known messages into counters/numeric fields; never retain a log line."""
    counts = Counter()
    found_sessions = set()
    last_transport = None
    last_queue_id = None
    projection_samples = []
    usb_profile_samples = []
    for line in output.splitlines():
        match = re.fullmatch(r"[VDIWEF]/([^()]+)\(\s*\d+\):\s?(.*)", line)
        if not match:
            continue
        tag, message = match[1].strip(), match[2]
        if tag not in LOG_TAGS:
            continue
        if tag == "CarProber":
            connection = re.fullmatch(r"Successfully detected (BMW|MINI|J29) connection at port .+:(\d{1,5})", message)
            if connection:
                port = int(connection[2])
                if port <= 65535:
                    last_transport = {"brand": connection[1], "port": port,
                                      "inferred_transport": {4004: "USB", 4007: "Bluetooth", 4008: "Ethernet"}.get(port, "unknown")}
                    counts["car_connection_detected"] += 1
            if message == "Previously-connected car has disconnected":
                counts["car_disconnection"] += 1
        elif tag == "CarDebugging":
            profiles = parse_usb_profiles(message)
            if profiles is not None:
                usb_profile_samples.append(profiles)
                usb_profile_samples = usb_profile_samples[-12:]
                counts["usb_profile_broadcast_observed"] += 1
        elif tag == "MainService":
            if message == "Starting to discover car capabilities":
                counts["capability_discovery_started"] += 1
            status = re.fullmatch(r"Not fully connected: IDrive:(true|false) SecurityService:(true|false)", message)
            if status:
                counts["connection_prerequisites_pending"] += 1
        elif tag == "MusicSessions":
            if message == "Can't discoverApps, user hasn't granted Notification Access yet":
                counts["notification_access_missing"] += 1
            prefix = "Found mediaSession for "
            if message.startswith(prefix) and message[len(prefix):] in MEDIA_PACKAGES:
                found_sessions.add(message[len(prefix):])
        elif tag == "MusicAppDiscovery":
            # App display labels may contain private text: only record the event.
            for prefix, event in (("Found music app ", "music_app_discovered"),
                                  ("Found music session ", "music_session_discovered")):
                if message.startswith(prefix):
                    counts[event] += 1
        elif tag == "MusicBrowser":
            for prefix, event in (("Successful MediaBrowser connection to ", "browser_connected"),
                                  ("Failed MediaBrowser connection to ", "browser_connection_failed")):
                if message.startswith(prefix):
                    counts[event] += 1
        elif tag == "MusicMetadata":
            queue = re.fullmatch(r"Playback state: queueId:(-?\d+)", message)
            if queue:
                last_queue_id = int(queue[1])
                counts["playback_queue_id_observed"] += 1
        elif tag == "GenericMusicController" and message.startswith("Received DeadObjectException from MediaController "):
            counts["media_session_disconnected"] += 1
        elif tag == "ScreenMirroring":
            if message in ("Screen mirror frame failed", "Failed to create mirror display"):
                counts["projection_failure"] += 1
            sample = PROJECTION_PATTERN.fullmatch(message)
            if sample:
                projection_samples.append({
                    field: int(value) if field in PROJECTION_INTEGERS else float(value)
                    for field, value in zip(PROJECTION_FIELDS, sample.groups())
                })
                projection_samples = projection_samples[-12:]
    return {"event_counts": dict(sorted(counts.items())),
            "known_session_packages_observed": sorted(found_sessions),
            "last_connection_observed": last_transport,
            "last_active_queue_id_observed_unattributed": last_queue_id,
            "projection_samples": projection_samples,
            "usb_profile_samples": usb_profile_samples,
            "bmw_usb_accessory_status": "not_logged_by_app",
            "usb_observation_scope": "Buffered phone USB mode broadcasts, possibly from a computer; accessory=true does not confirm a BMW accessory or BMW Apps connection. Missing profiles are unknown.",
            "observation_scope": "Recent buffered logs, possibly from earlier connections; not live state."}


def adb_read(adb, args):
    try:
        result = subprocess.run([adb] + args, stdout=subprocess.PIPE,
                                stderr=subprocess.PIPE, timeout=25, check=False)
    except FileNotFoundError:
        raise CollectionError("adb was not found. Install Android SDK Platform Tools or supply --adb PATH.") from None
    except subprocess.TimeoutExpired:
        raise CollectionError("An adb read timed out; reconnect the phone and try again.") from None
    except OSError:
        raise CollectionError("Could not execute adb. Check --adb and the Platform Tools installation.") from None
    if result.returncode:
        raise CollectionError("An adb read failed; check device authorization and connection.")
    return result.stdout.decode("utf-8", errors="replace")


def collect(adb, serial, read=adb_read):
    prefix = ["-s", serial, "shell"]
    report = {"schema_version": 1, "collected_at_utc": datetime.now(timezone.utc).isoformat(),
              "packages": [], "unavailable_sections": []}
    for package in PACKAGES:
        try:
            report["packages"].append(parse_package(read(adb, prefix + ["dumpsys", "package", package]), package))
        except CollectionError:
            report["packages"].append({"package": package, "status": "adb_read_failed"})
    try:
        raw = read(adb, prefix + ["getprop", "ro.build.version.sdk"]).strip()
        report["android_sdk"] = int(raw) if re.fullmatch(r"\d{1,3}", raw) else None
    except CollectionError:
        report["android_sdk"] = None
    try:
        report["media_sessions"] = parse_media_sessions(read(adb, prefix + ["dumpsys", "media_session"]))
        if not report["media_sessions"]:
            report["unavailable_sections"].append("No recognized selected media sessions; inactive apps, permission limits, or an unsupported dump format are possible.")
    except CollectionError:
        report["media_sessions"] = []
        report["unavailable_sections"].append("Media session adb read failed.")
    try:
        logs = read(adb, ["-s", serial, "logcat", "-d", "-t", "1500", "-v", "brief"]
                    + [tag + ":V" for tag in LOG_TAGS] + ["*:S"])
        report["log_observations"] = summarize_logs(logs)
    except CollectionError:
        report["unavailable_sections"].append("Filtered logcat adb read failed.")
    report["car_capabilities"] = {"status": "Read selected fields manually from AAIdrive Car Info; these values are not logged by the app."}
    return report


def save_report(report, parent):
    parent.mkdir(parents=True, exist_ok=True)
    # mkdtemp creates a private directory on POSIX. Windows inherits the user's ACL.
    directory = Path(tempfile.mkdtemp(prefix="aaidrive-diagnostics-", dir=str(parent)))
    destination = directory / "report.json"
    descriptor = os.open(str(destination), os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(descriptor, "w", encoding="utf-8") as stream:
        json.dump(report, stream, indent=2)
        stream.write("\n")
    return destination


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", default="adb", help="Path to adb or adb.exe")
    parser.add_argument("--serial", help="Select an authorized device from adb devices")
    parser.add_argument("--output-dir", type=Path, default=Path.home(),
                        help="Parent for a new report folder (default: your home folder, outside this checkout)")
    args = parser.parse_args()
    try:
        serial = select_device(adb_read(args.adb, ["devices"]), args.serial)
        print("Reading selected package versions, media capabilities, and filtered connection events...")
        destination = save_report(collect(args.adb, serial), args.output_dir.expanduser().resolve())
    except CollectionError as error:
        print(str(error), file=sys.stderr)
        return 1
    except OSError:
        print("Could not save the report; choose a writable --output-dir.", file=sys.stderr)
        return 1
    print("Saved local report: " + str(destination))
    print("No raw logs or media metadata were saved. Nothing was uploaded.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
