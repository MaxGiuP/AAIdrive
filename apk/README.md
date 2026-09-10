# Installable APK

[Download AAIdrive-MaxGiuP.apk](https://github.com/MaxGiuP/AAIdrive/raw/refs/heads/main/apk/AAIdrive-MaxGiuP.apk)

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
