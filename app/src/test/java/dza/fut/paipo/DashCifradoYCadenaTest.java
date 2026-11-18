package dza.fut.paipo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * v7.38.10 — dos correcciones sobre la v7.38.9, las dos medidas contra la API real.
 *
 * <h3>1. El «rescate de DASH» de la 7.38.9 estaba mal</h3>
 *
 * <p>Rescataba cualquier {@code .mpd} con 2xx. Pero esos {@code .mpd} son
 * {@code cenc.mpd} / {@code ..._dash_enc/index.mpd}: van cifrados en <b>Widevine</b>, que
 * no se abre sin licencia. El propio diagnóstico del proyecto
 * ({@code docs/DIAGNOSTICO-CANALES-DRAMA-v7.38.3.md}) ya lo decía. Meterlos en el plan
 * solo gasta turnos.</p>
 *
 * <h3>2. Un host de la cadena no debe pasar por el WebView</h3>
 *
 * <p>Medido con {@code live_tv_beinsport1max} (Bein Max. 1): su <b>única</b> opción sana
 * es {@code max10.maxwordsandroidios.shop/pages/…/index.html}. Las otras cinco son
 * Widevine o 403. La app la metía en el WebView, la página se iba a su propio reproductor
 * y no devolvía el manifiesto → canal en negro con «un servidor bueno» en la lista.</p>
 */
public class DashCifradoYCadenaTest {

    private static final String LINEA_DASH_CIFRADA =
            "https://admn-live-cdn-lb.starzplayarabia.com/out/v1/admn_tv/emarat_tv"
                    + "/emarat_tv_dash_enc/index.mpd";

    // ─────────────────── 1. DASH cifrado no se rescata ───────────────────

    @Test
    public void losDashRealesDeLaApiSeReconocenComoCifrados() {
        ApiDrama.Opciones o = ApiDrama.leerOpciones(JSON_MAX1);
        assertNotNull(o);
        boolean vioAlguno = false;
        for (ApiDrama.OpcionApi x : o.opciones) {
            if (x.url.endsWith(".mpd")) {
                vioAlguno = true;
                assertTrue("debe marcarse cifrado: " + x.url, x.esDashCifrado());
                assertFalse("y NO debe servir: " + x.url, x.sirve());
            }
        }
        assertTrue("el fixture trae .mpd", vioAlguno);
    }

    @Test
    public void unDashLimpioNoSeMarcaCifrado() {
        ApiDrama.OpcionApi limpio = new ApiDrama.OpcionApi(0, "directa",
                "https://x/manifest.mpd", "https://x/limpio/manifest.mpd",
                null, null, false, 200);
        assertFalse(limpio.esDashCifrado());
    }

    @Test
    public void unM3u8NoEsDash() {
        ApiDrama.OpcionApi hls = new ApiDrama.OpcionApi(0, "marcador",
                ".LS.", "https://x/a/index.m3u8", null, null, true, 200);
        assertFalse(hls.esDashCifrado());
    }

    /**
     * El caso que estaba mal en la 7.38.9: Abu Dhabi tenía 2 «dudosas» rescatadas (ambas
     * cifradas). Ahora son 0, y el canal no gasta turnos en algo imposible.
     */
    @Test
    public void abuDhabiYaNoRescataLosWidevine() {
        ApiDrama.Opciones o = ApiDrama.leerOpciones(JSON_ABUDABI);
        assertNotNull(o);
        assertEquals(0, o.emitiendo);
        assertEquals("ninguna dudosa vale: las 2 son CENC y la otra 403",
                0, o.queEmiten().size());
        assertTrue(ApiDrama.aOpciones("https://api", "232425", "live_tv_abudabyuae", o)
                .isEmpty());
    }

    /** Y beIN Max 1: de 6 opciones, solo sobrevive la sana de /pages/. */
    @Test
    public void beInMax1SoloDejaLaOpcionSana() {
        ApiDrama.Opciones o = ApiDrama.leerOpciones(JSON_MAX1);
        assertNotNull(o);
        assertEquals(1, o.emitiendo);
        List<DramaResolucion.Opcion> ops =
                ApiDrama.aOpciones("https://api", "232425", "live_tv_beinsport1max", o);
        assertEquals("proxada + página de la única sana", 2, ops.size());
        for (DramaResolucion.Opcion x : ops) {
            assertFalse("ningún Widevine: " + x.url, x.url.contains(".mpd"));
            assertFalse("ningún 403: " + x.url, x.url.contains("ordernap")
                    || x.url.contains("tinslime") || x.url.contains("tossequip"));
        }
    }

    // ─────────────── 2. host de la cadena → ExoPlayer directo ───────────────

    @Test
    public void reconoceLosHostsRealesDeLaCadena() {
        // las cinco formas que la cadena usa hoy (los dominios rotan cada pocos días)
        assertTrue(DramaResolucion.esHostDeLaCadena(
                "https://sup7.superdatanet.cfd/hd/azmyqsojuqbm/index.html"));
        assertTrue(DramaResolucion.esHostDeLaCadena(
                "https://ult11.ultraiossoftstarsfeedmoon.shop/fk/wmugpqjgfxnz/index.html"));
        assertTrue(DramaResolucion.esHostDeLaCadena(
                "https://bui8.buildapiplatform.cfd/sd/cvrdszxqccuh/index.html"));
        assertTrue(DramaResolucion.esHostDeLaCadena(
                "https://max10.maxwordsandroidios.shop/pages/vhxivhzkeuix/index.html"));
        assertTrue(DramaResolucion.esHostDeLaCadena(
                "https://dat3.datahostingapiportal.monster/mirsp/mrktygbmlhes/index.html"));
    }

    @Test
    public void noConfundeEmbedsNiElProxyDeLaApi() {
        assertFalse(DramaResolucion.esHostDeLaCadena("https://ordernap.net/e/z7iiiknxnuqjk"));
        assertFalse(DramaResolucion.esHostDeLaCadena("https://quotarevival.net/e/x"));
        assertFalse(DramaResolucion.esHostDeLaCadena(null));
        assertFalse(DramaResolucion.esHostDeLaCadena(""));
        // el proxy de la API NO es la cadena: es un proxy
        assertFalse(DramaResolucion.esHostDeLaCadena(
                "https://dramalive-api-08th.onrender.com/p/232425/live_tv_x/0.m3u8"));
        assertFalse(DramaResolucion.esHostDeLaCadena(
                "https://dramalive-api-08th.onrender.com/ps/232425/live_tv_x/0/a.js"));
        // pero el sitio sí
        assertTrue(DramaResolucion.esHostDeLaCadena("https://web.dramalive.cc/"));
    }

    /** Un host de la cadena va directo a ExoPlayer: no se abre el WebView. */
    @Test
    public void elHostDeLaCadenaPideExoPlayerDirecto() {
        assertTrue(DramaResolucion.abreEnExoPlayerDirecto(
                "https://max10.maxwordsandroidios.shop/pages/vhxivhzkeuix/index.html"));
        assertFalse(DramaResolucion.abreEnExoPlayerDirecto("https://ordernap.net/e/x"));
    }

    /** Y no se le debe filtrar como «portal», o perderíamos justo la buena. */
    @Test
    public void elHostDeLaCadenaNuncaEsPortal() {
        assertFalse(DramaResolucion.esPaginaDePortal(
                "https://max10.maxwordsandroidios.shop/pages/vhxivhzkeuix/index.html"));
        assertTrue(DramaResolucion.esPaginaDePortal("https://shoof.alkass.net/live?ch=two"));
        assertTrue(DramaResolucion.esPaginaDePortal("https://dlive.sx/cast/stream-91.php"));
    }

    // ─────────────────────────── fixtures reales ───────────────────────────

    /** Copia literal de `/opciones/232425/live_tv_abudabyuae` (2026-11-18). */
    private static final String JSON_ABUDABI = "{\"codigo\":\"232425\",\"canal\":"
            + "{\"id_live\":\"live_tv_abudabyuae\"},\"total\":3,\"emitiendo\":0,\"opciones\":["
            + "{\"n\":1,\"tipo\":\"directa\",\"origen\":\"https://uselector.cdn.intigral-ott.net/ADE/ADE.isml/manifest.mpd\","
            + "\"url\":\"https://uselector.cdn.intigral-ott.net/ADE/ADE.isml/manifest.mpd\","
            + "\"headers\":{},\"emite\":false,\"http\":299},"
            + "{\"n\":0,\"tipo\":\"directa\",\"origen\":\"" + LINEA_DASH_CIFRADA + "\","
            + "\"url\":\"" + LINEA_DASH_CIFRADA + "\","
            + "\"headers\":{},\"emite\":false,\"content_type\":\"application/dash+xml\",\"http\":200},"
            + "{\"n\":2,\"tipo\":\"directa\",\"origen\":\"https://x/emarat_tv_hls_nd/index.m3u8\","
            + "\"url\":\"https://x/emarat_tv_hls_nd/index.m3u8\","
            + "\"headers\":{},\"emite\":false,\"http\":403}]}";

    /** Copia literal de `/opciones/232425/live_tv_beinsport1max` (2026-11-18). */
    private static final String JSON_MAX1 = "{\"codigo\":\"232425\",\"canal\":"
            + "{\"id_live\":\"live_tv_beinsport1max\"},\"total\":6,\"emitiendo\":1,\"opciones\":["
            + "{\"n\":0,\"tipo\":\"marcador\",\"origen\":\"http://.LS.V2x/s\","
            + "\"url\":\"https://max10.maxwordsandroidios.shop/pages/vhxivhzkeuix/index.html\","
            + "\"headers\":{},\"emite\":true,\"hls\":true,\"http\":200},"
            + "{\"n\":2,\"tipo\":\"directa\",\"origen\":\"https://otte.live.fly.ww.aiv-cdn.net/gru-nitro/live/clients/dash/enc/ud6bnhthpj/out/v1/2639a2f4480f4269953de466d5f46463/cenc.mpd\","
            + "\"url\":\"https://otte.live.fly.ww.aiv-cdn.net/gru-nitro/live/clients/dash/enc/ud6bnhthpj/out/v1/2639a2f4480f4269953de466d5f46463/cenc.mpd\","
            + "\"headers\":{},\"emite\":false,\"http\":200},"
            + "{\"n\":5,\"tipo\":\"embed\",\"origen\":\"https://tinslime.net/e/yxtmusy4xj8tj\","
            + "\"url\":\"https://tinslime.net/e/yxtmusy4xj8tj\",\"headers\":{},\"emite\":false,\"http\":403},"
            + "{\"n\":1,\"tipo\":\"embed\",\"origen\":\"https://ordernap.net/e/b11wbtzmon53m\","
            + "\"url\":\"https://ordernap.net/e/b11wbtzmon53m\",\"headers\":{},\"emite\":false,\"http\":403},"
            + "{\"n\":4,\"tipo\":\"marcador\",\"origen\":\"http://.LS.V2daddy_966/s\","
            + "\"url\":\"https://tossequip.net/e/7i35d1gzs1wt\",\"headers\":{},\"emite\":false,\"http\":403},"
            + "{\"n\":3,\"tipo\":\"directa\",\"origen\":\"https://otte.cache.aiv-cdn.net/bom-nitro/live/clients/dash/enc/72sjo8hygl/out/v1/3079be34d72a4985852d299a02406a0c/cenc.mpd\","
            + "\"url\":\"https://otte.cache.aiv-cdn.net/bom-nitro/live/clients/dash/enc/72sjo8hygl/out/v1/3079be34d72a4985852d299a02406a0c/cenc.mpd\","
            + "\"headers\":{},\"emite\":false,\"http\":200}]}";

    /** El plan final nunca debe contener un manifiesto cifrado. */
    @Test
    public void elPlanFinalNoLlevaNingunCifrado() {
        for (String json : new String[]{JSON_ABUDABI, JSON_MAX1}) {
            ApiDrama.Opciones o = ApiDrama.leerOpciones(json);
            List<DramaResolucion.Opcion> ops =
                    ApiDrama.aOpciones("https://api", "232425", o.idLive, o);
            List<DramaResolucion.Opcion> plan =
                    DramaResolucion.planDeAtaque(null, ops, null);
            for (DramaResolucion.Opcion x : plan) {
                assertFalse("cifrado en el plan: " + x.url, x.url.contains(".mpd"));
            }
        }
        assertTrue(new ArrayList<DramaResolucion.Opcion>().isEmpty());
    }
}
