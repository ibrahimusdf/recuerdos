# v7.39.5 — el bucle infinito, y el arranque que se rendía

**Fecha:** 2026-11-18 · `versionCode 96` / `versionName "7.39.5"`

Tu `nuevo1.txt` trae **dos sesiones**. La primera es la grave.

---

## 1. Bucle infinito: 4 238 vueltas en 3 segundos (app congelada)

```
16:40:21.537  ↩️ sin stream nativo; se prueba el embed en la ventana -> …/androstreamlivebs4.m3u8
16:40:21.538  ↩️ … la MISMA url
16:40:21.538  ↩️ … la MISMA url
      … 891 en 16:40:21, 2 067 en :22, 1 280 en :23 …
```

Todo en el hilo de UI, sin red entre vueltas. La app no responde: por eso solo se arreglaba
volteándola (al girar, el `Activity` se recrea y mata la pila).

**La cadena era esta**, y los dos extremos son míos:

1. `avisarSinStream()` elegía con `DramaSesion.primeraEmbebida()` / `primeraOpcionNoMarcador()`
   — y **ninguna de las dos mira `servidoresProbados`**. Elegía «la primera embebida del canal»
   aunque ya se hubiera probado.
2. `probarServidor()` la añadía a `servidoresProbados` y, al ser un CDN de otro canal, fallaba.
3. Volvía a `avisarSinStream()`, que elegía **la misma** (porque no mira la lista de probados)
   → vuelta 2. Y así hasta 4 238.

Peor aún: la url elegida (`andro.mangeinde4.lat/…/androstreamlivebs4.m3u8`) **no era de ese
canal** — era el resto de la memoria del canal anterior, que seguía viva.

**Arreglo, dos cortes:**

- **Se elige lo que no se ha probado.** El aviso recorre las opciones del canal y descarta
  las que están en `servidoresProbados`, los marcadores `.LS.` y los portales. Si no queda
  nada, no hay nada que probar: sigue el camino normal.
- **Cortafuegos.** Un contador de vueltas seguidas sin que se ponga una fuente nueva. A la
  **8ª** se corta el bucle y se avisa al usuario. Se reinicia cada vez que una fuente entra
  al reproductor o se cambia de canal. Con esto, ningún camino circular que se me escape en
  el futuro puede volver a congelar la app.

Con los números del logcat: **4 238 → 8** vueltas como máximo.

## 2. Un 504/403 al arrancar no es «canal muerto»: es reintentar

La segunda sesión (v7.39.4) abre los canales nuevos con la proxada, pero se rendía al primer
error:

```
16:41:02.836  Buffering...   → 16:41:14.507  ❌ 504        ← 12 s en negro
16:41:24.490  Buffering...   → 16:41:24.715  ❌ 403   → 16:41:26.077  ✅ Ready
16:41:38.988  Buffering...   → (403 de ruido)         → 16:41:40.101  ✅ Ready
```

El **504 es Render despertando** (la misma causa que el `TIEMPO_DESPERTAR_MS` del API) y el
**403 es ruido** de esos CDN: a la petición siguiente sirven el playlist. El árbol de errores
lo trataba como servidor muerto y saltaba de canal.

**Arreglo.** Antes de nada, **dos reintentos de la misma url** con espera creciente (1,2 s y
2,4 s), siempre que la carrera no la haya enterrado con un NO rotundo. Solo después se pasa
al plan B. Los 12 s en negro del 504 se convierten en «imagen a la segunda».

## 3. La proxada se recuerda aunque Render conteste 504

`💾 servidor recordado` solo se apuntaba si había `READY`. En el primer canal de la sesión
nunca lo hubo (504) y no se apuntó **nada**: la próxima vez se empezaría otra vez por lo
lento. La `/p/…` es la mejor url imaginable para arrancar (responde 504 o 200, pero todos los
canales la tienen): ahora se apunta siempre.

Y lo que **falla** ahora también se apunta (`📉 apuntado el fallo de este servidor`): los CDN
`edgenextcdn` tx010/tx012 daban 403 y aun así eran lo primero en cada visita, porque la
memoria del canal solo sabía lo bueno. Es conservador a propósito: nunca se apunta un fallo
si esa url ya tuvo un éxito, ni si es la única opción del canal.

## 4. Un segmento `.ts` no es una fuente

En el logcat, un `.ts` de otro canal llegó a la ventana como si fuera un embed
(«TS not aligned»). `esPlaylist()` aceptaba `.ts`, `.jpg` y `.png` por contener `tracks-v1` o
`/hls/`. Ahora un segmento o un cartel **no** se clasifica como reproduccible en ningún sitio.

---

## Qué se puede comprobar sin instalar

```
node docs/repro-v3917.mjs  →  V3917_OK 17/17    (el bucle, contado con las 4 238 vueltas reales)
node docs/repro-v3916.mjs  →  V3916_OK 22/22
node docs/repro-v3915.mjs  →  V3915_OK 26/26
node docs/repro-v3914.mjs  →  V3914_OK 12/12
node docs/repro-v3913.mjs  →  V3913_OK 14/14
node docs/repro-v3912.mjs  →  V3912_OK 17/17
node docs/repro-v3911.mjs  →  V3911_OK 11/11
node docs/repro-v390.mjs   →  V390_OK  19/19
```

JUnit nuevos: `BucleDeAvisosTest` (10 casos).

## Lo que **no** puedo afirmar

- **No he compilado** (sin JDK/SDK aquí) ni probado en el dispositivo.
- El log del bucle **no trae stack trace** (son 3 s de líneas D, sin `FATAL`): la cadena la he
  reconstruido por el código, y es la única que puede producir exactamente esa repetición en
  el hilo de UI sin red de por medio. Si vuelve a pasar, el cortafuegos lo corta igual.
- Si tras la 96 algo sigue sin arrancar, las líneas nuevas que lo explican son
  `🛑 avisarSinStream … se corta el bucle`, `↻ el arranque falló (…); reintento n/2`,
  `📉 apuntado el fallo de este servidor`.

## Lo que verás si sale bien

- Se acabaron las ráfagas de `↩️ sin stream nativo` y la app no hay que voltearla.
- Los canales nuevos de Drama abren a la **segunda** petición en vez de quedarse 12 s en negro.
- Los CDN que dan 403 bajan puestos en el orden de ataque del canal, visita tras visita.
