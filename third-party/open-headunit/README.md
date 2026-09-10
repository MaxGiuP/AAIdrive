# Open Headunit redistribution

The [Open Headunit APK](../../apk/Open-Headunit-v3.4.0-beta1.apk) is the unchanged, upstream-signed [v3.4.0-beta1 GitHub release](https://github.com/andreknieriem/open-headunit/releases/tag/v.3.4.0-beta1) by André Rinas and the Open Headunit contributors, based on Michael A. Reid's Headunit project. It is distributed under [AGPL version 3 or later](LICENSE); retain [the upstream copyright notice](COPYRIGHT.txt). No warranty is provided.

The AAIdrive installer carries this APK as a separately installed application. Its application ID is `com.andrerinas.headunitrevived`, version code is `106`, version name is `3.4.0-beta1`, minimum Android API is `16`, and target API is `36`. Neither AAIdrive nor its projection add-on incorporates Open Headunit code into its own executable. Android Auto and Google Maps are separate Google applications and are not contained in this APK or in these source archives.

## Source downloads

The source accompanying this binary is available without charge here, next to the APK, and at this permanent repository location:

**https://github.com/MaxGiuP/AAIdrive/tree/main/third-party/open-headunit**

- [Open Headunit source snapshot](open-headunit-c5184d782aa5727572c1bd7ee131bbc84a55f75f-source.tar.gz), from release commit `c5184d782aa5727572c1bd7ee131bbc84a55f75f`. The APK's embedded version-control metadata identifies that same commit. The archive includes the application, resources, protocol definitions, Gradle wrapper and build scripts, native wrappers, notices, and upstream prebuilt native libraries. There are no submodules. The only omitted tracked file is `keystore.jkc`, an obsolete upstream signing keystore; it is not needed to build with your own key.
- [FFmpeg 7.1.1 source](ffmpeg-7.1.1.tar.xz), from the [official FFmpeg release](https://ffmpeg.org/releases/ffmpeg-7.1.1.tar.xz), for the bundled arm64 HEVC decoder libraries. They report FFmpeg 7.1.1 and LGPL 2.1 or later. See [the LGPL license](LICENSE-FFmpeg-LGPL-2.1.txt) and the individual source-file notices, including FFmpeg's license documentation inside the archive.
- [libusb 1.0.27 source](libusb-1.0.27.tar.bz2), from the [official libusb release](https://github.com/libusb/libusb/releases/tag/v1.0.27), for the bundled USB libraries. The APK's version structure is `1.0.27.11882`, matching that source release. libusb is copyright its contributors under [LGPL 2.1 or later](LICENSE-libusb-LGPL-2.1.txt).

[BUILDING.md](BUILDING.md) describes rebuilding the application and replacing its native libraries. Dependencies resolved by Gradle, including Conscrypt, AndroidX, and protobuf, are declared with versions and repository locations in the source snapshot. Their original licenses and notices remain applicable. The [exact upstream tree](https://github.com/andreknieriem/open-headunit/tree/c5184d782aa5727572c1bd7ee131bbc84a55f75f) is an additional source location.

This directory supplies network access to source alongside the binary under [AGPL section 6(d)](https://www.gnu.org/licenses/agpl-3.0.html#section6). Keep this source directory, its download links, and the license notices available whenever redistributing the standalone APK or the installer containing it. Source downloads do not require installing the binary.

## Verification

[manifest.json](manifest.json) records the APK's source URL, SHA-256, upstream signing-certificate fingerprint, manifest versions, source-archive hashes, and verification limits. The APK hash matches GitHub's release-asset digest; Android's `apksigner` verifies its original v1 and v2 signatures. The archive was neither re-signed nor modified.

The APK passes `zipalign -c -P 16 -v 4`, and all 19 native libraries have ELF load-segment alignment of at least 16,384 bytes. These are file checks; they do not establish runtime compatibility, measured performance, or successful phone/car operation. The upstream source has not been independently rebuilt into a byte-identical APK.
