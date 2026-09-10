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
   a prompt pauses installation; use Continue when ready. A differently signed app
   is reported as a conflict; setup never uninstalls it or erases its settings.
4. Open AAIdrive through setup, complete its permissions and car connection steps,
   and check that its apps appear on iDrive. Keep the phone's Bluetooth media output
   connected to the BMW for audio.
5. Open Open Headunit through setup and complete its first-run configuration. Use
   landscape orientation and hardware H.264 decoding.
6. Open Android Auto settings through setup. Tap its version information ten times
   to enable developer settings; in the menu choose **Start headunit server**.
   Check/start this server for each session when needed.
7. Open Projection through setup and tap **Start Android Auto on BMW**. Approve
   screen sharing, selecting the entire display if offered. Keep Open Headunit
   visible on the unlocked phone. In iDrive open **Android Auto (experimental)**,
   then **View Android Auto**.

Android controls its app-install permissions, install confirmations, Android Auto
server and screen-sharing consent. Setup guides these steps but cannot silently
grant them. Do the initial configuration and checks while parked.

Google's Android Auto and Google Maps apps normally come with supported phones.
They are not repackaged inside this installer. If either is missing, setup links
to its official Google Play listing; installing those Google apps needs internet.
AAIdrive also retains its own checks for any BMW connection prerequisites.
No Google Maps developer API key or billing project is needed for this projection path.

The new installer is tested with automated checks. Physical installation and
projection on the target Pixel/BMW remain untested. Screen sharing sends compressed
frames over the BMW connection; Bluetooth can limit smoothness. See the
[projection guide](android-auto-projection.md) for controls and limitations.

Open Headunit is an unmodified upstream release. Its license, exact binary/source
identity, source archives and third-party notices are available at
[third-party/open-headunit](https://github.com/MaxGiuP/AAIdrive/tree/main/third-party/open-headunit)
and through Setup's license screen. The main app and Projection source are in this
repository. Individual component APKs remain in `apk/` for manual installation.
