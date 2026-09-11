---
layout: page
title: Connection tips
permalink: /connection
---

The official MyBMW/MINI app (or the older Connected app on supported setups)
provides the BMW Apps connection used by AAIdrive. Bluetooth phone/audio pairing
alone is not an Apps connection. First check that AAIdrive connects and its apps
appear in iDrive.

# USB

USB compatibility depends on the phone, Android version, BMW connection app, and
head unit. The upstream maintainer [reported MyBMW USB problems in March 2025](https://github.com/BimmerGestalt/AAIdrive/discussions/517),
including failures on Android 15. This is a compatibility report, not a diagnosis
of every newer phone. A working Bluetooth connection does not guarantee USB works.

If the car says **device unsupported**:

1. Use a data-capable cable and the car's media USB port. Unlock the phone.
2. If Android offers **File Transfer**, select it instead of charging only.
3. If iDrive offers **Apps** or **USB Accessory Mode** for this connection, enable it.
4. Check AAIdrive's connection status. Charging, file transfer, and a working
   Bluetooth Apps session do not prove that the Apps transport switched to USB.

These steps check BMW Apps compatibility; they do not enable Android Auto or
CarPlay. If USB still fails, keep a working Bluetooth Apps connection and use the
[diagnostics collector](car-diagnostics.md) to investigate. The car's error alone
does not identify which connection stage failed.

![USB Charging](images/usb-charging.png)
![USB Settings](images/usb-charging2.png)
![USB File Transfer](images/usb-transfer.png)

# Bluetooth

Keep the phone's Bluetooth audio connected for music, and enable the car's **Apps**
option for the phone where available. If Apps does not connect automatically,
toggling the phone's Music/Audio connection in iDrive can trigger the BMW connection
app to try again. This is a troubleshooting step, not a guaranteed fix.

Bluetooth projection sends compressed frames through BMW Apps. A successful
connection does not promise native Android Auto or CarPlay smoothness. If pairing
only offers phone/audio/apps, see [what that means for CarPlay](car-compatibility.md#when-the-pairing-menu-only-offers-phone-audio-and-apps).

To improve reliability for Bluetooth connections, you should add the Connected app to the whitelist to prevent the phone from [killing the app](https://dontkillmyapp.com/).

![Connected App Info](images/memory-killing.png)
![App Whitelist](images/memory-applist.png)
![Change Whitelist Setting](images/memory-choose.png)
![Whitelisted Connected App](images/memory-success.png)

Some phones might apply their own restrictions, so [check this site](https://dontkillmyapp.com/) for more tips specific to your phone.
