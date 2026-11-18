# v7.38.4 — lo aplicado, y qué NO está probado

**Base:** v7.38.3 (`versionCode 82`) · **Resultado:** `versionCode 83` / `versionName "7.38.4"`
**Compañeros:** [`DIAGNOSTICO-CANALES-DRAMA-v7.38.3.md`](DIAGNOSTICO-CANALES-DRAMA-v7.38.3.md) · [`MEJORAS-APP-v7.38.3.md`](MEJORAS-APP-v7.38.3.md)

---

## 0. Estado de verificación (leer antes que nada)

| Comprobación | Resultado |
|---|---|
| Sintaxis Java de los 4 ficheros tocados | ✅ `java-parser` 3.0.1 (parser Java completo, npm) — `PARSE_OK` |
| Lógica del plan B y del troceado del backup | ✅ `node docs/repro-planb.mjs` → `REPRO_OK` (8 comprobaciones) |
| `node --check app/src/main/assets/drama_auto.js` | ✅ |
| Recursos XML (`strings.xml`) | ✅ parsean |
| **Compilación real** (`javac` / Gradle) | ❌ **NO**. Este entorno no tiene JDK ni Android SDK: Maven Central y Google Maven están bloqueados y `objects.githubusercontent.com` (de donde baja el tarro de Adoptium) también. Se intentó: `jdk4py` (solo trae JRE, sin `jdk.compiler`), `install-jdk` (necesita Adoptium), Temurin desde GitHub (302 → host bloqueado). |
| **Ejecución de la suite JUnit** | ❌ **NO**. Requiere `android.jar` + Media3 + Gson, que solo viven en Google Maven. |
| Prueba en dispositivo | ❌ no tengo acceso al teléfono. |

> **Lo que esto significa en una frase:** el código **parsea**, la lógica **está reproducida en node con datos reales**, pero **nadie lo ha compilado ni ejecutado**. Antes de publicar: `./gradlew :app:testDebugUnitTest` en una máquina con Android Studio.

---

## 1. Los cambios, uno por uno

### 🔴 #1 — El plan B ya no se rinde en el primer servidor

- **`DramaResolucion.siguiente(utiles, yaProbados, actual, enCurso)`** (nueva, pura): salta los ya probados con `continue` en vez de abortar.
- **`PlayerActivity.siguienteOpcionUtil()`** pasa a delegar en ella (antes tenía el `return null` dentro del bucle).

**Medición honesta del alcance** (`docs/repro-planb.mjs`, T1b/T1c):

| Servidores útiles | ¿Dispara el bug de la 7.38.3? |
|---|---|
| 2 | **No.** Los guards de `actual`/`enCurso` lo tapan. |
| 3 o más | **Sí.** Se aborta la lista y el tercero nunca se intenta. |

El backup real de beIN 1 copiado en el test deja **2 útiles** (1 embebida `ordernap` + 1 directa `primetv`); los otros 8 trozos son marcadores `.LS.` que `utiles()` filtra. **O sea: este bug afecta a los canales que traen 3+ servidores usables, no a todos.** El informe anterior decía «con siete sin abrir» — era la cuenta de *trozo de backup*, no de servidores útiles. Corregido aquí y en el test (`conDosServidoresUtilesElBugNoLlegaADispararse`).

### 🔴 #2 — El clip de relleno abre el plan B

`PlayerActivity`, rama `update.mp4`: antes solo mostraba el toast y hacía `return`. Ahora:

```
relleno -> siguienteOpcionUtil() -> mostrarEmbedEnLaVentana(planB)   // directo al marco
        o, si no queda ninguno -> aviso honesto + lanzarCuraDelCanal()
```

### 🔴 #3 — La cura vuelve a estar cableada

- **`lanzarCuraDelCanal(String canal)`** (nueva): asigna `canalCurado = pid`, respeta el cooldown de 100 s por canal, la lista `quemadosHeadless`, lanza `DramaFondo.abrirCaptadorHeadless(this)` (que **no tenía ningún llamador**) y programa el reintento a 70 s.
- Con eso vuelven a tener efecto `reintentaTrasCura()`, `notificarEntrega()` y `notificarCuraFracasada()`, que exigían `canalCurado != null` y por tanto eran inalcanzables.
- **No se repone** el salto al captador *visible* a pantalla completa (lo que se retiró en v3.2.46/v3.2.47 tras la queja del usuario).

### 🟠 #5 — Revertida la regresión de la 7.38.3

`avisarSinStream()`: el aviso de marcador `.LS.` (nuevo en 7.38.3) salía con un `return` **antes** de intentar el embed, así que los canales con backup bueno se quedaban en el toast. Ahora el orden es: **embebida → siguiente servidor pendiente → aviso de marcador → aviso genérico**.

### 🟠 #4 — `primeraEmbebida()` / `siguienteEmbebida()` por clasificador

Fuera `if (l.contains("ordernap."))`. Ahora: `esEmbedMuerto(u) ? skip : clasificar(u) == EMBEBIDA`. Un embed de dominio nuevo se acepta; `quotarevival` sigue fuera y la lista negra vive en un solo sitio.

### 🟡 #7 — Un POST menos por canal

`DramaSesion` cachea el bloque `live` **20 s** (`liveEnCache`/`recordarLive`). `resolverVivo` y `glasbiCrudo` dejan de pedir lo mismo dos veces. Hoy eran hasta **3 POST a glasbi por play**; el histórico del proyecto documenta que el edge castiga eso («98 POST en 13 s → 32 bytes en blanco a todo»). Ahorro medible: 1 POST por canal y el segundo y tercero solo si la caché expira.

### 🟡 #8 — Estado del canal anterior fuera

`limpiarEstadoDrama()` (nueva) en `onNewIntent`: limpia `streamUrl`, `streamBackup`, `streamOpcionesJson`, `embedEnCurso`, `playlistCazado*`, `servidoresProbados`, `marcadorForzado`, `calidadElegida`, `canalCurado`… Antes, al zapear con la Activity `singleTask`, el plan B podía sacar un servidor del canal **anterior** con su UA y su Referer.

### 🟡 #9 — Un solo parser del backup

`DramaSesion.trozosDeBackup()` con `\\s*-;-\\s*`, usado por `directaDeBackup`, `opcionesDeLive` y `DramaResolucion.opciones`. Antes había dos criterios: un backup con dos espacios daba **1 servidor** en el chip de calidad y **2** en el plan B.

### Textos

- `player_error_relleno`: «Servidor no disponible por ahora» → **«Ese servidor no emitió ahora; probando otro…»**.
- Nuevo `player_sin_mas_servidores`: **«No queda ningún servidor de este canal. Se cura solo en segundo plano.»**

---

## 2. Tests

**`app/src/test/java/dza/fut/paipo/PlanBTest.java`** (11 pruebas, JUnit puro, sin Android):

| Test | Qué fija |
|---|---|
| `conDosServidoresUtilesElBugNoLlegaADispararse` | el alcance REAL del bug (calibración) |
| `conTresServidoresElTerceroSiSeIntenta` | el bug bloqueante, en su condición real |
| `seRecorrenTodosLosServidoresYSeAcabaEnNull` | sin repetir, en orden, y `null` solo al final |
| `elPlanBSalteaElEmbedMuertoYNoElBueno` | `quotarevival` fuera, `ordernap` dentro |
| `cualquierEmbedVivoValeNoSoloOrdernap` | el dominio ya no está clavado |
| `elEmbedMuertoSigueEstandoExcluido` | regresión del anterior |
| `unaDirectaNoSeConfundeConEmbed` | un m3u8 no es un embed |
| `elBackupSeParteIgualConEspaciosRaros` | un solo criterio |
| `backupVacioNoRompeNada` | nulos y vacíos |
| `siguienteAguantaListasVaciasYBasura` | pureza |

**`docs/repro-planb.mjs`** — la misma lógica en node, con el backup real. Se ejecuta hoy:

```
$ node docs/repro-planb.mjs
servidores ÚTILES de beIN 1: 2 (EMBEBIDA, DIRECTA)
  ✓ T1b: con 2 útiles el código VIEJO acierta (el bug no aplica)
  ✓ ROJO3873: con 3 útiles y 2 probados el código viejo devuelve null
  ✓ REPRO_OK: el código nuevo devuelve el tercero (la directa)
  ✓ TODOS_OK: se prueban todos, sin repetir, y al final null
  ✓ EMBED_OK: un embed de dominio nuevo se acepta
  ✓ EMBED_MUERTO_OK: quotarevival sigue excluido
  ✓ EMBED_BUENO_OK: ordernap sigue entrando
  ✓ BACKUP_OK: el troceado tolerante ve 2 servidores donde el viejo veía 1
REPRO_OK · ROJO3873_OK · TODOS_OK · EMBED_OK · BACKUP_OK
```

---

## 3. Lo que sigue sin arreglar (a propósito)

| # | Qué | Por qué no está aquí |
|---|---|---|
| #6 | **ClearKey/DASH**: `claveClearKey()` se parsea y se tira; 0 usos de `MediaDrm` en `main` | Necesita dependencias nuevas y un `ExoPlayer` recreado por canal. La 7.38.1 lo intentó «para todos los MediaItem» y rompió la apertura en móvil real. Es trabajo aparte, con cuidado. |
| #10 | `DramaResolucion.embebidas/mejorEmbebida/siguienteEmbebida` solo las llama el test | Borrarlas o cablearlas es decisión de producto; no cambia el comportamiento y alarga el diff. |
| — | `Catalogo` unificado, `EmbudoDrama` (rate limit global), CI | Fase 7.39/7.40, ver `MEJORAS-APP` §9-§10. |
| — | `tienePlantillaGlbr()` exige `user_id` que `glbrReq` nunca trae | Hoy inofensiva (nadie la llama). Si se cablea como puerta de la cura, **vuelve el bug de v3.2.25**: marcarla `@Deprecated` antes de tocarla. |

---

## 4. Cómo probarlo en el teléfono

1. Compilar en una máquina con Android Studio: `./gradlew :app:testDebugUnitTest` (debe salir verde, incluido `PlanBTest`) y luego `:app:assembleDebug`.
2. Instalar y abrir un canal de Drama que antes fallara.
3. Filtrar el log: `adb logcat -s BLABONGO_DEBUG`
4. Líneas que deben aparecer **ahora** y antes no:

| Línea | Qué confirma |
|---|---|
| `Drama v7.38.4 (code 83)` | corre el build nuevo |
| `relleno -> plan B: EMBEBIDA …` | **#2** (antes no existía) |
| `↩️ sin stream nativo; queda un servidor por probar -> …` | **#1/#5** |
| `cura LANZADA para <id> — cosecha headless (invisible)` | **#3** (antes nunca salía) |
| `glasbiCrudo: servidores del canal desde la cache (0 POST)` | **#7** |
| `plan B: se han probado TODOS los servidores` **solo** tras agotar los útiles | **#1**: ahora el mensaje dice la verdad |

5. Si sale `No queda ningún servidor de este canal`, el canal es de los que de verdad no tienen alternativa: la app lo dice en vez de fingir.

---

## 5. Riesgo de esta entrega

Los cambios son quirúrgicos y aislados (una función pura nueva, dos métodos nuevos en la Activity, caché de 20 s, un parser unificado), pero **sin compilar**. Los puntos donde yo vigilaría primero, por orden:

1. **`limpiarEstadoDrama()`** en `onNewIntent`: si algún flujo dependía de que `opcionesServidor`/`streamUrl` sobrevivieran a un intent nuevo, aquí se rompe. Se limpia después de `setIntent` y antes de `leerOpcionesDeIntent`, que es el orden correcto, pero es el cambio con más superficie.
2. **`lanzarCuraDelCanal()`**: ahora sí se abre `DramaAutoActivity` headless. Si en el teléfono esa ventana se ve o roba el foco, hay que cerrarla — está en su propio task (`taskAffinity=dza.fut.paipo.cura`, `excludeFromRecents=true`) y en modo 2×2 px, pero **no se ha ejecutado**.
3. **Caché de 20 s del `live`**: si un canal cambia de servidores en menos de 20 s (posible en directo), el plan B puede usar la lista de hace 20 s. Se auto-corrige al expirar; el tope de 64 entradas evita crecer.
