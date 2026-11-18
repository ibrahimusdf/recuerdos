# v7.39.1 — «clico un canal y se queda en otro»: el guardián de la generación

**Fecha:** 2026-11-18 · `versionCode 92` / `versionName "7.39.1"`

---

## 1. La causa: el reproductor se reutiliza y nada del canal anterior se apagaba

En el manifiesto, `PlayerActivity` es **`singleTask`**:

```xml
<activity android:name=".PlayerActivity" android:launchMode="singleTask" … />
```

Eso significa que al tocar un segundo canal **Android no crea otra pantalla**: reutiliza
la que ya hay y avisa por `onNewIntent`. Hasta ahora ese aviso solo cambiaba cuatro
campos — y **todo lo demás del canal anterior seguía vivo**:

| lo que seguía corriendo del canal A | qué le hacía al canal B |
|---|---|
| las hebras de la carrera de opciones | su ganadora se le aplicaba encima → **se veía A** |
| el POST de glasbi/glbr (2-13 s) | su `rav==null` abría el plan B **de A** en pantalla |
| el WebView oculto cazando el manifiesto | metía el embed **de A** en la ventana de B |
| el vigilante del embed (15 s → 30 s) | a los 15 s cambiaba el servidor **de B** por el de A |
| `servidoresProbados` | las opciones de B se saltaban por «ya probadas» |
| `streamUrl` / `streamBackup` / `streamOpcionesJson` | el plan B ofrecía los servidores **de A** |
| `dramaLiveEnJuego`, `canalCurado`, `dramaSwapPares` | el glbr y la cura del canal viejo |

Y encima el zapeo (`zapearCanal`) cambia de canal **dentro de la misma pantalla, sin pasar
por `onNewIntent`**: mismo agujero, sin aviso.

## 2. El arreglo: un número de generación

`GeneracionDelCanal` (nueva, pura, 30 líneas): al abrir un canal se sube el número; todo
lo asíncrono guarda el que tenía **al nacer** y, al volver, pregunta. Si ya no es el
actual, se descarta en el log y no toca nada.

```java
private void invalidarCanalAnterior(String motivo) {
    int gen = generacionDelCanal.abrirCanal();   // todo lo anterior queda caducado
    urlsEnCarrera.clear();  servidoresProbados.clear();  opcionesFallidas.clear();
    imagenConfirmada = false;  embeddedEnVentana = false;  …
    streamUrl = null;  streamBackup = null;  streamOpcionesJson = null;
    mainHandler.removeCallbacksAndMessages(null);   // muere lo programado
}
```

Se llama en los **tres** sitios donde se cambia de canal: `onCreate`, `onNewIntent` y
**`zapearCanal`**. Y `onDestroy` sube la generación, para que no entregue nada al salir.

Cada vuelta asíncrona pregunta antes de actuar:

```java
final int miGeneracion = generacionDelCanal.actual();   // al nacer
…
if (!sigueVigente(miGeneracion)) { log("⏹ resolución del canal anterior descartada"); return; }
```

Cubiertos: la carrera (ganadora, descartes, «ninguna»), el arranque glasbi/glbr, la
consulta a la API, y los vigilantes del embed (15 s y 30 s).

### Además

- **Se para el stream anterior** antes de cargar el nuevo (`player.stop()` + `clearMediaItems()`).
  Antes se oía un canal mientras se veía otro durante el cambio.
- La memoria del canal anterior **no se hereda**: `streamUrl`, `streamBackup`,
  `streamOpcionesJson`, `dramaLiveEnJuego`, `canalCurado` y `dramaSwapPares` se vacían.

## 3. Medido (simulación del orden real de los hechos)

`node docs/repro-v3913.mjs` · **14/14** · `GeneracionDelCanalTest` · **7 `@Test`**

| | sin guardián | con guardián (7.39.1) |
|---|---|---|
| `t=900 ms` glasbi de A | fuente → A (**pisa B**) | descartada |
| `t=1200 ms` carrera de A | fuente → A (**pisa B**) | descartada |
| `t=1800 ms` resolución de B | fuente → B | fuente → **B** ✅ |
| `t=15,3 s` vigilante del embed de A | **cambia el servidor de B** | descartada |
| canal en pantalla al final | **A** | **B** ✅ |

## 4. Verificado / sin verificar

**Verificado:** `V3913_OK 14/14` (nuevo) · `V3912_OK 17/17` · `V3911_OK 11/11` ·
`V390_OK 19/19` · `V389_OK 16/16` · `PLAN_OK 18/18` · `PROXY_OK 19/19` · `SIM_OK 14/14` ·
16 ficheros Java con sintaxis comprobada.

**Sin verificar:** no compilado (sin JDK/SDK) y no probado en tu móvil.

```bash
./gradlew :app:testDebugUnitTest assembleDebug
```

En logcat, cada cambio de canal debe imprimir:

```
🧹 canal limpio (generación 2, onNewIntent)
⏹ resolución del canal anterior descartada (ya hay otro)
🏁 ganadora del canal ANTERIOR descartada: https://…
```

Si ves el `🧹` y luego descartes del canal viejo, está funcionando. Si vuelves a ver
«se queda en otro», pásame esas tres líneas del log: dicen exactamente quién llegó tarde.
