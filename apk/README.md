# One APK to set everything up

**[Download AAIdrive-Setup.apk — 23.49 MB](https://github.com/MaxGiuP/AAIdrive/raw/refs/heads/main/apk/AAIdrive-Setup.apk)**

Install and open this single APK, then tap **Install everything**. It includes:

- AAIdrive `1.4.4-19-b72475e` with the media and native BMW navigation improvements.
- AAIdrive Projection `0.2.0-b72475e` for the experimental car display connection.
- The unchanged, upstream-signed Open Headunit `3.4.0-beta1` receiver.

The three components install offline from the setup APK. Android still requires its
install-source permission and install confirmations; they become separate installed
apps. Setup skips compatible installed versions, verifies APKs before handing them
to Android, handles cancellation, and preserves existing apps when signing keys conflict.
It never uninstalls another app or removes its settings.

Setup starts with native BMW Apps: AAIdrive connection and notification permissions,
your installed music/audio apps, and sharing Google Maps destinations to
**BMW/MINI Navigation**. It recognizes supported YouTube Music/ReVanced, Audible,
YouTube/ReVanced and Rumble variants, with Open or Enable actions. Missing apps
have official ReVanced instructions or Google Play links; downloading them needs
internet. Patched media APKs and Google's apps are not bundled.

Android Auto projection has its own optional, collapsed setup section for
Open Headunit, the headunit server and screen sharing. Ordinary music controls
and native BMW navigation do not require that projection setup.

Follow the [one-APK setup guide](../docs/one-apk-setup.md) and
[BMW Apps music/navigation guide](../docs/bmw-apps.md). No Google Maps API key is
required. Android Auto projection remains experimental, and actual BMW latency,
audio and controls need testing on the phone/car. Bluetooth can limit smoothness.

## Build and verification

Setup version: `0.2.0-b72475e` (version code `4`).
All three fork APKs were built from
[`b72475e1`](https://github.com/MaxGiuP/AAIdrive/commit/b72475e14b3e2f5507a14cce8d4019a30693f2df).
The build passed **608 main app + 58 projection + 36 installer unit tests** (702 total),
plus 6 packaging checks and 12 diagnostics checks. Signatures, alignment, APK/source identities, update paths
and the exact three APKs embedded in Setup were verified.
The [installer build record](setup-build-info.json) lists each bundled checksum and signer.

Current runtime check: Setup `0.2.0-b72475e` passed an Android 35 emulator upgrade
over the earlier fork. With networking disabled, it updated AAIdrive and Projection,
handled cancellation/resume, and skipped the unchanged Open Headunit. The new
native-app setup, installed Google Maps launch, optional projection expansion,
and recognition of installed components after restarting also passed. The
[runtime record](test-history/setup-emulator-b72475e.json) binds these checks to
this APK's hash. Actual BMW media controls and navigation were not tested by the emulator.

Historical runtime check: Setup `0.1.0-9184a3f` passed on an Android 35 x86_64
emulator with networking disabled: install-source permission, cancellation,
resume, skipping an already installed component, and all three component
installations. The [archived emulator test record](test-history/setup-emulator-9184a3f.json)
applies only to its recorded APK hash and source commit. Later APKs require their
own runtime check; physical Pixel/BMW projection performance remains untested.

Fork APKs use a local Android development signing certificate. The main APK uses
version code `10404008` and Projection `5`, with the same certificate as
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
