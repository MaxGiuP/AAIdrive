#!/usr/bin/env python3
"""Focused regression checks for publication provenance; no SDK or signing key required."""

import hashlib
import importlib.util
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
            for build, count in zip(packager.BUILDS, (100, 5)):
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


if __name__ == "__main__":
    unittest.main()
