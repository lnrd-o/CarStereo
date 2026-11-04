# Builds archivados

APKs compilados del launcher. **Cada versión se instaló y probó en el radio**;
se guardan aquí porque son la única copia que existe de varias de ellas.

| APK | Fecha | Qué trae |
|---|---|---|
| `CleanLauncher-base.apk` | 29 oct | Fuente tal cual del repo (NWD/Epica) |
| `CleanLauncher-Corsa.apk` (v1) | 29 oct | Base adaptado al Corsa |
| v2 – v4 | 29 oct | v4 añade **WaveView** (fondo FFT) |
| v5 | 29 oct | Servicios **HCN** (HcnBt/HcnRadio), DspActivity, SysEq, ArtLookup |
| v6 – v6.3 | 4 nov | **Pager** (varias vistas), **ColorActivity/Accent** (colores de sistema), SpeedDialView, RootCheck |
| v7 – v8.2 | 4 nov | **ChipDsp** (DSP del chip, CD3313) |
| v8.3 – v8.4 | 4 nov | **MapView**, **BatteryLogger/StereoVoltage** (voltaje, sensor interno), ObdClient, DtcInfo |
| v8.5 | 4 nov | + **BleLink** (enlace BLE) |

## ⚠️ Falta la fuente de v4–v8.5

El código fuente de **v4 a v8.5** (WaveView/FFT, HCN, ChipDsp/DSP, Pager,
ColorActivity/Accent, MapView, BatteryLogger/StereoVoltage, ObdClient/BleLink,
DtcInfo…) **no está en este repositorio ni en el equipo**: esas versiones solo
existen como APK compilado. La fuente de este repo corresponde a `base`/v1.

Para recuperar ese código hay que descompilar `CleanLauncher-Corsa-v8.5.apk`
(p. ej. con jadx) o pedirle al agente que lo generó su copia del proyecto.
