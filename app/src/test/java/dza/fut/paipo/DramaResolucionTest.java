package dza.fut.paipo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

/**
 * v7.38.0 — los servidores se cuentan y se ordenan con el backup REAL de beIN 1
 * (copiado tal cual del servidor de Drama el 2026-11-17 21:1x).
 */
public class DramaResolucionTest {

    private static final String EMBED_BEIN1 = "https://ordernap.net/e/z7iiiknxnuqjk";
    private static final String EMBED_MUERTO = "https://quotarevival.net/e/yxtmusy4xj8tj";
    private static final String DIRECTO_PRIMETV =
            "http://primetv.cc.cd/beinfhd11/tracks-v1a1/mono.tbin.m3u8";

    /** live_tv_beinsport1 tal cual lo devolvió glasbi. */
    private static final String URL_BEIN1 =
            "http://.LS.V2live_tv_custom_handler_live_tv_mainevent_description_DL_/s";

    private static final String BACKUP_BEIN1 =
            "http://.LS.V2live_tv_custom_handler_live_tv_beinsport1_description_SD_/s -- redirect"
            + " -;- http://.LS.V2live_tv_custom_handler_live_tv_beinsport1_description_HD_/s -- redirect"
            + " -;- http://.LS.V2live_tv_custom_handler_live_tv_beinsport1_description_4K_/s -- redirect"
            + " -;- {\"url\":\"https:\\/\\/ordernap.net\\/e\\/z7iiiknxnuqjk\","
            + "\"agent\":\"Mozilla\\/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit\\/537.36 "
            + "(KHTML, like Gecko) Chrome\\/156.0.0.0 Safari\\/537.36\","
            + "\"iframe\":\"https:\\/\\/ordernap.net\\/e\\/z7iiiknxnuqjk\","
            + "\"headers\":{\"User-Agent\":\"Mozilla\\/5.0 (Macintosh; Intel Mac OS X 10_15_7) "
            + "AppleWebKit\\/537.36 (KHTML, like Gecko) Chrome\\/156.0.0.0 Safari\\/537.36\"},"
            + "\"description\":\"DLW\",\"data\":\" \"} -- double_redirect"
            + " -;- http://.LS.V2LOAD_BALANCERlive_tv_beinsport1/s -- redirect"
            + " -;- .LS.V2redirectV2_koora10_064762e6357527bc79dfba693c3490df_1_serv:GeneralCrawler; -- redirect"
            + " -;- .LS.V2redirectV2_livekora_064762e6357527bc79dfba693c3490df_1_serv:GeneralCrawler; -- redirect"
            + " -;- http://.LS.V2daddy_91/s -- redirect"
            + " -;- {\"url\":\"https:\\/\\/quotarevival.net\\/e\\/yxtmusy4xj8tj\","
            + "\"agent\":\"Mozilla\\/5.0 (Macintosh) Chrome\\/156.0.0.0\","
            + "\"headers\":{\"Referer\":\"https:\\/\\/new.aflam4you.net\\/\","
            + "\"Origin\":\"https:\\/\\/new.aflam4you.net\"},\"description\":\"SD\",\"data\":\" \"}"
            + " -- double_redirect"
            + " -;- http://primetv.cc.cd/beinfhd11/tracks-v1a1/mono.tbin.m3u8 -- Mozilla"
            + " -;- http://.LS.V2daddy_91/s -- redirect -;- ";

    @Test
    public void noSePierdeNingunServidor() {
        List<DramaResolucion.Opcion> todas = DramaResolucion.opciones(URL_BEIN1, BACKUP_BEIN1);
        // 1 (url) + 13 trozos del backup, menos los 2 daddy_91 repetidos
        assertEquals("se cuentan TODOS, sin borrar los marcadores", 11, todas.size());

        int marcadores = 0, embebidas = 0, directas = 0;
        for (DramaResolucion.Opcion o : todas) {
            if (o.tipo == DramaResolucion.Tipo.MARCADOR) marcadores++;
            if (o.tipo == DramaResolucion.Tipo.EMBEBIDA) embebidas++;
            if (o.tipo == DramaResolucion.Tipo.DIRECTA) directas++;
        }
        assertEquals("mainevent + SD/HD/4K + LB + koora + livekora + daddy", 8, marcadores);
        assertEquals(2, embebidas);
        assertEquals("primetv", 1, directas);
    }

    @Test
    public void lasEmbebidasVanPrimeroYLosMarcadoresAlFinal() {
        List<DramaResolucion.Opcion> todas = DramaResolucion.opciones(URL_BEIN1, BACKUP_BEIN1);
        assertEquals(DramaResolucion.Tipo.EMBEBIDA, todas.get(0).tipo);
        assertEquals(EMBED_BEIN1, todas.get(0).url);
        assertEquals(DramaResolucion.Tipo.EMBEBIDA, todas.get(1).tipo);
        assertEquals(DramaResolucion.Tipo.DIRECTA, todas.get(2).tipo);
        for (int i = 3; i < todas.size(); i++) {
            assertEquals("los marcadores, al final y sin borrarse",
                    DramaResolucion.Tipo.MARCADOR, todas.get(i).tipo);
        }
    }

    @Test
    public void seGuardanElUaYElRefererDeCadaServidor() {
        DramaResolucion.Opcion embed = DramaResolucion.mejorEmbebida(URL_BEIN1, BACKUP_BEIN1);
        assertNotNull(embed);
        assertEquals(EMBED_BEIN1, embed.url);
        assertTrue("UA del proveedor: " + embed.ua,
                embed.ua != null && embed.ua.contains("Macintosh"));
        assertTrue("tiene que ir en iframe", embed.iframe);
        assertEquals("descripcion", "DLW", embed.descripcion);

        // el segundo embed (muerto) se conserva en la lista, con su Referer
        List<DramaResolucion.Opcion> embeds = DramaResolucion.embebidas(URL_BEIN1, BACKUP_BEIN1);
        assertEquals(2, embeds.size());
        assertEquals(EMBED_MUERTO, embeds.get(1).url);
        assertEquals("https://new.aflam4you.net/", embeds.get(1).referer);
        // pero al buscar el siguiente NO se ofrece el muerto
        assertNull(DramaResolucion.siguienteEmbebida(URL_BEIN1, BACKUP_BEIN1, EMBED_BEIN1));
    }

    @Test
    public void losUtilesSonLosQueDeVerdadSePuedenAbrir() {
        List<DramaResolucion.Opcion> utiles = DramaResolucion.utiles(URL_BEIN1, BACKUP_BEIN1);
        // embed + primetv (el muerto y los marcadores no valen)
        assertEquals(2, utiles.size());
        assertEquals(EMBED_BEIN1, utiles.get(0).url);
        assertEquals(DIRECTO_PRIMETV, utiles.get(1).url);
    }

    @Test
    public void clasificaBienSinRed() {
        assertEquals(DramaResolucion.Tipo.MARCADOR,
                DramaResolucion.clasificar(".LS.V2redirectV2_koora10_x:GeneralCrawler;", false));
        assertEquals(DramaResolucion.Tipo.MARCADOR,
                DramaResolucion.clasificar("http://.LS.V2daddy_91/s", false));
        assertEquals(DramaResolucion.Tipo.EMBEBIDA,
                DramaResolucion.clasificar("https://ordernap.net/e/abc", false));
        assertEquals(DramaResolucion.Tipo.EMBEBIDA,
                DramaResolucion.clasificar("https://sitio.ext/pagina", true));
        assertEquals(DramaResolucion.Tipo.DIRECTA,
                DramaResolucion.clasificar("http://ua.online24.pm/play/1/t/video.m3u8", false));
        assertEquals(DramaResolucion.Tipo.DIRECTA,
                DramaResolucion.clasificar("https://x/9999/tracks-v1/mono.m3u8", false));
        assertEquals(DramaResolucion.Tipo.PAGINA,
                DramaResolucion.clasificar("https://www.youtube.com/@beINSPORTS/live", false));
        assertTrue(DramaResolucion.esPlaylist("https://x/a.m3u8?s=1"));
        assertFalse(DramaResolucion.esPlaylist("https://ordernap.net/e/z"));
        assertTrue(DramaResolucion.esEmbebida("https://ordernap.net/e/z"));
        assertFalse(DramaResolucion.esEmbebida("https://www.youtube.com/watch?v=1"));
    }

    @Test
    public void aguantaBasuraSinRomperse() {
        assertTrue(DramaResolucion.opciones(null, null).isEmpty());
        assertTrue(DramaResolucion.opciones("", "").isEmpty());
        assertEquals(1, DramaResolucion.opciones(URL_BEIN1, null).size());
        assertEquals(0, DramaResolucion.opciones(null, "{roto").size());
        assertEquals(0, DramaResolucion.opciones(null, " -;-  -;- ").size());
        assertNull(DramaResolucion.mejorEmbebida(null, null));
        assertNull(DramaResolucion.siguienteEmbebida(null, null, null));
        assertEquals(DramaResolucion.Tipo.MARCADOR, DramaResolucion.clasificar(null, false));
        assertEquals("", DramaResolucion.clave(null));
    }

    @Test
    public void dedupeDelMismoServidorConYSinEsquema() {
        assertEquals(DramaResolucion.clave("https://www.ORdernap.net/e/x/"),
                DramaResolucion.clave("http://ordernap.net/e/x"));
    }
}
