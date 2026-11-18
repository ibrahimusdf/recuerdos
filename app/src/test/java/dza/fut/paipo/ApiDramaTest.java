package dza.fut.paipo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * v7.38.8 — pruebas de {@link ApiDrama} contra la respuesta REAL de la API de la cadena
 * (beIN Sports 1, {@code 232425/live_tv_beinsport1}, medida el 2026-11-18).
 *
 * <p>El fixture {@code app/src/test/resources/opciones_bein1.json} es copia literal de
 * lo que devolvió {@code /opciones/232425/live_tv_beinsport1}: 7 opciones, 3 emitiendo.
 * Nada de esto toca la red, así que corre en CI sin conexión.</p>
 */
public class ApiDramaTest {

    private static final String BASE = "https://dramalive-api-08th.onrender.com";
    private static final String CODIGO = "232425";
    private static final String ID = "live_tv_beinsport1";

    /** Los tres hosts que SÍ emiten en beIN 1 (medido). */
    private static final String[] EMITEN = {
            "https://sup7.superdatanet.cfd/hd/azmyqsojuqbm/index.html",              // n=1 HD
            "https://ult11.ultraandroidsoftstarsfeedmoon.shop/fk/xfwurjrfwhim/index.html", // n=2 4K
            "https://spe6.speedsuperai.cfd/pages/alkoaqoohqze/index.html",           // n=0 SD
    };

    private static String fixture() throws IOException {
        try (InputStream in = ApiDramaTest.class.getClassLoader()
                .getResourceAsStream("opciones_bein1.json")) {
            assertNotNull("falta el fixture opciones_bein1.json", in);
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            return new String(bo.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    // ───────────────────────────── parseo ─────────────────────────────

    @Test
    public void leeLaRespuestaRealDeBein1() throws IOException {
        ApiDrama.Opciones o = ApiDrama.leerOpciones(fixture());
        assertNotNull(o);
        assertEquals(CODIGO, o.codigo);
        assertEquals(ID, o.idLive);
        assertEquals(7, o.opciones.size());
        assertEquals(3, o.emitiendo);
        assertEquals("las 3 confirmadas", 3, o.confirmadas().size());
        // v7.38.9: queEmiten() añade las que la API descarta con un 2xx (no solo las
        // confirmadas). En beIN 1 eso son las de ordernap/quotarevival/tossequip, que
        // responden 403 -> siguen fuera. Solo entra la que no es un no rotundo.
        for (ApiDrama.OpcionApi x : o.queEmiten()) {
            assertFalse("un 403 es un no de verdad: " + x.url, x.http == 403);
        }
    }

    @Test
    public void lasTresQueEmitenSonLosMarcadoresLs() throws IOException {
        List<ApiDrama.OpcionApi> e = ApiDrama.leerOpciones(fixture()).queEmiten();
        assertEquals(3, e.size());
        for (int i = 0; i < EMITEN.length; i++) {
            assertEquals(EMITEN[i], e.get(i).url);
            assertTrue("debe ser marcador: " + e.get(i).tipo, "marcador".equals(e.get(i).tipo));
            assertTrue(e.get(i).emite);
            assertEquals(200, e.get(i).http);
        }
    }

    /**
     * El dato que explica el cuadro negro: las «útiles» de antes daban 403.
     *
     * <p>v7.38.9 — además, un 403 no debe colarse ni siquiera como «dudosa»: es un no
     * rotundo del servidor.</p>
     */
    @Test
    public void lasQueLaAppIntentoAntesDan403() throws IOException {
        for (ApiDrama.OpcionApi x : ApiDrama.leerOpciones(fixture()).opciones) {
            if (!x.emite) assertEquals("no debería emitir: " + x.url, 403, x.http);
        }
    }

    @Test
    public void traeCabecerasYNumeroDeOpcion() throws IOException {
        List<ApiDrama.OpcionApi> e = ApiDrama.leerOpciones(fixture()).queEmiten();
        assertEquals(1, e.get(0).n);                     // HD
        assertEquals(2, e.get(1).n);                     // 4K
        assertEquals(0, e.get(2).n);                     // SD
        assertEquals("https://sup7.superdatanet.cfd/", e.get(0).referer);
        assertNotNull(e.get(0).ua);
        assertTrue(e.get(0).ua.contains("Macintosh"));
    }

    @Test
    public void aguantaBasuraSinLanzar() {
        assertNull(ApiDrama.leerOpciones(null));
        assertNull(ApiDrama.leerOpciones(""));
        assertNull(ApiDrama.leerOpciones("   "));
        assertNull(ApiDrama.leerOpciones("{roto"));
        assertNull(ApiDrama.leerOpciones("[]"));
        assertNull(ApiDrama.leerOpciones("{\"codigo\":\"1\"}"));            // sin canal
        assertNull(ApiDrama.leerOpciones("{\"canal\":{\"id_live\":\"x\"}}")); // sin opciones
        assertNull(ApiDrama.leerOpciones("<html>portal cautivo</html>"));
    }

    @Test
    public void torcidasNoRompemElParseo() {
        String j = "{\"codigo\":\"1\",\"canal\":{\"id_live\":\"x\"},\"emitiendo\":1,"
                + "\"opciones\":[{\"n\":\"0\",\"url\":\"https://ok/a\",\"emite\":\"true\"},"
                + "{\"n\":0},"
                + "{\"nul\":true}]}";
        ApiDrama.Opciones o = ApiDrama.leerOpciones(j);
        assertNotNull(o);
        assertEquals(1, o.opciones.size());          // las que no traen url se saltan
        assertEquals("https://ok/a", o.opciones.get(0).url);
    }

    // ────────────────────── el proxy (vía corta) ──────────────────────

    @Test
    public void armaLaUrlDelProxyConElNumeroDeOpcion() {
        assertEquals(BASE + "/p/232425/live_tv_beinsport1/0.m3u8",
                ApiDrama.urlPlaylist(BASE, CODIGO, ID, 0));
        assertEquals(BASE + "/p/232425/live_tv_beinsport1/2.m3u8",
                ApiDrama.urlPlaylist(BASE + "/", CODIGO, ID, 2));
        assertEquals(BASE + "/stream/232425/live_tv_beinsport1?n=1",
                ApiDrama.urlStream(BASE, CODIGO, ID, 1));
    }

    @Test
    public void sinDatosNoInventaUnaUrl() {
        assertNull(ApiDrama.urlPlaylist(null, CODIGO, ID, 0));
        assertNull(ApiDrama.urlPlaylist(BASE, null, ID, 0));
        assertNull(ApiDrama.urlPlaylist(BASE, CODIGO, "", 0));
        assertNull(ApiDrama.urlPlaylist(BASE, CODIGO, ID, -1));
        assertNull(ApiDrama.urlStream(null, CODIGO, ID, 0));
    }

    /**
     * La conversión a opciones del plan B: primero la playlist proxada (ExoPlayer nativo),
     * después la página original como respaldo. 3 opciones que emiten -> 6 entradas.
     */
    @Test
    public void aOpcionesPoneElProxyDelanteDeCadaEmisora() throws IOException {
        List<DramaResolucion.Opcion> l =
                ApiDrama.aOpciones(BASE, CODIGO, ID, ApiDrama.leerOpciones(fixture()));
        assertEquals(6, l.size());
        assertEquals(BASE + "/p/232425/live_tv_beinsport1/1.m3u8", l.get(0).url);
        assertEquals(DramaResolucion.Tipo.DIRECTA, l.get(0).tipo);
        assertEquals(EMITEN[0], l.get(1).url);
        assertEquals(DramaResolucion.Tipo.EMBEBIDA, l.get(1).tipo);
        assertTrue(l.get(1).iframe);        // se abre en el marco
        assertEquals(BASE + "/p/232425/live_tv_beinsport1/2.m3u8", l.get(2).url);
        assertEquals(BASE + "/p/232425/live_tv_beinsport1/0.m3u8", l.get(4).url);
    }

    @Test
    public void sinBaseSoloQuedanLasPaginasOriginales() throws IOException {
        List<DramaResolucion.Opcion> l =
                ApiDrama.aOpciones(null, null, null, ApiDrama.leerOpciones(fixture()));
        assertEquals(3, l.size());
        for (DramaResolucion.Opcion o : l) {
            assertEquals(DramaResolucion.Tipo.EMBEBIDA, o.tipo);
            assertFalse(o.url.contains("/p/"));
        }
        assertTrue(ApiDrama.aOpciones(null).isEmpty());
        assertTrue(ApiDrama.aOpciones(null, CODIGO, ID, null).isEmpty());
    }

    /** La proxada es un m3u8: tiene que clasificarse como DIRECTA, no como página. */
    @Test
    public void laProxadaSeVeComoPlaylist() {
        String u = ApiDrama.urlPlaylist(BASE, CODIGO, ID, 0);
        assertNotNull(u);
        assertTrue(DramaResolucion.esPlaylist(u));
        assertEquals(DramaResolucion.Tipo.DIRECTA, DramaResolucion.clasificar(u, false));
    }

    // ─────────────────── reescritura de manifiesto ───────────────────

    /** Copia literal de `/p/232425/live_tv_beinsport1/0.m3u8` (2026-11-18). */
    private static final String MANIFIESTO = "#EXTM3U\n"
            + "#EXT-X-VERSION:3\n"
            + "#EXT-X-TARGETDURATION:10\n"
            + "#EXT-X-MEDIA-SEQUENCE:1794925067\n"
            + "#EXTINF:10.000000,\n"
            + "/ps/232425/live_tv_beinsport1/0/k9x_1794919732-1794925067.js\n"
            + "#EXTINF:10.000000,\n"
            + "/ps/232425/live_tv_beinsport1/0/k9x_1794919732-1794925068.js\n";

    @Test
    public void reescribeLosSegmentosRelativosAAbsolutos() {
        String r = ApiDrama.reescribir(MANIFIESTO, BASE, CODIGO, ID, 0);
        assertTrue(r.startsWith("#EXTM3U"));
        assertTrue(r.contains("#EXT-X-TARGETDURATION:10"));
        assertTrue(r.contains(BASE + "/ps/232425/live_tv_beinsport1/0/k9x_1794919732-1794925067.js"));
        assertTrue(r.contains(BASE + "/ps/232425/live_tv_beinsport1/0/k9x_1794919732-1794925068.js"));
        assertFalse("no debe quedar ninguna ruta suelta", r.contains("\n/ps/"));
    }

    @Test
    public void reescribeLaClaveClearKeyYRespetAlasUrlsAbsolutas() {
        String m = "#EXTM3U\n"
                + "#EXT-X-KEY:METHOD=AES-128,URI=\"/ps/232425/live_tv_beinsport1/0/k.key\"\n"
                + "#EXTINF:10,\n"
                + "abc.js\n"
                + "#EXTINF:10,\n"
                + "https://otro.cdn/absoluto.ts\n";
        String r = ApiDrama.reescribir(m, BASE, CODIGO, ID, 0);
        assertTrue(r.contains("URI=\"" + BASE + "/ps/232425/live_tv_beinsport1/0/k.key\""));
        assertTrue(r.contains(BASE + "/ps/232425/live_tv_beinsport1/0/abc.js"));
        assertTrue("lo absoluto se deja como está", r.contains("https://otro.cdn/absoluto.ts"));
    }

    @Test
    public void reescribirNoTocaNadaSiFaltaContexto() {
        assertEquals(MANIFIESTO, ApiDrama.reescribir(MANIFIESTO, null, CODIGO, ID, 0));
        assertEquals(MANIFIESTO, ApiDrama.reescribir(MANIFIESTO, BASE, CODIGO, ID, -1));
        assertEquals("", ApiDrama.reescribir("", BASE, CODIGO, ID, 0));
        assertEquals("#EXT-X-KEY:METHOD=AES-128,URI=\"https://k/x\"",
                ApiDrama.reescribir("#EXT-X-KEY:METHOD=AES-128,URI=\"https://k/x\"",
                        BASE, CODIGO, ID, 0));
    }

    // ───────────── el acelerador nunca puede frenar (v7.38.8) ─────────────

    /**
     * Si la API no contesta una vez, no se vuelve a preguntar en toda la sesión. Sin
     * esto, un host dormido añadiría 2,5 s al arranque de CADA canal.
     */
    @Test
    public void cuandoLaApiCaeNoSeVuelveAPreguntar() {
        ApiDrama.olvidarEstado();
        assertFalse("al empezar se puede preguntar", ApiDrama.sesionSinApi());
        ApiDrama.marcarCaida();
        assertTrue("tras un fallo, la sesión queda sin API", ApiDrama.sesionSinApi());
        // y pedirOpciones ni siquiera intenta la red
        assertNull(ApiDrama.pedirOpciones(BASE, CODIGO, ID));
        ApiDrama.olvidarEstado();       // no contaminar el resto de pruebas
        assertFalse(ApiDrama.sesionSinApi());
    }

    @Test
    public void sinContextoNoHayApiPeroTampocoExcepcion() {
        ApiDrama.olvidarEstado();
        assertNull(ApiDrama.baseConfigurada(null));
        assertFalse(ApiDrama.estaViva(null));
        ApiDrama.olvidarEstado();
    }

    /**
     * v7.38.9 — el tope de la consulta tiene que dar tiempo a la API más lenta, porque
     * ya no está en el camino crítico. Medido: beIN 5 tarda 13,3 s en UNA opción.
     */
    @Test
    public void elTopeAguantaLaApiMasLenta() throws IOException {
        assertEquals(12000, ApiDrama.TIEMPO_MS);
    }

    /** La base por defecto es lo que hace que esto funcione sin tocar ajustes. */
    @Test
    public void hayUnHostPorDefecto() {
        assertNotNull(ApiDrama.BASE_POR_DEFECTO);
        assertTrue(ApiDrama.BASE_POR_DEFECTO.startsWith("https://"));
        assertFalse(ApiDrama.BASE_POR_DEFECTO.endsWith("/"));
    }

    /** Basura de punta a punta: nada de esto puede lanzar (la app no debe caerse). */
    @Test
    public void ningunMetodoLanzaConEntradasRaras() {
        assertNull(ApiDrama.pedirOpciones(null, CODIGO, ID));
        assertNull(ApiDrama.pedirOpciones("", CODIGO, ID));
        assertNull(ApiDrama.pedirOpciones("no-es-una-url", CODIGO, ID));
        assertTrue(ApiDrama.aOpciones(ApiDrama.leerOpciones("null")).isEmpty());
    }
}
