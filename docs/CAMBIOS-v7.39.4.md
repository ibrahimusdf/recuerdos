# v7.39.4 — la «línea blanca»: el embed encima del vídeo

**Fecha:** 2026-11-18 · `versionCode 95` / `versionName "7.39.4"`

Tu logcat de las 16:01 (`beinsportnews`) tiene la respuesta completa, y la buena noticia es
que **el canal se estaba viendo**:

```
16:01:50.273  🎬 la ganadora de la carrera va al reproductor -> /p/…/live_tv_beinsportnews/0.m3u8
16:01:50.284  Buffering...
16:01:50.963  ⚡ atajo: embed que exige iframe -> ordernap.net
16:01:50.977  ✅ plan B: embed dentro de la ventana          ← ENCIMA del vídeo
16:01:54.387  ✅ Ready                                        ← el vídeo, bien, todo el rato
16:01:55+     /p/…/0.m3u8 y sus .js: HTTP 200 una y otra vez
16:02:20.988  plan B: el embed no arranca ni con 30 s -> siguiente servidor
16:02:21.009  🔒 sin nada más que probar y el canal solo trae marcadores .LS.
```

Un iframe opaco encima de un vídeo que funciona: eso es la franja blanca, y por eso al girar
la pantalla desaparecía — el giro recrea la vista y ahí sí se retiraba el WebView.

## 1. El embed llegaba **después** de la directa, y se ponía encima

La 7.39.2 ya retiraba el embed cuando llegaba un vídeo, pero solo cuando la directa se ponía
**después**. En este logcat es al revés: la ganadora de la carrera entró a las `50.273` y el
embed a las `50.963` — 700 ms más tarde, con el reproductor ya en `Buffering`.

**Arreglo — `fuenteNativaEnMarcha`.** Mientras el reproductor nativo esté arrancando una
fuente que la carrera ya validó, el embed **no se pinta**. No es que el embed esté mal: es
que llega tarde y no tiene nada que arreglar. Se le da el mismo margen de 15 s que el
vigilante del embed ya usaba (así no se alarga nada, solo cambia quién manda), y si a los
15 s el nativo no dio imagen, el embed pasa como siempre. Cuando llega el `Ready`, el
arranque se da por terminado y, si quedara algún iframe, se retira.

## 2. La API no entraba en la carrera: se quedaba **sola con el embed**

```
16:01:51.109  api: 4 opciones, 1 emitiendo · 1 confirmadas · 173 ms
16:01:51.112  🏁 carrera: 1 opciones a la vez (la API respondió)
16:01:51.113  · en carrera: EMBEBIDA DLW → ordernap.net
16:01:52.679  🏁 descartada (no es manifiesto, 1444 ms): ordernap.net
```

«1 opciones… y esa una es el embed». La proxada `/p/…/0.m3u8` —la que responde **200 en
752 ms**— **nunca entró en la carrera del arranque**, porque el embed ya había puesto
`streamReady = true` y el plan del canal filtra por «útiles». La app gastó 1 444 ms en
descubrir que un iframe no es un manifiesto.

**Arreglo, dos partes.** (a) `urlsEnCarrera` se limpia con las opciones nuevas de la API, así
que «a la carrera» significa de verdad a la carrera. (b) Un **embed ya no es candidato** de
la carrera: se descarta antes de gastar un hilo. Los embeds van por su camino, el WebView de
la ventana, que es donde funcionan.

## 3. El vigilante cambiaba de servidor **con el vídeo reproduciendo**

A las `16:02:20.988` el vigilante de 30 s dijo «el embed no arranca» — cierto, el embed no
arrancaba **porque debajo había vídeo** — y pasó al siguiente servidor, lo que terminó en el
aviso «sin nada más que probar» a las `16:02:21.009`, **con el partido en pantalla**.

**Arreglo.** Los dos vigilantes (15 s y 30 s) comprueban primero `imagenConfirmada`: si hay
vídeo nativo, **retiran el embed** en vez de cambiar de servidor. Y `avisarSinStream()` no
avisa de ningún fallo si el canal ya está reproduciendo — un cartel de error encima de un
canal que se ve es peor que no avisar.

## 4. El marco ya no pinta blanco

El WebView del embed es opaco y pinta blanco por defecto: eso era la franja. Ahora el WebView
va con `Color.TRANSPARENT` y el HTML del marco lleva `background:#000` y el iframe a
`width/height:100%` sin borde. Aunque el embed llegue a estar encima, no tapa el vídeo con
blanco.

---

## Qué se puede comprobar sin instalar

```
node docs/repro-v3916.mjs  →  V3916_OK 22/22    (la línea de tiempo del logcat, entera)
node docs/repro-v3915.mjs  →  V3915_OK 26/26
node docs/repro-v3914.mjs  →  V3914_OK 12/12
node docs/repro-v3913.mjs  →  V3913_OK 14/14
node docs/repro-v3912.mjs  →  V3912_OK 17/17
node docs/repro-v3911.mjs  →  V3911_OK 11/11
node docs/repro-v390.mjs   →  V390_OK  19/19
```

JUnit nuevos: `EmbedSobreVideoTest` (11 casos) y un caso más en `CarreraDeOpcionesTest`
(`losEmbedsNoEntranEnLaCarrera`).

## Lo que **no** puedo afirmar

- **No he compilado** (sin JDK/SDK en este entorno). `PARSE_OK` es sintaxis válida, no
  «compila».
- **No he probado en el dispositivo.** El arreglo sale de tu logcat y de la lógica del
  código; lo que no he visto con mis ojos es el resultado en tu móvil.
- Si tras la 95 la franja sigue apareciendo, necesito saber **qué dice el log en el momento
  de la franja**. Las nuevas líneas que lo explican son:
  `⏳ el embed espera: hay una directa arrancando` (bien),
  `✅ la directa arrancó: el embed ya no hace falta` (bien),
  `🧽 el embed no hace falta: el vídeo nativo ya está en pantalla` (bien),
  `⏹ la directa no arrancó en 15 s: ahora sí se pinta el embed` (ahí sí toca mirar por qué).

## Lo que verás si sale bien

- El canal abre por la proxada de la API en menos de 1 s (`🏁 carrera ganada en ~750 ms`).
- Ya no aparece `1 opciones a la vez` con un embed dentro.
- No vuelve a salir `🔒 sin nada más que probar` con el canal reproduciendo.
- Y la franja blanca desaparece sin necesidad de girar el teléfono.
