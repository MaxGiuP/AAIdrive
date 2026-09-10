# Installable APK

[Download AAIdrive-MaxGiuP.apk](https://github.com/MaxGiuP/AAIdrive/raw/refs/heads/main/apk/AAIdrive-MaxGiuP.apk)

Version: `1.4.4-2-0f92c94` (21.3 MB). Built from
[`0f92c947`](https://github.com/MaxGiuP/AAIdrive/commit/0f92c947).
The final build passed all 531 unit tests; APK v1/v2 signatures were verified.
Full version, source, and checksum details are in [build-info.json](build-info.json).
Phone and in-car testing have not been performed.

This is the `nomapNonalyticsFullDebug` build: no embedded maps or analytics, with
normal Android media integration enabled. Spotify's optional proprietary API
integration is disabled because this fork has no Spotify API key.

The APK is signed with this machine's Android debug key. It uses AAIdrive's original
package name, so Android will not install it over an official build signed with
a different key. Save any settings you need before uninstalling an incompatible
existing version. This is a development build, not an upstream release.

See [media setup and compatibility](../docs/maxgiup-media.md) for the player defaults,
playlist controls, supported package names, and source-app limitations.

Build requirements: JDK 17, Android SDK platform 35, the Gradle wrapper, and the
upstream resources described in [external/README.md](../external/README.md).

```sh
./gradlew :app:testNomapNonalyticsFullDebugUnitTest \
  :app:assembleNomapNonalyticsFullDebug \
  -PAndroidAutoIdrive_SpotifyApiKey=unset
```

Check the packaged APK against its SHA-256 checksum from this directory:

```sh
sha256sum -c AAIdrive-MaxGiuP.apk.sha256
```
