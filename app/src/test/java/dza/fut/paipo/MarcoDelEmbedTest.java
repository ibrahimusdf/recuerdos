package dza.fut.paipo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * v7.37.18 — el marco del embed, medido contra el embed de verdad.
 *
 * Medido 2026-11-17 20:2x en Chromium, iframe 1280x720:
 *   ordernap.net/e/z7iiiknxnuqjk
 *     UA escritorio Windows Chrome/131 -> 403 nginx (página en blanco)
 *     UA Android Chrome/131           -> 200 y beIN Sports 1 EN VIVO
 *   quotarevival.net/e/yxtmusy4xj8tj
 *     UA móvil                        -> «NOT ALLOWED · domain protected»
 */
public class MarcoDelEmbedTest {

    private static final String EMBED = "https://ordernap.net/e/z7iiiknxnuqjk";

    @Test
    public void elUaDelMarcoEsDeMovil() {
        String ua = PlayerActivity.UA_DEL_EMBED;
        assertTrue(ua, ua.contains("Android"));
        assertTrue(ua, ua.contains("Mobile"));
        assertTrue("Chrome al día (el embed mira la versión)", ua.contains("Chrome/131"));
        assertFalse("un UA de escritorio da 403 en ordernap", ua.contains("Windows NT"));
        // el método que usa el WebView devuelve ESE, aunque le pasen el de escritorio
        assertEquals(ua, PlayerActivity.uaDelMarco(StreamResolver.DESKTOP_USER_AGENT));
        assertEquals(ua, PlayerActivity.uaDelMarco(null));
    }

    @Test
    public void elMarcoMeteElEmbedEnUnIframe() {
        String html = PlayerActivity.htmlMarcoSinEstorbos(EMBED);
        assertNotNull(html);
        assertTrue("tiene que ir dentro de un iframe o el embed se va a la landing",
                html.contains("<iframe src=\"" + EMBED + "\""));
        assertTrue(html.contains("allow=\"autoplay"));
        assertTrue("fondo negro", html.contains("background:#000"));
        // sin puente no se inventa JS de aviso
        assertFalse(html.contains("videoVivo"));
        assertNull(PlayerActivity.htmlMarcoSinEstorbos(null));
        assertNull(PlayerActivity.htmlMarcoSinEstorbos("  "));
    }

    @Test
    public void elMarcoAvisaCuandoElVideoAvanzaYLeDaAlPlay() {
        String html = PlayerActivity.htmlMarcoSinEstorbos(EMBED, "AppPuente");
        assertTrue("tiene que llamar al puente", html.contains("window.AppPuente.videoVivo(t)"));
        assertTrue("sólo cuando el vídeo lleva más de 1 s", html.contains("if(t>1&amp;&amp;!avisado)")
                || html.contains("if(t>1&&!avisado)"));
        assertTrue("un solo aviso", html.contains("avisado=false"));
        assertTrue("si se queda parado se le da al play", html.contains("v.play()"));
    }

    @Test
    public void losEmbedsMuertosSeSaltan() {
        // quotarevival pinta «domain protected»: no es un embed, es un cartel
        assertTrue(DramaSesion.esEmbedMuerto("https://quotarevival.net/e/yxtmusy4xj8tj"));
        assertFalse(DramaSesion.esEmbedMuerto(EMBED));
        assertFalse(DramaSesion.esEmbedMuerto(null));

        String ops = "[{\"n\":\"OTRA\",\"u\":\"https://quotarevival.net/e/yxtmusy4xj8tj\"},"
                + "{\"n\":\"OTRA\",\"u\":\"https://ordernap.net/e/z7iiiknxnuqjk\"}]";
        assertEquals("se prefiere el único que emite",
                EMBED, DramaSesion.primeraEmbebida(ops));
        assertNull(DramaSesion.siguienteEmbebida(ops, EMBED));
        // y si sólo queda el muerto, no se ofrece
        assertEquals(null, DramaSesion.primeraEmbebida(
                "[{\"n\":\"OTRA\",\"u\":\"https://quotarevival.net/e/yxtmusy4xj8tj\"}]"));
    }

    @Test
    public void siguienteEmbebidaSaltaLaQueYaFallo() {
        String ops = "[{\"n\":\"OTRA\",\"u\":\"https://ordernap.net/e/uno\"},"
                + "{\"n\":\"OTRA\",\"u\":\"https://ordernap.net/e/dos\"}]";
        assertEquals("https://ordernap.net/e/uno", DramaSesion.primeraEmbebida(ops));
        assertEquals("https://ordernap.net/e/dos",
                DramaSesion.siguienteEmbebida(ops, "https://ordernap.net/e/uno"));
        assertNull(DramaSesion.siguienteEmbebida(ops, "https://ordernap.net/e/dos"));
        assertNull(DramaSesion.siguienteEmbebida(null, "x"));
    }
}
