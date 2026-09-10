# Performance and navigation improvements

This update keeps the [ReVanced/media improvements](maxgiup-media.md) and focuses on
avoiding repeated work, cleaning up disconnected media requests, and improving
Google Maps destination sharing.

## Less repeated media work

- Parsed queues are reused while their tracks and order stay unchanged. Reordering,
  replacement, artwork, and action extras still update correctly.
- Playlist rendering checks for changes before allocating rows and cleaning every
  song title. Scrolling titles and buffering update the affected rows; unchanged
  queue artwork is not repeatedly recompressed and retransmitted.
- Metadata snapshots that share the same bitmap skip expensive pixel comparisons.
  The text-only instrument-cluster playlist ignores artwork-only changes.
- Media browsing and connection waits respond directly to callbacks instead of
  waking repeatedly to poll. Switching/disconnecting players cancels pending work
  and removes scheduled progress updates. Disconnected searches finish promptly.
- Spotify's session-refresh workaround allows one request at a time, at most once
  every five seconds, and stops when the controller disconnects.

## Navigation without API keys

Share a destination or directions link from Google Maps to AAIdrive. AAIdrive
resolves the destination and asks BMW/MINI's built-in navigation to route there.
The improved parser handles shared `geo:` and `google.navigation:` links, multiline
share text, encoded place names, modern directions URLs, and Maps short links.
For a route with several stops, it sends the final destination; it does not import
the entire itinerary, traffic data, or Google's chosen route.

The selected destination takes precedence over a map's camera coordinates or an
earlier waypoint. Short-link resolution has bounded redirects and network timeouts.
Failed lookups stop the progress indicator, and returning to the share window does
not automatically submit the same destination again.

Google's [Maps URLs interface](https://developers.google.com/maps/documentation/urls/get-started)
does not require an API key. Address lookup still needs the phone's geocoder and
short links require a network connection.

## Embedded maps and Android Auto

This APK uses AAIdrive's `nomap` variant. It sends destinations to the
car's navigation; embedded Google Maps tiles are not included.
Upstream's experimental Google Maps and Mapbox variants remain in the source, but
are not included in this download. The Google Maps SDK requires a
[configured Cloud project, billing, and an API key](https://developers.google.com/maps/documentation/android-sdk/get-api-key).

AAIdrive connects through BMW Connected Apps. Adding an Android Auto client library
would not turn that connection into an Android Auto projection receiver. Google's
[Android Auto platform](https://developer.android.com/training/cars/platforms/android-auto)
expects a compatible head unit. AAIdrive already provides its own music browsing,
messaging/replies, voice-assistant access, calendar integration, and native navigation
handoff over the BMW interface.

## Optimized APK

The `optimized` build type reuses upstream's release R8 rules, removes unused library
code, and disables debugging. It keeps the same local signing certificate as the
first fork APK and increases the version code, so this update can install over it.
No signing key is checked into the repository. A build made with another machine's
development key cannot update this APK unless that machine has the original key.

This build does not include analytics or embedded maps. Its smaller download is a
measured size improvement; phone startup speed and battery use have not been measured.

From a clean source commit with the Android SDK, JDK 17+, Python 3, and the upstream
external resources installed, run:

```sh
./scripts/build-apk.sh
```

The script runs unit tests, builds the optimized APK, verifies its signature,
alignment, source version, and update compatibility, and updates
`apk/AAIdrive-MaxGiuP.apk`, its SHA-256 checksum, and build metadata.
Increment `AAIdrive_ForkVersionCode` in `gradle.properties` before shipping
another update. Actual phone/player and BMW/MINI testing is still needed.
