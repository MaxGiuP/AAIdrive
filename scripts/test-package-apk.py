#!/usr/bin/env python3
"""Focused regression checks for publication provenance; no SDK or signing key required."""

import hashlib
import importlib.util
import json
import zipfile
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location("package_apk", Path(__file__).with_name("package-apk.py"))
packager = importlib.util.module_from_spec(spec)
spec.loader.exec_module(packager)


class PackagingTest(unittest.TestCase):
    def test_each_apk_uses_its_own_reports_and_rejects_changed_outputs(self):
        with tempfile.TemporaryDirectory() as directory:
            repo = Path(directory)
            stamp = {"test_reports": {}, "outputs_sha256": {}}
            for build, count in zip(packager.BUILDS, (100, 5, 8)):
                reports = repo / build["reports"]
                reports.mkdir(parents=True)
                report = reports / "TEST-suite.xml"
                report.write_text(f'<testsuite tests="{count}" failures="0" errors="0" skipped="0"/>')
                path = str(report.relative_to(repo))
                stamp["test_reports"][build["name"]] = [path]
                stamp["outputs_sha256"][path] = hashlib.sha256(report.read_bytes()).hexdigest()
                result = packager.verify_test_outputs(repo, build, stamp, [])
                self.assertEqual(count, result["total"])

            projection = packager.BUILDS[1]
            own_reports = stamp["test_reports"]["projection"]
            stamp["test_reports"]["projection"] = stamp["test_reports"]["main"]
            with self.assertRaisesRegex(RuntimeError, "test reports"):
                packager.verify_test_outputs(repo, projection, stamp, [])
            stamp["test_reports"]["projection"] = own_reports
            (repo / own_reports[0]).write_text('<testsuite tests="500" failures="0"/>')
            with self.assertRaisesRegex(RuntimeError, "output changed"):
                packager.verify_test_outputs(repo, projection, stamp, [])

    def test_failed_or_entirely_skipped_suite_cannot_be_published(self):
        for tests, failures, skipped in ((5, 1, 0), (5, 0, 5), (0, 0, 0)):
            with self.subTest(tests=tests, failures=failures, skipped=skipped), tempfile.TemporaryDirectory() as directory:
                repo = Path(directory)
                build = packager.BUILDS[1]
                report = repo / build["reports"] / "TEST-suite.xml"
                report.parent.mkdir(parents=True)
                report.write_text(f'<testsuite tests="{tests}" failures="{failures}" skipped="{skipped}"/>')
                path = str(report.relative_to(repo))
                stamp = {
                    "test_reports": {"projection": [path]},
                    "outputs_sha256": {path: hashlib.sha256(report.read_bytes()).hexdigest()},
                }
                with self.assertRaisesRegex(RuntimeError, "passing unit test"):
                    packager.verify_test_outputs(repo, build, stamp, [])

    def test_update_requires_same_signer_and_new_code_for_new_source(self):
        previous = ("io.github.maxgiup.aaidrive.projection", 1, "0.1.0-aaaaaaa")
        same = {"source_commit": "aaaaaaa", "version_code": 1}
        packager.check_update(previous, "signer", previous, "signer", "aaaaaaa", same)
        newer = (previous[0], 2, "0.1.0-bbbbbbb")
        packager.check_update(previous, "signer", newer, "signer", "bbbbbbb", same)
        for identity, signer, commit in (
            (previous, "different", "aaaaaaa"),
            (previous, "signer", "bbbbbbb"),
            ((previous[0], 0, previous[2]), "signer", "aaaaaaa"),
            (("different.package", 2, newer[2]), "signer", "bbbbbbb"),
        ):
            with self.subTest(identity=identity, signer=signer, commit=commit):
                with self.assertRaises(RuntimeError):
                    packager.check_update(previous, "signer", identity, signer, commit, same)


class SetupPayloadTest(unittest.TestCase):
    def fixture(self, repo, altered=None, missing=False):
        catalog = []
        payloads = {}
        for index, build in enumerate(packager.BUILDS[:2]):
            directory = repo / build["outputs"]
            directory.mkdir(parents=True)
            payload = f"built-apk-{index}".encode()
            (directory / "built.apk").write_bytes(payload)
            (directory / "output-metadata.json").write_text(json.dumps({"elements": [{"outputFile": "built.apk"}]}))
            payloads["apps/" + build["apk"]] = payload
        payloads["apps/Open-Headunit-v3.4.0-beta1.apk"] = b"pinned-upstream-apk"
        notice = repo / "third-party/open-headunit/manifest.json"
        notice.parent.mkdir(parents=True)
        notice.write_text(json.dumps({"apk": {"sha256": hashlib.sha256(b"pinned-upstream-apk").hexdigest()}}))
        for filename, payload in payloads.items():
            catalog.append({"file": filename, "sha256": hashlib.sha256(payload).hexdigest()})
        target = repo / "setup.apk"
        with zipfile.ZipFile(target, "w") as archive:
            archive.writestr("assets/bundled-apps.json", json.dumps(catalog[:-1] if missing else catalog))
            for filename, payload in payloads.items():
                archive.writestr("assets/" + filename, b"stale-or-tampered" if filename == altered else payload)
            archive.writestr("assets/LICENSES.txt", "source and licenses")
            archive.writestr("assets/SETUP.md", "offline setup")
        return target

    def test_setup_contains_exact_current_and_pinned_apks(self):
        with tempfile.TemporaryDirectory() as directory:
            repo = Path(directory)
            target = self.fixture(repo)
            self.assertEqual(3, len(packager.verify_setup_payload(repo, target)))

    def test_stale_or_tampered_component_is_rejected(self):
        for filename in ("apps/AAIdrive-MaxGiuP.apk", "apps/AAIdrive-Projection.apk", "apps/Open-Headunit-v3.4.0-beta1.apk"):
            with self.subTest(filename=filename), tempfile.TemporaryDirectory() as directory:
                repo = Path(directory)
                with self.assertRaisesRegex(RuntimeError, "stale or altered"):
                    packager.verify_setup_payload(repo, self.fixture(repo, altered=filename))

    def test_missing_component_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            repo = Path(directory)
            with self.assertRaisesRegex(RuntimeError, "exactly the three"):
                packager.verify_setup_payload(repo, self.fixture(repo, missing=True))


if __name__ == "__main__":
    unittest.main()
