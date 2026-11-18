# v7.38.6 — beIN Sports: de 62 s a 4 s

**Base:** v7.38.5 · **Resultado:** `versionCode 85` / `versionName "7.38.6"`
**Compañeros:** [`CAMBIOS-v7.38.5.md`](CAMBIOS-v7.38.5.md) · [`comparativa_bein1.py`](comparativa_bein1.py)

---

## 0. El hallazgo: beIN 1 no estaba roto, estaba lento

Leyendo el logcat de beIN 1 (2026-11-17 19:02) aparece esto, que no habíamos visto:

```
19:02:03  glasbi [glt] OK -> http://primetv.cc.cd/…/mono.tbin.m3u8   ← YA RESUELTO
          resolverVivo: hay embebida, se prefiere -> ordernap
19:02:04  plan B: embed ordernap
19:02:19  plan B: probando -> ordernap          (el mismo otra vez)
19:02:49  plan B: embed primetv  ← LA MISMA URL DEL SEGUNDO 3
19:02:51  el embed está pidiendo vídeo (segmentos .ts)
19:03:04  hay imagen (vídeo confirmado)
```

Dos cosas que nadie había señalado:

1. **beIN 1 SÍ se veía.** `hay imagen (vídeo confirmado)` a las 19:03:04.
2. **Tardaba 62 s** en algo que tenía en la mano **en el segundo 3**.

Y la puntilla: cuando por fin llegó a `primetv`, lo abrió **dentro del WebView**
(`embed dentro de la ventana -> http://primetv…`), no en el reproductor nativo.

## 1. Por qué pasaba

`resolverVivo` (DramaSesion) tenía este orden desde la v7.37.17:

```java
String embebida = primeraEmbebida(...);       // gana SIEMPRE
if (embebida != null) return new Vivo(embebida, ...);
if (siYaEsStream(vg[0]) ...) { ... }          // esto quedaba inalcanzable
```

Ese orden se puso por un motivo medido y correcto: la «directa» del backup podía
ser un 403 de Cloudflare o un m3u8 con la tarjeta rusa «ОТСУТСТВУЕТ ПОДПИСКА». Pero
se aplicó a **todo**, incluida la directa que glasbi ya había validado, y el precio
resultó ser 58 s de negro.

Y en `PlayerActivity`, **todos** los caminos del plan B metían el servidor en el
WebView, incluso cuando era un `.m3u8`.

## 2. Lo que cambia

### 🔴 El orden: lo que ya está resuelto se gasta primero

`DramaSesion.resolverVivo` ahora:

1. `siYaEsStream(vg[0])` → **se devuelve ese m3u8**. La embebida queda de plan B.
2. Si no hay directa → la embebida (como antes).
3. Si no → glbr, y si glbr tampoco, las opciones para el plan B.

El riesgo lo cubre `onPlayerError`: si la directa no arranca, ExoPlayer avisa y ahí
se prueba el embed. Se pierden unos segundos en el peor caso; se ganan 58 s en el
normal.

### 🔴 Un m3u8 vuelve al reproductor nativo

Nuevo despachador `probarServidor(Opcion)`: si el tipo es `DIRECTA` →
`probarDirectaConSusCabeceras()` (ExoPlayer); si es `EMBEBIDA` → el WebView.
Sustituye a `mostrarEmbedEnLaVentana()` en los 4 puntos del plan B (rama del
relleno, fallo de glbr, error de ExoPlayer, `avisarSinStream`).

Ganancia extra: la directa se ve **con PiP, selector de calidad y mando de
velocidad**, que el WebView no da.

### 🟡 `avisarSinStream` ya no se rinde con servidores sin probar

Antes: embed o toast. Ahora: embed → siguiente servidor pendiente → aviso honesto.

---

## 3. La cuenta, con los tiempos del log

```
$ python3 docs/comparativa_bein1.py

ANTES (v7.38.3): 62 s
   2.5s  POST glasbi
  15.0s  embed ordernap (1º intento)
  15.0s  mismo embed OTRA VEZ (el bug)
  15.0s  mismo embed, 2ª oportunidad
  15.0s  embed primetv cargando en WebView

DESPUÉS (v7.38.6): 4 s
   2.5s  POST glasbi (igual: es la API)
   2.0s  ExoPlayer abre el m3u8 directo   [ESTIMADO]
```

**De 62 s a 4 s: 13,9×.** Las etapas del «antes» salen del logcat; la del
«después» está marcada como **ESTIMADA** porque nadie ha ejecutado este build.

---

## 4. Verificación — el límite de siempre

| | |
|---|---|
| `java-parser` en los 4 ficheros | ✅ `PARSE_OK` |
| `repro-planb.mjs` | ✅ 8/8 |
| `prueba_bein1.py` | ✅ 17/17 |
| **Compilación** | ❌ **no verificada** |
| **En el móvil** | ❌ **pendiente** |

Sigue sin JDK ni Android SDK aquí. `./gradlew :app:testDebugUnitTest` (ahora
**18 tests**: 11 de `PlanBTest`, 3 tuyos de `DrmYActividadTest`, 4 de
`OrdenPorVelocidadTest`).

## 5. Qué mirar en el logcat

```bash
adb logcat -s BLABONGO_DEBUG | grep -E "resolverVivo|directa del canal|plan B|cura LANZADA"
```

| Línea nueva | Significa |
|---|---|
| `resolverVivo: glasbi dio el stream directo — se usa YA (la embebida queda de plan B)` | el orden nuevo entró |
| `↪ directa del canal -> ExoPlayer (no WebView)` | el m3u8 va al reproductor nativo |
| `glbr resuelto -> http://primetv…` **a los ~2 s** | el objetivo: ver imagen sin pasar por 30 s de embed |
| `plan B: probando ->` con urls distintas | el fallback sigue sano si la directa cae |

**Criterio de éxito:** `Reproduciendo stream m3u8 directo (sin WebView)` con
primetv en los primeros ~5 s.

---

## 6. Riesgo asumido (importante)

Se ha invertido una decisión que estaba tomada **con mediciones** (v7.37.17): si la
directa de glasbi es la tarjeta rusa «ОТСУТСТВУЕТ ПОДПИСКА», el usuario verá ese
cartel unos segundos antes de que `onPlayerError` dispare el plan B.

Por qué lo asumo: el cartel se ve **una vez y se corrige solo**, mientras que el
orden anterior garantizaba **62 s de negro en todos los canales con embed**. Si en
el móvil aparece el cartel con frecuencia, la vuelta atrás es una línea: mover el
bloque de `siYaEsStream` detrás del de `primeraEmbebida` y dejar el resto igual.

## 7. Sigue fuera de alcance

**Los 6 marcadores `.LS.`** de beIN 1. No son un defecto de este código: son cadenas
que solo resuelve el redirector del sitio **dentro de su web**, y
`tools/api-canales/README.md` documenta que el POST sin cifrar devuelve 200 con 32
bytes en blanco. Ninguna app puede abrirlos sin embeber el navegador del sitio.
