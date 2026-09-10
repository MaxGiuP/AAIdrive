#!/usr/bin/env python3
"""Create the offline installer payload from this build's verified component APKs."""
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile

ROOT = Path(__file__).resolve().parent.parent
spec = importlib.util.spec_from_file_location('apk_packaging', ROOT / 'scripts/package-apk.py')
packaging = importlib.util.module_from_spec(spec)
spec.loader.exec_module(packaging)


def build_tools():
    sdk = os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT')
    if not sdk:
        match = re.search(r'^sdk\.dir=(.+)$', (ROOT / 'local.properties').read_text(), re.MULTILINE)
        sdk = match.group(1).strip() if match else None
    if not sdk:
        raise RuntimeError('An Android SDK is required to verify bundled APKs')
    versions = [p for p in (Path(sdk) / 'build-tools').iterdir() if re.fullmatch(r'\d+\.\d+\.\d+', p.name)]
    return max(versions, key=lambda p: tuple(map(int, p.name.split('.'))))


def main():
    output = Path(sys.argv[1]).resolve()
    if not output.is_relative_to(ROOT / 'installer/build'):
        raise RuntimeError('Generated assets must be under installer/build')
    tools = build_tools()
    source_commit = packaging.run('git', '-C', str(ROOT), 'rev-parse', '--short=7', 'HEAD').strip()
    upstream = json.loads((ROOT / 'third-party/open-headunit/manifest.json').read_text())['apk']
    components = []
    for build, label in zip(packaging.BUILDS[:2], ['AAIdrive', 'AAIdrive Projection']):
        directory = ROOT / build['outputs']
        metadata = json.loads((directory / 'output-metadata.json').read_text())
        if len(metadata['elements']) != 1:
            raise RuntimeError('Expected one standalone APK per component')
        filename = metadata['elements'][0]['outputFile']
        if Path(filename).name != filename:
            raise RuntimeError('Invalid APK output filename')
        components.append((build['name'], label, directory / filename, build['apk'], build['package']))
    components.append(('headunit', 'Open Headunit', ROOT / 'apk/Open-Headunit-v3.4.0-beta1.apk',
                       'Open-Headunit-v3.4.0-beta1.apk', 'com.andrerinas.headunitrevived'))
    output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='bundle-', dir=output.parent) as temporary:
        staged = Path(temporary)
        (staged / 'apps').mkdir()
        catalog = []
        for app_id, name, apk, filename, expected_package in components:
            identity = packaging.apk_identity(tools, apk)
            signer = packaging.verified_signer(tools, apk)
            digest = hashlib.sha256(apk.read_bytes()).hexdigest()
            if identity[0] != expected_package:
                raise RuntimeError('Unexpected component package')
            if app_id == 'headunit':
                if digest != upstream['sha256'] or signer != upstream['signer_sha256'] or identity != (upstream['package_name'], upstream['version_code'], upstream['version_name']):
                    raise RuntimeError('Open Headunit differs from the pinned upstream release')
            elif source_commit not in identity[2]:
                raise RuntimeError('Rebuild the fork APKs from the current source before bundling')
            badging = packaging.run(str(tools / 'aapt'), 'dump', 'badging', str(apk))
            minimum = re.search(r"^sdkVersion:'(\d+)'", badging, re.MULTILINE)
            if not minimum:
                raise RuntimeError('Missing minimum Android version')
            asset = 'apps/' + filename
            shutil.copy2(apk, staged / asset)
            catalog.append(dict(id=app_id, name=name, file=asset, packageName=identity[0],
                                versionCode=identity[1], versionName=identity[2], sha256=digest,
                                signerSha256=signer, minSdk=int(minimum.group(1))))
        (staged / 'bundled-apps.json').write_text(json.dumps(catalog, indent=2) + '\n')
        shutil.copyfile(ROOT / 'docs/one-apk-setup.md', staged / 'SETUP.md')
        notices = ['Source and licenses for the bundled apps\n',
                   'AAIdrive and Projection source: https://github.com/MaxGiuP/AAIdrive\n',
                   (ROOT / 'LICENSE').read_text(),
                   (ROOT / 'screen-mirror/NOTICE.md').read_text(),
                   (ROOT / 'screen-mirror/LICENSE').read_text(),
                   'Open Headunit source and notices: https://github.com/MaxGiuP/AAIdrive/tree/main/third-party/open-headunit\n',
                   (ROOT / 'third-party/open-headunit/README.md').read_text(),
                   (ROOT / 'third-party/open-headunit/LICENSE').read_text()]
        for notice in ['COPYRIGHT.txt', 'LICENSE-FFmpeg-LGPL-2.1.txt', 'LICENSE-libusb-LGPL-2.1.txt', 'BUILDING.md']:
            notices.append((ROOT / 'third-party/open-headunit' / notice).read_text())
        (staged / 'LICENSES.txt').write_text('\n\n'.join(notices))
        if output.exists():
            shutil.rmtree(output)
        shutil.copytree(staged, output)
    print('Bundled verified AAIdrive, Projection, and Open Headunit APKs for offline setup')


if __name__ == '__main__':
    main()
