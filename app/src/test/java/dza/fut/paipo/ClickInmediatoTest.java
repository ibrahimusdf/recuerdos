package dza.fut.paipo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * v7.30 — el click en servidor y en canal se tiene que notar al toque.
 *
 * El usuario: «el click en canal no se cambia de inmediato y tampoco el click en
 * servidor». Eran dos cosas:
 *
 *   1. solo se pre-resolvía la PRIMERA opcion, asi que cambiar de servidor era
 *      siempre «a resolver de cero» salvo el que abría el reproductor;
 *   2. no habia feedback inmediato: `mostrarCargando()` no se llamaba al
 *      pulsar, solo cuando ExoPlayer entraba en buffering.
 */
public class ClickInmediatoTest {

    private static String fuente(String archivo) {
        String r = "dza/fut/paipo/" + archivo;
        try (InputStream in = ClickInmediatoTest.class.getClassLoader()
                .getResourceAsStream(r)) {
            if (in != null) return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (java.io.IOException ignore) { }
        java.io.File f = new java.io.File("src/main/java/dza/fut/paipo/" + archivo);
        if (!f.isFile()) f = new java.io.File("app/src/main/java/dza/fut/paipo/" + archivo);
        try {
            return new String(java.nio.file.Files.readAllBytes(f.toPath()),
                    StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            throw new AssertionError("no encuentro la fuente de " + archivo + ": " + e);
        }
    }

    private static String cuerpo(String s, String desde, String hasta) {
        int i = s.indexOf(desde);
        assertTrue("no encuentro «" + desde + "»", i >= 0);
        int f = s.indexOf(hasta, i + desde.length());
        assertTrue("no encuentro el final «" + hasta + "»", f > i);
        return s.substring(i, f);
    }

    // ───────────── 1. se calientan TODOS los servidores ─────────────

    @Test
    public void sePreResuelvenTodasLasOpcionesNoSoloLaPrimera() {
        String a = fuente("CanalAdapter.java");
        assertTrue("el adaptador debe llamar a preResolverVarios",
                a.contains("PlayerActivity.preResolverVarios("));
        assertFalse("y no debe quedarse en preResolver de una sola URL",
                a.contains("PlayerActivity.preResolver(context, c.urlInicial)"));

        // El bucle que junta las URLs tiene que recorrer todas, no solo la de
        // arranque. Va justo antes de la llamada.
        String trozo = cuerpo(a, "java.util.List<String> todas",
                "PlayerActivity.preResolverVarios(context, todas);");
        assertTrue("debe recorrer todas las urls", trozo.contains("for (String u : c.urls)"));
        assertFalse("y no puede quedarse en la de arranque",
                trozo.contains("c.urlInicial"));
    }

    @Test
    public void preResolverVariosExisteYDelega() {
        String p = fuente("PlayerActivity.java");
        assertTrue("tiene que existir preResolverVarios",
                p.contains("public static void preResolverVarios("));
        assertTrue("y debe delegar en preResolver (que ya filtra lo cacheado)",
                p.contains("for (String u : urls) preResolver(cx, u);"));
        // Las que ya estan en cache se saltan: si no, se resolverian otra vez.
        assertTrue("preResolver debe saltarse lo que ya esta en cache",
                p.contains("if (ResolucionCache.tiene(url)) return;"));
    }

    /** Con un solo hilo, tres servidores se calentaban en cola de uno. */
    @Test
    public void elExecutorDeCalentamientoTieneHilosDeSobra() {
        String p = fuente("PlayerActivity.java");
        int i = p.indexOf("private static final java.util.concurrent.ExecutorService PRE");
        assertTrue("tiene que estar el executor PRE", i > 0);
        String decl = p.substring(i, p.indexOf(';', i));
        assertTrue("debe ser un pool, no un hilo suelto",
                decl.contains("newFixedThreadPool("));
        assertFalse("newSingleThreadExecutor volveria a dejar los servidores en cola",
                decl.contains("newSingleThreadExecutor("));
        // Y con al menos 3: las opciones de un canal caben a la vez.
        assertTrue("con al menos 3 hilos", decl.contains("newFixedThreadPool(3)"));
    }

    // ───────────── 2. feedback inmediato al pulsar ─────────────

    /** El toque en un chip de servidor tiene que verse en el acto. */
    @Test
    public void elClickEnServidorMuestraElCargando() {
        String p = fuente("PlayerActivity.java");
        // OJO con el marcador final: el primer "showControls();" esta en el
        // guard de re-pulsacion, antes de mostrarCargando(). Se corta en lo que
        // viene DESPUES del metodo.
        String c = cuerpo(p, "private void cargarOpcionServidor(int idx) {",
                "private static final java.util.concurrent.ExecutorService PRE");
        assertTrue("debe llamar a mostrarCargando()", c.contains("mostrarCargando()"));
        // ...y NO solo desde el listener de ExoPlayer.
        assertFalse("el cargando no puede depender de STATE_BUFFERING",
                c.contains("onPlaybackStateChanged"));
    }

    /** El cargando va DESPUES de validar la URL: si no, un servidor sin URL
     *  dejaba el spinner 8 s con un Toast. */
    @Test
    public void elCargandoVaDespuesDeValidarLaUrl() {
        String p = fuente("PlayerActivity.java");
        String c = cuerpo(p, "private void cargarOpcionServidor(int idx) {",
                "opcionActual = idx;");
        int valida = c.indexOf("player_sin_url");
        int carga = c.indexOf("mostrarCargando()");
        assertTrue("debe validar la URL", valida >= 0);
        assertTrue("debe mostrar el cargando", carga >= 0);
        assertTrue("el cargando tiene que ir DESPUES de la validación",
                carga > valida);
    }

    /** Y lo mismo al zapear de canal. */
    @Test
    public void elClickEnCanalMuestraElCargando() {
        String p = fuente("PlayerActivity.java");
        String z = cuerpo(p, "private void zapearCanal(int idx) {",
                "opcionesServidor.clear();");
        assertTrue("zapear tambien debe mostrar el cargando", z.contains("mostrarCargando()"));
    }

    // ───────────── 3. el toque repetido no rompe nada ─────────────

    /**
     * `mostrarCargando()` quita el `ocultarCargandoRun` pendiente y pone uno
     * nuevo, así que llamarlo dos veces seguidas no deja el spinner colgado.
     * Este test lo fija por si alguien lo «simplifica».
     */
    @Test
    public void mostrarCargandoEsIdempotente() {
        String p = fuente("PlayerActivity.java");
        String m = cuerpo(p, "private void mostrarCargando() {",
                "private void ocultarCargando() {");
        assertTrue("debe quitar el ocultado pendiente antes de poner otro",
                m.contains("mainHandler.removeCallbacks(ocultarCargandoRun);"));
        assertTrue("y volver a programarlo", m.contains("postDelayed(ocultarCargandoRun,"));
    }

    /** La caché sigue siendo la que hace instantáneo el segundo click. */
    @Test
    public void laCacheSigueSiendoElAtajo() {
        String p = fuente("PlayerActivity.java");
        assertTrue("el reproductor debe consultar la caché antes de resolver",
                p.contains("ResolucionCache.get(claveCache)"));
        assertTrue("y guardar lo que resuelva",
                p.contains("ResolucionCache.put(claveCache,"));
    }

    /** Y la clave de caché es la URL de la opción, así que cada servidor
     *  tiene la suya. */
    @Test
    public void cadaServidorTieneSuClaveDeCache() {
        String p = fuente("PlayerActivity.java");
        // OJO con el marcador final: el primer "showControls();" esta en el
        // guard de re-pulsacion, antes de mostrarCargando(). Se corta en lo que
        // viene DESPUES del metodo.
        String c = cuerpo(p, "private void cargarOpcionServidor(int idx) {",
                "private static final java.util.concurrent.ExecutorService PRE");
        assertTrue("la clave es la URL de la opcion elegida",
                c.contains("urlIframeInicial = url;"));
        assertFalse("no debe reutilizar la url del servidor anterior",
                c.contains("urlIframeInicial = urlIframeInicial"));
    }
}
