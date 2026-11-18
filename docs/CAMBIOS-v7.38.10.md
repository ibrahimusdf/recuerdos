# v7.38.10 — dos correcciones sobre la 7.38.9

**Fecha:** 2026-11-18 · `versionCode 89` / `versionName "7.38.10"`

---

## 0. Primero, lo más importante

Dijiste: *«se mejoró mucho pero faltan canales que no funcionan»*.

Para cerrar el resto **necesito los nombres**. Sin ellos solo puedo cazar causas
sistemáticas, y ya llevo dos versiones arreglando lo que puedo medir. Con la lista de
canales que fallan (o mejor, el logcat desde que abres uno) voy directo al problema.

Lo que sigue es lo que **sí** encontré midiendo, y estaba mal.

---

## 1. Mi «rescate de DASH» de la 7.38.9 estaba mal

En la 7.38.9 dije que la API se equivocaba al marcar `emite:false` a un DASH, y rescaté
cualquier `.mpd` con 2xx. **Era un error.** Los `.mpd` reales de esta API son:

```
admn-live-cdn-lb.starzplayarabia.com/…/emarat_tv_dash_enc/index.mpd
otte.live.fly.ww.aiv-cdn.net/…/dash/enc/ud6bnhthpj/…/cenc.mpd
otte.cache.aiv-cdn.net/…/dash/enc/72sjo8hygl/…/cenc.mpd
```

`cenc` = **Widevine**. No se abre sin licencia. Y el propio diagnóstico del proyecto
(`docs/DIAGNOSTICO-CANALES-DRAMA-v7.38.3.md`, línea 275) ya lo decía:

> «los de starzplay son Widevine (imposible con clave suelta, comprobado en el propio .mpd)»

Así que en la 7.38.9 metí urls **imposibles** en el plan B de esos canales: en vez de un
canal en negro rápido, dos turnos gastados en algo que nunca iba a abrir.

**Arreglo:** `esDashCifrado()`. Se descarta un `.mpd` si trae `cenc`, `_enc`, `_drm`,
`/dash/enc/`, `dash_enc`, si es de `starzplay`/`intigral`, o si responde **299** (estado
que ningún CDN usa para un manifiesto válido; en esta API aparece justo en ese `.mpd`).

Abu Dhabi pasa de «2 opciones que nunca abrirán» a **0**, que es la verdad.

## 2. Un host de la cadena no debe pasar por el WebView

Este sí explica canales en negro **con un servidor bueno en la lista**. Medido con
**Bein Max. 1** (`live_tv_beinsport1max`):

| n | tipo | url | http | ¿emite? |
|---|------|-----|------|---------|
| 0 | marcador | `max10.maxwordsandroidios.shop/pages/…/index.html` | 200 | **sí** |
| 2 | directa | `otte…/dash/enc/…/cenc.mpd` | 200 | no (Widevine) |
| 3 | directa | `otte.cache…/dash/enc/…/cenc.mpd` | 200 | no (Widevine) |
| 5 | embed | `tinslime.net/e/…` | 403 | no |
| 1 | embed | `ordernap.net/e/…` | 403 | no |
| 4 | marcador | `tossequip.net/e/…` | 403 | no |

**Una sola opción sana.** La app la metía en el WebView… y la página se iba a su propio
reproductor, que no entrega el manifiesto sin sesión. Canal en negro con «un servidor
bueno» en la lista.

**Arreglo:** `DramaResolucion.esHostDeLaCadena()` → `abreEnExoPlayerDirecto()`. A un host
de la cadena el manifiesto se le pide **directamente** (su `index.html` responde
`#EXTM3U`, medido), así que va a ExoPlayer y no al WebView.

La detección es **por forma de la url**, no por lista de dominios: la cadena rota los
dominios cada pocos días (`superdatanet`, `maxcontenthub`, `maxwordsandroidios`,
`datahostingapiportal`, `rapidbackend`, `buildapiplatform`, `ultraiossoftstarsfeedmoon`…).
Se reconoce por `/hd/`, `/fk/`, `/sd/`, `/pages/`, `/mirsp/` + `index.html`.

## 3. Dos fallos que salieron al probar esto

- **`contains("dramalive")` marcaba el proxy de la API como host de la cadena.**
  `/p/232425/x/0.m3u8` habría ido «directo a ExoPlayer» por el camino equivocado. Ahora los
  proxies (`/p/`, `/ps/`) se excluyen explícitamente. Lo cazó una prueba, no una lectura.
- **`intigral-ott.net/…/manifest.mpd` no llevaba `cenc` en la ruta** y se colaba como
  «limpio». Se añadió la señal del 299 y de los CDN `starzplay`/`intigral`.

## 4. Portales ampliados

`esPaginaDePortal()` ahora también corta `shoof.alkass.net`, `alkass.net/live`,
`aflam4you` y `dlive.sx/cast` (webs con reproductor propio que aparecen como «opción»).
**Nunca** corta un host de la cadena: ahí vive el manifiesto.

---

## 5. Pruebas

| prueba | resultado |
|---|---|
| `docs/repro-v390.mjs` (**nuevo**) | **19/19** |
| `docs/repro-canales-que-no-abren.mjs` | **16/16** (corregido: antes exigía rescatar Widevine) |
| `docs/repro-plan-de-ataque.mjs` | **18/18** |
| `docs/repro-api-proxy.mjs` | **19/19** |
| `docs/repro-servidores.mjs` | **14/14** |
| `docs/repro-planb.mjs` | OK |
| `docs/prueba_bein1.py` | **17/17** |
| JUnit | **10 nuevos** en `DashCifradoYCadenaTest`; 2 pruebas de la 7.38.9 **invertidas** con nota |

`DashCifradoYCadenaTest` usa los JSON literales de Abu Dhabi y BeIN Max 1. Sin red.

> Dejé la prueba vieja `unDashQueEmiteNoSeTira` **invertida y con el porqué escrito**, no
> borrada: así el error no puede volver sin que salte.

---

## 6. Lo que sigue sin verificarse

**No compilado** (sin JDK ni SDK) y **no probado en tu móvil**.

```bash
./gradlew :app:testDebugUnitTest assembleDebug
```

Y para el resto de canales, lo que de verdad ayuda:

```
adb logcat -s BLABONGO_DEBUG | grep -E "api:|plan B|directa|servidores"
```

Ábreme uno que falle y con eso sé si es Widevine (imposible), 403 del CDN, o un host que
todavía no reconozco.
