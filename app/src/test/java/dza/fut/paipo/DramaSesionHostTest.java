package dza.fut.paipo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonObject;

import org.junit.Test;

/**
 * v7.37.15 — lo MEDIDO contra el host vivo y en el MOVIL del usuario el 2026-11-16.
 *
 * <p>Las respuestas de abajo son las REALES que devolvió
 * {@code https://main.fastconnections.online/api/live/livedrama/v13.0.0/glasbi}
 * al POST con la plantilla glt (HTTP 201), descifradas con AES-128-CBC y la clave
 * {@code 43fc9b1a3f854049}.
 *
 * <p><b>Las tres formas que da un canal Drama:</b>
 * <pre>
 *  1. m3u8 DIRECTO en live.url   -> el reproductor lo toca. 14 de 40 medidos.
 *  2. MARCADOR .LS. en live.url  -> solo lo resuelve el glbr, que está MUERTO (404).
 *                                    Su backup puede traer m3u8 directo.
 *  3. EMBEBIDO en el backup      -> ordernap.net/e/… con Cloudflare Turnstile.
 *                                    El m3u8 lo fabrica el JS del navegador.
 * </pre>
 */
public class DramaSesionHostTest {

    private static final String CANAL = "live_tv_beinsport1";

    /** Forma (2): beIN Sports 1 — marcador + backup CON m3u8 directo. REAL. */
    private static final String GLASBI_BEIN1 = "{\"result\":0,\"message\":{\"en\":\"operation succeeded\"},"
            + "\"live\":{\"type\":\"tv\",\"id_live\":\"live_tv_beinsport1\",\"name\":\"Bein S. 1\","
            + "\"url\":\"http:\\/\\/.LS.V2live_tv_custom_handler_live_tv_beinsport1_description_SD_\\/s\","
            + "\"agent\":\"redirect\","
            + "\"backup\":\"http:\\/\\/.LS.V2live_tv_custom_handler_live_tv_beinsport1_description_HD_\\/s -- redirect -;- "
            + "http:\\/\\/.LS.V2live_tv_custom_handler_live_tv_beinsport1_description_4K_\\/s -- redirect -;- "
            + "{\\\"url\\\":\\\"https:\\/\\/ordernap.net\\/e\\/z7iiiknxnuqjk\\\",\\\"agent\\\":\\\"Mozilla\\/5.0 (Macintosh)\\\","
            + "\\\"iframe\\\":\\\"https:\\/\\/ordernap.net\\/e\\/z7iiiknxnuqjk\\\"} -- double_redirect -;- "
            + "http:\\/\\/.LS.V2LOAD_BALANCERlive_tv_beinsport1\\/s -- redirect -;- "
            + "http:\\/\\/.LS.V2daddy_91\\/s -- redirect -;- "
            + "http:\\/\\/primetv.cc.cd\\/beinfhd11\\/tracks-v1a1\\/mono.tbin.m3u8 -- Mozilla -;- "
            + "http:\\/\\/.LS.V2daddy_91\\/s -- redirect -;- \","
            + "\"img_url\":\"http:\\/\\/logo.twoapistack.work\\/img\\/channels\\/live_tv_beinsport1.png\"}}";

    /** Forma (2) peor: beIN News — marcador y backup SIN ningún m3u8 directo. REAL. */
    private static final String GLASBI_BEINNEWS = "{\"result\":0,\"live\":{\"id_live\":"
            + "\"live_tv_beinsportnews\",\"name\":\"Bein S. News\","
            + "\"url\":\"http:\\/\\/.LS.V2live_tv_custom_handler_live_tv_beinsportnews_description_DL_\\/s\","
            + "\"agent\":\"redirect\","
            + "\"backup\":\"{\\\"url\\\":\\\"https:\\/\\/ordernap.net\\/e\\/b11wbtzmon53m\\\","
            + "\\\"agent\\\":\\\"Mozilla\\/5.0 (Macintosh)\\\",\\\"iframe\\\":"
            + "\\\"https:\\/\\/ordernap.net\\/e\\/b11wbtzmon53m\\\"} -- double_redirect -;- "
            + "{\\\"url\\\":\\\"https:\\/\\/quotarevival.net\\/e\\/vg4he6f2wmrj3\\\","
            + "\\\"agent\\\":\\\"Mozilla\\/5.0 (Macintosh)\\\"} -- double_redirect -;- "
            + "{\\\"url\\\":\\\"https:\\/\\/www.youtube.com\\/@beINSPORTS\\/live\\\","
            + "\\\"agent\\\":\\\"Mozilla\\/5.0 (Macintosh)\\\"} -- double_redirect -;- \"}}";

    /** Forma (1): live.url ya es el m3u8. */
    private static final String GLASBI_FORMA_B = "{\"result\":0,\"live\":{\"id_live\":\"x\","
            + "\"url\":\"http:\\/\\/stream.mcquack.net\\/30\\/index.m3u8\",\"agent\":\"Mozilla\"}}";

    /** Sesión ENVENENADA: la que saca una instalación reciente. */
    private static final String SESION_ENVENENADA =
            "{\"base\":\"https://main.fastconnections.online/api/live/livedrama/v13.0.0/\","
            + "\"url\":\"https://li.highperformanceconnect.shop/api/live/livedrama/v13.0.0/glt\","
            + "\"urlGlbr\":\"https://re.highperformanceconnect.shop/redirect/glbr\","
            + "\"wire\":\"ctiv\","
            + "\"plantillas\":{\"glt\":{\"user_id\":\"u1\"},\"glbrReq\":{\"user_id\":\"u1\"}}}";

    private static com.google.gson.JsonObject liveDe(String json) {
        return com.google.gson.JsonParser.parseString(json)
                .getAsJsonObject().getAsJsonObject("live");
    }

    // ─────────────── forma (2): el backup tiene m3u8 (medido en el móvil) ───────────────

    @Test
    public void laRespuestaRealDaElM3u8DirectoDelBackupYNoElMarcador() {
        String[] vg = DramaSesion.vivoDesdeGlasbi(GLASBI_BEIN1);
        assertNotNull(vg);
        assertEquals("http://primetv.cc.cd/beinfhd11/tracks-v1a1/mono.tbin.m3u8", vg[0]);
        assertEquals("Mozilla", vg[1]);
        assertTrue(DramaSesion.siYaEsStream(vg[0]));
    }

    @Test
    public void elMarcadorNuncaSeDevuelveComoUrlPrincipal() {
        String[] vg = DramaSesion.vivoDesdeGlasbi(GLASBI_BEIN1);
        assertNotNull(vg);
        assertFalse(vg[0].contains(".LS."));
    }

    @Test
    public void lasOpcionesDelChipSiguenSaliendo() {
        com.google.gson.JsonArray ops = DramaSesion.opcionesDeLive(liveDe(GLASBI_BEIN1));
        assertTrue("el chip saldria vacio", ops.size() >= 5);
        boolean estaElDirecto = false;
        for (com.google.gson.JsonElement e : ops)
            if (e.getAsJsonObject().get("u").getAsString().contains("primetv.cc.cd"))
                estaElDirecto = true;
        assertTrue("el servidor directo no estaba entre las opciones", estaElDirecto);
    }

    // ─────────── forma (2) peor: beIN News, sin directa (medido en el móvil) ───────────

    @Test
    public void beINNewsSeQuedaEnElMarcadorPorqueSuBackupNoTraeM3u8() {
        String[] vg = DramaSesion.vivoDesdeGlasbi(GLASBI_BEINNEWS);
        assertNotNull(vg);
        assertTrue(vg[0].contains(".LS."));
        assertFalse(DramaSesion.siYaEsStream(vg[0]));
        assertNull(DramaSesion.directaDeBackup(liveDe(GLASBI_BEINNEWS)));
    }

    @Test
    public void beINNewsTieneOpcionesNoMarcadorParaElPlanB() {
        // REGRESIÓN que el log del móvil destapó: las opciones se perdían y el plan B
        // no se ejecutaba. Con el separado de "URL -- AGENTE ya salen los 3 embebidos.
        String ops = DramaSesion.opcionesDeLive(liveDe(GLASBI_BEINNEWS)).toString();
        String embebida = DramaSesion.primeraOpcionNoMarcador(ops);
        assertNotNull("se perdian las opciones del backup", embebida);
        assertTrue("debia salir el embebido de ordernap: " + embebida,
                embebida.startsWith("https://ordernap.net/e/"));
    }

    @Test
    public void elEmbebidoNoSeConfundeConUnStream() {
        String ops = DramaSesion.opcionesDeLive(liveDe(GLASBI_BEINNEWS)).toString();
        String embebida = DramaSesion.primeraOpcionNoMarcador(ops);
        assertNotNull(embebida);
        assertFalse("un embebido no es un m3u8", DramaSesion.siYaEsStream(embebida));
    }

    @Test
    public void elChipDeBeINNewsYaNoSaleVacio() {
        // el backup de beIN News son tres embebidos; antes opcionesDeLive no podía
        // desempaquetarlos y el chip se quedaba en cero entradas.
        com.google.gson.JsonArray ops = DramaSesion.opcionesDeLive(liveDe(GLASBI_BEINNEWS));
        assertTrue("el chip saldria vacio", ops.size() >= 4);
    }

    // ─────────────────────────── forma (1): directa ───────────────────────────

    @Test
    public void formaBLaUrlYaEsElStream() {
        String[] vg = DramaSesion.vivoDesdeGlasbi(GLASBI_FORMA_B);
        assertNotNull(vg);
        assertEquals("http://stream.mcquack.net/30/index.m3u8", vg[0]);
        assertTrue(DramaSesion.siYaEsStream(vg[0]));
    }

    // ─────────────────────── directaDeBackup / primeraOpcion ───────────────────────

    @Test
    public void directaDeBackupPrefiereElPrimerM3u8() {
        String[] d = DramaSesion.directaDeBackup(liveDe(GLASBI_BEIN1));
        assertNotNull(d);
        assertTrue(d[0].contains("m3u8"));
        assertEquals("Mozilla", d[1]);
    }

    @Test
    public void directaDeBackupSaltaLasOpcionesEmbebidas() {
        JsonObject live = new JsonObject();
        live.addProperty("backup", "{\"url\":\"https://ordernap.net/e/z7iiiknxnuqjk\","
                + "\"agent\":\"Mozilla\"} -- double_redirect -;- "
                + "{\"url\":\"https://quotarevival.net/e/yxtmusy4xj8tj\","
                + "\"agent\":\"Mozilla\"} -- double_redirect -;- ");
        assertNull(DramaSesion.directaDeBackup(live));
    }

    @Test
    public void directaDeBackupAceptaMp4YMpd() {
        for (String u : new String[]{
                "https://x.net/a/b.mp4", "https://x.net/a/b.mpd", "https://x.net/manifest"}) {
            JsonObject live = new JsonObject();
            live.addProperty("backup", "http://.LS.V2daddy_91/s -- redirect -;- " + u + " -- Mozilla -;- ");
            String[] d = DramaSesion.directaDeBackup(live);
            assertNotNull("no saco la directa de " + u, d);
            assertEquals(u, d[0]);
        }
    }

    @Test
    public void directaDeBackupSinBackupONulo() {
        assertNull(DramaSesion.directaDeBackup(null));
        assertNull(DramaSesion.directaDeBackup(new JsonObject()));
        JsonObject vacio = new JsonObject();
        vacio.addProperty("backup", "");
        assertNull(DramaSesion.directaDeBackup(vacio));
    }

    @Test
    public void primeraOpcionNoMarcadorSaltaTodosLosMarcadores() {
        String ops = "[{\"n\":\"SD\",\"u\":\"http://.LS.V2a_description_SD_/s\"},"
                + "{\"n\":\"HD\",\"u\":\"http://.LS.V2a_description_HD_/s\"}]";
        assertNull(DramaSesion.primeraOpcionNoMarcador(ops));
    }

    @Test
    public void primeraOpcionNoMarcadorDevuelveLaPrimeraUtilizable() {
        String ops = "[{\"n\":\"SD\",\"u\":\"http://.LS.V2a_SD_/s\"},"
                + "{\"n\":\"OTRA\",\"u\":\"https://ordernap.net/e/abc\"},"
                + "{\"n\":\"OTRA\",\"u\":\"https://quotarevival.net/e/def\"}]";
        assertEquals("https://ordernap.net/e/abc", DramaSesion.primeraOpcionNoMarcador(ops));
    }

    @Test
    public void primeraOpcionNoMarcadorConBasura() {
        assertNull(DramaSesion.primeraOpcionNoMarcador(null));
        assertNull(DramaSesion.primeraOpcionNoMarcador(""));
        assertNull(DramaSesion.primeraOpcionNoMarcador("no es json"));
        assertNull(DramaSesion.primeraOpcionNoMarcador("{}"));
        assertNull(DramaSesion.primeraOpcionNoMarcador("[1,2,3]"));
    }

    // ─────────────────────────── siYaEsStream ───────────────────────────

    @Test
    public void siYaEsStreamReconoceLosFormatosYRechazaElResto() {
        for (String u : new String[]{"http://a/b.m3u8", "https://a/b.m3u8?token=1-2-3",
                "http://a/b.mp4", "http://a/b.mpd", "http://a/x/stream.ts",
                "http://a/b.mkv", "http://a/b.flv", "http://a/manifest"})
            assertTrue(u, DramaSesion.siYaEsStream(u));
        for (String u : new String[]{"http://.LS.V2daddy_91/s", "https://ordernap.net/e/z7iiiknxnuqjk",
                "http://primetv.cc.cd/beinfhd11", "https://web.dramalive.cc/", "", null})
            assertFalse(String.valueOf(u), DramaSesion.siYaEsStream(u));
    }

    // ─────────────────────────── urlSana ───────────────────────────

    @Test
    public void urlSanaMandaLosHostsCaidosAlVivo() {
        assertEquals("https://main.fastconnections.online/api/live/livedrama/v13.0.0/glbr",
                DramaSesion.urlSana("https://re.highperformanceconnect.shop/redirect/glbr"));
        assertEquals("https://main.fastconnections.online/api/live/livedrama/v13.0.0/glasbi",
                DramaSesion.urlSana(
                        "https://li.highperformanceconnect.shop/api/live/livedrama/v13.0.0/glasbi"));
    }

    @Test
    public void urlSanaArreglaLaRutaDelRedirector() {
        assertEquals("https://main.fastconnections.online/api/live/livedrama/v13.0.0/glbr",
                DramaSesion.urlSana("https://main.fastconnections.online/redirect/glbr"));
    }

    @Test
    public void urlSanaNoTocaLoQueYaEstaSano() {
        assertEquals("http://primetv.cc.cd/beinfhd11/tracks-v1a1/mono.tbin.m3u8",
                DramaSesion.urlSana("http://primetv.cc.cd/beinfhd11/tracks-v1a1/mono.tbin.m3u8"));
        assertEquals("", DramaSesion.urlSana(null));
    }

    @Test
    public void laSesionEnvenenadaSeResuelveContraElHostVivo() {
        String glbr = DramaSesion.urlGlbrDe(SESION_ENVENENADA);
        String glasbi = DramaSesion.urlGlasbiDe(SESION_ENVENENADA);
        assertFalse(glbr.contains("highperformanceconnect"));
        assertFalse(glasbi.contains("highperformanceconnect"));
        assertTrue(glasbi.startsWith("https://main.fastconnections.online"));
        assertTrue(glasbi.endsWith("/glasbi"));
    }

    // ─────────────────────────── payloadGlasbi ───────────────────────────

    @Test
    public void glasbiNuncaSeMandaConLaPlantillaDelGlbr() {
        String sesConGlbrReq = "{\"plantillas\":{\"glbrReq\":{\"user_id\":\"otro\","
                + "\"token_de_ads\":\"xyz\"}}}";
        JsonObject glt = new JsonObject();
        glt.addProperty("user_id", "1791616726363000_notloggedin.com_dramalive3");
        JsonObject paraGlasbi = DramaSesion.payloadGlasbi(glt, CANAL, "1", "all_streams_redirect");
        JsonObject paraGlbr = DramaSesion.payloadGlbr(sesConGlbrReq, glt, CANAL, "1", "all_streams_redirect");
        assertEquals("1791616726363000_notloggedin.com_dramalive3",
                paraGlasbi.get("user_id").getAsString());
        assertEquals("otro", paraGlbr.get("user_id").getAsString());
        assertFalse(paraGlasbi.has("token_de_ads"));
        assertTrue(paraGlbr.has("token_de_ads"));
    }

    @Test
    public void payloadGlasbiSoloLlevaLaFilaSobreLaPlantillaGlt() {
        JsonObject glt = new JsonObject();
        glt.addProperty("user_id", "u1");
        glt.addProperty("device_id", "d1");
        JsonObject g = DramaSesion.payloadGlasbi(glt, CANAL, "1", "all_streams_redirect");
        assertEquals(5, g.size());
        assertEquals(CANAL, g.get("id").getAsString());
        assertEquals(2, glt.size());
    }

    // ─────────────────────────── rechazos ───────────────────────────

    @Test
    public void vivoDesdeGlasbiRechazaLoQueNoEsUnLive() {
        assertNull(DramaSesion.vivoDesdeGlasbi(null));
        assertNull(DramaSesion.vivoDesdeGlasbi("\t\t"));
        assertNull(DramaSesion.vivoDesdeGlasbi("{\"result\":1}"));
        assertNull(DramaSesion.vivoDesdeGlasbi(
                "{\"result\":0,\"live\":{\"url\":\"sin-marca\",\"agent\":\"\"}}"));
    }
}
