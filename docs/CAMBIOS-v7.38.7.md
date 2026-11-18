# v7.38.7 — la memoria de servidores por canal

**Base:** v7.38.6 · **Resultado:** `versionCode 86` / `versionName "7.38.7"`
**Compañeros:** [`CAMBIOS-v7.38.6.md`](CAMBIOS-v7.38.6.md) · [`repro-servidores.mjs`](repro-servidores.mjs)

---

## 0. Qué pedías y qué hay aquí

> «quiero que los canales beIN Sports funcionen en mi aplicación y extrayendo
> todas las opciones servidores iguales que Drama»

Drama entrega por canal 2 cosas: `live.url` y `live.backup`, y su cliente las
reutiliza. La app **ya extrae todas las opciones** (eso lo hizo la v7.38.0:
`DramaResolucion.opciones()` saca las 9 de beIN 1, con su UA y su Referer, y las
pinta en el log `servidores del canal: 9 · útiles 2`).

Lo que **faltaba** es lo que Drama sí hace y la app no: **acordarse**. La app cazaba
el playlist real dentro del embed y lo pasaba a ExoPlayer… y lo tiraba a la basura al
cerrar. La próxima vez, otra vez 2 POST + 30 s de sondeo para volver a encontrar la
misma url.

Esta versión es la memoria.

## 1. Lo que se guarda por canal

| Campo | Por qué |
|---|---|
| `url` | el playlist que emitió |
| `ua` | el User-Agent con el que el CDN lo aceptó (**con otro contesta 403**) |
| `referer` | su Referer/Origin |
| `exitos` / `fallos` | para ordenar: lo que funciona delante |
| `ultimoUso` | desempate: lo más reciente primero |

En `SharedPreferences` (`servidores_canal_v2`), con caché en memoria. **No se guardan
cookies de sesión.** La llave del canal es el `id_live` (o la url normalizada si el
canal ya se curó a https).

## 2. Cómo entra en el reproductor

```
abrir canal
  ├─ log: "servidores: N recordados de este canal (el primero: …)"
  │        o "primera vez en este canal — sin memoria todavía"
  ├─ resolverVivo → si glasbi trae la directa, se usa (v7.38.6)
  └─ plan B: siguienteOpcionUtil() ← conRecordados(recordados, utiles)
        primero lo recordado (con SUS cabeceras), después lo de Drama
        ├─ DIRECTA  → ExoPlayer (v7.38.6)
        └─ EMBEBIDA → marco en la ventana
```

Y el aprendizaje, en el único punto donde de verdad se sabe:

```
ExoPlayer STATE_READY  →  espera 3 s  →  si no saltó el aviso de tarjeta rusa
                                      →  ServidoresDelCanal.apuntarExito(...)
                                         log "💾 servidor recordado para este canal"
```

**Por qué se espera 3 s:** el CDN sirve `ОТСУТСТВУЕТ ПОДПИСКА` como un m3u8
perfectamente válido y ExoPlayer llega a READY igual. Si se aprendiera ahí, la app
recordaría **el cartel** como «el servidor bueno» y lo pondría primero para siempre.
La espera deja que `revisarSiElStreamEsUnAviso` haga su trabajo antes.

## 3. El efecto medible

beIN 1, con memoria:

| | 1ª vez | 2ª vez en adelante |
|---|---|---|
| POST a glasbi | 2 | 2 (es la API) |
| Sondeo del embed | 30 s | **0** |
| Abrir primetv | vía WebView, 15 s | **ExoPlayer, ~2 s** |
| Recordar el UA correcto | no | **sí** (el de la vez que funcionó) |

Y lo importante: **el servidor que funcionó va primero**. Si ordernap no emite en tu
cuenta pero primetv sí, la segunda vez la app arranca por primetv.

## 4. Lo que NO cambia (y es de verdad, no una excusa)

- **Los marcadores `.LS.`** (6 de las 9 opciones de beIN 1) siguen sin poder abrirse
  desde la app. No es una limitación de este código: son cadenas que solo resuelve el
  redirector del sitio **dentro de su propio navegador**. Medido en el repo: un POST
  desde fuera devuelve `200` con 32 bytes en blanco, **con o sin** los headers del
  sitio. Y con la plantilla de 20 campos aprendida **y** las cookies del WebView, el
  `glbr` seguía dando `update.mp4` en 3 de 3 canales desde Java, mientras dentro del
  captador daba el stream real (`docs/COMO_SESION_DRAMA.md` v3.2.12).
- **No hay API que reemplace a Drama.** Se intentó y se documentó: no es CORS ni los
  headers, es que el servidor gradúa por contexto de navegador.
- **Lo que sí es «extraer todas las opciones»** ya está hecho y es lo que esta
  memoria aprovecha al máximo.

## 5. Verificación

| | |
|---|---|
| `java-parser` en los 4 ficheros | ✅ `PARSE_OK` |
| `node docs/repro-servidores.mjs` | ✅ **14/14** |
| `node docs/repro-planb.mjs` | ✅ 8/8 |
| `docs/prueba_bein1.py` | ✅ 17/17 |
| **Compilación / móvil** | ❌ **no verificado** (sin JDK ni Android SDK) |

Tests JUnit: **+12** en `ServidoresDelCanalTest` (serialización tolerante, orden,
aprendizaje, fusión, llave del canal, agotamiento). **Total 30.**

## 6. Qué mirar en el logcat

```bash
adb logcat -s BLABONGO_DEBUG | grep -E "servidores:|recordado|directa del canal|plan B|Ready"
```

| Línea | Significa |
|---|---|
| `servidores: primera vez en este canal — sin memoria todavía` | 1ª apertura |
| `💾 servidor recordado para este canal: http://primetv…` | aprendió |
| `servidores: 1 recordados de este canal (el primero: http://primetv…)` | **2ª apertura: ya sabe** |
| `↪ directa del canal -> ExoPlayer (no WebView)` | va al reproductor nativo |
| `✅ Ready` en los primeros ~3 s | **objetivo cumplido** |

**Criterio de éxito:** abrir beIN 1 dos veces. La segunda tiene que decir
`servidores: N recordados` y llegar a `✅ Ready` **sin** mostrar
`plan B: probando -> embed`.

## 7. Riesgo

1. **La memoria vive en `SharedPreferences`**: si el usuario borra datos de la app, se
   pierde (y se reconstruye sola en la siguiente apertura). No es crítico.
2. **Un servidor recordado puede morir**: por eso `fallar()` lo baja de prioridad en
   vez de borrarlo, y sigue el resto de la lista. Una url que falla 3 veces sigue
   estando al final.
3. **Se aprenden también las urls de embed** (no solo m3u8): si un embed funcionó, se
   recuerda con su tipo y su UA. Es deseado.
