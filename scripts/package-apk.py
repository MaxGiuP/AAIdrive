#!/usr/bin/env python3
"""Verify and package the tested component and setup APKs without including private signing material."""

import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile
import xml.etree.ElementTree as ET
import zipfile


BUILDS = (
    {
        "name": "main",
        "outputs": "app/build/outputs/apk/nomapNonalyticsFull/optimized",
        "reports": "app/build/test-results/testNomapNonalyticsFullDebugUnitTest",
        "test_task": ":app:testNomapNonalyticsFullDebugUnitTest",
        "variant": "nomapNonalyticsFullOptimized",
        "package": "me.hufman.androidautoidrive",
        "apk": "AAIdrive-MaxGiuP.apk",
        "info": "build-info.json",
    },
    {
        "name": "projection",
        "outputs": "screen-mirror/build/outputs/apk/release",
        "reports": "screen-mirror/build/test-results/testDebugUnitTest",
        "test_task": ":screen-mirror:testDebugUnitTest",
        "variant": "release",
        "package": "io.github.maxgiup.aaidrive.projection",
        "apk": "AAIdrive-Projection.apk",
        "info": "projection-build-info.json",
    },
    {
        "name": "setup",
        "outputs": "installer/build/outputs/apk/release",
        "reports": "installer/build/test-results/testDebugUnitTest",
        "test_task": ":installer:testDebugUnitTest",
        "variant": "release",
        "package": "io.github.maxgiup.aaidrive.setup",
        "apk": "AAIdrive-Setup.apk",
        "info": "setup-build-info.json",
    },
)


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
        raise RuntimeError("Increment this APK's version code before publishing changed source")


def verify_test_outputs(repo, build, stamp, outputs):
    reports = sorted((repo / build["reports"]).glob("TEST-*.xml"))
    relative_reports = [str(path.relative_to(repo)) for path in reports]
    stamped_reports = stamp.get("test_reports", {}).get(build["name"], [])
    if not reports or relative_reports != sorted(stamped_reports):
        raise RuntimeError(f"{build['name']}: test reports do not match the successful build stamp")
    output_hashes = stamp.get("outputs_sha256", {})
    for output in [*outputs, *reports]:
        expected = output_hashes.get(str(output.relative_to(repo)))
        if expected != hashlib.sha256(output.read_bytes()).hexdigest():
            raise RuntimeError("Build output changed after Gradle completed; run scripts/build-apk.sh")
    suites = [ET.parse(path).getroot() for path in reports]
    tests = sum(int(suite.get("tests", 0)) for suite in suites)
    failures = sum(int(suite.get("failures", 0)) + int(suite.get("errors", 0)) for suite in suites)
    skipped = sum(int(suite.get("skipped", 0)) for suite in suites)
    if tests <= skipped or failures:
        raise RuntimeError(f"{build['name']}: passing unit test reports are required before packaging")
    return {"total": tests, "failed": failures, "skipped": skipped}


def prepare_artifact(repo, build_tools, source_commit, stamp, build):
    outputs = repo / build["outputs"]
    metadata = json.loads((outputs / "output-metadata.json").read_text())
    if metadata["variantName"] != build["variant"] or len(metadata["elements"]) != 1:
        raise RuntimeError(f"Expected one {build['variant']} APK for {build['name']}")
    element = metadata["elements"][0]
    if Path(element["outputFile"]).name != element["outputFile"]:
        raise RuntimeError("Expected an APK filename within its build output directory")
    source_apk = outputs / element["outputFile"]
    tests = verify_test_outputs(repo, build, stamp, [outputs / "output-metadata.json", source_apk])
    signer = verified_signer(build_tools, source_apk)
    identity = apk_identity(build_tools, source_apk)
    expected_identity = metadata["applicationId"], int(element["versionCode"]), element["versionName"]
    if identity != expected_identity or identity[0] != build["package"]:
        raise RuntimeError("APK manifest package/version does not match the expected build metadata")
    hashes = re.findall(r"(?:^|-)([0-9a-f]{7,40})(?=-|$)", identity[2])
    if not any(source_commit.startswith(commit) for commit in hashes):
        raise RuntimeError("APK version does not identify the current source commit; rebuild before packaging")

    run(str(build_tools / "zipalign"), "-c", "-p", "4", str(source_apk))
    manifest = run(str(build_tools / "aapt"), "dump", "xmltree", str(source_apk), "AndroidManifest.xml")
    if re.search(r"android:debuggable[^\n]*0xffffffff", manifest):
        raise RuntimeError("Published APKs must not be debuggable")
    if build["name"] == "main":
        key_entry = manifest.split('"com.spotify.music.API_KEY"', 1)[1].splitlines()[1]
        if '"unset"' not in key_entry:
            raise RuntimeError("Refusing to package a public APK with an unexpected Spotify API key")

    artifact_dir = repo / "apk"
    target = artifact_dir / build["apk"]
    if target.exists():
        previous_info_file = artifact_dir / build["info"]
        previous_info = json.loads(previous_info_file.read_text()) if previous_info_file.exists() else {}
        check_update(apk_identity(build_tools, target), verified_signer(build_tools, target),
                     identity, signer, source_commit, previous_info)
    digest = stamp["outputs_sha256"][str(source_apk.relative_to(repo))]
    info = {
        "file": target.name,
        "application_id": metadata["applicationId"],
        "variant": metadata["variantName"],
        "version_name": element["versionName"],
        "version_code": element["versionCode"],
        "source_commit": source_commit,
        "size_bytes": source_apk.stat().st_size,
        "sha256": digest,
        "signing": "Local Android development certificate; APK signature and alignment verified",
        "signer_certificate_sha256": signer,
        "debuggable": False,
        "unit_tests": tests,
        "unit_test_task": build["test_task"],
        "phone_and_car_tested": False,
    }
    if build["name"] == "setup":
        info["bundled_apps"] = verify_setup_payload(repo, source_apk)
    return source_apk, build, info


def verify_setup_payload(repo, setup_apk):
    expected = {}
    for build in BUILDS[:2]:
        directory = repo / build["outputs"]
        metadata = json.loads((directory / "output-metadata.json").read_text())
        path = directory / metadata["elements"][0]["outputFile"]
        expected["apps/" + build["apk"]] = hashlib.sha256(path.read_bytes()).hexdigest()
    upstream = json.loads((repo / "third-party/open-headunit/manifest.json").read_text())["apk"]
    expected["apps/Open-Headunit-v3.4.0-beta1.apk"] = upstream["sha256"]
    with zipfile.ZipFile(setup_apk) as archive:
        catalog = json.loads(archive.read("assets/bundled-apps.json"))
        if len(catalog) != 3 or {app["file"] for app in catalog} != set(expected):
            raise RuntimeError("Setup must bundle exactly the three expected component APKs")
        for app in catalog:
            payload = archive.read("assets/" + app["file"])
            digest = hashlib.sha256(payload).hexdigest()
            if digest != expected[app["file"]] or digest != app["sha256"]:
                raise RuntimeError("Setup includes a stale or altered component APK")
        if not archive.read("assets/LICENSES.txt") or not archive.read("assets/SETUP.md"):
            raise RuntimeError("Setup must include offline help and source/license notices")
        return catalog


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

    sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    if not sdk:
        local = (repo / "local.properties").read_text()
        match = re.search(r"^sdk\.dir=(.+)$", local, re.MULTILINE)
        sdk = match.group(1).strip() if match else None
    if not sdk:
        raise RuntimeError("Set ANDROID_HOME to the installed Android SDK")
    versions = [path for path in (Path(sdk) / "build-tools").iterdir() if re.fullmatch(r"\d+\.\d+\.\d+", path.name)]
    build_tools = max(versions, key=lambda path: tuple(map(int, path.name.split("."))))

    # Validate all artifacts and update paths before replacing either published APK.
    artifacts = [prepare_artifact(repo, build_tools, source_commit, stamp, build) for build in BUILDS]
    artifact_dir = repo / "apk"
    artifact_dir.mkdir(exist_ok=True)
    with tempfile.TemporaryDirectory(prefix=".package-", dir=artifact_dir) as staging:
        staging = Path(staging)
        for source_apk, build, info in artifacts:
            staged = staging / build["apk"]
            shutil.copy2(source_apk, staged)
            if hashlib.sha256(staged.read_bytes()).hexdigest() != info["sha256"]:
                raise RuntimeError("APK changed during packaging; run scripts/build-apk.sh")
            (staging / (build["apk"] + ".sha256")).write_text(f"{info['sha256']}  {build['apk']}\n")
            (staging / build["info"]).write_text(json.dumps(info, indent=2) + "\n")
        for _, build, info in artifacts:
            for filename in (build["apk"], build["apk"] + ".sha256", build["info"]):
                os.replace(staging / filename, artifact_dir / filename)
            print(f"Packaged {build['apk']}: {info['size_bytes']:,} bytes, {info['unit_tests']['total']} tests, {info['sha256']}")


if __name__ == "__main__":
    main()
