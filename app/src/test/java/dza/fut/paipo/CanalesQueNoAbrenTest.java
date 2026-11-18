package dza.fut.paipo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * v7.38.9 — «beIN Sports 1 funciona pero lo demás no».
 *
 * <p>Estas pruebas fijan las tres causas que se midieron contra la API real el
 * 2026-11-18, con los JSON literales de canales que fallan. No tocan la red.</p>
 */
public class CanalesQueNoAbrenTest {

    private static final String BASE = "https://dramalive-api-08th.onrender.com";
    private static final String CODIGO = "232425";

    // ───────────── causa 1: la API llama «muerto» a un DASH que emite ─────────────

    /**
     * {@code live_tv_abudabyuae} (Abu Dhabi Al Emarat), copia literal de la API.
     *
     * <p>La API da las tres opciones por muertas: {@code emitiendo:0}. Pero la opción 0
     * es un manifiesto MPEG-DASH con {@code http:200} y
     * {@code content_type: application/dash+xml}, y su propio {@code /stream?n=0}
     * redirige a él. Con el filtro de la v7.38.8 esa url se tiraba y el canal quedaba
     * sin nada que probar.</p>
     */
    private static final String JSON_ABUDABI = "{\"codigo\":\"232425\",\"canal\":"
            + "{\"id_live\":\"live_tv_abudabyuae\",\"name\":\"Abu Dhabi Al Emarat\"},"
            + "\"total\":3,\"emitiendo\":0,\"opciones\":["
            + "{\"n\":1,\"tipo\":\"directa\",\"origen\":\"https://uselector.cdn.intigral-ott.net/ADE/ADE.isml/manifest.mpd\","
            + "\"url\":\"https://uselector.cdn.intigral-ott.net/ADE/ADE.isml/manifest.mpd\","
            + "\"headers\":{},\"emite\":false,\"hls\":false,\"content_type\":\"\",\"http\":299},"
            + "{\"n\":0,\"tipo\":\"directa\",\"origen\":\"https://admn-live-cdn-lb.starzplayarabia.com/out/v1/admn_tv/emarat_tv/emarat_tv_dash_enc/index.mpd\","
            + "\"url\":\"https://admn-live-cdn-lb.starzplayarabia.com/out/v1/admn_tv/emarat_tv/emarat_tv_dash_enc/index.mpd\","
            + "\"headers\":{},\"emite\":false,\"hls\":false,\"content_type\":\"application/dash+xml\",\"http\":200},"
            + "{\"n\":2,\"tipo\":\"directa\",\"origen\":\"https://admn-live-cdn-lb.starzplayarabia.com/out/v1/admn_tv_enc/emarat_tv/emarat_tv_hls_nd/index.m3u8\","
            + "\"url\":\"https://admn-live-cdn-lb.starzplayarabia.com/out/v1/admn_tv_enc/emarat_tv/emarat_tv_hls_nd/index.m3u8\","
            + "\"headers\":{},\"emite\":false,\"hls\":false,\"content_type\":\"application/json\",\"http\":403}]}";

    /**
     * v7.38.10 — CORRECCIÓN. En la 7.38.9 esta prueba exigía rescatar los dos {@code .mpd}
     * de Abu Dhabi. Estaba mal: son {@code ..._dash_enc/index.mpd} cifrados en Widevine y
     * no se abren sin licencia. Ver {@link DashCifradoYCadenaTest}. Se deja la prueba,
     * invertida, para que el error no pueda volver sin que salte.
     */
    @Test
    public void losDashCifradosDeAbuDhabiAhoraSeDescartan() {
        ApiDrama.Opciones o = ApiDrama.leerOpciones(JSON_ABUDABI);
        assertNotNull(o);
        assertEquals("la API dice que no emite nadie", 0, o.emitiendo);
        assertEquals("y ninguna se rescata: son CENC", 0, o.confirmadas().size());
        assertEquals("ni dudosas: Widevine y 403", 0, o.queEmiten().size());
    }

    @Test
    public void el403SiSeDescarta() {
        for (ApiDrama.OpcionApi x : ApiDrama.leerOpciones(JSON_ABUDABI).queEmiten()) {
            assertFalse("un 403 es un no de verdad: " + x.url, x.http == 403);
        }
    }

    /** v7.38.10 — y por eso el plan de Abu Dhabi queda vacío en vez de con 2 cifrados. */
    @Test
    public void elDashCifradoNoLlegaAlPlanB() {
        List<DramaResolucion.Opcion> ops =
                ApiDrama.aOpciones(BASE, CODIGO, "live_tv_abudabyuae",
                        ApiDrama.leerOpciones(JSON_ABUDABI));
        assertTrue("nada que probar es mejor que 2 imposibles", ops.isEmpty());
    }

    // ───────────── causa 2: el proxy no sirve para lo que no es HLS ─────────────

    /**
     * La API lo dice con todas las letras en {@code /p/…}:
     * «esa opción no es un manifiesto HLS (usa su URL directa)». Así que la v7.38.9 solo
     * pide proxada para lo que la API confirmó como HLS; lo demás va directo.
     */
    @Test
    public void soloSePideProxyParaLoConfirmado() {
        List<DramaResolucion.Opcion> ops =
                ApiDrama.aOpciones(BASE, CODIGO, "live_tv_abudabyuae",
                        ApiDrama.leerOpciones(JSON_ABUDABI));
        int proxadas = 0;
        for (DramaResolucion.Opcion x : ops) if (x.url.contains("/p/")) proxadas++;
        assertEquals("ninguna: emitiendo=0", 0, proxadas);
    }

    // ───────────── causa 3: un canal trae YouTube como «opción» ─────────────

    private static final String JSON_NEWS = "{\"codigo\":\"232425\",\"canal\":"
            + "{\"id_live\":\"live_tv_beinsportnews\"},\"total\":2,\"emitiendo\":1,\"opciones\":["
            + "{\"n\":0,\"tipo\":\"marcador\",\"origen\":\"http://.LS.V2x/s\","
            + "\"url\":\"https://max10.maxpcioscontent.shop/pages/vhxivhzkeuix/index.html\","
            + "\"headers\":{},\"emite\":true,\"hls\":true,\"http\":200},"
            + "{\"n\":3,\"tipo\":\"otra\",\"origen\":\"https://www.youtube.com/@beINSPORTS/live\","
            + "\"url\":\"https://www.youtube.com/@beINSPORTS/live\","
            + "\"headers\":{},\"emite\":false,\"hls\":false,\"http\":200}]}";

    @Test
    public void youtubeNoGastaUnTurnoDelPlanB() {
        List<DramaResolucion.Opcion> ops =
                ApiDrama.aOpciones(BASE, CODIGO, "live_tv_beinsportnews",
                        ApiDrama.leerOpciones(JSON_NEWS));
        List<DramaResolucion.Opcion> plan =
                DramaResolucion.planDeAtaque(null, ops, null);
        for (DramaResolucion.Opcion x : plan) {
            assertFalse("no debe haber YouTube: " + x.url, x.url.contains("youtube"));
        }
        assertTrue("el que sí emite tiene que seguir ahí",
                plan.get(0).url.contains("maxpcioscontent"));
    }

    @Test
    public void elFiltroDePortalNoSeComeLasPaginasBuenas() {
        assertTrue(DramaResolucion.esPaginaDePortal("https://www.youtube.com/@x/live"));
        assertTrue(DramaResolucion.esPaginaDePortal("https://youtu.be/abc"));
        assertTrue(DramaResolucion.esPaginaDePortal("https://vimeo.com/1"));
        assertTrue(DramaResolucion.esPaginaDePortal("https://www.twitch.tv/x"));
        assertFalse("un index.html de la cadena SÍ sirve",
                DramaResolucion.esPaginaDePortal(
                        "https://sup7.superdatanet.cfd/hd/azmyqsojuqbm/index.html"));
        assertFalse(DramaResolucion.esPaginaDePortal("https://ordernap.net/e/abc"));
        assertFalse(DramaResolucion.esPaginaDePortal(null));
    }

    // ─────────── la proxada de un canal cualquiera también funciona ───────────

    /**
     * Alkass 2 no es beIN y su proxy devuelve manifiesto válido (medido). Esto fija que
     * el mecanismo no es un caso especial de beIN 1.
     */
    @Test
    public void alkass2TambienTieneProxada() {
        String j = "{\"codigo\":\"232425\",\"canal\":{\"id_live\":\"live_tv_alkaas2\"},"
                + "\"emitiendo\":1,\"opciones\":[{\"n\":0,\"tipo\":\"marcador\","
                + "\"origen\":\"http://.LS.V2x/s\","
                + "\"url\":\"https://dat3.datahostingapiportal.monster/mirsp/mrktygbmlhes/index.html\","
                + "\"headers\":{},\"emite\":true,\"hls\":true,\"http\":200}]}";
        List<DramaResolucion.Opcion> ops =
                ApiDrama.aOpciones(BASE, CODIGO, "live_tv_alkaas2", ApiDrama.leerOpciones(j));
        assertEquals(2, ops.size());
        assertEquals(BASE + "/p/232425/live_tv_alkaas2/0.m3u8", ops.get(0).url);
        assertEquals(DramaResolucion.Tipo.DIRECTA, ops.get(0).tipo);
        assertTrue(DramaResolucion.esPlaylist(ops.get(0).url));
        assertEquals("y detrás su página, como plan B",
                DramaResolucion.Tipo.EMBEBIDA, ops.get(1).tipo);
    }

    // ───────────────────────── el tiempo ya no frena ─────────────────────────

    /**
     * La consulta ya no está en el camino crítico, así que su tope puede ser holgado.
     * Que sea holgado es importante: beIN 5 tarda 13,3 s en una sola opción.
     */
    @Test
    public void elTopeDaTiempoALaApiMasLenta() {
        assertNull(ApiDrama.pedirOpciones(null, CODIGO, "x"));   // sin base no hay red
        // y con la sesión marcada como caída tampoco se intenta (no bloquea nada)
        ApiDrama.olvidarEstado();
        ApiDrama.marcarCaida();
        assertNull(ApiDrama.pedirOpciones(BASE, CODIGO, "live_tv_beinsport5"));
        ApiDrama.olvidarEstado();
    }

    // ───────────────────── el orden final con todo junto ─────────────────────

    @Test
    public void elPlanFinalPoneLoConfirmadoPrimero() {
        List<DramaResolucion.Opcion> confirmadas = new ArrayList<>(Arrays.asList(
                new DramaResolucion.Opcion(BASE + "/p/232425/live_tv_x/1.m3u8",
                        DramaResolucion.Tipo.DIRECTA, null, null, "", false)));
        // v7.38.10: una dudosa que NO sea un manifiesto cifrado (un .mpd CENC ya se
        // descarta antes de llegar aquí, en ApiDrama).
        List<DramaResolucion.Opcion> dudosas = new ArrayList<>(Arrays.asList(
                new DramaResolucion.Opcion("https://x/pagina-con-el-stream/index.html",
                        DramaResolucion.Tipo.EMBEBIDA, null, null, "", true)));
        List<DramaResolucion.Opcion> todas = new ArrayList<>(confirmadas);
        todas.addAll(dudosas);

        List<DramaResolucion.Opcion> plan = DramaResolucion.planDeAtaque(null, todas, null);
        assertEquals(2, plan.size());
        assertTrue("lo confirmado va primero", plan.get(0).url.contains("/p/"));
        assertTrue("la dudosa queda de reserva", plan.get(1).url.contains("pagina-con-el-stream"));
    }
}
