# v7.39.0 — la carrera: todas las opciones a la vez, gana la que responde primero

**Fecha:** 2026-11-18 · `versionCode 91` / `versionName "7.39.0"`

---

## 1. Tenías razón, y el logcat lo demuestra con números

Lo pediste así: *«el resolver debe pasar todas las options en paralelo, la que responde
primero la pasas al player, se puede, porque así demora mucho»*. Se puede, y hacía falta.
Tu logcat de `live_tv_aljeriaelheddaf`:

```
01:09:19.207  glasbi «OK» -> live.eighty-eight.tv:7770/elheddaf1/index.m3u8
01:09:30.941  api: 4 opciones, 1 emitiendo · 11728 ms      ← la API ya traía las proxadas
01:09:34.335  ❌ SocketTimeoutException … after 15000ms
01:09:49.358  ❌ SocketTimeoutException … after 15000ms
01:10:05.393  ❌ SocketTimeoutException … after 15000ms
01:10:22.431  ❌ SocketTimeoutException … after 15000ms     ← 63 s en negro
01:10:22.469  ⚠️ Refrescando stream (intento 1/5)  →  WebView, 40 s más
01:11:02.511  ↩️ sin stream nativo; queda un servidor por probar -> proxada [0]
01:11:03.557  ✅ Conexión exitosa (HTTP 200)                ← 1 s
```

**63 segundos esperando a un host muerto** mientras la proxada buena llevaba 11,7 s
disponible y contestaba en 1 s. Y el fallo de fondo: `streamReady` era `true` desde el
`prepare()`, así que el plan B **nunca se activó** — la app creía estar reproduciendo y
solo tenía una promesa.

## 2. La carrera (`CarreraDeOpciones`)

Clase nueva, pura en sus decisiones, con su hilo propio:

1. **Se lanzan todas a la vez.** Un hilo por opción, tope 8. Son urls distintas en hosts
   distintos: no compiten por nada.
2. **Gana la primera que da un manifiesto de verdad.** No basta un 200: se exige
   `#EXTM3U` en la cabecera (8 bytes, no el archivo entero) **y** que no sea la tarjeta
   de aviso `ОТСУТСТВУЕТ ПОДПИСКА` que el CDN sirve como si fuera un canal.
3. **Las demás se cierran.** No esperan su timeout: un host muerto se queda atrás.
4. **La ganadora va al reproductor**, con 1,2 s de cortesía para no cortar un arranque
   que estaba a punto de cuajar. Si ya hay imagen confirmada, **no se toca**: no se le
   cambia el canal al usuario mientras lo ve.

### Es incremental, y por eso no se pierde la API

Arranca **en cuanto hay algo que probar** (la url del glasbi, la memoria del canal) y
cuando la API contesta a los 11,7 s **se le añaden sus opciones** a la misma carrera. Con
una lista de urls ya lanzadas, no se pide dos veces lo mismo.

### `imagenConfirmada` — el otro fallo, y era grande

`streamReady` se pone a `true` al llamar a `prepare()`. **Un host muerto también vale
`true`.** Por eso el plan B dormía. Ahora hay una bandera aparte que solo se enciende en
`STATE_READY` y se apaga cada vez que se carga otra fuente.

### Un detalle medido: `connect 15 s → 8 s`

Cuatro timeouts de 15 s contra un host que **no acepta ni la conexión TCP** son 60 s
tirados. Con la carrera ya no bloquea, pero 8 s es un techo más sensato. La lectura se
queda en 15 s: ahí sí puede haber un directo lento que merezca la pena.

## 3. Medido en la simulación (latencias del logcat)

`node docs/repro-v3912.mjs` · **17/17** · `CarreraDeOpcionesTest` · **14 `@Test`**

| estrategia | tiempo hasta imagen |
|---|---|
| **en serie** (7.38.11: 4 reintentos de 15 s en el host muerto, luego la proxada) | **76,1 s** |
| **carrera** (7.39.0) | **1,0 s** |

**76× más rápido** en el caso exacto de tu móvil. Y la carrera **no espera nunca** al
host muerto (`< 15 s` en la prueba, porque la ganadora cierra la carrera en cuanto llega).

## 4. Lo que sigue igual (a propósito)

- Modo sin API, memoria de servidores por canal, radio, plan B con embebidas de Drama.
- **Widevine sigue fuera**: no gasta hueco en la carrera (`cenc`, `_enc`,
  `starzplay`, `intigral`).
- Un `timeout`/`http:null`/500 **no descarta** nada — la lección de la 7.38.11.
- Lo que trae Drama sí se pone a sonar sin comprobar (es lo que hacía la app de
  referencia); lo que promete la API entra en la carrera.

## 5. Verificado / sin verificar

**Verificado:** `V3912_OK 17/17` (nuevo) · `V3911_OK 11/11` · `V390_OK 19/19` ·
`V389_OK 16/16` · `PLAN_OK 18/18` · `PROXY_OK 19/19` · `SIM_OK 14/14` · plan B OK ·
12 ficheros Java con sintaxis comprobada.

**Sin verificar:** no compilado (sin JDK/SDK) y no probado en tu móvil.

```bash
./gradlew :app:testDebugUnitTest assembleDebug
```

En logcat deberías ver, al abrir ese mismo canal:

```
🏁 carrera: 4 opciones a la vez (arranque del canal)
   · en carrera: DIRECTA → https://live.eighty-eight.tv:7770/elheddaf1/index.m3u8
   · en carrera: DIRECTA api·proxy? [0] → https://dramalive-api-08th.onrender.com/p/…
🏁 descartada (timeout, 15000 ms): https://live.eighty-eight.tv:7770/…
🏁 carrera ganada en 1002 ms (manifiesto #EXTM3U) -> …/p/232425/live_tv_aljeriaelheddaf/0.m3u8
🎬 la ganadora de la carrera va al reproductor -> …/p/…/0.m3u8
```

Y el `⏱️` que delataba el problema (`4 × SocketTimeoutException`) ya no debería aparecer.
