# v7.39.2 — el embed encima del vídeo: tres defectos del logcat de beIN 1

**Fecha:** 2026-11-18 · `versionCode 93` / `versionName "7.39.2"`

---

## 1. Lo que dice tu logcat, y es importante: **el vídeo sí funcionaba**

```
13:49:22.155  api: 9 opciones, 4 emitiendo · 4 confirmadas
13:49:22.177  🏁 carrera: 2 opciones a la vez (la API respondió)
13:49:23.184  🏁 carrera: ninguna responde (6 probadas)
13:49:23.191  ↩️ sin stream nativo; se prueba el embed -> ordernap.net     ← embed ENCIMA
13:49:24.229  🏁 carrera ganada en 1038 ms (manifiesto #EXTM3U) -> /p/…/2.m3u8
13:49:24.244  vídeo bueno → ExoPlayer                                    ← debajo del iframe
13:49:29.805  ✅ Ready
13:49:55+     🌍 /p/232425/live_tv_beinsport1/2.m3u8 → HTTP 200 (una y otra vez)
13:49:53.249  plan B: probando -> PAGINA → http://.LS.V2daddy_91/s        ← encima del vídeo
```

El `/p/…/2.m3u8` respondió **200 veinte veces seguidas**. El canal emitía. Lo que fallaba es
que **el embed del plan B se quedó encima del reproductor** y, a los 30 s, su vigilante
intentó cambiar a `http://.LS.V2daddy_91/s`, que no es ni un host.

## 2. Los tres defectos

### a) El embed nunca se retiraba al llegar un vídeo nativo

El fallo de fondo. `embeddedEnVentana` seguía en `true` y el WebView seguía **visible**
encima de `playerView`. Se ponía un vídeo nativo **debajo de un iframe** y el iframe ganaba
la vista.

**Arreglo:** `retirarEmbedSiEstaba()` — al cargar una directa se apaga
`embeddedEnVentana`, se limpia el embed y se manda el WebView a `about:blank` (oculto).
Al apagarse esa bandera, el vigilante de 30 s sale solo en su primera línea y **ya no salta
a otro «servidor»**.

### b) Los marcadores `.LS.` entraban como servidores

```
13:49:21.715  descartada (UnknownHostException, 1 ms): http://.LS.V2…_HD_/s
13:49:21.717  descartada (UnknownHostException, 0 ms): http://.LS.V2LOAD_BALANCER…/s
13:49:21.718  descartada (UnknownHostException, 1 ms): http://.LS.V2…_4K_/s
```

Cuatro de las seis de la carrera eran **órdenes para el redirector**, no urls: empiezan por
punto, así que no tienen host y mueren en 1 ms. Peor: llegaban mal clasificadas como
`PAGINA`/`EMBEBIDA`, así que el plan B las seguía ofreciendo — y acabó abriendo
`http://.LS.V2daddy_91/s` en el WebView.

**Arreglo:** `DramaResolucion.esMarcadorSinClasificar()` (por **forma**, no por etiqueta) y
`CarreraDeOpciones.esCandidata()` los descarta. Efecto medido: **la carrera de ese canal baja
de 6 hilos a 2**.

### c) La ganadora podía llegar después de que se hubiera puesto el embed

Entre `:23.191` (embed) y `:24.229` (ganadora) hay **1,04 s**: la primera pasada no encontró
nada, el plan B puso el embed, y la segunda pasada (la de la API) sí encontró un manifiesto
bueno. Iba bien encaminada, pero el embed ya estaba puesto y armado.

**Arreglo:** `hayFuentePuestaYSonando()` — si **ya hay imagen confirmada** o el embed **ya
está pidiendo vídeo**, una ganadora tardía no toca nada. Si el embed está puesto pero aún
sin imagen, la ganadora **entra y retira el embed** (es lo que hace el punto a).

Y de paso: el apunte de «servidor recordado» (3 s después) lleva ahora la generación del
canal, así que un apunte del canal viejo ya no cae en el nuevo. (En tu log se repitió 8
veces en 40 s.)

## 3. Medido — `node docs/repro-v3914.mjs` · **12/12**

| | sin arreglo | con arreglo (7.39.2) |
|---|---|---|
| 13:49:23 embed puesto | sí | sí |
| 13:49:24 vídeo bueno llega | al reproductor **debajo del embed** | al reproductor, **embed retirado** 🧽 |
| 13:49:53 vigilante de 30 s | salta a `http://.LS.V2daddy_91/s` | sin vigilante armado |
| hilos de la carrera | 6 (4 morían en 1 ms) | **2** |

## 4. Verificado / sin verificar

**Verificado:** `V3914_OK 12/12` (nuevo) · `V3913_OK 14/14` · `V3912_OK 17/17` ·
`V3911_OK 11/11` · `V390_OK 19/19` · `V389_OK 16/16` · `PLAN_OK 18/18` · `PROXY_OK 19/19` ·
`SIM_OK 14/14` · `MarcadoresYFuentePuestaTest` **7 `@Test`** · 16 ficheros con sintaxis OK.

**Sin verificar:** no compilado (sin JDK/SDK) y no probado en tu móvil.

En logcat, en ese mismo canal, ahora deberías ver:

```
🏁 carrera ganada en 1038 ms (manifiesto #EXTM3U) -> /p/…/2.m3u8
🧽 el vídeo nativo manda: se retira el embed que estaba en la ventana
✅ Ready
```

y **ya no** el `plan B: probando -> PAGINA → http://.LS.V2daddy_91/s`.
