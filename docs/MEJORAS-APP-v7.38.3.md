# Plan de mejora — PelisLatino / BLABONGO 7.38.3 → 7.39

**Fecha:** 2026-11-17 · **Base:** `PelisLatino-Android-v7.38.3-SOLO-CODIGO.zip` · **Compañero:** [`DIAGNOSTICO-CANALES-DRAMA-v7.38.3.md`](DIAGNOSTICO-CANALES-DRAMA-v7.38.3.md)

Este documento **no** repite el diagnóstico de reproducción de canales (eso está en el otro informe). Aquí va lo estructural: qué está frenando al proyecto, qué sobra, qué falta y en qué orden tocarlo. Todas las cifras salen del árbol del ZIP.

---

## 1. Foto del proyecto (medida)

```
app/src/main/java/dza/fut/paipo/          64 ficheros .java · 23 410 líneas
  PlayerActivity.java                      3 990 líneas  ← el 17 % de todo el Java
  DramaSesion.java                         2 331 líneas
  CanalesEnVivoFragment.java               1 673 líneas
  FuenteCanales.java                       1 154 líneas
  StreamResolver.java                      1 088 líneas
app/src/test/java/dza/fut/paipo/          67 ficheros de test
app/src/main/assets/drama_auto.js         47 898 B (un solo fichero, sin tests propios)
app/src/main/res/layout/                  18 layouts · drawable 79 ficheros
docs/                                     14 ficheros de documentación + 6 scripts .mjs/.py
tests_python/                             1 fichero (23 KB, solo de la curación)
```

Cuatro ficheros concentran el 44 % del código Java. `PlayerActivity` a 3 990 líneas ya no cabe en una cabeza: los tres bugs bloqueantes del diagnóstico viven **todos** en esa clase y dos de ellos son de control de flujo a simple vista — nadie los vio porque nadie puede leerla entera.

---

## 2. Riesgo número uno del proyecto: el conocimiento vive en `docs/`, no en el código

`docs/COMO_SESION_DRAMA.md` son **39 379 B** describiendo 47 versiones del mecanismo de Drama (v3.2.5 → v3.2.47). El diagnóstico encontró la trampa:

> El documento describe con todo lujo de detalles la máquina de auto-curación (v3.2.13 → v3.2.47). El código **ya no la ejecuta**: `canalCurado` nunca se asigna, `DramaFondo.abrirCaptadorHeadless()` no tiene llamadores, `Vivo.crudoUrl`/`crudoBackup` están declarados y siempre valen `null`.

No es un fallo de nadie en concreto: es lo que pasa cuando **la fuente de verdad es un changelog en prosa y no un test**. El changelog documenta la intención; solo el test documenta el comportamiento.

**Propuesta concreta:** una tabla de invariantes vivos en el repo, con un test por invariante, y que el changelog enlace al test en vez de describir la mecánica:

| Invariante | Test que debe existir | Hoy |
|---|---|---|
| «Se prueban TODOS los servidores del canal, sin repetir» | `PlanBTest.elSegundoSeIntentaCuandoElPrimeroFalla` | ❌ no existe |
| «El relleno `update.mp4` abre el plan B» | `PlanBTest.elRellenoNoEsUnCallejonSinSalida` | ❌ no existe |
| «La cura headless se lanza con un solo POST de diferencia» | `CuraTest.seLanzaUnaVezPorCanal` | ❌ no existe |
| «`glbrReq` entra sin exigir `user_id`» | `DramaSesionTest` ✅ existe | ✅ (pero la guarda rota sigue en `tienePlantillaGlbr`) |
| «Una entrega vacía no pisa la rejilla» | `DramaSesionTest.fusionar*` ✅ existe | ✅ |

Las tres primeras son de aritmética pura o casi: no necesitan Android, caben en la suite que ya corre.

---

## 3. Tests que no prueban lo que se ejecuta

Caso medido, el más caro:

- `DramaResolucionTest` tiene **7 pruebas** y cubre `embebidas()`, `mejorEmbebida()` y `siguienteEmbebida()`.
- `grep -rn "DramaResolucion.\(embebidas\|mejorEmbebida\|siguienteEmbebida\)" app/src/main` → **0 resultados**. Esas tres funciones solo las llama el test.
- Mientras tanto `PlayerActivity.siguienteOpcionUtil()` — el que **sí** elige servidor y que tiene el bug bloqueante — no tiene ni una prueba.

Consecuencia: la suite marca **174/174 verde** y el usuario no puede ver un canal. El número de tests dejó de significar algo.

**Acciones:**
1. Regla de revisión: *una función `public static` de `DramaResolucion`/`DramaSesion` sin llamador en `app/src/main` no se testea: se borra o se cablea.* Aplicarla hoy deja fuera `mejorEmbebida`, `siguienteEmbebida`, `DramaResolucion.embebidas`, `tienePlantillaGlbr`.
2. Mover la *decisión* (no la vista) fuera de `PlayerActivity`: `DramaResolucion.siguiente(utiles, yaProbados, actual)` como función pura → testeable, y la Activity solo la invoca.
3. Un test de humo de recursos ya se tiene (`RecursosLayoutTest`); añadir uno de **cableado**: que cada constante pública de `DramaSesion`/`DramaResolucion` tenga al menos un uso en `main` (reflexión + grep en build, o simplemente un script en `tools/`).

---

## 4. `PlayerActivity` (3 990 líneas): dividir por responsabilidad

Lo que hoy vive en una sola clase y no debería:

| Responsabilidad | Síntomas de que está mal ahí | Extraer a |
|---|---|---|
| Elección de servidor / plan B | Bug #1 del diagnóstico | `PlanDeServidores` (pura) |
| Curación y polling de entrega | Bug #3 | `CuradorDeCanal` (pura + callback) |
| Resolución glasbi/glbr | 3 POST al mismo endpoint | Ya está en `DramaSesion`, pero la Activity la orquesta a mano |
| PiP + ciclo de vida | `cerrandoDesdePip`, `entrandoEnPip`, `lanzandoCaptador`, `enPrimerPlano` | `PipController` |
| WebView de embeds | `htmlMarco*`, `embedEnCurso`, `peticionesDeVideo` | `EmbedEnVentana` |
| Cabeceras / UA | `UA_DEL_EMBED`, `uaDelMarco()`, `Encabezados` | `Encabezados` |
| Log en pantalla + `RegistroCarga` | `log()` mezclado con lógica | `DiarioCarga` |

No es refactor cosmético: los tres bugs bloqueantes son **de estado compartido** entre esas responsabilidades. Al separarlas, el bug #3 (un campo que nadie asigna) lo caza el compilador.

**Objetivo realista para 7.39:** no reescribir, sino sacar `PlanDeServidores` y `CuradorDeCanal` (≈200 líneas cada una, puras, con test). `PlayerActivity` baja a ~3 500 y las dos piezas críticas quedan cubiertas.

---

## 5. Lo que sobra (borrar sin miedo)

| Código | Por qué | Evidencia |
|---|---|---|
| `DramaResolucion.embebidas()`, `mejorEmbebida()`, `siguienteEmbebida()` | 0 llamadores en `main` | `grep` → solo tests |
| `Vivo.crudoUrl`, `Vivo.crudoBackup` **o** la llamada a `cargarServidoresDelCanal` | Uno de los dos sobra: o se rellenan y se ahorra un POST, o se borran | 4 `new Vivo(...)` usan el constructor de 3 args |
| `tienePlantillaGlbr()` tal como está | Exige `user_id` a una plantilla que nunca lo trae; hoy inofensiva, trampa mañana | docs v3.2.25 |
| `lanzandoCaptador` | Se pone a `false` en `onResume`, nunca a `true` | `grep` |
| `play-services-maps3d` en `libs.versions.toml` | Comentario dice «eliminado» pero la entrada sigue | `libs.versions.toml:5,19` |
| `testInstrumentationRunner` sin ningún `androidTest` real | Solo `ExampleInstrumentedTest` | `app/src/androidTest/` |
| Los dos historiales de handoff (`HANDOFF.md` + `HANDOFF-agente-local.md`, 62 KB) | Se contradicen entre sí y con el código | comparar §v3.2.9 de ambos |

**Sobre `media3-exoplayer-dash`:** no borrarla. Está ahí por el DRM ClearKey (#6 del diagnóstico); lo que falta es el cableado, no la dependencia.

---

## 6. Robustez y anti-flood (el histórico del proyecto da miedo)

Del changelog: «98 POST a la API en 13 s → el servidor empezó a contestar 32 bytes en blanco **a todo**, incluido el glbt del propio sitio». El edge castiga por IP/identidad. Hoy quedan tres multiplicadores vivos:

1. **#7 del diagnóstico:** `resolverVivo` + `cargarServidoresDelCanal` = **2 POST a glasbi idénticos** por cada play. Con la caché de marcador (`marcaEnCache`) se evita el segundo *si* hay marca fresca, pero no siempre.
2. **Búsqueda de la calidad:** `resolverMarcado()` vuelve a llamar a `glasbiPrimero()` — otro POST — cuando el usuario cambia de calidad en el chip.
3. **Sin límite global:** no hay un rate-limiter. Cada canal abierto, cada reintento de cura y cada zapeo van por su cuenta.

**Propuesta:** un único `EmbudoDrama` (token bucket: p. ej. 6 POST/minuto, cola con descarte) por el que pasen **todos** los POST de `DramaSesion.post()`. Es una clase de 40 líneas, pura, con test de tiempos (los hay ya de este estilo: `BarraProgresoTest`, `StreamPlazosTest`). Protege la cuenta del usuario mejor que cualquier reintento inteligente.

Complemento: **una sola llamada a glasbi por canal y por play**, con los datos crudos reutilizados (`Vivo.crudoUrl/crudoBackup`).

---

## 7. Deuda de datos: el catálogo

`DramaFondo.arrancar()` primero intenta `CatalogoRemoto.refrescar()` desde `https://pelislatino-canales.pages.dev` (shards `scopes/{corto,latam,global}.json`). Si eso funciona, **no se toca DramaLive en absoluto** — que es exactamente lo que se quiere.

Pero:

- `CatalogoRemoto` es un tercer formato de catálogo. En el árbol conviven: `assets/catalogo/cat/*.json` (más de 300 ficheros), `CatalogoRemoto` (shards JSON), `CatalogoJson` (deriva m3u), `M3uParser` (parsea m3u), `Xtream` (Xtream Codes), `ListaPegada` (pegado libre). **Cinco entradas distintas** al mismo tipo `Canal`.
- `docs/` documenta que la vuelta por m3u «pierde canales (282 de 12.000 en global)» y aun así `aM3uUnico`/`aM3u` sigue siendo el camino en varios sitios.
- No hay validación de esquema del JSON bajado más allá de «parsea».

**Propuesta:** un `Catalogo` único que produzca `List<Canal>` y del que el m3u sea solo un **exportador** (`Catalogo.aM3u()`), no una fuente. Los adaptadores (`M3uParser`, `Xtream`, `CatalogoJson`) pasan a ser *lectores* de ese único tipo. Es el refactor de mayor retorno a medio plazo y el que más duele; no meterlo en 7.38.4.

---

## 8. UX / mensajes: la app miente por pesimismo

Tres textos de la versión actual que el usuario lee como «está roto»:

| Texto actual | Dónde | Propuesta |
|---|---|---|
| `«Servidor no disponible por ahora»` para el relleno **y** para «no queda ningún servidor» | `strings.xml:55` | Dos mensajes: `«Ese servidor no emitió ahora; probando otro…»` y `«No queda ningún servidor de este canal»` |
| `«No se pudo cargar este enlace. Prueba con otra opción del canal.»` | `PlayerActivity:2645` | Decir **cuántas** opciones se probaron: `«Se probaron 5 servidores y ninguno emitió»` |
| `«Este canal lo sirve el proveedor y su redirector no responde»` | v7.38.3, nuevo | Correcto en diagnóstico, pero hoy **sustituye** al plan B (bug #5). Después del arreglo, mantenerlo solo como último mensaje |

Regla que el propio handoff se puso y que conviene recuperar: **«PELIGRO: no culpar al usuario»** — y su hermana, que aquí se incumple: **no culpar al servidor antes de haber probado los servidores que quedan**.

Además, dos mejoras visibles con poco coste:
- **Botón «Reintentar» real** en el aviso de error (hoy solo hay chips de servidor y un toast que se va).
- **Contador de progreso al usuario** mientras se prueban servidores: `Probando 2/8…` en el chip. Convierte 90 s de negro en información.

---

## 9. Calidad de build

| Punto | Estado | Acción |
|---|---|---|
| `minSdk 30` / `targetSdk 34` / `compileSdk 36` | coherente con ARREGLOS-2026-09-28 | ✅ nada |
| `unitTests.returnDefaultValues = true` | enmascara stubs de Android (ya mordió con `android.util.Base64` en el DRM, ver `DramaResolucion.java:251`) | mantener, pero **prohibir** `android.util.*` en clases que se testean |
| `gradle-wrapper` 8.13 vs `COMO-ABRIR.txt` que dice «Gradle 9.1.0» | **contradicción** en la documentación | corregir `COMO-ABRIR.txt` |
| `local.properties` y `build/` dentro del ZIP entregado | ruido y ruta de otra máquina | `COMO-ABRIR.txt` ya advierte; excluirlos del ZIP |
| Sin CI | 174 tests que solo corre una persona en su máquina | GitHub Actions con `testDebugUnitTest` + `node --check drama_auto.js` + script de «constantes sin uso» (§3) |
| `proguard-rules.pro` | release con `minifyEnabled true` | verificar que `drama_auto.js` y los `WebViewClient` sobreviven; un release sin probar es un bug esperando |

**El CI es la mejora individual más barata de esta lista.** Los tres bugs bloqueantes del diagnóstico habrían muerto en un PR con dos tests.

---

## 10. Hoja de ruta sugerida

| Versión | Contenido | Coste |
|---|---|---|
| **7.38.4** | Bugs #1, #2, #3, #5 del diagnóstico + 3 tests nuevos + mensajes de §8. **Solo esto: es el arreglo que el usuario nota.** | 1 sesión |
| **7.38.5** | #4, #7, #8, #9 + `EmbudoDrama` (rate limit) + limpieza de §5 | 1-2 sesiones |
| **7.39.0** | Extraer `PlanDeServidores` y `CuradorDeCanal`; CI en GitHub Actions; tabla de invariantes | 2-3 sesiones |
| **7.40.0** | `Catalogo` unificado (§7) + DRM ClearKey por-item | varias |

---

## 11. Lo que este documento no afirma

- No hay nada compilado ni ejecutado (sin JDK ni Android SDK en este entorno; ver §0 del diagnóstico).
- Las cifras de líneas y de llamadores salen del ZIP v7.38.3; si el árbol ha cambiado desde entonces, hay que re-medir.
- Ningún plazo en «sesiones» es una promesa: es una estimación de tamaño, no de calendario.
