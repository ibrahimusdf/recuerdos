package dza.fut.paipo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * v7.38.1 — DRM ClearKey de Drama y las utilidades de la captura del embed.
 *
 * Medido con Abu Dhabi Al Emarat (live_tv_abudabyuae), payload real:
 *   "drm":{"uuid":"CLEARKEY_UUID","clearkey":"HLefmCmE7OkZMgMKS7f6qg:4uUPr6kjguc1vA6KofZ7IA"}
 * y su manifiesto declara cenc:default_KID="1CB79F98-2984-ECE9-1932-030A4BB7FAAA",
 * que es exactamente el KID de ese par. Ojo: ese .mpd anuncia además Widevine
 * (urn:uuid:edef8ba9…) y empaquetado cbcs.
 */
public class DrmYActividadTest {

    private static final String KID_HEX = "1cb79f982984ece91932030a4bb7faaa";
    private static final String CLAVE_HEX = "e2e50fafa92382e735bc0e8aa1f67b20";

    @Test
    public void elParDeAbuDhabiSeConvierteBien() {
        assertEquals(KID_HEX, DramaResolucion.b64aHex("HLefmCmE7OkZMgMKS7f6qg"));
        assertEquals(CLAVE_HEX, DramaResolucion.b64aHex("4uUPr6kjguc1vA6KofZ7IA"));
        // 16 bytes cada uno: es lo que exige ClearKey
        assertEquals(32, KID_HEX.length());
        assertEquals(32, CLAVE_HEX.length());
    }

    @Test
    public void elServidorDeAbuDhabiLlevaSuDrm() {
        String json = "{\"url\":\"https://admn-live-cdn-lb.starzplayarabia.com/out/v1/admn_tv/"
                + "emarat_tv/emarat_tv_dash_enc/index.mpd\",\"agent\":\"Mozilla/5.0 (Macintosh) "
                + "Chrome/149.0.0.0\",\"acceptSSL\":\"1\",\"drm\":{\"uuid\":\"CLEARKEY_UUID\","
                + "\"clearkey\":\"HLefmCmE7OkZMgMKS7f6qg:4uUPr6kjguc1vA6KofZ7IA\"},"
                + "\"headers\":{\"User-Agent\":\"Mozilla/5.0 (Macintosh) Chrome/149.0.0.0\"}}"
                + " -- redirect";
        java.util.List<DramaResolucion.Opcion> ops = DramaResolucion.opciones(null, json);
        assertEquals(1, ops.size());
        DramaResolucion.Opcion o = ops.get(0);
        assertEquals(DramaResolucion.Tipo.DIRECTA, o.tipo);
        assertEquals(KID_HEX + ":" + CLAVE_HEX, o.clearKey);
        assertTrue("el UA del proveedor se conserva", o.ua.contains("Macintosh"));
    }

    @Test
    public void sinDrmNoSeInventaNada() {
        assertNull(DramaResolucion.claveClearKey(null));
        String sinDrm = "{\"url\":\"https://x/y.m3u8\",\"agent\":\"Mozilla/5.0\"}";
        assertNull(DramaResolucion.opciones(null, sinDrm).get(0).clearKey);
        // un par incompleto no revienta: se ignora
        String roto = "{\"url\":\"https://x/y.mpd\",\"drm\":{\"clearkey\":\"soloKID\"}}";
        assertNull(DramaResolucion.opciones(null, roto).get(0).clearKey);
        assertNull(DramaResolucion.b64aHex(null));
        assertNull(DramaResolucion.b64aHex(""));
    }

    @Test
    public void losMarcadoresLsSeReconocen() {
        assertTrue(PlayerActivity.esMarcadorLs(
                "http://.LS.V2live_tv_custom_handler_alwan3_1_description_DL_/s"));
        assertTrue(PlayerActivity.esMarcadorLs("https://.LS.V2otro/s"));
        assertTrue(!PlayerActivity.esMarcadorLs("https://tvsen7.aynascope.net/fx/index.m3u8"));
        assertTrue(!PlayerActivity.esMarcadorLs(null));
    }

    /** Los 8 servidores de beIN 1, tal como los listó el móvil del usuario (log 19:02:04). */
    private static final String BACKUP_BEIN1 =
            "http://primetv.cc.cd/beinfhd11/tracks-v1a1/mono.tbin.m3u8 -- Mozilla -;- "
            + "http://.LS.V2live_tv_custom_handler_live_tv_beinsport1_description_SD_/s -- redirect -;- "
            + "http://.LS.V2live_tv_custom_handler_live_tv_beinsport1_description_HD_/s -- redirect -;- "
            + "http://.LS.V2live_tv_custom_handler_live_tv_beinsport1_description_4K_/s -- redirect";

    @Test
    public void sePruebanTodosLosServidoresDelCanalSinRepetir() {
        java.util.Set<String> probados = new java.util.HashSet<>();
        java.util.List<String> orden = new java.util.ArrayList<>();
        DramaResolucion.Opcion o;
        while ((o = PlayerActivity.siguienteOpcionUtil(null, BACKUP_BEIN1, probados)) != null) {
            probados.add(o.url);
            orden.add(o.url);
            assertTrue("no debe repetir " + o.url, orden.size() <= 8);
        }
        // la directa (primetv) y los marcadores: los marcadores NO son útiles
        assertTrue("debe llegar a la directa", orden.contains("http://primetv.cc.cd/beinfhd11/tracks-v1a1/mono.tbin.m3u8"));
        assertEquals("solo hay una opción útil en este backup", 1, orden.size());
    }

    @Test
    public void lasEmbebidasVanPrimeroYNoSeRepiten() {
        // ordernap SÍ se prueba; quotarevival está medido como muerto y se filtra
        String backup = "https://ordernap.net/e/z7iiiknxnuqjk -- redirect -;- "
                + "https://quotarevival.net/e/yxtmusy4xj8tj -- redirect -;- "
                + "http://primetv.cc.cd/x/mono.m3u8 -- Mozilla";
        java.util.List<String> probados = new java.util.ArrayList<>();
        DramaResolucion.Opcion p1 = PlayerActivity.siguienteOpcionUtil(null, backup, probados);
        assertEquals("primero la embebida", DramaResolucion.Tipo.EMBEBIDA, p1.tipo);
        assertEquals("https://ordernap.net/e/z7iiiknxnuqjk", p1.url);
        probados.add(p1.url);
        DramaResolucion.Opcion p2 = PlayerActivity.siguienteOpcionUtil(null, backup, probados);
        assertEquals("después la directa", DramaResolucion.Tipo.DIRECTA, p2.tipo);
        probados.add(p2.url);
        assertNull("ya no queda nada", PlayerActivity.siguienteOpcionUtil(null, backup, probados));
    }

    @Test
    public void unMarcadorLsNoCuentaComoServidorUtil() {
        java.util.Set<String> probados = new java.util.HashSet<>();
        assertNull(PlayerActivity.siguienteOpcionUtil(
                "http://.LS.V2live_tv_custom_handler_alwan3_1_description_DL_/s", null, probados));
    }

    @Test
    public void utilidadesDeLaCapturaDelEmbed() {
        assertEquals("https://ordernap.net", PlayerActivity.origenDe("https://ordernap.net/e/z7"));
        assertEquals("https://a.b", PlayerActivity.origenDe("https://a.b"));
        assertNull(PlayerActivity.origenDe("ordernap.net/e/z"));
        assertNull(PlayerActivity.origenDe(null));
        assertTrue(PlayerActivity.esPlaylistCazable("https://x/hls/a.m3u8?s=1"));
        assertTrue(!PlayerActivity.esPlaylistCazable("https://x/a.m3u8.bak"));
        assertTrue(!PlayerActivity.esPlaylistCazable("https://ordernap.net/e/z"));
    }
}
