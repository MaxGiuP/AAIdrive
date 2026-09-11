# Install everything from one APK

Download [AAIdrive-Setup.apk](https://github.com/MaxGiuP/AAIdrive/raw/refs/heads/main/apk/AAIdrive-Setup.apk)
onto your Android phone. This single download includes the signed AAIdrive,
AAIdrive Projection and Open Headunit v3.4.0-beta1 APKs. They install as separate
component apps; Setup stays available to guide configuration and repair installation.
No additional download is needed to install these three bundled components.

1. Install and open **AAIdrive Setup**. Android may first ask you to allow your
   browser or file manager to install this APK.
2. Tap **Install everything**. If Android asks, allow **AAIdrive Setup** to install
   apps, then return to setup and continue.
3. Confirm Android's install prompts for the components. Setup checks what actually
   installed before proceeding. Compatible installed versions are skipped. Cancelling
   a prompt pauses installation; tap **Install everything** again when ready. A differently signed app
   is reported as a conflict; setup never uninstalls it or erases its settings.
4. Open AAIdrive through setup, complete its permissions and car connection steps,
   and check that its apps appear on iDrive. Keep the phone's Bluetooth media output
   connected to the BMW for audio. After updating, reconnect the BMW Apps session
   so the car receives the new shortcut registrations.
5. Use Setup's phone-app list to open your installed YouTube Music ReVanced,
   Audible, YouTube, and Rumble apps, or visit their official installation sources.
   Start playback once in the apps you want to control, and give AAIdrive notification
   access. Installed, unhidden supported apps gain their own car shortcuts, with
   YouTube Music first. Open **Now Playing** inside AAIdrive to choose from the
   tracks the player publishes.
6. For native navigation, open Google Maps from Setup, choose a destination, and
   share it to **BMW/MINI Navigation** while connected. Check the destination on
   the BMW screen. The car calculates its own route; Google's exact route is not imported.

Music and native BMW navigation are ready after these steps. The
[app list and official references](bmw-apps.md) describe each integration.

## Optional: Google Maps through Android Auto projection

Expand the optional projection section in Setup to show these controls.

1. Open Open Headunit through setup and complete its first-run configuration. Use
   landscape orientation and hardware H.264 decoding.
2. Open Android Auto settings through setup. Tap its version information ten times
   to enable developer settings; in the menu choose **Start headunit server**.
   Check/start this server for each session when needed.
3. Open Projection through setup and tap **Start Android Auto on BMW**. Approve
   screen sharing, selecting the entire display if offered. Keep Open Headunit
   visible on the unlocked phone. In iDrive open **Android Auto (experimental)**,
   then **View Android Auto**.

Android controls its app-install permissions, install confirmations, Android Auto
server and screen-sharing consent. Setup guides these steps but cannot silently
grant them. Do the initial configuration and checks while parked.

Google's Android Auto and Google Maps apps normally come with supported phones.
They and the media apps are not repackaged inside this installer. Setup links
to official Google Play listings and the official ReVanced patching project;
getting those apps needs internet. An existing ReVanced installation is detected
and can be opened directly; installing Setup does not patch YouTube or YouTube Music.
AAIdrive also retains its own checks for any BMW connection prerequisites.
No Google Maps developer API key or billing project is needed for this projection path.

If iDrive only offers **phone/audio/apps** when adding a device, use its **Apps**
connection. Setup does not require or activate CarPlay. A USB **device unsupported**
message is a separate [connection troubleshooting issue](connection.md); keep
Bluetooth Apps connected if that already works.

The new installer is tested with automated checks. Physical installation and
projection on the target Pixel/BMW remain untested. Screen sharing sends compressed
frames over the BMW connection; Bluetooth can limit smoothness. See the
[projection guide](android-auto-projection.md) for controls and limitations.

Open Headunit is an unmodified upstream release. Its license, exact binary/source
identity, source archives and third-party notices are available at
[third-party/open-headunit](https://github.com/MaxGiuP/AAIdrive/tree/main/third-party/open-headunit)
and through Setup's license screen. The main app and Projection source are in this
repository. Individual component APKs remain in `apk/` for manual installation.
