# Diagnóstico: «no se pueden reproducir canales de Drama» — v7.38.3

**Fecha:** 2026-11-17
**Fuente analizada:** `PelisLatino-Android-v7.38.3-SOLO-CODIGO.zip` (Google Drive, `1DiIpN6AGFtC4R1tWe31Asrgl4G2KgMEJ`, 4 070 519 B, `versionCode 82`, `versionName 7.38.3`, 853 ficheros, 117 `.java`).
**Contraste:** `PlayerActivity.java` de la entrega anterior (v7.38.2, Drive `1q0tmNyczfGgn...`, 194 860 B) para separar **regresión nueva** de deuda vieja.

---

## 0. Qué se pudo medir y qué no (honestidad de medición)

| Comprobación | Resultado |
|---|---|
| Lectura íntegra del árbol Java/JS/res | ✅ 23 410 líneas de `app/src/main/java` revisadas por búsqueda dirigida |
| `node --check app/src/main/assets/drama_auto.js` | ✅ OK (47 898 B) |
| `diff` v7.38.2 ↔ v7.38.3 de `PlayerActivity.java` | ✅ 34 líneas de diferencia (4 cambios) |
| Compilar (`./gradlew test`) | ❌ **imposible en este entorno**: no hay JDK (`java: command not found`), no hay Android SDK, y la única red saliente es github.com / npm / pypi — Maven Central y Google Maven están bloqueados. **Ningún arreglo de este informe está compilado ni probado en ejecución.** |
| Hablar con la API de Drama (`main.fastconnections.online`, `web.dramalive.cc`) | ❌ **HTTP 000** (sin ruta de red desde el sandbox). No puedo afirmar si el edge está caído hoy ni si `/v13.0.0/glasbi` sigue devolviendo `live.url`. |

> **Regla que respeto:** nada de aquí dice «ya funciona». Son defectos **leídos en el código**, con archivo y línea, más el parche propuesto. La confirmación es el logcat del teléfono (§6).

---

## 1. Resumen ejecutivo

El canal **sí entra** al reproductor; lo que se rompe es la **cadena de reintentos entre servidores**. Encontré un error de control de flujo que hace que, *tras el primer servidor fallido*, la app declare «se han probado TODOS los servidores» aunque queden siete sin abrir. Ese único `return null` explica el síntoma «pulso el canal y no reproduce, o vuelve a la lista» sin necesidad de que el proveedor esté caído.

Ordenado por impacto:

| # | Defecto | Archivo:línea | Severidad |
|---|---|---|---|
| 1 | `siguienteOpcionUtil()` aborta la lista entera en vez de saltar al siguiente | `PlayerActivity.java:2253-2263` | 🔴 Bloqueante |
| 2 | El clip de relleno `update.mp4` **no dispara ningún plan B** | `PlayerActivity.java:644-659` | 🔴 Bloqueante |
| 3 | `canalCurado` nunca se asigna → toda la máquina de auto-curación es código muerto | `PlayerActivity.java:221, 473-520` | 🔴 Bloqueante |
| 4 | `primeraEmbebida()` solo reconoce `ordernap.` — cualquier otro embed se descarta | `DramaSesion.java:786-800` | 🟠 Alto |
| 5 | Regresión 7.38.3: el aviso de marcador `.LS.` **se come el plan B de embed** | `PlayerActivity.java:2608-2624` | 🟠 Alto |
| 6 | ClearKey se **parsea y se tira**: los canales DASH cifrados no pueden abrir | `DramaResolucion.java:228-260` | 🟠 Alto |
| 7 | `cargarServidoresDelCanal()` hace un **POST extra a glasbi en cada play** (hasta 3 POST al mismo endpoint) | `PlayerActivity.java:580, 2225` | 🟡 Medio |
| 8 | `streamUrl` / `streamBackup` no se limpian al cambiar de canal (`singleTask`) | `PlayerActivity.java:3140-3160` | 🟡 Medio |
| 9 | Dos parseadores del `live.backup` con separadores distintos | `DramaSesion.java:724,1268` vs `DramaResolucion.java:98` | 🟡 Medio |
| 10 | `DramaResolucion.embebidas()/mejorEmbebida()/siguienteEmbebida()` solo las usa la suite → tests que dan falsa confianza | `DramaResolucion.java:117-140` | 🟢 Bajo |

---

## 2. 🔴 BUG #1 — el plan B muere en el primer servidor fallido

**`PlayerActivity.java:2253`**

```java
private DramaResolucion.Opcion siguienteOpcionUtil() {
    List<DramaResolucion.Opcion> utiles = DramaResolucion.utiles(streamUrl, streamBackup);
    for (DramaResolucion.Opcion o : utiles) {
        if (urlEnReproduccion != null && o.url.equals(urlEnReproduccion)) continue;
        if (embedEnCurso != null && o.url.equals(embedEnCurso.url)) continue;
        for (String vista : servidoresProbados) if (o.url.equals(vista)) return null;  // ← AQUÍ
        return o;
    }
    return null;
}
```

### Por qué rompe todo

`servidoresProbados` se llena en `mostrarEmbedEnLaVentana(Opcion)` (línea 2267). Secuencia real de un canal de Drama:

1. `glasbi` devuelve `live.url` = marcador `.LS.` y 3-8 entradas en `live.backup`.
2. `DramaResolucion.utiles()` ordena **EMBEBIDA primero** (comentario de la propia clase, líneas 285-300).
3. `siguienteOpcionUtil()` → devuelve el **primer** embed, correcto. Se abre, `servidoresProbados = [embed1]`.
4. El embed no arranca (30 s, `revisarSiElEmbedArranco()`, línea 2372).
5. `siguienteOpcionUtil()` otra vez: `utiles.get(0)` **es** `embed1`, ya está en `servidoresProbados` → **`return null`**.
6. Línea 2381: `log("plan B: se han probado TODOS los servidores del canal")` → `avisarSinStream()` → «No se pudo cargar este enlace».

**Los servidores 2..N nunca se abren.** El `return null` está dentro del bucle interior; debe ser `continue` sobre el bucle exterior (o comprobar con un booleano y seguir).

### Parche

```java
private DramaResolucion.Opcion siguienteOpcionUtil() {
    List<DramaResolucion.Opcion> utiles = DramaResolucion.utiles(streamUrl, streamBackup);
    for (DramaResolucion.Opcion o : utiles) {
        if (urlEnReproduccion != null && o.url.equals(urlEnReproduccion)) continue;
        if (embedEnCurso != null && o.url.equals(embedEnCurso.url)) continue;
        boolean yaVisto = false;
        for (String vista : servidoresProbados) {
            if (o.url.equals(vista)) { yaVisto = true; break; }   // ← saltar, NO abortar
        }
        if (yaVisto) continue;
        return o;
    }
    return null;
}
```

### Test que lo pinta en rojo (JUnit puro, sin Android)

`DramaResolucion` es pura; `siguienteOpcionUtil` no lo es porque toca campos de Activity. **Extraer la elección a `DramaResolucion.siguiente(List<Opcion>, Set<String> vistas, String actual)`** y testear:

```java
@Test public void elSegundoServidorSeIntentaCuandoElPrimeroFalla() {
    List<Opcion> u = DramaResolucion.utiles(MARCADOR, BACKUP_BEIN1); // 8 servidores
    String primero = u.get(0).url;
    Opcion sig = DramaResolucion.siguiente(u, Set.of(primero), null);
    assertNotNull(sig);
    assertNotEquals(primero, sig.url);        // hoy devuelve null -> rojo
}
```

---

## 3. 🔴 BUG #2 — `update.mp4` (el clip de relleno) no abre ninguna puerta

**`PlayerActivity.java:644`**

```java
if (rr[0].contains("update.mp4")) {
    ...
    mostrarErrorPlayer(R.string.player_error_relleno);   // "Servidor no disponible por ahora"
    showControls();
    log("glbr devolvio el clip de relleno; se conserva el video actual y solo se muestra el aviso");
    return;                                              // ← se va sin plan B
}
```

Y el `strings.xml:55` dice literalmente **«Servidor no disponible por ahora»** — es el mensaje que el usuario lee y traduce como «no se pueden reproducir los canales».

### Por qué rompe

En **todas** las demás ramas de fallo (`glbr fallo`, `onPlayerError`, `avisarSinStream`) la app intenta el siguiente servidor o el embed. **Solo aquí** se rinde. Pero `Documentación v3.2.12` (docs/COMO_SESION_DRAMA.md:60) demuestra que el sueño es una respuesta legítima del servidor al POST desde Java — **y que el embed del mismo canal sí emite**. Es el caso en el que más falta hace el plan B y es el único que no lo tiene.

Peor: hay un `return` antes de ejecutar la lógica de `siguienteOpcionUtil()` que existe 20 líneas más abajo en la rama gemela.

### Parche

```java
if (rr[0].contains("update.mp4")) {
    RegistroCarga.paso("glbr devolvio relleno: se prueban los servidores del canal");
    RegistroCarga.fin();
    streamReady = false;
    if (player != null) player.pause();
    ocultarCargando();
    if (opcionActual >= 0) opcionesFallidas.add(opcionActual);
    DramaResolucion.Opcion planB = siguienteOpcionUtil();
    if (planB != null) {
        log("relleno -> plan B: " + planB.etiqueta());
        mostrarErrorPlayer(R.string.player_error_relleno);  // aviso honesto, no silencio
        urlIframeInicial = planB.url;
        mostrarEmbedEnLaVentana(planB);                     // va directo, sin 20 s de carrera
        return;
    }
    mostrarErrorPlayer(R.string.player_error_relleno);
    showControls();
    return;
}
```

Además: **suavizar el texto**. «Servidor no disponible por ahora» se lee como que la app está rota. Propuesta: `«Ese servidor no emitió ahora; probando otro…»` y un `«No queda ningún servidor de este canal»` cuando de verdad se agoten (hoy los dos casos dicen lo mismo).

---

## 4. 🔴 BUG #3 — la auto-curación está desconectada: `canalCurado` nunca deja de ser `null`

**`PlayerActivity.java:221`** declaración, y estas son **todas** las asignaciones del fichero:

```
221:  private String canalCurado;          // declaración, vale null
483:  canalCurado = null;
513:  canalCurado = null;
655:  canalCurado = null;
662:  canalCurado = null;
```

Medido con `grep -n "canalCurado\s*=" | grep -v "= null"` → **cero resultados**.

### Consecuencia en cadena

- `reintentaTrasCura()` (línea 473) empieza con `if (isFinishing() || canalCurado == null) return;` → **siempre** retorna en la primera línea.
- `curaReintentoRun` (línea 226) solo llama a `reintentaTrasCura` → **nunca hace nada**.
- `notificarEntrega()` (línea 435) exige `p.canalCurado != null` → **el «despierto al player ya» de v3.2.21 nunca se dispara**.
- `notificarCuraFracasada()` (línea 456) exige `pid.equals(p.canalCurado)` → **nunca coincide**.

O sea: **toda la máquina documentada en `docs/COMO_SESION_DRAMA.md` (§v3.2.13 → v3.2.47) está inerte.** Cuando el `glbr` devuelve relleno, el player ya no lanza la cosecha headless, ni se auto-reintenta a los 70 s, ni reproduce la fila curada «sin que el usuario toque nada». El usuario toca play, ve «Servidor no disponible», y nada más pasa. **Ese es, con mucha probabilidad, el síntoma exacto que reporta.**

### Contexto (regresión histórica, no de 7.38.3)

El `diff` contra v7.38.2 confirma que **7.38.3 no lo empeoró**: el campo ya estaba huérfano. Se perdió en la limpieza de v3.2.46/v3.2.47, que retiró «el salto al captador VISIBLE» (tres caminos) y con ellos, al parecer, el único punto que hacía `canalCurado = pid`. La documentación quedó describiendo un flujo que el código ya no ejecuta.

### Parche mínimo (devolver el disparo, sin reabrir el infierno de v3.2.47)

En la rama de relleno del BUG #2, **después** de intentar el plan B de servidores:

```java
// v7.38.4 — reactivar la cura: el pid se marca y el player se auto-reintenta.
String pid = DramaSesion.idDeCanal(canal);          // dramaLive:// -> id_live
if (pid != null && !quemadosHeadless.containsKey(pid)
        && !DramaSesion.curaFresca(curaTsMap, pid, System.currentTimeMillis(), 100_000L)) {
    canalCurado = pid;
    curaTsMap.put(pid, System.currentTimeMillis());
    DramaFondo.abrirCaptadorHeadless(this);          // sigue existiendo y NO abre UI
    mainHandler.removeCallbacks(curaReintentoRun);
    mainHandler.postDelayed(curaReintentoRun, 70_000L);
}
```

`DramaFondo.abrirCaptadorHeadless(Context)` está **vivo** (`DramaFondo.java:221`) y **sin un solo llamador** (`grep -rn abrirCaptadorHeadless app/src` → solo su definición). Es literalmente la pieza que se quedó sin cable.

---

## 5. 🟠 Bugs #4 a #6 — lo que impide abrir canales concretos

### #4 `primeraEmbebida()` tiene el dominio clavado a mano — `DramaSesion.java:796`

```java
if (l.contains("ordernap.")) return u;   // el único que emite
```

`resolverVivo()` (línea 1414) hace `primeraEmbebida(vg[2])` y **prefiere siempre el embed**. Pero esta función **descarta todos** los que no sean `ordernap.`. Consecuencias:

- Si el proveedor rota de dominio de embed (ya pasó: `quotarevival` → muerto, `ordernap` → el bueno), **todos** los canales de beIN se quedan sin su vía buena y caen al `glbr`/`update.mp4`.
- Si un canal trae un único embed nuevo (p. ej. otro `/e/…`), se ignora aunque `DramaResolucion.esEmbebida()` (línea 150) sí sepa reconocerlo.

**Parche:** usar el clasificador que ya existe y no una subcadena de dominio.

```java
for (JsonElement e : r.getAsJsonArray()) {
    ...
    String l = u.toLowerCase(Locale.ROOT);
    if (DramaResolucion.esEmbedMuerto(u)) continue;            // quotarevival y futuros
    if (DramaResolucion.clasificar(u, false) == DramaResolucion.Tipo.EMBEBIDA) return u;
}
```

`clasificar()` reconoce `/e/`, `/embed/`, `embed.`, `player.` y `iframe` — y `esEmbedMuerto()` centraliza la lista negra para que el próximo dominio muerto se añada en un solo sitio.

### #5 Regresión 7.38.3: el aviso de `.LS.` se come el plan B — `PlayerActivity.java:2608`

Del `diff` real contra v7.38.2, esto **es nuevo en 7.38.3**:

```java
private void avisarSinStream() {
    // v7.38.3 — marcador .LS.: ...
    if (esMarcadorLs(streamDirecto) || esMarcadorLs(urlIframeInicial)) {
        ... Toast "el redirector no responde" ...
        return;                                   // ← sale ANTES del plan B de embed
    }
    String elegida = DramaSesion.primeraEmbebida(streamOpcionesJson);   // esta línea ya no se alcanza
    ...
}
```

`streamDirecto` conserva el marcador de la fila (`dramalive://` → `u=http://.LS.V2…`) aunque el canal **tenga embeds que sí emiten**. En 7.38.2 ese canal aún probaba el embed; en 7.38.3 se queda en el toast. **Una regresión que reduce lo que se reproduce**, justo en la versión que se está reportando.

**Parche:** el aviso de marcador debe ser el **último** recurso, después de intentar los embeds:

```java
private void avisarSinStream() {
    String elegida = DramaSesion.primeraEmbebida(streamOpcionesJson);
    if (elegida == null) elegida = DramaSesion.primeraOpcionNoMarcador(streamOpcionesJson);
    if (elegida != null && !embeddedEnVentana && !isFinishing()) {
        mostrarEmbedEnLaVentana(elegida);
        return;
    }
    if (esMarcadorLs(streamDirecto) || esMarcadorLs(urlIframeInicial)) { ... return; }
    ...
}
```

### #6 ClearKey: se lee la clave y se tira — `DramaResolucion.java:228`

`claveClearKey()` decodifica `drm.clearkey` → `KID:CLAVE` en hex, se guarda en `Opcion.clearKey`… y **`clearKey` no se usa en ninguna parte de `app/src/main`**:

```
grep -rn "clearKey" app/src/main/java  → 6 hits, todos de declaración/parseo en DramaResolucion
grep -rn "MediaDrm|LocalMediaDrmCallback|DrmSessionManager" app/src/main/java → 0 hits
```

El comentario de `PlayerActivity.java:1778-1785` explica el porqué: en **7.38.2 se retiró el gestor DRM «para todos los MediaItem»** porque reventaba la apertura en móvil real. La retirada fue correcta; el efecto colateral es que los canales DASH/ClearKey (Abu Dhabi Al Emarat y los starzplay/intigral) **ya no pueden abrirse nunca**, y la dependencia `media3-exoplayer-dash` sigue en el build.

**Parche correcto (no el de 7.38.1):** instalar el DRM **solo para el `MediaItem` que lo necesita**, nunca a nivel de factory.

```java
// en mediaItemPara(url), solo si la opcion elegida trae KRY
if (clearKeyActual != null) {           // "kid:key" hex que ya sabe sacar DramaResolucion
    b.setDrmConfiguration(new MediaItem.DrmConfiguration.Builder(C.WIDEVINE_UUID) ...
        // ClearKey: usar LocalMediaDrmCallback con la clave, y
        // b.setMimeType(MPD) — el DRM se construye por-item.
}
```

Requiere `androidx.media3.exoplayer.drm` + `setMediaDrmSessionManager` en un `ExoPlayer` **recreado** para ese canal (no en el factory global). Es trabajo real: **prioridad menor** frente a #1-#3, pero hay canales hoy en el catálogo que dependen de ello.

---

## 6. 🟡 Deuda que hay que cerrar antes de la próxima versión

### #7 Un POST extra a glasbi en cada play — `PlayerActivity.java:580`

```java
DramaSesion.Vivo vv = DramaSesion.resolverVivo(canal, ses);   // 1º POST glasbi (a veces 2)
if (vv != null) {
    streamOpcionesJson = vv.opciones;
    cargarServidoresDelCanal(canal, ses);                      // 2º POST glasbi — ¡otra vez!
```

`cargarServidoresDelCanal()` llama a `DramaSesion.glasbiCrudo()`, que repite **exactamente** el POST que `resolverVivo` acaba de hacer. Son hasta **3 POST al mismo endpoint por cada play**. El propio histórico del proyecto documenta el castigo: «98 POST a la API en 13 s → el servidor empezó a contestar 32 bytes en blanco a todo» (`docs/COMO_SESION_DRAMA.md`, v3.2.11). Y el `Vivo` ya tiene los campos para evitarlo:

```java
/** v7.38.0 — el live.url y el live.backup EN CRUDO. */
@Nullable public final String crudoUrl;
@Nullable public final String crudoBackup;
```

**Están declarados y son SIEMPRE `null`**: los cuatro `new Vivo(...)` del fichero (líneas 1417, 1422, 1436, 1442) usan el constructor de 3 argumentos. Es una optimización empezada y no terminada.

**Parche:** rellenar `crudoUrl`/`crudoBackup` en `vivoDesdeGlasbi` (ya tiene el `JsonObject live` en la mano) y borrar la llamada a `cargarServidoresDelCanal()` del camino feliz.

### #8 Servidores rancios al zapear — `PlayerActivity.java:3140`

`onNewIntent` (la Activity es `launchMode=singleTask`) limpia `streamCabeceras`, `dramaLiveEnJuego`, `streamDirecto`, `urlIframeInicial`… pero **no** `streamUrl`, `streamBackup`, `opcionesServidor`, `servidoresProbados`. Si la resolución del canal nuevo falla, el plan B puede abrir **un servidor del canal anterior**. Añadir al inicio de `abrirEnEstePlayer`:

```java
streamUrl = null; streamBackup = null; embedEnCurso = null;
servidoresProbados.clear(); opcionesServidor.clear(); opcionesFallidas.clear();
opcionActual = -1; yaSeHizoNativo = false; playlistCazado = null;
```

### #9 Dos parseadores del `backup` con separadores distintos

```
DramaSesion.java:724, 1268   →  bk.split(" -;- ")        (literal, revienta con "  -;-  " o " -;-")
DramaResolucion.java:98      →  backup.split("\\s*-;-\\s*")  (tolerante)
```

El chip de calidad y `directaDeBackup` usan el estricto; el plan B usa el tolerante. **El mismo canal puede tener 8 servidores para el plan B y 1 en el chip.** Unificar en `DramaResolucion` y llamarlo desde los dos sitios.

### #10 Tests que dan falsa confianza

`DramaResolucionTest` tiene 7 pruebas sobre `embebidas()`, `mejorEmbebida()`, `siguienteEmbebida()`… y **ninguna de esas tres funciones se llama desde `app/src/main`** (solo desde el test). La cobertura mide código que el usuario no ejecuta, mientras el que sí se ejecuta (`siguienteOpcionUtil`, #1) no tiene ni un test. La suite de 174 pruebas verdes convivió con el bug bloqueante del §2.

### Nota sobre `tienePlantillaGlbr()` — `DramaSesion.java:1584`

Exige `plantillas.glbrReq.user_id`, y la propia documentación del proyecto dice que **`glbrReq` nunca trae ese campo** (§v3.2.25: «DOS guardas `user_id` descartaban la plantilla glbrReq (que NUNCA trae ese campo)»). Hoy es inofensiva porque **no la llama nadie** (solo `DramaSesionTest:814`). Si alguien la cablea como puerta de la cura, **vuelve el bug de v3.2.25 tal cual**. Marcarla `@Deprecated` o alinearla con `plant.has("glbrReq")` a secas.

---

## 7. Cómo confirmar en el teléfono en 60 segundos (logcat filtrado `BLABONGO_DEBUG`)

Abrir **un** canal de Drama que falle y buscar estas líneas. Cada una delata un bug concreto:

| Línea en logcat | Significa |
|---|---|
| `▶️ canal abierto · servidor 1/N · …` | entró al player (si NO sale: el tap ni llega, otro problema) |
| `servidores del canal: N · útiles M` | ¿M > 1? Entonces el #1 debería probarlos todos |
| `plan B: se han probado TODOS los servidores del canal` **con M>1** | 🔴 **BUG #1 confirmado** en tu móvil |
| `glbr devolvio el clip de relleno` y **nada después** | 🔴 **BUG #2 + #3 confirmados** (no hubo plan B ni cura) |
| `🔒 canal con marcador .LS.` seguido del toast | 🟠 **BUG #5** (7.38.3 se comió el embed) |
| `Drama v7.38.3 (code 82)` | confirma que corre el build correcto |
| Ausencia total de `captador(headless)` / `LANZANDO` | 🔴 **BUG #3** (la cura no se lanza nunca) |
| `glasbi [glt] HTTP 201` **dos veces** por canal | 🟡 **BUG #7** (POST duplicado) |

Comando: `adb logcat -s BLABONGO_DEBUG | grep -E "canal abierto|servidores del canal|plan B|relleno|marcador|glasbi \[|cura|LANZANDO"`

Y aparte, la comprobación de si el problema es del proveedor y no de la app: abrir `https://web.dramalive.cc` en el navegador del móvil, entrar con el código y **ver si el canal se ve en el sitio**. Si en el sitio sí y en la app no → es nuestro (§2-§5). Si en el sitio tampoco → el edge/la cuenta está caído y la app está dando la cara correcta.

---

## 8. Orden de trabajo propuesto

1. **#1** (`return null` → `continue`) — 3 líneas, desbloquea el plan B. Con test JUnit puro.
2. **#2** (rama `update.mp4` con plan B) — desbloquea el caso más frecuente.
3. **#3** (reactivar `canalCurado` con `DramaFondo.abrirCaptadorHeadless`) — devuelve la auto-curación; **con tope de 90 s y sin abrir UI** (el infierno de v3.2.47 fue por pantalla completa, no por el headless).
4. **#5** (revertir la regresión de 7.38.3) — 10 líneas, restaura lo que 7.38.2 hacía.
5. **#4** (`primeraEmbebida` por clasificador, no por dominio).
6. **#7/#8/#9** (una sola pasada de limpieza; ganan velocidad y anti-flood).
7. **#6** (ClearKey por-item) — al final, es el único que necesita dependencias nuevas.

**Versión sugerida: 7.38.4 / `versionCode 83`.** Los puntos 1-5 son quirúrgicos y no tocan la arquitectura; se pueden entregar juntos con la suite en verde y un test nuevo por bug.

---

## 9. Lo que NO voy a afirmar

- No digo que con estos parches «se vean todos los canales». El clip `update.mp4` puede seguir saliendo: la documentación del proyecto lo atribuye a una puerta del servidor (IP/datacenter/SDK de anuncios) y **eso no se arregla en el cliente**.
- No he compilado ni ejecutado nada (§0). El parche del §6 no está validado contra `javac`.
- No sé si `main.fastconnections.online` está vivo hoy. Si está caído, **todo** falla y los bugs de este informe siguen siendo ciertos pero no serían la causa del día.

---

*Informe generado leyendo el árbol completo de la v7.38.3 y comparando `PlayerActivity.java` contra la v7.38.2. Todas las cifras de línea corresponden a los ficheros del ZIP analizado.*
