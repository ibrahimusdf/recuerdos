package dza.fut.paipo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * v7.37.16 — el plan B que sí reproduce beIN Sports (Drama).
 *
 * Medido 2026-11-17 con Chromium real:
 *   · el embed de ordernap.net/quotarevival.net cargado DIRECTO (window.top == self)
 *     salta a la landing del proveedor y nunca da playlist;
 *   · el MISMO embed dentro de un iframe devuelve HLS 200 y vídeo 1280x720
 *     (beIN Sports 1, 2, 5, 6 y News).
 *   · los .ts de su CDN exigen el MISMO User-Agent que pidió el m3u8.
 *
 * Aquí se prueba lo que es puro: qué enlaces hay que enmarcar y cómo es el marco.
 */
public class EmbedEnLaVentanaTest {

    @Test
    public void reconoceLosEmbedsQueExigenIframe() {
        assertTrue(PlayerActivity.esEmbedQueExigeIframe("https://ordernap.net/e/z7iiiknxnuqjk"));
        assertTrue(PlayerActivity.esEmbedQueExigeIframe("https://quotarevival.net/e/yxtmusy4xj8tj"));
        assertTrue(PlayerActivity.esEmbedQueExigeIframe("https://ORDERNAP.net/e/x"));
        assertFalse(PlayerActivity.esEmbedQueExigeIframe("https://tinslime.net/e/yxtmusy4xj8tj"));
        assertFalse(PlayerActivity.esEmbedQueExigeIframe("https://embed69.org/f/tt0133093/"));
        assertFalse(PlayerActivity.esEmbedQueExigeIframe(null));
        assertFalse(PlayerActivity.esEmbedQueExigeIframe(""));
    }

    @Test
    public void elMarcoMeteElEmbedEnUnIframeYNoEnLaRaiz() {
        String html = PlayerActivity.htmlMarcoParaEmbed("https://ordernap.net/e/z7iiiknxnuqjk");
        assertNotNull(html);
        // lo esencial: el embed viaja dentro de <iframe src="...">
        assertTrue(html.contains("<iframe src=\"https://ordernap.net/e/z7iiiknxnuqjk\""));
        assertTrue(html.contains("allow=\"autoplay"));
        // fondo negro y sin márgenes: el vídeo llena la ventana del reproductor
        assertTrue(html.contains("background:#000"));
        assertTrue(html.contains("margin:0"));
    }

    @Test
    public void elMarcoEscapaLoQuePuedaRomperElHtml() {
        String html = PlayerActivity.htmlMarcoParaEmbed("https://x.net/e/a?b=1&c=2\"onload=x");
        assertNotNull(html);
        assertTrue(html.contains("&amp;c=2"));
        assertFalse(html.contains("\"onload=x"));
    }

    @Test
    public void elMarcoNoSeInventaUnHtmlSinEnlace() {
        assertEquals(null, PlayerActivity.htmlMarcoParaEmbed(null));
        assertEquals(null, PlayerActivity.htmlMarcoParaEmbed("   "));
    }

    /** v7.37.18 — el UA del marco paso a ser de MOVIL: con el de escritorio
     *  ordernap contesta 403 (medido). El detalle fino esta en MarcoDelEmbedTest. */
    @Test
    public void elUaDelMarcoEsElDeMovil() {
        String ua = PlayerActivity.uaDelMarco("Mozilla/5.0 (Windows NT 10.0) Chrome/124.0.0.0");
        org.junit.Assert.assertTrue(ua, ua.contains("Android"));
        org.junit.Assert.assertFalse(ua, ua.contains("Windows"));
    }
}
