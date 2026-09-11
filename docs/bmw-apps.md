# Apps for the BMW Apps connection

Install the [single Setup APK](https://raw.githubusercontent.com/MaxGiuP/AAIdrive/main/apk/AAIdrive-Setup.apk),
open it, and tap **Install everything**. This installs AAIdrive and its two
projection companions with Android's confirmation prompts. You can use music and
BMW navigation without configuring projection. No ZIP extraction is needed.

Setup's phone-app section checks the supported apps already installed on your
phone, offers an **Open** action, and links to official sources for missing apps.
Your media apps and Google Maps remain separate installations. ReVanced setup
opens its official patching project; Setup does not include prepatched YouTube
APKs or silently patch your apps.

ReVanced's official [Manager download](https://revanced.app/download) and
[installation and patching guide](https://github.com/ReVanced/revanced-manager/tree/main/docs)
cover creating and updating your patched apps. Keep an existing working ReVanced
installation; this Setup update does not require reinstalling it.

## Music shortcuts in the car

After connecting AAIdrive, the following installed, unhidden apps get their own
ConnectedDrive/Online Services shortcuts. Each uses the phone app's actual icon.
YouTube Music is given the highest shortcut priority; exact placement relative
to BMW's own entries depends on the head unit. Reconnect after updating AAIdrive
or installing a new player to refresh discovery and the car menu.

| Shortcut | What opens in the BMW | Official app source |
| --- | --- | --- |
| YouTube Music ReVanced | AAIdrive music controls, published queue and library when available | [ReVanced](https://revanced.app/) · [Stock YouTube Music](https://play.google.com/store/apps/details?id=com.google.android.apps.youtube.music) |
| Audible | AAIdrive audiobook controls and the browsing/custom actions exposed by Audible | [Audible on Google Play](https://play.google.com/store/apps/details?id=com.audible.application) |
| YouTube ReVanced | AAIdrive controls for the active audio session | [ReVanced](https://revanced.app/) · [Stock YouTube](https://play.google.com/store/apps/details?id=com.google.android.youtube) |
| Rumble | AAIdrive controls for the active audio session | [Rumble on Google Play](https://play.google.com/store/apps/details?id=com.rumble.battles) |

The YouTube families show one preferred variant each: ReVanced, ReVanced Extended,
then stock/root-patched; YouTube also supports an existing Vanced installation as
a fallback. The shortcut names distinguish known variants. Hiding a variant in
AAIdrive excludes it from shortcut selection; hiding every variant removes that
family's shortcut. Other supported players remain in the normal music selector.

Open each media app on the phone once, sign in if needed, and start playback.
Grant AAIdrive notification access to expose active media sessions. A shortcut
can appear before its player is ready to accept controls. Background audio,
library browsing and queue contents depend on the installed player. The
[media guide](maxgiup-media.md) explains playlist selection and supported controls.
These media shortcuts do not show the apps' video interfaces.

## Navigation and voice

For native BMW navigation, open [Google Maps](https://play.google.com/store/apps/details?id=com.google.android.apps.maps)
on the phone, choose a destination, and share it to **BMW/MINI Navigation** while
AAIdrive is connected. BMW calculates and displays its own route. This path uses
the car's navigation screen rather than transmitting map screenshots; it does
not import Google's exact route or require a Maps developer API key.

To display Google's route on the car screen, expand the optional projection setup
in Setup and follow the [projection guide](android-auto-projection.md). That view
uses the existing BMW Apps connection, with lower smoothness over Bluetooth.
The Google voice-assistant entry launches voice assistance, not Google Maps.

If pairing only offers phone/audio/apps, use **Apps**. This project does not
activate CarPlay or native Android Auto. See [connection troubleshooting](connection.md)
for USB errors and [car diagnostics](car-diagnostics.md) for identifying the
reported software and measuring the projection pipeline.
