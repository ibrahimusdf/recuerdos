package dza.fut.paipo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

/**
 * v7.38.11 — <b>la estrategia de la app que sí funciona.</b>
 *
 * <p>Analizada {@code DramaLive-Android-v1.0} (la app de referencia que el usuario
 * confirma que abre todos los canales con el mismo backend), su regla es esta y solo
 * esta ({@code Api.java}):</p>
 *
 * <pre>
 * public String urlReproducible(String codigo, String idCanal) {
 *     if (!hls) return url;                                    // radio: directa
 *     return BASE + "/p/" + codigo + "/" + idCanal + "/" + n + ".m3u8";
 * }
 * </pre>
 *
 * <p>Es decir: <b>para TODAS las opciones, en el orden de la API, se le pide el
 * manifiesto al proxy</b> — sin mirar {@code emite}, sin WebView, sin sesión de Drama.
 * Y funciona porque <b>el proxy resuelve aunque la sonda de la API haya fallado</b>.</p>
 *
 * <p>Esto es lo que estaba mal en mis versiones 7.38.8–7.38.10: yo filtraba por
 * {@code emite} y por {@code http}, y la API contesta {@code http:null} (timeout a los
 * 10 s) a hosts que luego el proxy resuelve perfectamente. La API decía «no sé» y yo lo
 * leía como «no».</p>
 */
public class EstrategiaDeLaReferenciaTest {

    private static final String BASE = "https://dramalive-api-08th.onrender.com";
    private static final String CODIGO = "232425";

    // ─────────────── el hallazgo: timeout NO es «no emite» ───────────────

    /**
     * Copia literal de {@code /opciones/232425/live_tv_beinsport5} (medido 2026-11-18).
     *
     * <p>La opción 0 es la clave: {@code emite:false}, {@code http:null} y un timeout de
     * <b>13,3 s</b> contra {@code rap8.rapidbackend.shop}. Con mi filtro se tiraba. Pero:</p>
     *
     * <pre>
     * GET /p/232425/live_tv_beinsport5/0.m3u8  →  #EXTM3U válido
     * </pre>
     */
    private static final String JSON_BEIN5 = "{\"codigo\":\"232425\",\"canal\":"
            + "{\"id_live\":\"live_tv_beinsport5\"},\"total\":5,\"emitiendo\":2,\"opciones\":["
            + "{\"n\":1,\"tipo\":\"marcador\",\"origen\":\"http://.LS.V2x/s\","
            + "\"url\":\"https://sup7.superdatanet.cfd/hd/mzphgusqtrxo/index.html\","
            + "\"headers\":{},\"emite\":true,\"hls\":true,\"http\":200},"
            + "{\"n\":2,\"tipo\":\"marcador\",\"origen\":\"http://.LS.V2y/s\","
            + "\"url\":\"https://tur11.turboapistorage.shop/fk/vshlsjobrcee/index.html\","
            + "\"headers\":{},\"emite\":true,\"hls\":true,\"http\":200},"
            + "{\"n\":0,\"tipo\":\"marcador\",\"origen\":\"http://.LS.V2z/s\","
            + "\"url\":\"https://rap8.rapidbackend.shop/sd/unlceroyjeny/index.html\","
            + "\"headers\":{},\"emite\":false,\"hls\":false,\"http\":null,"
            + "\"error\":\"Read timed out. (read timeout=10)\"},"
            + "{\"n\":7,\"tipo\":\"directa\",\"origen\":\"http://primetv.cc.cd/beinfhd55/tracks-v1a1/mono.tbin.m3u8\","
            + "\"url\":\"http://primetv.cc.cd/beinfhd55/tracks-v1a1/mono.tbin.m3u8\","
            + "\"headers\":{},\"emite\":false,\"http\":403},"
            + "{\"n\":3,\"tipo\":\"embed\",\"origen\":\"https://ordernap.net/e/9pqqqn7ow18a7\","
            + "\"url\":\"https://ordernap.net/e/9pqqqn7ow18a7\","
            + "\"headers\":{},\"emite\":false,\"http\":403}]}";

    @Test
    public void unTimeoutNoDescartaLaOpcion() {
        ApiDrama.Opciones o = ApiDrama.leerOpciones(JSON_BEIN5);
        assertNotNull(o);
        assertEquals(2, o.emitiendo);

        ApiDrama.OpcionApi conTimeout = null;
        for (ApiDrama.OpcionApi x : o.opciones) if (x.url.contains("rapidbackend")) conTimeout = x;
        assertNotNull(conTimeout);
        assertFalse("la API la da por muerta", conTimeout.sirve());
        assertTrue("pero NO es un no rotundo: es un timeout",
                conTimeout.podriaEmitir());
    }

    /**
     * El corazón del arreglo: beIN 5 debe producir <b>3</b> turnos (las 2 verdes + la del
     * timeout), y la del timeout tiene que estar. En la v7.38.10 producía 2 y se dejaba
     * justo la que el proxy resuelve.
     */
    @Test
    public void beIn5ProduceLosTresTurnos() {
        List<DramaResolucion.Opcion> ops =
                ApiDrama.aOpciones(BASE, CODIGO, "live_tv_beinsport5", ApiDrama.leerOpciones(JSON_BEIN5));
        assertEquals("2 verdes + la del timeout", 3, ops.size());
        assertTrue("la del timeout debe estar: su proxy da #EXTM3U",
                contiene(ops, "/p/232425/live_tv_beinsport5/0.m3u8"));
        // las confirmadas primero
        assertEquals(BASE + "/p/232425/live_tv_beinsport5/1.m3u8", ops.get(0).url);
        assertEquals(BASE + "/p/232425/live_tv_beinsport5/2.m3u8", ops.get(1).url);
    }

    @Test
    public void un403SiEsUnNoRotundo() {
        for (DramaResolucion.Opcion o :
                ApiDrama.aOpciones(BASE, CODIGO, "live_tv_beinsport5", ApiDrama.leerOpciones(JSON_BEIN5))) {
            assertFalse("un 403 no se prueba: " + o.url, o.url.contains("primetv"));
            assertFalse("ni un embed 403: " + o.url, o.url.contains("ordernap"));
        }
    }

    // ─────────────── un turno por opción: solo la proxada ───────────────

    @Test
    public void todasLasOpcionesSePidenPorElProxy() {
        for (DramaResolucion.Opcion o :
                ApiDrama.aOpciones(BASE, CODIGO, "live_tv_beinsport5", ApiDrama.leerOpciones(JSON_BEIN5))) {
            assertTrue("todo va por /p/: " + o.url, o.url.startsWith(BASE + "/p/"));
            assertEquals(DramaResolucion.Tipo.DIRECTA, o.tipo);
            assertTrue(DramaResolucion.esPlaylist(o.url));
        }
    }

    /** Y el proxy se pide con el número de opción de la API, no con un índice propio. */
    @Test
    public void elNumeroDeOpcionEsElDeLaApi() {
        ApiDrama.Opciones o = ApiDrama.leerOpciones(JSON_BEIN5);
        List<DramaResolucion.Opcion> ops =
                ApiDrama.aOpciones(BASE, CODIGO, "live_tv_beinsport5", o);
        for (ApiDrama.OpcionApi x : o.opciones) {
            if (x.http != null && x.http == 403) continue;      // descartada
            boolean esta = false;
            for (DramaResolucion.Opcion y : ops) {
                if (y.url.endsWith("/" + x.n + ".m3u8")) esta = true;
            }
            assertTrue("falta la opción n=" + x.n, esta);
        }
    }

    /** Con 8 opciones (beIN 2) el plan B no debe duplicar turnos. */
    @Test
    public void sinDuplicarTurnos() {
        List<DramaResolucion.Opcion> ops =
                ApiDrama.aOpciones(BASE, CODIGO, "live_tv_beinsport5", ApiDrama.leerOpciones(JSON_BEIN5));
        java.util.HashSet<String> v = new java.util.HashSet<>();
        for (DramaResolucion.Opcion o : ops) {
            assertTrue("repetida: " + o.url, v.add(DramaResolucion.clave(o.url)));
        }
    }

    private static boolean contiene(List<DramaResolucion.Opcion> l, String trozo) {
        for (DramaResolucion.Opcion o : l) if (o.url.contains(trozo)) return true;
        return false;
    }
}
