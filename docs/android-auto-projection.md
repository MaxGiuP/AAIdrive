# Android Auto on the BMW display

This experimental screen-mirroring add-on uses Open Headunit on the phone to receive Android Auto locally, then sends captured frames through AAIdrive's existing BMW Connected Apps display connection. Google Maps and other Android Auto apps run on the phone. No Google Maps API key is required.

The car does not need native Android Auto support. It does need a working AAIdrive/BMW Connected Apps connection and support for the screen-mirroring add-on. This is not a CarPlay converter, an Android Auto retrofit, or an import of Google's route into BMW navigation.

## Companion version

The [one-APK setup](one-apk-setup.md) includes an unmodified copy of [Open Headunit v3.4.0-beta1](https://github.com/andreknieriem/open-headunit/releases/tag/v.3.4.0-beta1), the companion release used to check this integration. Its application ID remains `com.andrerinas.headunitrevived` despite the newer display name. Android Auto must also be installed and enabled.

The integration uses that release's public [automation and key-event contract](https://github.com/andreknieriem/open-headunit/blob/c5184d782aa5727572c1bd7ee131bbc84a55f75f/contract/src/main/java/com/andrerinas/openheadunit/contract/HeadUnitIntent.kt) and [automation documentation](https://github.com/andreknieriem/open-headunit/blob/c5184d782aa5727572c1bd7ee131bbc84a55f75f/contract/README.md), pinned to source commit `c5184d782aa5727572c1bd7ee131bbc84a55f75f`. The AGPL-licensed receiver remains a separate installed app. Its APK is bundled inside Setup, with corresponding source archives and notices in [third-party/open-headunit](../third-party/open-headunit/). Future Android Auto or Open Headunit updates may need compatibility changes.

## Setup and start

1. Download [AAIdrive-Setup.apk](../apk/AAIdrive-Setup.apk), open it and select **Install everything**. Follow Android's prompts to install all three included components. Connect the phone to the BMW and check that ordinary AAIdrive apps appear on the car display.
2. Open Open Headunit once and complete its setup. Use landscape orientation and hardware H.264 decoding. Start with a moderate resolution such as 720p and 30 fps; the BMW frame connection may deliver far fewer frames. TextureView is a useful rendering option on modern phones. Enable **Rotary Controller** in Open Headunit before starting Android Auto.
3. In Android Auto settings, tap its version information ten times to enable developer settings. Open the menu and choose **Start headunit server**. Check this before each session and start it again whenever Android Auto or the phone has stopped it. Open Headunit's self-mode connects to `127.0.0.1:5277`; recent Android Auto versions require this manual server step. [Google's developer-server instructions](https://developer.android.com/training/cars/testing/dhu#connection-options), [Open Headunit self-mode implementation](https://github.com/andreknieriem/open-headunit/blob/c5184d782aa5727572c1bd7ee131bbc84a55f75f/app/src/main/java/com/andrerinas/openheadunit/connection/self/launchers/SelfLauncherV17_4.kt).
4. Start Android Auto projection from the screen-mirroring add-on. In Android's capture prompt, choose the **entire screen**, then allow capture. The add-on starts capture before opening Open Headunit's self-mode. Capturing only the add-on's setup activity would show the wrong app.
5. Keep Open Headunit's projection visible on the unlocked phone, in landscape. Select the projection add-on on iDrive, then open the Android Auto view. Use its controls to navigate Android Auto and open Google Maps.

The control menu provides Up, Down, Left, Right, Select and Android Auto Back. Menu commands keep the control menu open; choose **View Android Auto** again to see the result. In the full-screen view, turn the iDrive knob to move Android Auto's focus and press to select. BMW Back returns to the add-on's control menu, where **Android Auto Back** sends Back to the receiver.

Projection 0.3.0 defaults to native Android Auto rotary input. Open Headunit maps the paired Android SOFT_LEFT/RIGHT key events to [ScrollWheelEvent](https://github.com/andreknieriem/open-headunit/blob/c5184d782aa5727572c1bd7ee131bbc84a55f75f/app/src/main/java/com/andrerinas/openheadunit/aap/AapTransport.kt). If rotary navigation is unavailable in your receiver configuration, select **Up/down keys** in the projection phone app. **Reverse knob direction** changes either mapping. These settings take effect on the next turn.

The controller waits for a running receiver and active capture session. Repeated presses of the same key retain 310 ms spacing for the bundled receiver's debounce; select or Back after a turn no longer waits unnecessarily. Accepted commands remain ordered, with at most four waiting. Leaving the car view clears them. Very fast knob turns can still exceed this queue.

## What to expect

Projection 0.3.0 offers **Smooth — 30 FPS target** (the default), **Balanced — 15 FPS target**, and **Sharper — 10 FPS target** in the phone app. The setting applies to Bluetooth and USB and can change during a session. Install the separately built 0.3.0 projection APK to use these settings; the existing Setup APK still bundles 0.2.0.

Capture and JPEG encoding run on a dedicated worker while the previous image transfers to the car. The transfer queue holds one image in flight and only the newest pending image, replacing older pending frames. Each transfer yields to queued car input and focus callbacks before starting another. This removes the old 4 FPS Bluetooth/10 FPS USB software caps and prevents a slow connection from accumulating delayed video.

Capture keeps the dimensions reported by the car. Two reusable bitmaps skip JPEG encoding when pixels are unchanged, and byte-identical JPEGs are not sent again. JPEG quality adapts to completed send duration against the selected frame budget (33 ms for Smooth). Smooth uses quality 18–40 over Bluetooth, starting at 30, or 30–70 over USB, starting at 60. Repeated slow sends reduce quality; sustained spare capacity restores it gradually. The other profiles give image detail more priority.

**30 FPS is a target, not a measured BMW result.** Phone-to-car projection, knob behaviour, audio, thermal load and sustained frame rate still need testing on the target Pixel and BMW. This path transfers JPEG screenshots; negotiating 30/60 FPS in Android Auto does not change the BMW link's bandwidth or rendering speed. For 30 completed transfers per second, the link must sustain roughly one image every 33 ms (for example, 20 KB images need about 600 KB/s before protocol overhead). A supported USB BMW Apps connection may help; plugging into a USB port alone does not establish that connection. If transfers still take longer than 33 ms at the quality floor, this build cannot deliver 30 FPS on that link. The existing 1 FPS throttle remains when the car is moving and Android is outside car mode.

While capture is processing frames, diagnostics receive numerical summaries at most once every five seconds: dimensions, quality, frame counts, skipped compression, bytes, and capture/copy, encoding and send times. Reported send rate describes completed local sends; actual car display refresh and latency still need measurement. These summaries contain no captured images or app/navigation text. See the [car compatibility notes](car-compatibility.md) for why display size and connectivity-version numbers alone cannot identify a head unit.

To measure a run, select Smooth, show continuously changing content in Android Auto while parked, and collect at least 30 seconds of [car diagnostics](car-diagnostics.md). Compare `sends_per_s` and `send_ms` in the `ProjectionPerf` samples after the first five-second window. `frames` counts captured images, while `sent` counts completed transfers; a static map deliberately sends fewer frames. Camera observation of the BMW screen is needed to establish its actual displayed frame rate.

Build just this add-on with JDK 21 and the Android SDK configured:

```sh
./gradlew :screen-mirror:testDebugUnitTest :screen-mirror:assembleRelease --configure-on-demand --max-workers=4
```

`--configure-on-demand` also avoids configuring the unrelated main application's older Git-version plugin, which does not support a linked Git worktree. The APK is under `screen-mirror/build/outputs/apk/release/`.

Keep the phone paired to the BMW for normal media/call audio and check the phone's selected Bluetooth output. The add-on transports the display and navigation keys; it does not capture or forward audio. Open Headunit's self-mode omits its media/speech audio sinks, and actual media, navigation prompts, microphone use and calls need a test on the phone/car combination. If sound is missing, check the Bluetooth media/call settings and Open Headunit audio configuration.

Whole-screen capture can show notifications, the phone keyboard and any other app brought to the foreground. The add-on sends those frames to the connected car; it does not record a screen video. Keep private content off screen while sharing. Switching away from Open Headunit or locking the phone can interrupt its controls or picture, and locking can end Android's capture session.

Stop sharing using Android's screen-sharing controls or the add-on's stop control. Leaving the car projection view clears pending navigation commands; ending capture or disconnecting the car must also stop the add-on's control forwarding. Stopping screen sharing does not necessarily stop Android Auto's developer server: stop that separately in Android Auto settings when finished. Restarting sharing requires fresh Android capture consent.

The projection modules derive from the MIT-licensed BimmerGestalt screen-mirroring
add-on. Original licenses and the exact upstream commit are retained in
[source attribution](../screen-mirror/NOTICE.md).
