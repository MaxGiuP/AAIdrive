# MaxGiuP media support

This fork prefers YouTube Music ReVanced when no player has been selected yet.
It tries ReVanced, then ReVanced Extended, then stock or root-patched YouTube Music.
An already playing app, a saved player, and apps you explicitly hide keep their priority.
The preference controls AAIdrive; it does not change Android's system defaults.

## Setup

1. Install the [fork APK](../apk/README.md) and your chosen media apps.
2. Grant AAIdrive notification access so it can discover active media sessions.
3. Open YouTube Music ReVanced on the phone and start the playlist you want.
4. Select YouTube Music in AAIdrive. The main ID5+ music selector lists the published
   playlist tracks instead of Back / current song / Next. Scroll to a song and select it.
   You can also open **Now Playing** (the queue), or the queue tab in AAIdrive's phone player.

Audible, Rumble, and YouTube/ReVanced are recognized by package name, including
when their display names mention podcasts, live streams, or shows. Installed
players appear in AAIdrive's phone app list. A player that offers no media browser
must start playback on the phone before its active session can be controlled in
the car. Background playback must also be enabled/supported in that player.

## Changes

- The main ID5+ music selector and instrument-cluster playlist display all published
  queue tracks. Back/Next controls remain available when a player supplies no queue.
- YouTube Music's existing ReVanced Android Auto browse-root workaround also applies
  to ReVanced Extended. Stock/root-patched installations keep their negotiated root.
- The queue retains every track supplied by the player, including its media ID,
  queue ID, artwork, and playback extras. Selection uses the queue command when
  supported, or playback by media ID when the player offers that alternative.
- Queue highlighting handles players without an active queue ID, and the phone
  queue refreshes when its songs change even if the title and length stay the same.
- YouTube's missing-metadata reconnection workaround also covers ReVanced variants.
- Playback progress follows the reported speed, including faster audiobook playback.
- Rewinding Audible, Rumble, or YouTube videos stops at the start of the current
  chapter/video. Seeking does not exceed a known duration.
- Namespaced custom seek actions and actions containing seek intervals are recognized
  for the existing previous/next controls when a player does not offer those controls directly.
- Players that expose only a play/pause toggle can be discovered and controlled using
  media-button events, with the current playback state checked before toggling.

## Playlist and app limits

AAIdrive shows the queue that the media app publishes through Android. Android
explicitly allows a player to publish a [sliding window of its queue](https://developer.android.com/reference/android/media/session/MediaSession#setQueue(java.util.List%3Candroid.media.session.MediaSession.QueueItem%3E)).
If your YouTube Music version publishes the entire playlist, every song is available;
if it publishes only some tracks or no queue, AAIdrive cannot obtain the missing
tracks through that interface. Browse available playlists through the player's
library when supported. This fork does not infer a playlist from an album name
or promise access to a private library API.

Library browsing, chapter lists, searching, artwork, custom controls, and queue
selection depend on what the installed app exposes. Rumble and YouTube integration
controls their audio sessions; it does not display videos in iDrive. Custom package
names can still work through normal browser/session discovery, but the default
preference and package-specific fixes cover these known IDs:

| App | Packages |
| --- | --- |
| YouTube Music | `app.revanced.android.apps.youtube.music`, `app.rvx.android.apps.youtube.music`, `com.google.android.apps.youtube.music` |
| YouTube | `app.revanced.android.youtube`, `app.rvx.android.youtube`, `com.google.android.youtube`, `com.vanced.android.youtube` |
| Audible | `com.audible.application` |
| Rumble | `com.rumble.battles` |

The code includes automated regression tests. Playback with your particular
phone, player versions, and BMW/MINI still needs an in-car check.
