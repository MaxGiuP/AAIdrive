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
