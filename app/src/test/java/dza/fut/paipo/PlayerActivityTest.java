package dza.fut.paipo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** v3.2.29: la logica pura del relai HLS (swap de segmentos + absolutizado) se prueba
 *  sin contexto Android — es la que convierte el advanced de glasbi en play real. */
public class PlayerActivityTest {

    @Test
    public void nuevasOpcionesCalidadLeenElJsonDelGlasbi() {
        java.util.List<String[]> ops = PlayerActivity.nuevasOpcionesCalidad(
                "[{\"n\":\"HD\",\"u\":\"http://.LS.A\"},{\"n\":\"SD\",\"u\":\"http://.LS.B\"}]");
        assertEquals(2, ops.size());
        assertEquals("HD", ops.get(0)[0]);
        assertEquals("http://.LS.B", ops.get(1)[1]);
        assertTrue(PlayerActivity.nuevasOpcionesCalidad(null).isEmpty());
        assertTrue(PlayerActivity.nuevasOpcionesCalidad("{no-es-array}").isEmpty());
        assertTrue(PlayerActivity.nuevasOpcionesCalidad("[{\"n\":\"HD\"}]").isEmpty());
    }

    @Test
    public void esPaginaPlayerDetectaSoloLaPaginaDelSitio() {
        assertTrue(PlayerActivity.esPaginaPlayerHls("https://sup6.superfastnet.cyou/pages/fnwzpoqozpru/index.html"));
        // v3.2.30: el logcat real mostro /mirsp/ y /malasp2/ con la MISMA papeleta — el gate
        // ya no mira la ruta, solo que sea la pagina index.html del player (y el relay exige
        // que la respuesta empiece con #EXTM3U antes de tocar nada).
        assertTrue(PlayerActivity.esPaginaPlayerHls("https://dat3.datahostingapiplatform.monster/mirsp/fibkkpqmtvsp/index.html"));
        assertTrue(PlayerActivity.esPaginaPlayerHls("https://api4.apidatahostingsolutions.shop/malasp2/idtagclozbrc/index.html"));
        assertFalse(PlayerActivity.esPaginaPlayerHls("https://cdn.example/live/master.m3u8"));
        assertFalse(PlayerActivity.esPaginaPlayerHls("http://file.twoapistack.work/stream/update.mp4"));
        assertFalse(PlayerActivity.esPaginaPlayerHls(null));
    }

    @Test
    public void reescribirPlaylistAplicaSwapYAbsolutiza() {
        String pl = "#EXTM3U\n#EXT-X-VERSION:3\n#EXTINF:4.760000,\nk9x_1791793804-1791802092.js\n"
                + "#EXTINF:3.440000,\nk9x_1791793804-1791802093.js\nhttps://otro.cdn/x.ts\n";
        String out = PlayerActivity.reescribirPlaylist(pl, "https://sup6.superfastnet.cyou/pages/fnwzpoqozpru/index.html");
        assertTrue(out.contains("https://sup6.superfastnet.cyou/pages/fnwzpoqozpru/1791793804-1791802092.js"));
        assertTrue(out.contains("https://sup6.superfastnet.cyou/pages/fnwzpoqozpru/1791793804-1791802093.js"));
        assertTrue(out.contains("https://otro.cdn/x.ts"));
        assertFalse(out.contains("k9x_"));
        assertTrue(out.startsWith("#EXTM3U"));
    }

    @Test
    public void reescribirPlaylistInocuaConBasura() {
        assertNull(PlayerActivity.reescribirPlaylist(null, "https://x/index.html"));
        assertNull(PlayerActivity.reescribirPlaylist("", "https://x/index.html"));
        String soloTags = PlayerActivity.reescribirPlaylist("#EXTM3U\n#EXT-X-ENDLIST\n", "https://x/y/index.html");
        assertEquals("#EXTM3U\n#EXT-X-ENDLIST", soloTags.trim());
    }

    /**
     * v7.22 — el relay sale del camino normal porque ExoPlayer ya aplica el mismo
     * swap (DramaSwapDataSource) y ya fuerza el MIME m3u8 (mediaItemPara): medido,
     * sólo aportaba la extensión .m3u8, y cobraba 1.068 ms por ello.
     *
     * Lo que sí hace falta es que un fallo en el camino directo no deje al usuario
     * sin canal: el reintento va por el relay, y el indicador se resetea al abrir
     * otro canal. Estas dos pruebas leen el código para que eso no se pierda.
     */
    @Test
    public void elCaminoDirectoDejaElIndicadorDeVueltaPorElRelay() throws Exception {
        String src = leerFuente();
        assertTrue("si el camino sin relay falla, el reintento tiene que poder ir por el relay",
                src.contains("relayForzado = true;"));
        assertTrue("y tiene que avisar en el log",
                src.contains("el camino sin relay falló"));
        assertTrue("paginaSinRelay es la marca de que se está probando sin relay",
                src.contains("paginaSinRelay"));
    }

    @Test
    public void elIndicadorDeVueltaPorElRelaySeReseteaAlAbrirOtroCanal() throws Exception {
        String src = leerFuente();
        assertTrue("el reintento por relay se decide en onPlayerError",
                src.contains("relayForzado = true;"));
        int j = src.indexOf("RegistroCarga.abrir(\"arranque de canal\");");
        assertTrue("el bloque del canal se abre en resolverDramaLive", j > 0);
        // el reset va justo antes de abrir el bloque, así que se mira hacia atrás
        String antes = src.substring(Math.max(0, j - 900), j);
        assertTrue("abrir un canal tiene que resetear relayForzado, si no un fallo"
                + " puntual se queda para siempre",
                antes.contains("relayForzado = false;"));
        assertTrue("y también paginaSinRelay", antes.contains("paginaSinRelay = null;"));
    }

    private static String leerFuente() throws Exception {
        java.io.File f = new java.io.File("src/main/java/dza/fut/paipo/PlayerActivity.java");
        assertTrue("no encuentro PlayerActivity.java", f.isFile());
        byte[] b = new byte[(int) f.length()];
        java.io.InputStream in = new java.io.FileInputStream(f);
        try {
            int off = 0;
            while (off < b.length) {
                int n = in.read(b, off, b.length - off);
                if (n < 0) break;
                off += n;
            }
        } finally { in.close(); }
        return new String(b, "UTF-8");
    }

    /**
     * v7.23 — el volcado del usuario (v7.22) traía cuatro bloques de canal ABIERTOS y
     * VACIOS: la rejilla iba bien (15.014 canales) pero los cuatro toques morían antes
     * del player y el registro no decía dónde. Estos tests fijan que las salidas que
     * REALMENTE se toman cuando un canal no arranca apuntan algo.
     */
    @Test
    public void losCuatroRegistrosDeLoQueMataUnCanal() throws Exception {
        String src = leerFuente();
        final String q = String.valueOf('"');   // para no pelear con las comillas
        String[] huellas = {
            "resolver canal (2 POST)",                       // el camino feliz
            "paso(" + q + "FALLO v" + q + " + ",            // v7.37.15: la version en cada
                                                       // registro de fallo (literal en el fuente)
            "el glbr no dio stream",                          // el glbr no contesta
            "FALLO: clip de relleno update.mp4",             // el suenio
            "sin relay: la url no es pagina del player",     // no era del player
        };
        for (String h : huellas) {
            assertTrue("el diagnostico tiene que apuntar: " + h, src.contains(h));
        }
        // y cada salida de fallo tiene que CERRAR el bloque, o se apilan vacios
        // (hay un tercer fin() en STATE_READY, que es el del camino feliz: por eso
        //  se mira la proximidad y no el numero total)
        int idx = 0;
        while ((idx = src.indexOf("RegistroCarga.paso(" + q + "FALLO:", idx)) >= 0) {
            int cierre = src.indexOf("RegistroCarga.fin();", idx);
            assertTrue("un FALLO sin fin() deja el bloque abierto y vacio, que es"
                    + " justo lo que le paso al usuario",
                    cierre > idx && cierre - idx < 400);
            idx = cierre;
        }
    }

    @Test
    public void elPasoDelResolverSeApuntaEnLosDosCaminos() throws Exception {
        String src = leerFuente();
        final String q = String.valueOf('"');
        int glasbi = src.indexOf("play nativo (glasbi) en ");
        int glbr = src.indexOf("ra = DramaSesion.resolverDramaLive(canal, ses);");
        int paso = src.indexOf("RegistroCarga.paso(" + q + "resolver canal (2 POST)"
                + q + ")", glasbi);
        assertTrue("esta el camino del glasbi", glasbi > 0);
        assertTrue("esta el camino del glbr", glbr > 0);
        assertTrue("y el paso va DESPUES de los dos: si no, un canal resuelto por el"
                + " glbr deja el bloque vacio", paso > glbr && paso > glasbi);
    }

    @Test
    public void clipDeRellenoSoloMuestraAvisoSinVaciarNiCambiarDePantalla() throws Exception {
        String src = leerFuente();
        int inicio = src.indexOf("if (rr[0].contains(\"update.mp4\")) {");
        int fin = src.indexOf("return;", inicio);
        assertTrue("se encuentra el manejo del clip de relleno", inicio >= 0 && fin > inicio);
        String manejo = src.substring(inicio, fin);

        assertTrue(manejo.contains("player.pause();"));
        assertTrue(manejo.contains("ocultarCargando();"));
        assertTrue(manejo.contains("mostrarErrorPlayer(R.string.player_error_relleno);"));
        assertFalse("no debe vaciar el video anterior", manejo.contains("player.stop()"));
        assertFalse("no debe limpiar los elementos del reproductor", manejo.contains("clearMediaItems()"));
        assertFalse("no debe abrir otra Activity", manejo.contains("startActivity("));
    }

    @Test
    public void avisoDeRellenoEsTemporalYNoSeSuperponeAlVideo() throws Exception {
        String portrait = leerLayout("activity_player_portrait.xml");
        String landscape = leerLayout("activity_player.xml");
        assertFalse("retrato no tiene texto de error fijo", portrait.contains("@+id/mensajeErrorPlayer"));
        assertFalse("paisaje no tiene texto de error fijo", landscape.contains("@+id/mensajeErrorPlayer"));

        String src = leerFuente();
        assertTrue("el aviso usa notificación temporal",
                src.contains("Toast.makeText(getApplicationContext(), mensajeResId, Toast.LENGTH_SHORT).show();"));
    }

    private static String leerLayout(String nombre) throws Exception {
        java.io.File archivo = new java.io.File("app/src/main/res/layout/" + nombre);
        if (!archivo.isFile()) archivo = new java.io.File("src/main/res/layout/" + nombre);
        return new String(java.nio.file.Files.readAllBytes(archivo.toPath()),
                java.nio.charset.StandardCharsets.UTF_8);
    }

    private static String leerStrings() throws Exception {
        java.io.File archivo = new java.io.File("app/src/main/res/values/strings.xml");
        if (!archivo.isFile()) archivo = new java.io.File("src/main/res/values/strings.xml");
        return new String(java.nio.file.Files.readAllBytes(archivo.toPath()),
                java.nio.charset.StandardCharsets.UTF_8);
    }

    @Test
    public void cargaOcultaSoloControlesCentralesYConservaLaTiraDeCanales() throws Exception {
        String src = leerFuente();
        assertTrue(src.contains("cargaEnCurso = true;"));
        assertTrue(src.contains("ocultarControlesPorCarga();"));
        assertTrue(src.contains("mostrarServidores = fueraDePip && opcionesServidor.size() > 1\n"
                + "                && (!esHorizontal || controlesVisibles);"));
        assertTrue("servidores y canales conservan visibilidad durante carga",
                src.contains("filaServidores != null && (!cargaEnCurso || !fueraDePip)")
                        && src.contains("tiraCanalesPlayer != null && (!cargaEnCurso || !fueraDePip)"));
        assertFalse("la fila de servidores no mantiene visible todo el overlay",
                src.contains("if (esHorizontal && mostrarServidores && controlsOverlay != null"));
        assertTrue("la visibilidad de canales no cambia durante la carga",
                src.contains("tiraCanalesPlayer != null && (!cargaEnCurso || !fueraDePip)"));
        assertTrue("un toque durante la carga no vuelve a mostrar los controles",
                src.contains("if (cargaEnCurso) {\n                ocultarControlesPorCarga();\n                return;"));
        assertTrue("el timeout de carga restablece los controles",
                src.contains("cargaEnCurso = false;\n        showControls();"));

        int ready = src.indexOf("case Player.STATE_READY:");
        int nextCase = src.indexOf("case Player.STATE_ENDED:", ready);
        assertTrue("se encuentran los estados READY y ENDED", ready >= 0 && nextCase > ready);
        assertTrue("al terminar la carga se vuelven a mostrar los controles",
                src.substring(ready, nextCase).contains("showControls();"));

        java.io.File retrato = new java.io.File(
                "app/src/main/res/layout/activity_player_portrait.xml");
        if (!retrato.isFile()) {
            retrato = new java.io.File("src/main/res/layout/activity_player_portrait.xml");
        }
        String xml = new String(java.nio.file.Files.readAllBytes(retrato.toPath()),
                java.nio.charset.StandardCharsets.UTF_8);
        int fila = xml.indexOf("@+id/filaServidores");
        int etiqueta = xml.indexOf("<TextView", fila);
        int scroll = xml.indexOf("<HorizontalScrollView", etiqueta);
        assertTrue("el rótulo de servidores no debe sacar los chips de pantalla",
                fila >= 0 && etiqueta > fila && scroll > etiqueta
                        && xml.substring(etiqueta, scroll).contains("android:layout_width=\"wrap_content\""));
    }

    @Test
    public void logosDeLaTiraTienenUnAreaDeImagenLimitada() throws Exception {
        String src = leerFuente();
        int inicio = src.indexOf("private void extenderTira(");
        int fin = src.indexOf("private void zapearCanal(", inicio);
        assertTrue("se encuentra la construcción de tarjetas", inicio >= 0 && fin > inicio);
        String tira = src.substring(inicio, fin);
        assertTrue("el logo usa altura fija adaptada a la orientación",
                tira.contains("(int) ((horizontal ? 60 : 48) * d)"));
        assertTrue("el logo mantiene el ajuste de imagen dentro del recuadro",
                tira.contains("img.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);"));
    }

    @Test
    public void agendaPreparaUnaListaAleatoriaDesdeElCatalogoLocal() throws Exception {
        String src = leerFuente();
        int inicio = src.indexOf("private void prepararZapeoAgenda()");
        int fin = src.indexOf("private void actualizarIconoPantalla()", inicio);
        assertTrue("se encuentra la preparación de canales de agenda", inicio >= 0 && fin > inicio);
        String preparacion = src.substring(inicio, fin);
        assertTrue(preparacion.contains("getBooleanExtra(\"agenda_evento\", false)"));
        assertTrue(preparacion.contains("CatalogoJson.aCanales(getApplicationContext()), 10"));
        assertTrue(preparacion.contains("ZapeoCanales.fijar(seleccionados, evento)"));
    }

    private static int contar(String s, String sub) {
        int c = 0, i = 0;
        while ((i = s.indexOf(sub, i)) >= 0) { c++; i += sub.length(); }
        return c;
    }
}
