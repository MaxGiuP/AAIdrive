#!/usr/bin/env python3
"""Verify and package the tested, optimized APK without including private signing material."""

import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import xml.etree.ElementTree as ET


def run(*args):
    return subprocess.check_output(args, text=True, stderr=subprocess.STDOUT)


def apk_identity(build_tools, apk):
    badging = run(str(build_tools / "aapt"), "dump", "badging", str(apk))
    package = re.search(r"^package: name='([^']+)' versionCode='([0-9]+)' versionName='([^']*)'", badging, re.MULTILINE)
    if not package:
        raise RuntimeError("Could not read APK package/version from its manifest")
    return package.group(1), int(package.group(2)), package.group(3)


def verified_signer(build_tools, apk):
    signature = run(str(build_tools / "apksigner"), "verify", "--verbose", "--print-certs", str(apk))
    certificates = re.findall(r"Signer #[0-9]+ certificate SHA-256 digest: ([0-9a-f]+)", signature)
    if len(certificates) != 1 or "Verified using v2 scheme (APK Signature Scheme v2): true" not in signature:
        raise RuntimeError("Expected an APK with one verified v2 signer")
    return certificates[0]


def check_update(previous_identity, previous_signer, identity, signer, source_commit, previous_info):
    if previous_identity[0] != identity[0] or previous_signer != signer:
        raise RuntimeError("APK package or signing certificate differs from the published APK; it cannot install as an update")
    if identity[1] < previous_identity[1]:
        raise RuntimeError("APK version code would downgrade the published APK")
    if identity[1] == previous_identity[1] and (
        previous_info.get("source_commit") != source_commit
        or previous_info.get("version_code") != identity[1]
    ):
        raise RuntimeError("Increment AAIdrive_ForkVersionCode before publishing changed source")


def main():
    repo = Path(__file__).resolve().parent.parent
    os.chdir(repo)
    source_commit = run("git", "rev-parse", "HEAD").strip()
    if len(sys.argv) != 3 or sys.argv[1] != source_commit:
        raise RuntimeError("Run scripts/build-apk.sh to build and package the current source commit")
    if run("git", "status", "--porcelain", "--untracked-files=normal", "--", ".", ":(exclude)apk").strip():
        raise RuntimeError("Source changed during the build; commit it and rebuild")

    stamp = json.loads(Path(sys.argv[2]).read_text())
    started_ns = int(stamp.get("started_ns", 0))
    completed_ns = int(stamp.get("completed_ns", 0))
    if stamp.get("source_commit") != source_commit or not 0 < started_ns <= completed_ns:
        raise RuntimeError("A successful build stamp for this source commit is required; run scripts/build-apk.sh")

    outputs = repo / "app/build/outputs/apk/nomapNonalyticsFull/optimized"
    metadata = json.loads((outputs / "output-metadata.json").read_text())
    if metadata["variantName"] != "nomapNonalyticsFullOptimized" or len(metadata["elements"]) != 1:
        raise RuntimeError("Expected one optimized APK")
    element = metadata["elements"][0]
    source_apk = outputs / element["outputFile"]

    reports = list((repo / "app/build/test-results/testNomapNonalyticsFullDebugUnitTest").glob("TEST-*.xml"))
    relative_reports = sorted(str(path.relative_to(repo)) for path in reports)
    if not reports or relative_reports != sorted(stamp.get("test_reports", [])):
        raise RuntimeError("Test reports do not match the successful build stamp")
    output_hashes = stamp.get("outputs_sha256", {})
    for output in [outputs / "output-metadata.json", source_apk, *reports]:
        expected = output_hashes.get(str(output.relative_to(repo)))
        if expected != hashlib.sha256(output.read_bytes()).hexdigest():
            raise RuntimeError("Build output changed after Gradle completed; run scripts/build-apk.sh")
    suites = [ET.parse(path).getroot() for path in reports]
    tests = sum(int(suite.get("tests", 0)) for suite in suites)
    failures = sum(int(suite.get("failures", 0)) + int(suite.get("errors", 0)) for suite in suites)
    skipped = sum(int(suite.get("skipped", 0)) for suite in suites)
    if tests <= skipped or failures:
        raise RuntimeError("Passing unit test reports are required before packaging")

    sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    if not sdk:
        local = (repo / "local.properties").read_text()
        match = re.search(r"^sdk\.dir=(.+)$", local, re.MULTILINE)
        sdk = match.group(1).strip() if match else None
    if not sdk:
        raise RuntimeError("Set ANDROID_HOME to the installed Android SDK")
    versions = [path for path in (Path(sdk) / "build-tools").iterdir() if re.fullmatch(r"\d+\.\d+\.\d+", path.name)]
    build_tools = max(versions, key=lambda path: tuple(map(int, path.name.split("."))))
    signer = verified_signer(build_tools, source_apk)
    identity = apk_identity(build_tools, source_apk)
    expected_identity = metadata["applicationId"], int(element["versionCode"]), element["versionName"]
    if identity != expected_identity or identity[0] != "me.hufman.androidautoidrive":
        raise RuntimeError("APK manifest package/version does not match optimized build metadata")
    hashes = re.findall(r"(?:^|-)([0-9a-f]{7,40})(?=-|$)", identity[2])
    if not any(source_commit.startswith(commit) for commit in hashes):
        raise RuntimeError("APK version does not identify the current source commit; rebuild before packaging")

    run(str(build_tools / "zipalign"), "-c", "-p", "4", str(source_apk))
    manifest = run(str(build_tools / "aapt"), "dump", "xmltree", str(source_apk), "AndroidManifest.xml")
    if re.search(r"android:debuggable[^\n]*0xffffffff", manifest):
        raise RuntimeError("The optimized APK must not be debuggable")
    key_entry = manifest.split('"com.spotify.music.API_KEY"', 1)[1].splitlines()[1]
    if '"unset"' not in key_entry:
        raise RuntimeError("Refusing to package a public APK with an unexpected Spotify API key")

    artifact_dir = repo / "apk"
    artifact_dir.mkdir(exist_ok=True)
    target = artifact_dir / "AAIdrive-MaxGiuP.apk"
    if target.exists():
        previous_info_file = artifact_dir / "build-info.json"
        previous_info = json.loads(previous_info_file.read_text()) if previous_info_file.exists() else {}
        check_update(apk_identity(build_tools, target), verified_signer(build_tools, target),
                     identity, signer, source_commit, previous_info)
    shutil.copy2(source_apk, target)
    digest = hashlib.sha256(target.read_bytes()).hexdigest()
    (artifact_dir / (target.name + ".sha256")).write_text(f"{digest}  {target.name}\n")
    info = {
        "file": target.name,
        "application_id": metadata["applicationId"],
        "variant": metadata["variantName"],
        "version_name": element["versionName"],
        "version_code": element["versionCode"],
        "source_commit": source_commit,
        "size_bytes": target.stat().st_size,
        "sha256": digest,
        "signing": "Local Android development certificate; APK signature and alignment verified",
        "signer_certificate_sha256": signer,
        "debuggable": False,
        "unit_tests": {"total": tests, "failed": failures, "skipped": skipped},
        "phone_and_car_tested": False,
    }
    (artifact_dir / "build-info.json").write_text(json.dumps(info, indent=2) + "\n")
    print(f"Packaged {target.name}: {target.stat().st_size:,} bytes, {tests} tests, {digest}")


if __name__ == "__main__":
    main()
