# Source attribution

`screen-mirror` and `screen-mirror-lib` derive from BimmerGestalt/IDriveConnectAddons,
commit `270032dac044140aafd59ad91723040bd326b232` (MIT).
https://github.com/BimmerGestalt/IDriveConnectAddons/tree/270032dac044140aafd59ad91723040bd326b232
The original license is retained in both directories. This fork modernizes capture,
adds Open Headunit integration and iDrive controls, and changes the application ID.

Open Headunit is a separately installed application under AGPL-3.0. Its receiver
implementation is not linked into the Projection app. The unchanged receiver APK
is bundled in the separate Setup APK; see [source and license notices](../third-party/open-headunit/README.md).
