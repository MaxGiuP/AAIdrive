# Installable APKs

| App | Version | Size | Download |
| --- | --- | --- | --- |
| AAIdrive | `1.4.4-9-943fab1` | 8.06 MB | [Download APK](https://github.com/MaxGiuP/AAIdrive/raw/refs/heads/main/apk/AAIdrive-MaxGiuP.apk) |
| Projection companion (experimental) | `0.1.0-943fab1` | 2.54 MB | [Download APK](https://github.com/MaxGiuP/AAIdrive/raw/refs/heads/main/apk/AAIdrive-Projection.apk) |

Both APKs were built from source commit
[`943fab15`](https://github.com/MaxGiuP/AAIdrive/commit/943fab1512235cae0a26b8b5831c976da40b3bb1).
The final build passed **586 AAIdrive tests and 39 projection tests**.
APK signatures, alignment, source versions and the main APK update path were verified.
Phone and in-car testing have not been performed.

## Android Auto on the BMW screen

Install both APKs above, plus the separately distributed
[Open Headunit v3.4.0-beta1](https://github.com/andreknieriem/open-headunit/releases/tag/v.3.4.0-beta1).
Follow the [projection setup guide](../docs/android-auto-projection.md): enable Android
Auto developer settings, start its headunit server, then use **Start Android Auto on
BMW** in AAIdrive Projection and grant screen-capture permission.

The phone runs the Android Auto receiver and sends its picture through AAIdrive's
BMW Connected Apps connection. The companion forwards iDrive navigation controls.
No Google Maps API key is required. This remains experimental: the JPEG connection
limits frame rate, especially over Bluetooth, and actual Pixel/BMW operation still
needs testing. Keep the phone unlocked with Open Headunit visible.
This does not add native Android Auto or translate CarPlay.

Capture reuses image memory, suppresses identical frames, releases capture buffers
before sending, and bounds frame scheduling and pending navigation commands.
Capture stops on consent revocation, explicit Stop, or car disconnection.

## Main app features and signing

AAIdrive uses the `nomapNonalyticsFullOptimized` variant: R8 enabled, debugging
disabled, no embedded maps or analytics. It retains the ReVanced music default,
selectable exposed playlists, expanded media-app support and native BMW destination
handoff. See [media setup](../docs/maxgiup-media.md),
[performance improvements](../docs/maxgiup-improvements.md), and
[navigation options](../docs/maxgiup-navigation-options.md).
Spotify's optional proprietary API integration is disabled because no API key is configured.

The main APK uses version code `10404004` and the same local Android
development certificate as earlier fork builds, allowing updates over those
installations. Projection has a separate application ID and version code
`1`. Both are signed, non-debuggable custom builds; private signing
material stays outside this repository. An official AAIdrive installation signed
with a different certificate cannot be updated by this fork; preserve settings
before uninstalling an incompatible build.

Exact source, test, certificate and checksum information is recorded separately in
[build-info.json](build-info.json) and [projection-build-info.json](projection-build-info.json).

## Rebuild and verify

Requirements: JDK 17+, Python 3, Android SDK platform 35, the Gradle wrapper, and the
upstream resources described in [external/README.md](../external/README.md).
From a clean source commit, run:

```sh
./scripts/build-apk.sh
```

The script builds, tests and verifies both apps before packaging them. Increment
`AAIdrive_ForkVersionCode` in `gradle.properties` and Projection's `versionCode` in
`screen-mirror/build.gradle` before publishing changed source. Another machine needs
the original signing key to produce compatible updates.

Check downloads against the included checksums from this directory:

```sh
sha256sum -c AAIdrive-MaxGiuP.apk.sha256 AAIdrive-Projection.apk.sha256
```
