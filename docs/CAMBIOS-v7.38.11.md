# v7.38.11 — por qué la app de referencia abre todo y la mía no

**Fecha:** 2026-11-18 · `versionCode 90` / `versionName "7.38.11"`

---

## 1. El análisis que pediste

Leí `DramaLive-Android-v1.0`. Son **21 ficheros** y **692 líneas** de Java, de las que
solo dos importan: `Api.java` (219 líneas) y `MainActivity.java` (473).

Toda su lógica de «qué se reproduce» está en **un método de tres líneas** (`Api.java`):

```java
public String urlReproducible(String codigo, String idCanal) {
    if (!hls) return url;                                    // radio: stream directo
    return BASE + "/p/" + esc(codigo) + "/" + esc(idCanal) + "/" + n + ".m3u8";
}
```

Y su bucle de arranque (`MainActivity.pintarOpciones`):

```java
int primera = -1;
for (int i = 0; i < opciones.size(); i++) {
    if (opciones.get(i).emite) { primera = i; break; }
}
if (primera >= 0) reproducir(primera);
```

Más un vigilante: si en **9 s** no hay imagen, `falloFuente()` salta a la siguiente que
tenga `emite`, con un tope de 3 saltos.

### Su algoritmo completo

1. `GET /opciones/{codigo}/{canal}` → la lista entera.
2. Pinta **todas** con punto verde/rojo.
3. Arranca con la primera con `emite=true`, apuntando ExoPlayer **al proxy** `/p/…`.
4. Si en 9 s no hay imagen, salta a la siguiente con `emite=true`.
5. Nada más. Sin WebView, sin sesión Drama, sin sniffers, sin memoria.

### Lo que yo tenía de más (y estorbaba)

| mi app | la referencia | efecto |
|---|---|---|
| Sesión Drama (`glt`→`glbt`→`glasbi`→`glbr`), 2-4 POST | ninguna | 2-13 s antes de ver nada |
| WebView + cazador de manifiestos | no existe | páginas que se van a su reproductor |
| Filtraba por `emite` **y** por `http` | no filtra al pedir | **se comía opciones buenas** |
| Pedía proxada **y** página (2 turnos) | 1 turno por opción | el doble de tiempo al fallar |
| Memoria de servidores, cura, plan B | ninguna | complejidad que puede fallar |

---

## 2. El hallazgo que explica «faltan canales»

La referencia no mira `emite` **al pedir el proxy**. Yo sí. Y ahí está el fallo.

Medido hoy con dos canales reales:

| canal | opción | lo que dice la API | lo que da el proxy |
|---|---|---|---|
| beIN 5 | n=0 | `emite:false`, `http:null`, timeout **13,3 s** | `#EXTM3U` **válido** |
| beIN 2 | n=0 | `emite:false`, `http:null`, timeout **13,1 s** | `#EXTM3U` **válido** |

La sonda de la API es un GET con **10 s** de timeout. Un host lento aparece como muerto…
y luego el proxy —que sí espera— lo resuelve sin problema.

**La API decía «no sé» y yo lo leía como «no».** Ese era el fallo de las 7.38.8 a 7.38.10.

---

## 3. Lo que cambia

### `podriaEmitir()`: solo un NO ROTUNDO descarta

| respuesta de la sonda | antes | ahora |
|---|---|---|
| `emite:true` | ✅ | ✅ |
| `http:403` / `404` / `410` | ❌ | ❌ (es un no de verdad) |
| `http:200` | ✅ | ✅ |
| **`http:null` (timeout)** | ❌ | **✅** ← el arreglo |
| DASH cifrado en Widevine | ❌ | ❌ (imposible sin licencia) |

### `aOpciones()`: un turno por opción, siempre el proxy

Antes ponía **dos** entradas por opción (proxada + página). Ahora **una**: la proxada.
Eso es lo que hace la referencia, y es el doble de rápido al fallar.

La página original queda solo como respaldo cuando no hay proxy disponible (sin API
configurada), que es el modo local de siempre.

### Orden de ataque

1. Las que la API confirma (`emite:true`), en el orden de la API.
2. Las demás (timeout, 2xx sin leer), **por número de opción**.
3. Y de cada una, su `/p/….m3u8`.

### Arranque sin esperar a Drama

`pedirOpcionesEnSegundoPlano` ahora, cuando la API contesta y **todavía no hay imagen**,
lanza directamente la primera proxada a ExoPlayer. No espera a la sesión de Drama, que es
justo lo que la referencia no tiene. Si ya hay vídeo, no toca nada.

---

## 4. Lo que conservo a propósito (y la referencia no tiene)

No copio la referencia al pie de la letra porque la mía resuelve cosas que ella no:

- **Radio**: la referencia mira `hls` y usa la url directa. Yo ya lo cubro con
  `esPlaylist`.
- **Memoria de servidores por canal**: cuando la API duerme o cambia, lo que ya funcionó
  en este móvil sigue primero.
- **Modo sin API**: la referencia es inútil sin Render. La mía cae al plan local.

Lo que **sí** he eliminado del camino crítico: la danza de sesión ya no bloquea.

---

## 5. Pruebas

| prueba | resultado |
|---|---|
| `docs/repro-v3911.mjs` (**nuevo**) | **11/11** |
| `docs/repro-v390.mjs` | **19/19** |
| `docs/repro-canales-que-no-abren.mjs` | **16/16** |
| `docs/repro-plan-de-ataque.mjs` | **18/18** |
| `docs/repro-api-proxy.mjs` | **19/19** |
| `docs/repro-servidores.mjs` | **14/14** |
| `docs/repro-planb.mjs` | OK |
| JUnit | **7 nuevos** en `EstrategiaDeLaReferenciaTest` + 3 actualizados |

`EstrategiaDeLaReferenciaTest` usa el JSON **literal** de beIN 5, con la opción del
timeout incluida. Es el caso que estaba mal.

> Nota de honestidad: he tenido que **corregir tres pruebas** mías de las 7.38.8–7.38.10
> porque afirmaban la estrategia vieja (proxada + página, y descartar timeouts). Quedan
> actualizadas con el porqué escrito.

---

## 6. Lo que sigue sin verificarse

**No compilado** (sin JDK ni SDK) y **no probado en tu móvil**. Lo importante esta vez:

```bash
./gradlew :app:testDebugUnitTest assembleDebug
```

Abre un canal que antes fallaba y mira el logcat:

```
api: 6 opciones, 2 emitiendo · 4 para el plan B · NNN ms
   · API api·proxy [1] DIRECTA → …/p/232425/live_tv_beinsport5/1.m3u8
   · API api·proxy? [0] DIRECTA → …/p/232425/live_tv_beinsport5/0.m3u8   ← la del timeout
api: sin esperar a Drama -> api·proxy? [0] DIRECTA → …
```

Si ves `proxy?`, es una que la API daba por muerta y estamos probando igual.

**Medido:** que el proxy sirve manifiestos para opciones que la API marca `emite:false`
con timeout (beIN 2 y beIN 5, dos casos). **No medido:** cuántos canales del catálogo
completo caen en ese caso; eso lo dirá tu prueba.
