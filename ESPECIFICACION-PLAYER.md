# Especificación completa del estilo del player (documento normativo)

> Especificación facilitada por el usuario el 2026-10-28, "sin omisiones", como
> referencia para replicar el estilo del player al 100 %.
> Este documento es la fuente de la verdad del estilo: no resume ni interpreta.

---

## 1. Layouts

### Landscape — `activity_player.xml`

- Root: `RelativeLayout`, fondo `#000000`.
- `PlayerView` ocupa toda la pantalla, `use_controller=false`, `resize_mode=fit`.
- `ProgressBar` centrado (loading).
- Overlay vertical transparente:
  - Header con servidores (`headerLayout`), padding 8dp:
    - "Servidores:" blanco, 12sp, bold.
    - `HorizontalScrollView` → `serversContainer`.
    - Botón refresh 28dp (`ic_refresh`).
  - Centro: controles horizontales (inicialmente `gone`):
    - Rewind / PlayPause / Forward / Mute / Fullscreen, 55dp, `selectableItemBackgroundBorderless`.
  - Canales abajo: 135dp, fondo `#80000000`, padding 9dp, `HorizontalScrollView` → `channelsContainer`.

### Portrait — `activity_player_portrait.xml`

- Video container: 300dp arriba.
- `PlayerView` dentro con fondo negro.
- Loading centrado.
- Controles centrales: 55dp, incluye PiP (`ic_pip`).
- Header:
  - Back button 40dp (`ic_arrow_back`, tint blanco).
  - Título canal: blanco, 16sp, bold, maxLines 1, ellipsize end.
  - Servidores + refresh 28dp.
- Canales: 115dp, padding 8dp, superpuestos al video.
- Banner de anuncios: 60dp, fondo `#C9F0C5`, debajo del video.

## 2. Colores

- Fondo general: `#000000`.
- Overlay canales: `#80000000`.
- Banner: `#C9F0C5`.
- Texto: `#FFFFFF`.
- Bordes/botones: `selectableItemBackgroundBorderless`.

## 3. Controles e interacción

- Auto-ocultar: 5s.
- Tap alterna visibilidad.
- Si el tap cae en header / channels / centerControls, no ocultar.
- Loading: visible al cargar, se oculta a los 8s.
- Al estado READY, ocultar logs si visibles.
- PiP: oculta header / channels / centerControls; al salir, muestra controles.
- Config change: re-aplicar hideSystemUI.

## 4. ExoPlayer

- Media3 ExoPlayer.
- HLS con `HlsMediaSource` + `DefaultHttpDataSource`.
- SSL inseguro universal (TrustManager + HostnameVerifier true).
- `resize_mode=fit` (landscape) / default (portrait).
- Listeners: error, buffering, ready, ended.
- Release en stop/destroy (excepto PiP).

## 5. UIController

- Inflar layout según orientación.
- Métodos: show/hide controls, loading timeout, update buttons, PiP.
- `setLandscape` para icono fullscreen.

## 6. Iconos requeridos

- `ic_play`, `ic_pause`, `ic_rewind`, `ic_forward`, `ic_volume`, `ic_volume_mute`,
  `ic_fullscreen`, `ic_fullscreen_exit`, `ic_pip`, `ic_refresh`, `ic_arrow_back`.

## 7. Para replicar sin fallas

1. Copiar ambos layouts y drawables.
2. Implementar `UIController` completo.
3. Configurar ExoPlayer con SSL bypass.
4. Probar landscape + portrait.
5. Verificar PiP, mute, fullscreen, canales, servidores.

---

*Con esto, otro agente puede replicar el estilo exacto.*

---

## Nota de estado (actualizada el 2026-10-29 (v7.11))

**La v7.9 (versionCode 27) IMPLEMENTA esta especificación en PAISAJE**, con el
ajuste que pidió el usuario sobre los bordes:

- **Landscape** (`activity_player.xml`): `RelativeLayout` negro, `PlayerView` a
  pantalla completa con `use_controller=false` y `resize_mode=fit`, `ProgressBar`
  centrado (`cargandoPlayer`, se oculta a los 8 s o al `STATE_READY`) y overlay
  vertical transparente: cabecera de servidores **pegada al borde de arriba**
  («Servidores:» 12sp bold + `HorizontalScrollView` + refresh 28dp), controles del
  centro de 55dp (rewind / play-pause / forward / mute / fullscreen, inicialmente
  `gone`) y tira de canales de 135dp con `#80000000` y padding 9dp **pegada al
  borde de abajo**.
- Auto-ocultar a los 5 s. El toque en cabecera, controles o canales no oculta nada.
- **Sin botón de PiP ni barra de progreso en paisaje** (la spec no los pone): el PiP
  se entra con el botón de inicio.
- **Portrait** (`activity_player_portrait.xml`): desde la **v7.11 (versionCode 29)** es
  también la especificación de retrato, que es la del repo de referencia
  (`belkaperu/PelisLatino-Android-v7.8.5-SOLO-CODIGO`): `RelativeLayout` negro con la
  cabecera NEGRA fuera del vídeo arriba (atrás de 40dp + título 16sp bold y, debajo,
  «Servidores:» 12sp bold con su `HorizontalScrollView` y el refrescar de 28dp), vídeo
  de **300dp** justo bajo la cabecera, cargando centrado, controles **centrados de 55dp**
  sobre el vídeo (rewind / play-pause / forward / volumen / **PiP** / pantalla), tira de
  canales de **115dp** con `#80000000` y padding 8dp **superpuesta al borde de abajo del
  vídeo**, y el **marco del banner de 60dp `#C9F0C5`** debajo del vídeo (marco reservado,
  sin anuncio real). En retrato la cabecera, los servidores y los canales siguen a la
  vista aunque se oculten los controles (`aplicarVisibilidadExtra`), y al vídeo se entra
  en PiP con el botón de inicio.
- Cadena de datos: se sigue usando `DefaultDataSource` envolviendo la fábrica HTTP
  para abrir el `file://` del relay HLS. Copiar `HlsMediaSource` +
  `DefaultHttpDataSource` tal cual reintroduce el `ClassCastException` que se arregló
  en la v7.8.1.
