# v7.38.5 — la unión: tu parche medido + lo que solo tenía el mío

**Base:** el ZIP `v7.38x.4` (tuyo, 3 ficheros) · **Resultado:** `versionCode 84` / `versionName "7.38.5"`
**Compañeros:** [`DIAGNOSTICO-CANALES-DRAMA-v7.38.3.md`](DIAGNOSTICO-CANALES-DRAMA-v7.38.3.md) · [`CAMBIOS-v7.38.4.md`](CAMBIOS-v7.38.4.md) · [`repro-planb.mjs`](repro-planb.mjs)

---

## 0. Por qué existe esta versión

Había **dos** parches 7.38.4 distintos sobre la misma 7.38.3:

| | Tu `v7.38x.4` | Mi `v7.38.4` |
|---|---|---|
| Ficheros | 3 | 8 |
| `servidoresProbados.add(embed)` al pintar | ✅ | ❌ |
| Reset de probados al cambiar de canal | ✅ (en `cargarOpcionServidor`) | ✅ (en `onNewIntent`) |
| Directa con SU UA/Referer | ✅ | ❌ |
| Re-chequeo de `videoDeEmbedAvanza` | ✅ | ❌ |
| `idDeCanal()` con fallback a `tvg-id` | ❌ | ✅ |
| Caché de 20 s del `live` (1 POST menos) | ❌ | ✅ |
| `trozosDeBackup()` unificado | ❌ | ✅ |
| `lanzarCuraDelCanal()` (la cura estaba muerta) | ❌ | ✅ |
| Rama del relleno con plan B | ❌ | ✅ |
| Tests | 3 (en `DrmYActividadTest`) | 11 (en `PlanBTest`) |

**Tu parche gana en el sitio correcto.** El logcat del móvil (19:02:04→19:02:49) demuestra que el problema real no era el `return null` que yo arreglé, sino que **nadie apuntaba el embed al pintarlo**, así que el temporizador de 30 s reponía *el mismo* una y otra vez. Eso solo lo arreglas tú. La v7.38.5 parte de tu base.

---

## 1. El bug que mi 7.38.4 NO arreglaba (y tu log destapó)

Reproducido con la secuencia exacta del logcat, con el código de mi 7.38.4:

```
intento 1: ordernap
intento 2 (tras 30 s): ordernap   ← MISMO. El parche del `return null` no basta
```

**Por qué:** el ATAJO de `resolverStreamEnSegundoPlano` (línea ~3388) llama a
`mostrarEmbedEnLaVentana(String)`, y esa sobrecarga **no añadía nada a
`servidoresProbados` ni fijaba `embedEnCurso`**. Al volver por el temporizador,
`embedEnCurso` seguía `null` y `ordernap` no constaba como probado → salía otra vez.
Arreglar el `return null` cambia *rendirse* por *saltar*; nunca tocó **quién apunta lo
probado**. Tu parche tapa exactamente ese agujero.

Ahora hay un test que lo fija: `PlanBTest.elEmbedQueYaSePintoNoSeVuelveAElegir`.

---

## 2. Lo que se porta del mío (no estaba en tu ZIP)

### 🔴 `idDeCanal()` con fallback a `tvg-id` — el bug del segundo toque

`FuenteCanales.fijarUrlResuelta()` reemplaza el `dramalive://…?id_live=X` de la rejilla
por la URL https real, y ese texto **ya no lleva `id_live=`**. La fila sobrevive porque
`idFila()` mira también el `tvg-id` del EXTINF, pero `idDeCanal()` no lo hacía — y es la
que usa la cura.

> Un canal que ya se reprodujo alguna vez **no se podía curar nunca más**. Los canales
> que funcionan eran justo los que se quedaban sin red de seguridad.

Mismo bug que la v3.2.21 arregló en `idFila()` y quedó sin replicar aquí.

### 🔴 `lanzarCuraDelCanal()` — la máquina de cura estaba inerte

`canalCurado` tenía 4 asignaciones en todo el fichero y **las 4 eran a `null`**. Por eso:
`reintentaTrasCura()` retornaba en su primera línea, `notificarEntrega()` nunca
despertaba a nadie y `notificarCuraFracasada()` comparaba contra `null`.
**Y `DramaFondo.abrirCaptadorHeadless()` no tenía un solo llamador.**

Ahora se cablea con cooldown de 100 s por canal y tope de ciclos.
**No se repone** el captador *visible* a pantalla completa (retirado en
v3.2.46/v3.2.47 tras tu queja): solo el headless.

### 🔴 La rama del clip de relleno ya no es un callejón sin salida

Era la única de las cuatro rutas de fallo que **no** probaba nada: mostraba el toast y
hacía `return`. Y es la más frecuente, porque el clip de relleno es una respuesta
legítima del edge al POST desde Java. Ahora prueba el siguiente servidor o el embed, y si
no queda ninguno lo dice y lanza la cura.

### 🟡 Un POST menos por canal

Caché de 20 s del bloque `live`: `resolverVivo` y `glasbiCrudo` pedían lo mismo dos veces
(hasta 3 POST por play; el histórico documenta que el edge castiga eso con 32 bytes en blanco).

### 🟡 Un solo parser del backup

`DramaSesion.trozosDeBackup()` con `\\s*-;-\\s*`, usado por `directaDeBackup`,
`opcionesDeLive` y `DramaResolucion.opciones`. Antes había dos criterios distintos.

### 🟡 Un solo selector de servidor

Había tres implementaciones de «¿cuál toca ahora?». Ahora
`PlayerActivity.siguienteOpcionUtil(url, backup, probados)` **delega** en
`DramaResolucion.siguiente()`, que es pura, tiene guards de `actual`/`enCurso` y 11
pruebas. Los 3 tests tuyos siguen pasando por la misma ruta.

### Textos

`player_error_relleno` → «Ese servidor no emitió ahora; probando otro…» · nuevo
`player_sin_mas_servidores` → «No queda ningún servidor de este canal. Se cura solo en segundo plano.»

---

## 3. Lo que tu logcat desmiente de mi diagnóstico anterior

Honestidad ante todo, porque cambia prioridades:

1. **«2 útiles de 10» era una lectura pobre.** beIN news trae también
   `EMBEBIDA → youtube.com/@beINSPORTS/live`; beIN 1 trae `últiles 2` pero tres embeds en
   total (ordernap, quotarevival, youtube). `utiles()` filtra YouTube a propósito.
2. **Mi sospecha de `onNewIntent` no se materializó.** El log abre `beinsport1` a las
   19:02:01 tras agotar `beinsportnews` a las 19:01:50: el reset **sí** ocurrió. Por eso
   **no** porto mi `limpiarEstadoDrama()` en `onNewIntent`: duplicaría tu reset.
3. **El bug del separador (#9) sigue sin confirmarse en campo.** Los dos backups del log
   venían con `" -;- "` exacto. El arreglo queda como preventivo, no medido.
4. **El embed cambia justo cuando empieza a emitir.** `hay imagen del embed (vídeo
   confirmado)` a las 19:03:04 — pero 15 s después de que la app ya hubiera saltado, y a
   esa hora lo que se veía era **primetv**. Tu re-chequeo evita el parpadeo; no garantiza
   que ordernap llegue a arrancar.

---

## 4. Verificación (y su límite)

| Comprobación | Resultado |
|---|---|
| `java-parser` 3.0.1 en los 5 ficheros tocados | ✅ `PARSE_OK` |
| `node docs/repro-planb.mjs` | ✅ 8/8 `REPRO_OK` |
| Simulación del selector unificado (tus 3 tests) | ✅ pasan por la misma ruta |
| Simulación del test del bucle (ordernap → primetv → null) | ✅ correcto |
| `strings.xml` parsea / `node --check drama_auto.js` | ✅ |
| **Compilación real** | ❌ **NO.** Este entorno no tiene JDK ni Android SDK: Maven Central, Google Maven y el tarro de Adoptium están bloqueados. |
| **Ejecución de la suite** | ❌ **NO.** Necesita `android.jar`, Media3 y Gson de Google Maven. |

> Antes de instalarlo: `./gradlew :app:testDebugUnitTest` (deben pasar los 14 tests: 11 de
> `PlanBTest` + los 3 tuyos de `DrmYActividadTest`) y luego `:app:assembleDebug`.

---

## 5. Qué mirar en el próximo logcat

```bash
adb logcat -s BLABONGO_DEBUG | grep -E "plan B|cura LANZADA|servidores del canal|relleno|directa con su"
```

Cambios que deben verse:

| Línea | Confirma |
|---|---|
| `Drama v7.38.5 (code 84)` | corre el build nuevo |
| **`plan B: probando ->` con una url DISTINTA cada 30 s** | tu arreglo del bucle — la prueba clave |
| `🎬 directa con su UA/Referer -> …` | la nueva ruta de directas |
| `cura LANZADA para <id> — cosecha headless` | la cura vuelve a existir |
| `glasbiCrudo: … cache (0 POST)` | el POST ahorrado |
| `relleno -> plan B: …` | el callejón sin salida cerrado |
| `No queda ningún servidor de este canal` | solo cuando de verdad no queda |

**El criterio de éxito es uno:** que `plan B: probando ->` muestre urls distintas y no
repita la misma. Si se repite, el bug sigue vivo y el log lo va a decir en la primera
línea.

---

## 6. Riesgo

1. **`lanzarCuraDelCanal()` ahora sí abre `DramaAutoActivity` headless.** Está en su
   propio task, 2×2 px y `excludeFromRecents`, pero **no se ha ejecutado nunca en este
   árbol**. Si en el móvil se ve una ventana o roba el foco, hay que cerrarla.
2. **El `idDeCanal` con `tvg-id`** afecta a `UsoCanales` (estadísticas) además de la cura.
   Si un canal sin `id_live` pasa a contar con id, las métricas cambian de forma.
3. **La rama del relleno ahora pinta el embed** donde antes solo avisaba. Si el embed del
   canal es un `youtube.com/@…/live`, se abrirá el reproductor de YouTube dentro del marco.
   Es lo que ya hacía `avisarSinStream()`, pero ahora ocurre antes.
