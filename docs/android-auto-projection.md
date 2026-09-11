# Android Auto on the BMW display

This experimental screen-mirroring add-on uses Open Headunit on the phone to receive Android Auto locally, then sends captured frames through AAIdrive's existing BMW Connected Apps display connection. Google Maps and other Android Auto apps run on the phone. No Google Maps API key is required.

The car does not need native Android Auto support. It does need a working AAIdrive/BMW Connected Apps connection and support for the screen-mirroring add-on. This is not a CarPlay converter, an Android Auto retrofit, or an import of Google's route into BMW navigation.

## Companion version

The [one-APK setup](one-apk-setup.md) includes an unmodified copy of [Open Headunit v3.4.0-beta1](https://github.com/andreknieriem/open-headunit/releases/tag/v.3.4.0-beta1), the companion release used to check this integration. Its application ID remains `com.andrerinas.headunitrevived` despite the newer display name. Android Auto must also be installed and enabled.

The integration uses that release's public [automation and key-event contract](https://github.com/andreknieriem/open-headunit/blob/c5184d782aa5727572c1bd7ee131bbc84a55f75f/contract/src/main/java/com/andrerinas/openheadunit/contract/HeadUnitIntent.kt) and [automation documentation](https://github.com/andreknieriem/open-headunit/blob/c5184d782aa5727572c1bd7ee131bbc84a55f75f/contract/README.md), pinned to source commit `c5184d782aa5727572c1bd7ee131bbc84a55f75f`. The AGPL-licensed receiver remains a separate installed app. Its APK is bundled inside Setup, with corresponding source archives and notices in [third-party/open-headunit](../third-party/open-headunit/). Future Android Auto or Open Headunit updates may need compatibility changes.

## Setup and start

1. Download [AAIdrive-Setup.apk](../apk/AAIdrive-Setup.apk), open it and select **Install everything**. Follow Android's prompts to install all three included components. Connect the phone to the BMW and check that ordinary AAIdrive apps appear on the car display.
2. Open Open Headunit once and complete its setup. Use landscape orientation and hardware H.264 decoding. Start with a moderate resolution such as 720p and 30 fps; the BMW frame connection may deliver far fewer frames. TextureView is a useful rendering option on modern phones.
3. In Android Auto settings, tap its version information ten times to enable developer settings. Open the menu and choose **Start headunit server**. Check this before each session and start it again whenever Android Auto or the phone has stopped it. Open Headunit's self-mode connects to `127.0.0.1:5277`; recent Android Auto versions require this manual server step. [Google's developer-server instructions](https://developer.android.com/training/cars/testing/dhu#connection-options), [Open Headunit self-mode implementation](https://github.com/andreknieriem/open-headunit/blob/c5184d782aa5727572c1bd7ee131bbc84a55f75f/app/src/main/java/com/andrerinas/openheadunit/connection/self/launchers/SelfLauncherV17_4.kt).
4. Start Android Auto projection from the screen-mirroring add-on. In Android's capture prompt, choose the **entire screen**, then allow capture. The add-on starts capture before opening Open Headunit's self-mode. Capturing only the add-on's setup activity would show the wrong app.
5. Keep Open Headunit's projection visible on the unlocked phone, in landscape. Select the projection add-on on iDrive, then open the Android Auto view. Use its controls to navigate Android Auto and open Google Maps.

The control menu provides Up, Down, Left, Right, Select and Android Auto Back. Menu commands keep the control menu open; choose **View Android Auto** again to see the result. In the full-screen view, the iDrive knob moves the selection and clicking selects it; BMW Back returns to the add-on's control menu. The controller sends paired key-down/key-up events only to Open Headunit. It waits for a running receiver session, spaces commands by at least 310 ms to accommodate the companion's debounce, and keeps at most four commands waiting. Very fast knob turns can exceed that queue; use deliberate steps.

## What to expect

This implementation has automated control-queue tests and build checks. Phone-to-car projection, control behavior, audio and sustained frame rate still need validation on the target BMW and Pixel. Do not assume Android Auto's negotiated 30/60 fps will reach iDrive: the final link sends compressed screenshots, with speed and clarity limited by the car, transport, capture resolution and phone load. Capture is capped at 10 frames per second on other transports and 4 on Bluetooth, before any bandwidth limits. The upstream 1 fps throttle remains when the car is moving and Android is outside car mode. Bluetooth can be substantially slower than a supported USB connection. No measured latency or frame-rate promise is made.

Capture keeps the dimensions reported by the car. Two reusable bitmaps allow the add-on to compare successive images and skip JPEG compression and transmission when the pixels are unchanged. Over Bluetooth, repeated slow sends reduce the JPEG quality setting within 25–30; sustained faster sends gradually restore it toward 30. This changes compression quality while retaining the car's reported resolution and the frame-rate limits above.

While capture is processing frames, diagnostics receive numerical summaries at most once every five seconds: dimensions, quality, frame counts, skipped compression, bytes, and capture/copy, encoding and send times. Reported send rate describes completed local sends; actual car display refresh and latency still need measurement. These summaries contain no captured images or app/navigation text. See the [car compatibility notes](car-compatibility.md) for why display size and connectivity-version numbers alone cannot identify a head unit.

Keep the phone paired to the BMW for normal media/call audio and check the phone's selected Bluetooth output. The add-on transports the display and navigation keys; it does not capture or forward audio. Open Headunit's self-mode omits its media/speech audio sinks, and actual media, navigation prompts, microphone use and calls need a test on the phone/car combination. If sound is missing, check the Bluetooth media/call settings and Open Headunit audio configuration.

Whole-screen capture can show notifications, the phone keyboard and any other app brought to the foreground. The add-on sends those frames to the connected car; it does not record a screen video. Keep private content off screen while sharing. Switching away from Open Headunit or locking the phone can interrupt its controls or picture, and locking can end Android's capture session.

Stop sharing using Android's screen-sharing controls or the add-on's stop control. Leaving the car projection view clears pending navigation commands; ending capture or disconnecting the car must also stop the add-on's control forwarding. Stopping screen sharing does not necessarily stop Android Auto's developer server: stop that separately in Android Auto settings when finished. Restarting sharing requires fresh Android capture consent.

The projection modules derive from the MIT-licensed BimmerGestalt screen-mirroring
add-on. Original licenses and the exact upstream commit are retained in
[source attribution](../screen-mirror/NOTICE.md).
