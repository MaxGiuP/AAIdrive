# One APK to set everything up

**[Download AAIdrive-Setup.apk — 23.46 MB](https://github.com/MaxGiuP/AAIdrive/raw/refs/heads/main/apk/AAIdrive-Setup.apk)**

Install and open this single APK, then tap **Install everything**. It includes:

- AAIdrive `1.4.4-14-ac4a3dd-dirty` with the media and native BMW navigation improvements.
- AAIdrive Projection `0.2.0-ac4a3dd` for the experimental car display connection.
- The unchanged, upstream-signed Open Headunit `3.4.0-beta1` receiver.

The three components install offline from the setup APK. Android still requires its
install-source permission and install confirmations; they become separate installed
apps. Setup skips compatible installed versions, verifies APKs before handing them
to Android, handles cancellation, and preserves existing apps when signing keys conflict.
It never uninstalls another app or removes its settings.

Setup then guides AAIdrive permissions, Open Headunit configuration, Android Auto's
headunit server and screen sharing. Google's Android Auto and Google Maps apps are
usually already on the phone. They are not repackaged; setup links to Google Play
if either is missing. Installing those Google apps needs internet.

Follow the [one-APK setup guide](../docs/one-apk-setup.md). No Google Maps API key is
required. Android Auto projection remains experimental, and actual BMW latency,
audio and controls need testing on the phone/car. Bluetooth can limit smoothness.

## Build and verification

Setup version: `0.1.0-ac4a3dd` (version code `2`).
All three fork APKs were built from
[`ac4a3ddd`](https://github.com/MaxGiuP/AAIdrive/commit/ac4a3ddd1017b3f951e5371835060cf7593769a9).
The build passed **597 main app + 39 projection + 25 installer unit tests**,
plus 6 packaging checks and 7 diagnostics checks. Signatures, alignment, APK/source identities, update paths
and the exact three APKs embedded in Setup were verified.
The [installer build record](setup-build-info.json) lists each bundled checksum and signer.

Historical runtime check: Setup `0.1.0-ac4a3dd` passed on an Android 35 x86_64
emulator with networking disabled: install-source permission, cancellation,
resume, skipping an already installed component, and all three component
installations. The [archived emulator test record](test-history/setup-emulator-9184a3f.json)
applies only to its recorded APK hash and source commit. Later APKs require their
own runtime check; physical Pixel/BMW projection performance remains untested.

Fork APKs use a local Android development signing certificate. The main APK uses
version code `10404006` and Projection `3`, with the same certificate as
previous fork releases. Open Headunit keeps its original upstream certificate.
Private signing keys are excluded from this repository. Official AAIdrive builds
with a different signer cannot be updated by this fork; setup reports the conflict
and leaves that installation intact.

The main APK uses `nomapNonalyticsFullOptimized`: R8 optimization, no embedded maps,
no analytics and no optional Spotify API key. See [media setup](../docs/maxgiup-media.md)
and [navigation options](../docs/maxgiup-navigation-options.md).

## Individual APKs and source

These downloads are optional when using the Setup APK:

| Component | Download | Build record |
| --- | --- | --- |
| AAIdrive | [APK](AAIdrive-MaxGiuP.apk) | [Metadata](build-info.json) |
| Projection | [APK](AAIdrive-Projection.apk) | [Metadata](projection-build-info.json) |
| Open Headunit | [APK](Open-Headunit-v3.4.0-beta1.apk) | [Upstream identity and sources](../third-party/open-headunit/README.md) |

Open Headunit's license, source snapshots, FFmpeg/libusb sources and build notes are
available at [third-party/open-headunit](../third-party/open-headunit/README.md).
Source and license notices are also included in Setup's offline license screen.

## Rebuild

Requirements: JDK 17+, Python 3, Android SDK platform 35, the Gradle wrapper, the
checked-in Open Headunit artifacts and [upstream resources](../external/README.md).
From a clean source commit:

```sh
./scripts/build-apk.sh
```

This builds and tests all three fork APKs, validates the nested component payloads,
and packages them. Increment each fork app's version code before publishing changed
source: `AAIdrive_ForkVersionCode` in `gradle.properties`, Projection's code in
`screen-mirror/build.gradle`, and Setup's code in `installer/build.gradle`.
Compatible updates require the original signing key.

Check the single download from this directory:

```sh
sha256sum -c AAIdrive-Setup.apk.sha256
```
