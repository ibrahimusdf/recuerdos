package dza.fut.paipo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * v7.39.4 — el embed encima de un vídeo que sí funciona («línea blanca»).
 *
 * <p>Logcat del usuario, beIN Sports News, 2026-11-18 16:01. Las tres piezas: el embed se
 * pinta 700 ms después de la ganadora de la carrera; la carrera se queda sin candidatas
 * porque el embed ya puso {@code streamReady} (sale «1 opciones a la vez», y esa una es el
 * embed); y el vigilante de 30 s cambia de servidor con el vídeo reproduciendo debajo.</p>
 */
public class EmbedSobreVideoTest {

    // ─────────────────────── 1. quién manda: el vídeo, no el iframe ───────────────────────

    /** Estado mínimo del reproductor con las reglas de la v7.39.4. */
    private static final class Estado {
        boolean imagenConfirmada;
        boolean fuenteNativaEnMarcha;
        boolean embeddedEnVentana;
        boolean ponerDirecta() { fuenteNativaEnMarcha = true; return true; }
        void llegaReady() { imagenConfirmada = true; fuenteNativaEnMarcha = false;
            if (embeddedEnVentana) embeddedEnVentana = false; }
    }

    private static String decideEmbed(Estado e) {
        if (e.imagenConfirmada) return "no-hace-falta";
        if (e.fuenteNativaEnMarcha) return "espera";
        return "pinta";
    }

    @Test public void elEmbedNoSePintaSobreUnaDirectaArrancando() {
        Estado e = new Estado();
        assertTrue(e.ponerDirecta());                    // 16:01:50.273
        assertEquals("espera", decideEmbed(e));          // 16:01:50.963
    }

    @Test public void conImagenElEmbedYaNoHaceFaltaYSeRetira() {
        Estado e = new Estado();
        e.ponerDirecta();
        e.embeddedEnVentana = true;                      // el caso malo: ya estaba puesto
        e.llegaReady();                                  // 16:01:54.387
        assertEquals("no-hace-falta", decideEmbed(e));
        assertFalse("no debe quedar iframe encima", e.embeddedEnVentana);
    }

    @Test public void sinNadaNativoElEmbedSePintaComoSiempre() {
        assertEquals("pinta", decideEmbed(new Estado()));
    }

    @Test public void laEsperaEsFinita() {
        // Si el nativo no arranca en 15 s, el embed pasa: no se cierra ninguna puerta.
        assertEquals(15000L, PlayerActivity.ESPERA_AL_NATIVO_MS);
        Estado e = new Estado();
        e.ponerDirecta();
        e.fuenteNativaEnMarcha = false;                  // el temporizador lo marca así
        assertEquals("pinta", decideEmbed(e));
    }

    // ─────────────────────── 2. la carrera no se queda sin candidatas ───────────────────────

    @Test public void unEmbedNoEsCandidatoDeLaCarrera() {
        // 16:01:51.113 «en carrera: EMBEBIDA DLW -> ordernap.net» … y 1 444 ms después,
        // «descartada (no es manifiesto)».
        assertFalse(CarreraDeOpciones.esCandidata(
                opcion("https://ordernap.net/e/b11wbtzmon53m")));
        assertFalse(CarreraDeOpciones.esCandidata(
                opcion("https://quotarevival.net/e/vg4he6f2wmrj3")));
    }

    @Test public void laProxadaSigueSiendoCandidata() {
        assertTrue(CarreraDeOpciones.esCandidata(opcion(
                "https://dramalive-api-08th.onrender.com/p/232425/live_tv_beinsportnews/0.m3u8")));
    }

    @Test public void elIndexHtmlDeLaCadenaNoSeRompe() {
        // beIN Max 1 abre así: al mismo host se le pide el manifiesto y responde #EXTM3U.
        assertTrue(CarreraDeOpciones.esCandidata(opcion(
                "https://max10.maxwordsandroidios.shop/pages/vhxivhzkeuix/index.html")));
    }

    // ─────────────────────── 3. el aviso no miente ───────────────────────

    @Test public void noSeAvisaDeFalloSiElCanalSeVe() {
        assertTrue(hayFuentePuestaYSonando(true, true, 0));
        assertTrue("el embed con vídeo también cuenta", hayFuentePuestaYSonando(false, true, 3));
    }

    @Test public void sinNadaPuestoSiSeAvisa() {
        assertFalse(hayFuentePuestaYSonando(false, false, 0));
    }

    @Test public void elVigilanteRetiraEnVezDeCambiarDeServidor() {
        // 16:02:20.988: «el embed no arranca ni con 30 s -> siguiente servidor» mientras
        // ExoPlayer llevaba 26 s reproduciendo.
        assertEquals("retirar", queHaceElVigilante(true, 0));
        assertEquals("se-queda", queHaceElVigilante(false, 5));
        assertEquals("siguiente", queHaceElVigilante(false, 0));
    }

    private static String queHaceElVigilante(boolean imagenConfirmada, int peticionesDeVideo) {
        if (imagenConfirmada) return "retirar";
        if (peticionesDeVideo > 0) return "se-queda";
        return "siguiente";
    }

    // ─────────────────────── 4. el marco ya no pinta blanco ───────────────────────

    @Test public void elMarcoDelEmbedTieneFondoNegro() {
        String html = PlayerActivity.htmlMarcoSinEstorbos("https://ordernap.net/e/abc");
        assertTrue("fondo negro", html.contains("background:#000"));
        assertTrue("sin borde", html.contains("border:0"));
    }

    @Test public void elHtmlSigueSiendoValidoYConElMarco() {
        String html = PlayerActivity.htmlMarcoSinEstorbos("https://ordernap.net/e/abc", "AppPuente");
        assertTrue(html.contains("<iframe"));
        assertTrue("el puente sigue avisando del vídeo", html.contains("AppPuente"));
        assertTrue(html.endsWith("</body></html>") || html.contains("</body>"));
    }

    // ─────────────────────── utilidades ───────────────────────

    private static boolean hayFuentePuestaYSonando(boolean imagenConfirmada,
                                                   boolean embeddedEnVentana,
                                                   int peticionesDeVideo) {
        return imagenConfirmada || (embeddedEnVentana && peticionesDeVideo > 0);
    }

    private static DramaResolucion.Opcion opcion(String url) {
        return DramaResolucion.directaCon(url, null, null);
    }
}
