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
