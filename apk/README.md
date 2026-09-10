# Installable APK

[Download AAIdrive-MaxGiuP.apk](https://github.com/MaxGiuP/AAIdrive/raw/refs/heads/main/apk/AAIdrive-MaxGiuP.apk)

Version: `1.4.4-7-904ae9d` (8.06 MB), version code
`10404003`. Built from
[`904ae9d2`](https://github.com/MaxGiuP/AAIdrive/commit/904ae9d2b0e1188111cdafabb96da737d4cad6e6).
The final build passed all 586 unit tests; APK signature,
alignment, source version, and update compatibility checks passed.
Full source, signing certificate, and checksum details are in
[build-info.json](build-info.json). Phone and in-car testing have not been performed.

This is the `nomapNonalyticsFullOptimized` build: R8 optimization enabled, debugging
disabled, no embedded maps or analytics, and normal Android media integration enabled.
It is 62.1% smaller than the first fork APK (21.3 MB).
Phone startup speed and battery use have not been measured.
Spotify's optional proprietary API integration is disabled because this fork has
no Spotify API key.

This APK uses the same local Android development signing certificate as the first
fork APK and a higher version code, allowing an update over that installation.
The private signing key is kept outside the repository. This is a custom fork
build, not an upstream release. It uses AAIdrive's original package name, so an
official build signed with a different key is incompatible; save settings you need
before uninstalling an incompatible existing version.

See [media setup and compatibility](../docs/maxgiup-media.md) for ReVanced defaults,
Audible/Rumble/YouTube support, and selectable playlists. Playlist availability is
limited to the queue the player exposes through Android's media APIs.
See [performance and navigation notes](../docs/maxgiup-improvements.md) for caching,
request cleanup, and Google Maps destination sharing to BMW/MINI navigation.
The latest native handoff uses embedded destination coordinates when available,
responds to navigation status events, and avoids duplicate lookups and replacement
route retries. This APK does not provide embedded Google Maps, Android Auto
projection, or Android-to-CarPlay translation. See [navigation options and API costs](../docs/maxgiup-navigation-options.md)
for the supported paths and limitations.

Build requirements: JDK 17+, Python 3, Android SDK platform 35, the Gradle wrapper,
and the upstream resources described in [external/README.md](../external/README.md).
From a clean source commit, run:

```sh
./scripts/build-apk.sh
```

Increment `AAIdrive_ForkVersionCode` in `gradle.properties` before publishing a new
source version. Packaging verifies the signing certificate against the existing
APK; another machine needs the original signing key to produce an update.

Check the packaged APK against its SHA-256 checksum from this directory:

```sh
sha256sum -c AAIdrive-MaxGiuP.apk.sha256
```
