# v7.38.8 — «lo mismo que la API, pero en Android»

**Fecha:** 2026-11-18 · `versionCode 87` / `versionName "7.38.8"`

---

## 1. Qué pediste y qué se hizo

Dijiste: *la API de Drama funciona bien; haz lo mismo en Android.* Esto es eso, medido
contra la API real el 2026-11-18.

## 2. El hallazgo que lo cambia todo

La app llevaba versiones intentando resolver los marcadores `.LS.V2…` con el redirector
del sitio (`glbr`) y descartándolos si no salía nada. Al pedirle a la API las opciones de
**beIN Sports 1** (`/opciones/232425/live_tv_beinsport1`), la respuesta es esta:

| n | tipo | origen | url final | http | ¿emite? |
|---|------|--------|-----------|------|---------|
| 1 | marcador | `.LS.V2…_HD_/s` | `sup7.superdatanet.cfd/hd/azmyqsojuqbm/index.html` | **200** | **sí** |
| 2 | marcador | `.LS.V2…_4K_/s` | `ult11.ultraandroidsoftstarsfeedmoon.shop/fk/…/index.html` | **200** | **sí** |
| 0 | marcador | `.LS.V2…_SD_/s` | `spe6.speedsuperai.cfd/pages/alkoaqoohqze/index.html` | **200** | **sí** |
| 7 | directa | `primetv.cc.cd/…mono.tbin.m3u8` | la misma | 403 | no |
| 6 | embed | `quotarevival.net/e/…` | la misma | 403 | no |
| 3 | embed | `ordernap.net/e/…` | la misma | 403 | no |
| 5 | marcador | `.LS.V2daddy_91/s` | `tossequip.net/e/…` | 403 | no |

**Los 3 que emiten son exactamente los `.LS.` que la app tiraba. Los 4 que la app
intentaba dan 403.** Eso explica el cuadro negro sin más hipótesis.

## 3. Cómo se abre, sin WebView

La API publica, en la **misma URL** de la página, un manifiesto HLS completo:

```
GET /p/232425/live_tv_beinsport1/0.m3u8
→ #EXTM3U
  #EXT-X-VERSION:3
  #EXT-X-TARGETDURATION:10
  #EXT-X-MEDIA-SEQUENCE:1794925067
  #EXTINF:10.000000,
  /ps/232425/live_tv_beinsport1/0/k9x_1794919732-1794925067.js
  …
```

Sus segmentos son `.js` servidos bajo un manifiesto con forma de `.m3u8`, a 10 s. La ruta
`/ps/…` es **absoluta**, así que ExoPlayer la resuelve solo.

Por eso la vía corta es apuntar ExoPlayer a `/p/….m3u8`: el proxy de la API ya inyecta el
UA y el Referer que el CDN exige. **Sin WebView, sin sniffer, sin CORS.** Eso es
literalmente «lo mismo que la API, en Android».

## 4. Qué se construyó

### `ApiDrama.java` (nuevo)

| método | qué hace |
|---|---|
| `leerOpciones(json)` | puro: lee `/opciones`. Devuelve `null` en vez de lanzar |
| `aOpciones(base, codigo, id, o)` | por cada opción que emite: **playlist proxada** (DIRECTA) y detrás su **página original** (EMBEBIDA, plan B) |
| `urlPlaylist(…)` / `urlStream(…)` | arman `/p/…` y `/stream/…` |
| `reescribir(m3u8, …)` | pasa a absolutas las rutas relativas y el `URI=` de las claves ClearKey |
| `pedirOpciones(…)` | GET con 2,5 s de tope. **Nunca lanza** |
| `baseConfigurada/guardarBase` | host configurable; `""` = apagado; sin configurar = el de siempre |
| `estaViva/sesionSinApi/marcarCaida` | si falla **una vez**, no se vuelve a preguntar en la sesión |

### `DramaResolucion.planDeAtaque(memoria, api, drama)` (nuevo)

El orden en que se prueba cada servidor:

1. **la memoria del canal** — lo que en este móvil ya se vio emitir, con su UA y Referer exactos;
2. **lo que la API probó**, *en el orden de la API* — dentro va primero la playlist proxada de cada opción y detrás su página;
3. **lo que Drama entrega**, con el orden de siempre.

> **Por qué un método nuevo y no reutilizar `conRecordados`:** aquel manda las nuevas por
> `ordenarSinRepetir`, que pone **todas** las EMBEBIDA antes que las DIRECTA. Aplicado a
> las opciones de la API eso pondría la página lenta delante de la playlist proxada
> rápida, justo al revés. Hay una prueba que fija las dos conductas (`elPlanNoReordenaLoQueDiceLaApi`).

### `PlayerActivity`

- `pedirOpcionesALaApi(canal)` en el **mismo hilo de fondo** del arranque, justo después de
  `cargarServidoresDelCanal`. El `id_live` sale de `DramaSesion.idDeCanal` y el `codigo` del
  FG guardado (si no hay, `232425`).
- `siguienteOpcionUtil(…)` pasa a tener **5 argumentos**: `(url, backup, probados, recordados, deLaApi)`.
  Se conservan las sobrecargas de 3 y 4 para no tocar el código ni los tests que ya las usaban.

> **Nota de honestidad:** la sobrecarga de 4 argumentos que la v7.38.7 decía tener **no
> existía en el código**. `ServidoresDelCanalTest` la llamaba, así que esa clase no
> compilaba. Ahora existe y hay un test que la fija (`laSobrecargaDeCuatroSigueExistiendo`).

## 5. Lo que NO se hizo

- **No hay dependencia dura del host.** Vive en un plan gratuito que duerme. Si no contesta
  una vez, la app deja de preguntar en toda la sesión y se comporta **exactamente** como la
  v7.38.7. El peor caso es un único arranque 2,5 s más lento, y solo si estaba dormido.
- **No se redistribuye el catálogo de Drama.** Solo se consultan opciones de canales que el
  usuario ya está abriendo, con su propia sesión.
- **No se ha compilado aquí** (no hay JDK ni SDK en este entorno) y **no se ha probado en
  un móvil**. Ver §7.

## 6. Pruebas

| prueba | resultado |
|---|---|
| `docs/repro-api-proxy.mjs` — armado de URLs + reescritura de manifiesto | **19/19** |
| `docs/repro-plan-de-ataque.mjs` — orden, dedupe, casos degradados | **18/18** |
| `docs/repro-servidores.mjs` (v7.38.7) | **14/14** |
| `docs/repro-planb.mjs` | **8/8** |
| `docs/prueba_bein1.py` | **17/17** |
| JUnit en el árbol (71 clases) | **557** `@Test`, de los que **29 son nuevos**: 18 en `ApiDramaTest`, 11 en `PlanDeAtaqueTest` |

`ApiDramaTest` usa `app/src/test/resources/opciones_bein1.json`: copia **literal** de la
respuesta real de beIN 1 (7 opciones, 3 emitiendo). No toca la red.

### Un fallo que salió al simular

Mi primera simulación en JS daba 12/18. El culpable era **la simulación**, no el Java: en
JavaScript `Set.add()` devuelve el propio `Set` (siempre *truthy*), así que
`if (!vistas.add(k)) continue;` **nunca** deduplicaba. En Java `Set.add` devuelve `boolean`.
Se corrigió el simulador con un ayudante `nuevo(set, k)` y quedó 18/18. Lo dejo escrito
porque es un error fácil de repetir.

## 7. Lo que falta para decir «esto funciona»

1. **Compilar**: `./gradlew :app:testDebugUnitTest assembleDebug`
2. **Instalar** y abrir **beIN Sports 1**. En el logcat debe verse:
   ```
   api: 7 opciones, 3 emitiendo · 6 utiles para el plan B · NNN ms
      · API api·proxy [1] DIRECTA → …/p/232425/live_tv_beinsport1/1.m3u8
      · API api·marcador [1] EMBEBIDA → https://sup7.superdatanet.cfd/hd/…/index.html
   ```
3. **Cronometrar** hasta la primera imagen.

**Lo que está medido**: la API sirve opciones y manifiestos válidos. **Lo que no**: que
ExoPlayer reproduzca esos segmentos `.js` en tu móvil. Eso solo lo dice tu logcat.

## 8. Para apagarlo

Preferencias `api_drama` → clave `base`:

- sin configurar → el host por defecto
- `""` (vacío) → apagado, la app se comporta como la v7.38.7
- otra URL → ese host
