# v7.38.9 — «beIN Sports 1 funciona pero lo demás no»

**Fecha:** 2026-11-18 · `versionCode 88` / `versionName "7.38.9"`

---

## 1. Qué reportaste

> beIN Sports 1 funciona pero lo demás no.

Reproducido y medido contra la API real. Había **tres** causas, y solo la primera
explicaba el síntoma. La primera era mía.

---

## 2. Causa 1 (la que yo introduje): la consulta bloqueaba y colgaba del camino feliz

La v7.38.8 llamaba a la API **en serie** y **dentro** del `if (vv != null)` de
`resolverVivo`. Dos consecuencias graves:

1. **Si `resolverVivo` devolvía null, la app no preguntaba nunca.** Justo el caso de los
   canales que fallan.
2. **La consulta podía tardar 13 s.** Medido:

| canal | tiempo de `/opciones` |
|---|---|
| beIN 5 | **13,3 s** en *una sola* opción (`rap8.rapidbackend.shop` da timeout) |
| beIN 2 | **13,1 s** en otra (`bui8.buildapiplatform.cfd` da timeout) |

Y mi tope eran 2,5 s. O sea: en esos canales la consulta **caducaba siempre** y el plan B
se quedaba sin las opciones buenas. beIN 1 sí respondía en ~3 s, y por eso beIN 1 era el
único que funcionaba. Encaja exacto con lo que viste.

**Arreglo:** la consulta sale del camino crítico (`pedirOpcionesEnSegundoPlano`) y se lanza
**al principio** del hilo de fondo, antes de resolver nada. El tope sube a **12 s** porque
ya no frena a nadie: si llega tarde, se guarda y la usa el siguiente intento del plan B.

---

## 3. Causa 2: la API llama «muerto» a un DASH que emite

Aquí la API se equivoca, y mi filtro se lo creía. **Abu Dhabi Al Emarat**
(`live_tv_abudabyuae`):

| n | tipo | url | http | content-type | la API dice |
|---|------|-----|------|--------------|-------------|
| 1 | directa | `uselector.cdn.intigral-ott.net/ADE/ADE.isml/manifest.mpd` | 299 | — | no emite |
| 0 | directa | `admn-live-cdn-lb.starzplayarabia.com/…/emarat_tv_dash_enc/index.mpd` | **200** | **application/dash+xml** | **no emite** |
| 2 | directa | `…/emarat_tv_hls_nd/index.m3u8` | 403 | application/json | no emite |

`emitiendo: 0`. Pero la opción 0 es un **manifiesto MPEG-DASH válido** y su propio
`/stream?n=0` redirige a él (lo comprobé). La API solo sabe probar HLS, así que marca
`emite:false` a todo lo que no sea un `.m3u8` — y lo dice con todas las letras cuando
le pides el proxy:

> `GET /p/232425/live_tv_abudabyuae/0.m3u8` → **«esa opción no es un manifiesto HLS (usa su URL directa)»**

Con el filtro de la v7.38.8 esa url se tiraba y el canal quedaba **sin nada que probar**.

**Arreglo:** nuevo `podriaEmitir()`. Un `emite:false` solo se acepta como «no» cuando el
fallo es de verdad (**403, 404, 410** o timeout). Un **2xx con cuerpo**, o un `.mpd`/`.m3u8`
declarado, se conserva **al final** del plan B. Orden: confirmadas → dudosas → Drama. Si la
API acertó, el orden bueno no cambia; si se equivocó, hay segunda oportunidad.

La app ya trae `media3-exoplayer-dash`, así que el `.mpd` lo abre ExoPlayer directamente.

Y el proxy solo se pide para lo que la API **confirmó** como HLS: su propio proxy rechaza
lo demás, así que una url que se sabe que falla no ocupa turno.

---

## 4. Causa 3: un canal traía YouTube como «opción»

BeIN News trae, entre sus 4 opciones, `https://www.youtube.com/@beINSPORTS/live` (tipo
`otra`, http 200). Con el filtro nuevo entraba y podía gastar un turno cargando un YouTube
en el marco.

**Arreglo:** `esPaginaDePortal()` — se descartan YouTube, Vimeo, Dailymotion, Twitch y
Facebook watch, **en origen** (al construir la lista) y otra vez en el plan. Se corta a
propósito solo los portales: un `index.html` de los hosts de la cadena **sí** sirve (ahí
vive el manifiesto), y ese no se toca.

---

## 5. Lo que NO estaba roto

Lo bueno, medido, para no tocar lo que funciona:

| canal | ¿proxada? | emitiendo |
|---|---|---|
| beIN Sports 1 | sí (`/p/…/1.m3u8`, segmentos 10 s) | 3 de 7 |
| **beIN Sports 2** | sí (`/p/…/4.m3u8`, segmentos 6 s) | 3 de 8 |
| **beIN Sports 5** | sí | 2 de 7 |
| **BeIN News** | sí | 1 de 4 |
| **Alkass 2** | sí (segmentos 10 s) | 1 de 3 |

Alkass 2 no es beIN y su proxy funciona igual: **el mecanismo no era un caso especial de
beIN 1**. El problema era que la consulta no llegaba a completarse.

> Nota: la ruta `/canales/{codigo}/{tema}` está devolviendo **502** ahora mismo, mientras
> `/opciones` y `/p` responden. Si algún canal sale sin opciones, puede ser eso. La app no
> depende de `/canales`, pero conviene saberlo.

---

## 6. Un bug de proceso que casi se cuela

Al reescribir `pedirOpcionesALaApi` con reemplazo por índice, la versión vieja **quedó
duplicada**: dos métodos con la misma firma en la misma clase. Eso **no compila**, y
`PARSE_OK` no lo detecta (el archivo es sintácticamente válido).

Lo encontré con un contador de firmas. Dejo la herramienta y el resultado:

```
NO_DUP_OK  PlayerActivity.java (177 firmas)
NO_DUP_OK  ApiDrama.java (28 firmas)
NO_DUP_OK  DramaResolucion.java (26 firmas)
NO_DUP_OK  ServidoresDelCanal.java (22 firmas)
```

Misma lección que el `Set.add` de la v7.38.8: `PARSE_OK` no es compilar.

---

## 7. Pruebas

| prueba | resultado |
|---|---|
| `docs/repro-canales-que-no-abren.mjs` (**nuevo**) | **16/16** |
| `docs/repro-plan-de-ataque.mjs` | **18/18** |
| `docs/repro-api-proxy.mjs` | **19/19** |
| `docs/repro-servidores.mjs` | **14/14** |
| `docs/repro-planb.mjs` | OK |
| `docs/prueba_bein1.py` | **17/17** |
| JUnit | **9 nuevos** en `CanalesQueNoAbrenTest` + 2 en `ApiDramaTest` |

`CanalesQueNoAbrenTest` usa los JSON **literales** de Abu Dhabi y BeIN News medidos contra
la API. Sin red.

---

## 8. Lo que sigue sin estar verificado

Igual que la v7.38.8, y hay que decirlo: **no compilado aquí** (sin JDK ni SDK) y **no
probado en tu móvil**.

```bash
./gradlew :app:testDebugUnitTest assembleDebug
```

Abre un canal que **no** sea beIN 1 y mira el logcat:

```
api: 8 opciones, 3 emitiendo · 3 confirmadas · 6 para el plan B · NNN ms
   · API api·proxy [4] DIRECTA → …/p/232425/live_tv_beinsport2/4.m3u8
   · API api·marcador [4] EMBEBIDA → https://max11.maxcontenthub.shop/fk/…/index.html
```

Y para un canal DASH:

```
   · API api·directa [0] DIRECTA → https://…/emarat_tv_dash_enc/index.mpd
```

**Medido:** qué opciones emiten, que el proxy sirve manifiestos válidos para 5 canales
distintos y que la API tarda hasta 13 s. **No medido:** que ExoPlayer reproduzca el DASH y
los segmentos `.js` en tu móvil. Eso solo lo dice tu logcat.
