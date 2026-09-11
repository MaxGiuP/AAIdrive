# Native navigation, Google Maps, and CarPlay

The downloadable fork APK uses BMW/MINI's native navigation for destination
handoff. No Google Maps API key, paid API subscription, or embedded map renderer is
needed. Select a destination in Google Maps, share it to BMW/MINI Navigation while
AAIdrive is connected, and check the destination on the car's navigation screen.

## Can BMW follow the exact Google Maps route?

The Connected Apps interface used here accepts one destination through an RHMI
`navigate` action with an `Address` model. BMW calculates the route itself. The
interface inspected does not provide a way to import Google's road-by-road path,
traffic choices, avoidances, or a list of intermediate stops into native guidance.
A directions link with multiple stops sends its final destination.

The separate BMW `map_importData` API accepts KMZ overlays for an application's
mini-map. It is not evidence of native route import: see the upstream
[RHMI map analysis](https://bimmergestalt.github.io/BMWConnectedAnalysis/rhmi-map/).
Sending a succession of destinations would replace guidance repeatedly; this fork
does not present that as route preservation.

## Google Maps from an Android phone on the car screen

Android Auto displays supported phone apps on a compatible vehicle system. The
Google Maps app handles its own services; an end user does not need a developer
API key. AAIdrive's BMW Connected Apps connection is a different interface from
Android Auto or CarPlay. See Google's
[Android Auto overview](https://developer.android.com/training/cars/platforms/android-auto).

This fork now includes an experimental [AAIdrive Projection companion](android-auto-projection.md).
It runs alongside separately installed Open Headunit, which receives Android Auto on
the same phone. Android screen capture sends that receiver view to the BMW through
Connected Apps; Google Maps continues to calculate and display its own route.
The companion also forwards iDrive navigation controls to Open Headunit.
No Google Cloud account or API key is needed for this path.

Use the [single Setup APK](one-apk-setup.md), which includes AAIdrive, Projection
and Open Headunit, then follow its guided setup. Recent Android Auto versions require manually starting the
Android Auto developer headunit server. This has not yet been tested on a phone/car.
The final link transfers JPEG frames, so BMW transport bandwidth still limits
smoothness, especially over Bluetooth. Native BMW destination handoff remains
available independently.

## Can an ordinary APK make an Android phone act as a CarPlay phone?

No working, reusable transmitter implementation for this fork has been established.
The requested bridge is not implemented in the downloadable APK.

Apple's [wireless CarPlay architecture](https://developer.apple.com/videos/play/wwdc2017/717/)
uses Bluetooth discovery and iAP2 setup, then Wi-Fi for audio, video, and control.
An Android sender would need to implement the phone side of those protocols;
changing the phone's Bluetooth name or USB mode does not implement them.
CarPlay receiver/head-unit projects implement the opposite side of the connection.
No compatible sender for a stock phone and this BMW has been established here.

If the car's pairing menu only offers phone/audio/apps, there is no CarPlay
connection offered in its current setup. Use the existing BMW Apps path described
above. The [compatibility guide](car-compatibility.md) explains why this does not
by itself identify the hardware or establish whether a retrofit is possible.

Vehicle telephone/media version numbers are not a CarPlay entitlement or a complete
head-unit identification. The navigation/head-unit version and whether the car
offers an active wired or wireless CarPlay endpoint must be established before
evaluating a bridge or retrofit. This fork does not change vehicle firmware.

## Google API costs

Checked against Google's global pay-as-you-go pricing on 10 September 2026. Prices
below are USD; features can invoke separately billed services.

| Service | Monthly free usage | Initial price above the free cap |
| --- | --- | --- |
| Standard Maps SDK map display | Unlimited | No charge for this SKU |
| Compute Routes Essentials | 10,000 requests | $5 per 1,000 requests |
| Navigation SDK navigation requests | 1,000 requests | $25 per 1,000 requests |

Thus 100 basic route requests per month would be within that SKU's free cap;
12,000 would cost $10 for that SKU at the initial tier. Places searches, Street
View, advanced routing, and other services can add charges. A billing-enabled
project and credentials are still required for the Maps SDK even when usage is
free. No Google billing project is configured by this fork.

Sources: [Google Maps Platform pricing](https://developers.google.com/maps/billing-and-pricing/pricing)
and [Maps SDK for Android billing requirements](https://developers.google.com/maps/documentation/android-sdk/usage-and-billing).
Using the consumer Google Maps app or AAIdrive's native destination handoff does
not create these developer API charges.
