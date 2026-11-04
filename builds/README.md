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
| v9 | 4 nov | **Rehecha sobre la fuente local** (ver abajo): FFT 24 bandas, 3 vistas, mapa OSM, voltaje interno, EQ 24 bandas y apariencia |
| v9.1 | 4 nov | **HCN**: radio y BT de fábrica del Corsa + **RDS RadioText** |

## v9.1: HCN + RadioText

- **`HcnRadio`**: cliente Binder de `com.hcn.autoradio` (radio HCN). Lee
  frecuencia, PS y **RadioText** (`getRdsRT`, TX 54), presets, y controla
  seek/preset/play.
- **`HcnBt`**: cliente de `com.hcn.bluetoothservice` (música BT HCN) con ID3,
  estado de reproducción y comandos AVRCP.
- **`Profile.isHcn()`**: detecta equipos HCN (Corsa) frente a NWD (Epica).
- El inicio y la pantalla de Radio muestran el **RadioText** con marquesina.
- La pantalla Bluetooth usa el servicio HCN y enlaza al teléfono de fábrica
  (`com.autochips.bluetooth`).

## v9: continuación sobre la fuente local

`CleanLauncher-v9.apk` no se compiló sobre v8.5 (esa fuente no existe), sino sobre
la fuente de este repo. Implementa de nuevo lo pedido:

- **WaveView**: espectro FFT de 24 bandas como fondo, encendible/apagable.
- **MainActivity**: pager con 3 vistas (Clásico, Enfoque, Viaje); la tarjeta de
  audio muestra radio (RDS) o Bluetooth/canción según lo que esté sonando.
- **MapView/MapActivity**: mapa OpenStreetMap/CARTO con tema claro/oscuro y el
  color de acento del sistema.
- **StereoVoltage/BatteryLog/BatteryLogger/BatteryActivity**: voltaje del sensor
  interno del radio (sin OBD), muestra cada minuto, gráfico e historial de 7 días.
- **ColorActivity**: color de acento, tema y toggle del fondo FFT.
- **EqActivity**: 24 bandas de software mapeadas al DSP de 10 bandas (CD3313).
- `CleanLauncher-v9-compartir.apk`: variante sin fotos ni voces, lista para correo.

## ⚠️ Falta la fuente de v4–v8.5

El código fuente de **v4 a v8.5** (WaveView/FFT, HCN, ChipDsp/DSP, Pager,
ColorActivity/Accent, MapView, BatteryLogger/StereoVoltage, ObdClient/BleLink,
DtcInfo…) **no está en este repositorio ni en el equipo**: esas versiones solo
existen como APK compilado. La fuente de este repo corresponde a `base`/v1.

Para recuperar ese código hay que descompilar `CleanLauncher-Corsa-v8.5.apk`
(p. ej. con jadx) o pedirle al agente que lo generó su copia del proyecto.
