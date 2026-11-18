package dza.fut.paipo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * v7.37.17 — los tres fallos que destapó el log del móvil del usuario
 * (17-nov-2026, 14:05) con beIN Sports de Drama:
 *
 *  1. beIN Fr1: el "directo" del backup contesta 200 y ExoPlayer reproduce… una
 *     TARJETA en ruso «ОТСУТСТВУЕТ ПОДПИСКА» (no hay suscripción). El usuario:
 *     «nada no es el canal sale solo imagen en ruso».
 *  2. beIN 1: el plan B cogía la PRIMERA opción no-marcador, que era el mismo
 *     m3u8 directo que acababa de dar 403 → se "enmarcaba" un m3u8 y no se veía
 *     nada, teniendo el embed bueno dos líneas más abajo en la lista.
 *  3. El log de Fr1: 302 en bucle (http → http:8222 → https:8443).
 */
public class AvisoSinCanalTest {

    /** Un playlist de mentira con la tarjeta rusa en el comentario y un .ts real. */
    private static final String CARTEL_RUSO =
            "#EXTM3U\n#EXT-X-VERSION:3\n# \u041e\u0422\u0421\u0423\u0422\u0421\u0422\u0412\u0423\u0415\u0422 \u041f\u041e\u0414\u041f\u0418\u0421\u041a\u0410\n"
            + "#EXT-X-TARGETDURATION:11\n#EXTINF:10.000,\n2026/11/17/19/09/18-07167.ts\n";

    private static final String CANAL_DE_VERDAD =
            "#EXTM3U\n#EXT-X-PROGRAM-DATE-TIME:2026-11-17T19:08:50Z\n"
            + "#EXT-X-TARGETDURATION:11\n#EXTINF:10.000,\n2026/11/17/19/09/18-07167.ts\n";

    @Test
    public void reconoceLaTarjetaDeNoHaySuscripcion() {
        assertTrue("cartel", DramaSesion.esAvisoSinCanal(CARTEL_RUSO));
        assertTrue(DramaSesion.esAvisoSinCanal("\u041e\u0422\u0421\u0423\u0422\u0421\u0422\u0412\u0423\u0415\u0422 \u041f\u041e\u0414\u041f\u0418\u0421\u041a\u0410"));
        assertTrue("ruso en mayusculas", DramaSesion.esAvisoSinCanal(
                "\u041e\u0422\u0421\u0423\u0422\u0421\u0422\u0412\u0423\u0415\u0422 \u041f\u041e\u0414\u041f\u0418\u0421\u041a\u0410"));
        assertTrue("ingles", DramaSesion.esAvisoSinCanal("no subscription"));
        assertTrue("espanol", DramaSesion.esAvisoSinCanal("SIN SUSCRIPCIÓN"));
        assertFalse(DramaSesion.esAvisoSinCanal(CANAL_DE_VERDAD));
        assertFalse(DramaSesion.esAvisoSinCanal("#EXTM3U"));
        assertFalse(DramaSesion.esAvisoSinCanal(null));
        assertFalse(DramaSesion.esAvisoSinCanal(""));
    }

    @Test
    public void prefiereElEmbedAunqueHayaUnaDirectaQuePareceBuena() {
        // las opciones REALES que el log del móvil mostró para beIN 1
        String ops = "[{\"n\":\"SD\",\"u\":\"http://.LS.V2live_tv_custom_handler_live_tv_beinsport1_description_SD_/s\"},"
                + "{\"n\":\"HD\",\"u\":\"http://.LS.V2live_tv_custom_handler_live_tv_beinsport1_description_HD_/s\"},"
                + "{\"n\":\"OTRA\",\"u\":\"https://ordernap.net/e/z7iiiknxnuqjk\"},"
                + "{\"n\":\"OTRA\",\"u\":\"https://quotarevival.net/e/yxtmusy4xj8tj\"},"
                + "{\"n\":\"OTRA\",\"u\":\"http://primetv.cc.cd/beinfhd11/tracks-v1a1/mono.tbin.m3u8\"}]";
        String emb = DramaSesion.primeraEmbebida(ops);
        assertEquals("https://ordernap.net/e/z7iiiknxnuqjk", emb);
        // y "primera opción no marcador" devuelve la PRIMERA que no sea .LS., que
        // en la lista real es una directa (la que da 403): por eso el plan B tiene
        // que buscar el embed aparte y no conformarse con esa.
        assertEquals("https://ordernap.net/e/z7iiiknxnuqjk",
                DramaSesion.primeraOpcionNoMarcador(ops));
    }

    @Test
    public void primeraEmbebidaSoloDevuelveLasQueEmiten() {
        // v7.37.18: quotarevival.net esta muerto («NOT ALLOWED · domain protected»,
        // medido 17-nov con UA de movil dentro de iframe) y ya no se ofrece.
        assertNull(DramaSesion.primeraEmbebida(
                "[{\"n\":\"SD\",\"u\":\"http://.LS.V2x/s\"},"
                + "{\"n\":\"OTRA\",\"u\":\"https://quotarevival.net/e/yxtmusy4xj8tj\"}]"));
        assertEquals("https://ordernap.net/e/z7iiiknxnuqjk",
                DramaSesion.primeraEmbebida(
                        "[{\"n\":\"OTRA\",\"u\":\"https://quotarevival.net/e/yxtmusy4xj8tj\"},"
                        + "{\"n\":\"OTRA\",\"u\":\"https://ordernap.net/e/z7iiiknxnuqjk\"}]"));
        assertNull(DramaSesion.primeraEmbebida(null));
        assertNull(DramaSesion.primeraEmbebida(""));
        assertNull(DramaSesion.primeraEmbebida("no es json"));
        assertNull(DramaSesion.primeraEmbebida("[{\"n\":\"SD\",\"u\":\"http://.LS.V2x/s\"}]"));
    }

    @Test
    public void cuentaSoloLasOpcionesUtiles() {
        String ops = "[{\"n\":\"SD\",\"u\":\"http://.LS.V2x/s\"},"
                + "{\"n\":\"OTRA\",\"u\":\"https://ordernap.net/e/z7iiiknxnuqjk\"},"
                + "{\"n\":\"OTRA\",\"u\":\"http://primetv.cc.cd/beinfhd11/tracks-v1a1/mono.tbin.m3u8\"}]";
        // el marcador .LS. no cuenta; el embed y la directa sí
        assertEquals(2, DramaSesion.opcionesUtiles(ops));
        assertEquals(0, DramaSesion.opcionesUtiles(null));
        assertEquals(0, DramaSesion.opcionesUtiles("[{\"n\":\"SD\",\"u\":\"http://.LS.V2x/s\"}]"));
    }
}
