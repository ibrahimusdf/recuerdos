# v7.39.3 — la regresión de la 93 y los tres frenos que quedaban

**Fecha:** 2026-11-18 · `versionCode 94` / `versionName "7.39.3"`

Esta versión nace de tres logcats tuyos del mismo día: **14:23** (`beinsportnews`),
**14:25–14:26** (`beinsport1`) y **15:07** (`aljeria1`). Los tres señalan cosas distintas,
y una de ellas es culpa mía.

---

## 1. La regresión: en la 93 **ningún canal de beIN podía abrir**

Es lo primero porque es lo que rompí yo.

La v7.39.2 añadió una guarda para que los marcadores `.LS.` no llegasen al plan B. El
razonamiento era correcto; el sitio, el peor posible: la puse en la **primera línea** de
`avisarSinStream()`, mirando la **lista** de opciones del canal.

```
15:07:36  🔒 canal con marcador .LS.        ← aljeria1, sin probar NADA
14:25:25  🔒 canal con marcador .LS.        ← beinsport1, sin probar NADA
```

Y **todos** los canales de beIN traen marcadores `.LS.` en su lista (son 7 de sus 9
«servidores»: `V2live_tv_custom_handler_…`, `V2LOAD_BALANCER…`, `V2daddy_91`). Resultado:
desde la 93 la app se rendía antes de intentar `ordernap` / `quotarevival`, que es la
**única vía real** de esos canales. La 92 sí los abría; la 93, ninguno.

**Arreglo.** La guarda de la *fuente* sigue primero (si el stream que tenemos en la mano es
un marcador, no hay nada que hacer). La guarda de la *lista* pasa al **final**, donde
significa lo que debe: «me he quedado sin nada que probar y lo único que traía el canal
eran marcadores».

```
🔒 la fuente de este canal es un marcador .LS.   ← 1º, legítimo
↩️ sin stream nativo; se prueba el embed         ← 2º, ordernap/quotarevival
↩️ sin stream nativo; queda un servidor por probar
🔒 sin nada más que probar y el canal solo trae marcadores  ← 4º y ÚLTIMO
```

## 2. La API se rendía para toda la sesión tras **un** timeout

```
14:23:13  W  api drama: (SocketTimeoutException) — no se vuelve a intentar esta sesion
14:25:12  D  api: sin respuesta util ... en 0 ms     ← otro canal, ni lo intenta
14:26:17  D  api: sin respuesta util ... en 0 ms     ← Activity NUEVA, sigue rendida
```

Un solo fallo ponía `estado = MUERTA` para **todo el proceso**, y ni siquiera recrear la
Activity lo reseteaba. En `beinsport8` eso dejó la carrera con dos candidatos, los dos
muertos.

**Arreglo: enfriamiento, no muerte.** Tras un fallo se espera **60 s** y se vuelve a
probar. Es lo que hace la app de referencia (que reintenta 3 veces), y aquí sale gratis
porque la consulta va en paralelo y nadie la espera.

Y el primer intento tras un enfriamiento ya no lleva 12 s, lleva **45 s**
(`TIEMPO_DESPERTAR_MS`): Render en frío no contesta en 12 s — es el mismo motivo por el que
el plan gratuito duerme, no una caída. La referencia usaba 70 s; 45 s cubre de sobra los
**13,3 s** medidos.

## 3. La proxada se descartaba por 100 ms

```
14:25:25  🏁 descartada (timeout, 15007 ms): …/p/232425/live_tv_beinsport1/0.m3u8
14:26:31  🏁 descartada (timeout, 15084 ms): …/p/232425/live_tv_beinsport1/0.m3u8
```

El tope era 15 000 ms para todo. Morir a los 15,0 s cuando el host tarda 15,1 s es perder el
canal por **100 ms**.

**Arreglo: tope por tipo de fuente.** El proxy de la cadena (`/p/…`, `/ps/…`) recibe
**22 s**; un CDN sigue con **15 s**. En caliente nada cambia: la misma proxada ganó la
carrera en **1 038 ms** a las 13:49:24.

## 4. Una url **ya enterrada** seguía gastando 44 s

```
14:25:12  primetv … 403  (×4, 16 ms)
14:25:26  🏁 descartada (no rotundo, 175 ms): …/mono.tbin.m3u8
luego     StreamResolver 20 s + WebView oculto 20 s
```

La carrera sabía a los **175 ms** que ese servidor estaba muerto. El reproductor, no: gastó
sus 4 reintentos y los 40 s de resolver + WebView en una url que había contestado 403.

**Arreglo: `Descartes`.** La carrera apunta lo que entierra —**solo lo rotundo**: 403/404/410
y fallos de DNS— y al recibir el error, el reproductor se para en seco, sin refrescos y sin
WebView, y sigue con el plan del canal. Un **timeout** o un **503** no se entierran: un CDN
lento puede contestar a la segunda, y un 503 es «ahora no», no «nunca».

44 s → ~0,3 s.

## 5. El árbol de errores ya no puede ofrecer un `.LS.`

Era el último agujero por el que un marcador llegaba a ExoPlayer (visto en el logcat de la
tarde: `http://.LS.V2daddy_91/s`). `onPlayerError` ahora descarta, en este orden, cualquier
candidata que sea marcador o que ya esté enterrada. Y si no queda nada válido, **no elige
nada** — mejor un aviso honesto que un `.mp4` de mentira.

También se cerró `DramaSesion.primeraOpcionNoMarcador`: solo miraba `.LS.` a pelo; ahora usa
el mismo clasificador que el resto (`/.ls` y los hosts que empiezan por punto incluidos).

---

## Lo que se puede comprobar sin instalar

```
node docs/repro-v3915.mjs     →  V3915_OK 26/26   (los 5 defectos, con los ms del logcat)
node docs/repro-v3914.mjs     →  V3914_OK 12/12
node docs/repro-v3913.mjs     →  V3913_OK 14/14
node docs/repro-v3912.mjs     →  V3912_OK 17/17
node docs/repro-v3911.mjs     →  V3911_OK 11/11
node docs/repro-v390.mjs      →  V390_OK  19/19
```

JUnit nuevos en `app/src/test/java/dza/fut/paipo/EnfriamientoYTopeTest.java` (14 casos,
con los números del logcat clavados).

## Lo que **no** puedo afirmar

- **No he compilado.** En este entorno no hay JDK ni SDK de Android. `PARSE_OK` significa
  «el árbol de sintaxis es válido», **no** «compila». Los 622 `@Test` del proyecto siguen
  sin ejecutarse nunca.
- **No he probado en el dispositivo.** Lo que afirmo sale de tu logcat y de los tiempos que
  tú mediste; lo que cambia lo afirmo por la lógica del código, no por una captura.
- Si con la 94 `beinsport1` sigue sin abrir, lo que necesito del logcat es una sola línea:
  cuál de los cuatro caminos de `avisarSinStream()` se tomó (lleva emoji distinto cada uno).

## Lo que verás si sale bien

- `beinsport1`, `beinsportnews`, `aljeria1` vuelven a abrir por embed (`⚡ atajo: embed que
  exige iframe` → `✅ embed dentro de la ventana`).
- Deja de aparecer `no se vuelve a intentar esta sesion`; en su lugar,
  `se reintenta en 60 s, no se rinde la sesion`.
- La proxada del canal ya no muere a los 15 007 ms.
- Cuando la carrera entierra algo, verás `⏹ la carrera ya enterró esta url …` en vez de 40 s
  de `StreamResolver`.
