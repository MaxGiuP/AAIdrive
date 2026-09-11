# Reading BMW compatibility information

BMW's [UPD11011 update notes](https://static.bmw.com/content/dam/bmw/staticContent/static_bmw_com/bluetooth/updates/bmw/pdf/Readme_UPD11011_en.pdf)
list TT/MT versions, including the `001.058.004` update section, under connectivity
updates. They describe telephone interoperability changes. These numbers alone
do not identify the complete head-unit hardware, iDrive generation, display
capabilities, or available smartphone-projection features. That is the limit of
what can be inferred from this update document.

A small screen also does not establish an older iDrive generation. BMW UK's
[1 Series specification effective production week 44 of 2017](https://www.press.bmwgroup.com/united-kingdom/article/attachment/T0274992EN_GB/395392)
lists the 118i SE with “BMW Navigation system (latest ID6 generation)” and a
6.5-inch monitor; the same page specifies an 800×480 display. This documents one
market's offered specification, rather than identifying any particular car's
installed equipment or later modifications.

AAIdrive uses the connected car's reported capabilities and display dimensions
instead of choosing a resolution from its model year, screen diagonal, or TT/MT
version. A working Connected Apps connection remains necessary. The
[projection guide](android-auto-projection.md) describes the frame limits and
diagnostics available for checking an actual connection; no vehicle-specific
performance result is claimed here.

## When the pairing menu only offers phone, audio, and apps

Treat CarPlay as unavailable in the current setup if **Manage mobile devices →
Add new device** does not offer **Apple CarPlay**. This does not distinguish
missing equipment from a feature that is not activated; it is not enough evidence
to prescribe coding, a firmware update, or replacement hardware.

The same late-2017 BMW specification lists CarPlay as optional equipment `6CP`,
requiring the Professional navigation package `ZNP`, and describes a wireless
connection. An Android phone's USB “device unsupported” message neither identifies
the head unit nor establishes that a CarPlay receiver is available.

For a car where AAIdrive already works over Bluetooth, use that BMW Apps connection
for the music interface and [native BMW destination handoff](maxgiup-navigation-options.md).
The [experimental projection companion](android-auto-projection.md) can use the
same connection, with its existing bandwidth limits. Installing Setup does not
activate CarPlay or native Android Auto in the head unit.

To investigate USB or identify the reported software, follow the
[connection guide](connection.md) and [diagnostics guide](car-diagnostics.md).
Read `hmi.type` and `hmi.version` while AAIdrive is connected before considering
equipment-specific changes. A laptop can collect information from the Android
phone; plugging it into the car's media USB port does not expose BMW firmware.
