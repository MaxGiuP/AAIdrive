# Building the pinned Open Headunit source

Unpack `open-headunit-c5184d782aa5727572c1bd7ee131bbc84a55f75f-source.tar.gz`. Its build files specify Android SDK 36, NDK `29.0.14206865`, CMake `3.22.1`, Gradle `8.13`, Android Gradle Plugin `8.13.2`, and Kotlin `1.9.22`. Use a compatible Java installation, such as JDK 17. Set `ANDROID_HOME` to your Android SDK, or create `local.properties` with `sdk.dir`.

From the unpacked application directory, run:

```sh
./gradlew :app:assembleGithubDebug
```

Gradle downloads the pinned dependencies from Google Maven and Maven Central and generates a local Android debug certificate if needed. Its output is under `app/build/outputs/apk/github/debug/`. You can change and rebuild the application or its libraries using this source. The historical `README_OPENSOURCE_BUILDING.txt` is retained as an upstream notice; use the Gradle files for this modern release.

For an optimized build, use `:app:assembleGithubRelease` and configure your own release key as described by `signingConfigs` in `app/build.gradle.kts`. The excluded upstream `keystore.jkc` is not required. A build signed with your key cannot update the upstream-signed package in place. Keep your own keys private. Without Git metadata, upstream's optional build-commit display says `unknown`; the source archive name and manifest record its origin.

## Native libraries

The snapshot retains the exact upstream prebuilts used by its build configuration. To replace them with modified libraries, build the source archives supplied here, copy the resulting libraries into the matching `app/src/main/jniLibs/<ABI>/` directories, and rebuild the application. The `usbhelper.c` and `ffmpeg_hevc_decoder.cpp` wrappers and CMake build scripts are part of the application snapshot.

FFmpeg's bundled arm64 libraries were built with NDK `27.0.12077973`. Their embedded configure string specifies the following options; machine-specific compiler and output paths have been replaced with variables. Set `FFMPEG_TOOLCHAIN` to the NDK's `toolchains/llvm/prebuilt/<host>` directory and `FFMPEG_INSTALL` to an output directory, then run this from unpacked `ffmpeg-7.1.1`:

```sh
./configure --prefix="$FFMPEG_INSTALL" \
  --target-os=android --arch=aarch64 --cpu=armv8-a --enable-cross-compile \
  --cc="$FFMPEG_TOOLCHAIN/bin/aarch64-linux-android21-clang" \
  --cxx="$FFMPEG_TOOLCHAIN/bin/aarch64-linux-android21-clang++" \
  --ar="$FFMPEG_TOOLCHAIN/bin/llvm-ar" \
  --ranlib="$FFMPEG_TOOLCHAIN/bin/llvm-ranlib" \
  --strip="$FFMPEG_TOOLCHAIN/bin/llvm-strip" \
  --sysroot="$FFMPEG_TOOLCHAIN/sysroot" \
  --disable-everything --disable-programs --disable-doc --disable-debug \
  --disable-network --disable-autodetect --disable-avdevice --disable-avfilter \
  --disable-avformat --disable-postproc --disable-swresample \
  --disable-encoders --disable-muxers --disable-demuxers \
  --enable-avcodec --enable-avutil --enable-swscale \
  --enable-decoder=hevc --enable-parser=hevc \
  --enable-shared --disable-static --enable-pic \
  --extra-cflags='-O3 -fPIC' --extra-ldflags='-Wl,-z,max-page-size=16384'
make
make install
```

Copy `libavcodec.so`, `libavutil.so`, and `libswscale.so` to `app/src/main/jniLibs/arm64-v8a/`. Copy modified public headers to `app/src/main/cpp/ffmpeg/include/` when needed. The upstream FFmpeg build has no `--enable-gpl` or `--enable-nonfree` option.

libusb `1.0.27` supplies its Android makefiles in `android/jni/`. Set `LIBUSB_NDK` to an Android NDK installation. From unpacked `libusb-1.0.27/android`, its library-only build can be invoked with:

```sh
"$LIBUSB_NDK/ndk-build" NDK_PROJECT_PATH=. \
  APP_BUILD_SCRIPT=jni/libusb.mk NDK_APPLICATION_MK=jni/Application.mk \
  APP_ABI='armeabi-v7a arm64-v8a x86 x86_64' \
  APP_LDFLAGS='-llog -Wl,-z,max-page-size=16384'
```

Copy each `libs/<ABI>/libusb1.0.so` to the corresponding application `jniLibs/<ABI>/` directory. The upstream [native-library update](https://github.com/andreknieriem/open-headunit/commit/d645cdbd92a116bed899cfd0b4e995c1cd684b73) identifies the 16 KB alignment change. The upstream repository does not include its original libusb build invocation; the command above uses the supplied libusb Android build and the documented alignment option.

These rebuild instructions and sources support modifying the libraries and relinking the application. They have not been independently executed for this redistribution, and a byte-identical rebuild of the upstream-signed APK is not claimed.
