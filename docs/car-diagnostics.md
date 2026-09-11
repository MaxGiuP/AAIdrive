# Car and playlist diagnostics

This local collector helps distinguish a missing Android media queue from a car
connection problem. It reads an authorized phone through USB debugging; it does
not connect to or extract the BMW firmware. The phone can stay connected to the
BMW by Bluetooth while connected to the laptop by USB.

1. Install Python 3.8+ and Google's [Android SDK Platform Tools](https://developer.android.com/tools/releases/platform-tools).
2. Enable USB debugging on the phone, connect it to the laptop, and approve the
   phone's debugging prompt for that laptop. Keep AAIdrive connected to the car.
3. In YouTube Music ReVanced, start a playlist with several songs. Open AAIdrive's
   player and reproduce the missing song selection before collecting.
4. From the downloaded repository, run:

   ```text
   python scripts/collect-car-diagnostics.py
   ```

   Use `python3` on Linux if needed, or `py -3` on Windows. If Platform Tools is
   outside your PATH, add `--adb "C:\platform-tools\adb.exe"` (Windows) or
   `--adb /path/to/platform-tools/adb` (Linux).

If multiple devices/emulators are authorized, run `adb devices` locally and add
`--serial SERIAL` to select the phone. Device serials are not saved in the report.
No root access or wireless debugging is required. Disable USB debugging afterward
if you do not otherwise use it.

The script prints the location of `report.json` in a new folder under your home
directory, outside this repository by default. `--output-dir PATH` changes the
parent folder. Reports are private to your user on Linux; Windows uses the parent
folder's access permissions. Nothing is uploaded automatically.

The report contains versions for the three fork apps, Open Headunit, Android Auto,
and the fixed list of supported media packages. Media-session fields include
published action flags, queue length, and the current numeric queue item ID.
Raw dumps and logs are processed in memory and discarded: song titles, artists,
playlist names, media URLs, session tokens, vehicle identifiers, addresses,
notifications, and unrelated app names are not written. Log messages become
allowlisted counters and selected numeric fields; there is no full bug report.

Interpret `queue_length: 0` as no queue exposed in that session at that moment.
`null` means the field could not be read. An empty session list can mean the app
was inactive, access was restricted, or the Android dump format was unsupported.
`SKIP_TO_QUEUE_ITEM` advertises direct queue selection; `PLAY_FROM_MEDIA_ID` may
offer another route when the app publishes media IDs. These flags alone do not
prove selection succeeds. Android's [standard session dump](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/services/core/java/com/android/server/media/MediaSessionRecord.java)
normally omits the full queue item list, so `queue_item_ids` remains `null`.

Connection observations come from recent buffered logs and may describe an older
connection. Transport is inferred from AAIdrive's known port mapping; absence of
an observation does not mean the car is disconnected.

For a USB **device unsupported** error, reproduce it with AAIdrive open before
connecting the phone to the laptop. The collector retains up to 12 recent USB
profile broadcasts as fixed booleans such as `connected`, `configured`, `mtp`,
and `accessory`. Connecting to the laptop can add newer broadcasts: these samples
are phone USB modes, not proof of a BMW connection. An absent key means unknown,
and `accessory: true` does not identify the accessory as a BMW. The app does not
log its separate BMW accessory check, so the report marks that result unavailable.
No USB device names, serial numbers, or arbitrary extras are saved.

During projection, the updated app logs numeric pipeline measurements about every
five seconds. The collector keeps up to 12 recent samples: capture dimensions,
configured interval, JPEG quality, byte counts, unchanged frames, and average
copy/encoding/send times. `sends_per_s` measures completed local sends, not the
car's actual refresh rate. A static picture intentionally needs few or no new
sends. Empty samples can mean projection was inactive or an older build is installed.

For the BMW software details, enable **Show Advanced Settings** in AAIdrive, open
**Car Information**, and copy only `hmi.type`, `hmi.version`, `hmi.display-width`,
`hmi.display-height`, `navi`, `map`, and `tts` from **Detailed Car Capabilities**.
The nearby live data can include location; do not share the whole page. These
fields describe capabilities reported through the live BMW app protocol, not a
firmware image. Share the report and these selected fields only when you choose.

Parser/privacy checks use synthetic fixtures and never contact a device:

```text
python scripts/test-car-diagnostics.py
```
