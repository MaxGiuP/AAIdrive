#!/usr/bin/env bash
set -euo pipefail

aaidrive_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$aaidrive_root"

# Preserve build provenance while allowing a previously packaged APK to be replaced.
if [[ -n "$(git status --porcelain --untracked-files=normal -- . ':(exclude)apk')" ]]; then
    echo 'Commit source changes before packaging an APK; changes under apk/ are allowed.' >&2
    exit 1
fi
aaidrive_commit="$(git rev-parse HEAD)"
mkdir -p .gradle
aaidrive_stamp="$(mktemp .gradle/apk-build-XXXXXXXX.json)"
trap 'rm -f -- "$aaidrive_stamp"' EXIT
python3 -c 'import json, pathlib, sys, time; pathlib.Path(sys.argv[2]).write_text(json.dumps({"source_commit": sys.argv[1], "started_ns": time.time_ns()}))' "$aaidrive_commit" "$aaidrive_stamp"

AndroidAutoIdrive_SpotifyApiKey=unset ./gradlew \
    :app:testNomapNonalyticsFullDebugUnitTest \
    :app:assembleNomapNonalyticsFullOptimized \
    :screen-mirror:testDebugUnitTest \
    :screen-mirror:assembleRelease \
    :installer:testDebugUnitTest \
    :installer:assembleRelease \
    -PAndroidAutoIdrive_SpotifyApiKey=unset --max-workers=4 --console=plain

# Gradle validates test inputs even when their outputs are UP-TO-DATE. Bind those exact
# reports and the APK to this successful invocation before handing them to the packager.
python3 - "$aaidrive_stamp" <<'PYTHON'
import hashlib
import json
from pathlib import Path
import sys
import time

stamp_path = Path(sys.argv[1])
stamp = json.loads(stamp_path.read_text())
builds = {
    "main": ("app/build/outputs/apk/nomapNonalyticsFull/optimized",
             "app/build/test-results/testNomapNonalyticsFullDebugUnitTest"),
    "projection": ("screen-mirror/build/outputs/apk/release",
                   "screen-mirror/build/test-results/testDebugUnitTest"),
    "setup": ("installer/build/outputs/apk/release",
              "installer/build/test-results/testDebugUnitTest"),
}
files = []
stamp["test_reports"] = {}
for name, (outputs_dir, reports_dir) in builds.items():
    outputs = Path(outputs_dir)
    metadata_path = outputs / "output-metadata.json"
    metadata = json.loads(metadata_path.read_text())
    reports = sorted(Path(reports_dir).glob("TEST-*.xml"))
    files.extend([metadata_path, outputs / metadata["elements"][0]["outputFile"], *reports])
    stamp["test_reports"][name] = [str(path) for path in reports]
stamp["outputs_sha256"] = {str(path): hashlib.sha256(path.read_bytes()).hexdigest() for path in files}
stamp["completed_ns"] = time.time_ns()
stamp_path.write_text(json.dumps(stamp))
PYTHON
python3 scripts/package-apk.py "$aaidrive_commit" "$aaidrive_stamp"
