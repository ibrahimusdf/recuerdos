package dza.fut.paipo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

/**
 * v7.38.6 — EL ORDEN POR VELOCIDAD. Lo que glasbi ya resolvió se gasta primero.
 *
 * <p><b>El dato del logcat del usuario</b> (2026-11-17, canal {@code live_tv_beinsport1}):
 *
 * <pre>
 * 19:02:03  glasbi [glt] OK -> http://primetv.cc.cd/…/mono.tbin.m3u8   ← YA resuelto
 *           resolverVivo: hay embebida, se prefiere -> ordernap
 * 19:02:04  plan B: embed ordernap
 * 19:02:19  plan B: probando -> ordernap          (otra vez)
 * 19:02:49  plan B: embed primetv  ← LA MISMA URL DEL SEGUNDO 3
 * 19:02:51  el embed está pidiendo vídeo (segmentos .ts)
 * 19:03:04  hay imagen (vídeo confirmado)
 * </pre>
 *
 * <p>beIN 1 no «no funcionaba»: tardaba <b>61 s</b> en algo que tenía a los <b>2 s</b>, y al
 * final lo veía por el WebView en vez del reproductor nativo.
 *
 * <p>Estas pruebas fijan la decisión con el backup REAL de beIN 1 del log.
 */
public class OrdenPorVelocidadTest {

    /** El live.url: un marcador .LS. que solo resuelve el redirector del sitio. */
    private static final String URL_LS =
            "http://.LS.V2live_tv_custom_handler_live_tv_beinsport1_description_DL_/s";

    /** El bloque `live` crudo del canal, en la forma que gasta DramaResolucion. */
    private static final String BACKUP_BEIN1 =
            "http://.LS.V2live_tv_custom_handler_live_tv_beinsport1_description_SD_/s -- redirect"
            + " -;- http://.LS.V2live_tv_custom_handler_live_tv_beinsport1_description_HD_/s -- redirect"
            + " -;- http://.LS.V2live_tv_custom_handler_live_tv_beinsport1_description_4K_/s -- redirect"
            + " -;- {\"url\":\"https:\\/\\/ordernap.net\\/e\\/z7iiiknxnuqjk\","
            + "\"agent\":\"Mozilla\\/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit\\/537.36\","
            + "\"iframe\":\"https:\\/\\/ordernap.net\\/e\\/z7iiiknxnuqjk\","
            + "\"description\":\"DLW\",\"data\":\" \"} -- double_redirect"
            + " -;- http://.LS.V2LOAD_BALANCERlive_tv_beinsport1/s -- redirect"
            + " -;- http://.LS.V2daddy_91/s -- redirect"
            + " -;- {\"url\":\"https:\\/\\/quotarevival.net\\/e\\/yxtmusy4xj8tj\","
            + "\"agent\":\"Mozilla\\/5.0 (Macintosh) Chrome\\/156.0.0.0\","
            + "\"description\":\"SD\",\"data\":\" \"} -- double_redirect"
            + " -;- http://primetv.cc.cd/beinfhd11/tracks-v1a1/mono.tbin.m3u8 -- Mozilla";

    private static final String DIRECTA = "http://primetv.cc.cd/beinfhd11/tracks-v1a1/mono.tbin.m3u8";
    private static final String EMBED_ORDEN = "https://ordernap.net/e/z7iiiknxnuqjk";

    /**
     * LA DECISIÓN NUEVA: cuando glasbi ya trae un m3u8 utilizable, se devuelve ESE,
     * no la embebida. Es lo contrario de lo que hacía la 7.37.17 (y el logcat
     * demuestra el coste: 61 s de sondeo para volver al mismo m3u8).
     */
    @Test
    public void cuandoGlasbiTraeDirectaSeUsaLaDirectaYNoElEmbed() {
        // Orden nuevo: directa primero. Antes, `primeraEmbebida` ganaba siempre.
        assertTrue("primetv tiene que ser reconocida como DIRECTA",
                DramaResolucion.clasificar(DIRECTA, false) == DramaResolucion.Tipo.DIRECTA);
        assertTrue("ordernap tiene que ser reconocida como EMBEBIDA",
                DramaResolucion.clasificar(EMBED_ORDEN, false) == DramaResolucion.Tipo.EMBEBIDA);

        // Y ambas sobreviven al filtro de útiles: el plan B las tiene para el fallback.
        List<DramaResolucion.Opcion> utiles = DramaResolucion.utiles(URL_LS, BACKUP_BEIN1);
        boolean hayDirecta = false, hayEmbed = false;
        for (DramaResolucion.Opcion o : utiles) {
            if (DIRECTA.equals(o.url)) hayDirecta = true;
            if (EMBED_ORDEN.equals(o.url)) hayEmbed = true;
        }
        assertTrue("la directa cruda de glasbi sigue siendo una opción válida", hayDirecta);
        assertTrue("y la embebida queda como plan B, no se pierde", hayEmbed);
    }

    /**
     * La directa lleva SU agente (`-- Mozilla`), no el de escritorio global. El CDN
     * del proveedor contesta 403 si no le llega el mismo UA con el que se pidió.
     */
    @Test
    public void laDirectaConservaSuPropioAgente() {
        for (DramaResolucion.Opcion o : DramaResolucion.utiles(URL_LS, BACKUP_BEIN1)) {
            if (!DIRECTA.equals(o.url)) continue;
            assertEquals("el agente del trozo «-- Mozilla»", "Mozilla", o.ua);
            return;
        }
        throw new AssertionError("no se encontró la directa en la lista de útiles");
    }

    /**
     * El orden de {@code utiles()} sigue poniendo las embebidas primero — y está
     * bien: ese orden es para <em>listar</em>. La elección de «qué se gasta primero»
     * es otra decisión y ahora vive en {@code resolverVivo}. Esta prueba documenta
     * la diferencia para que nadie las confunda otra vez.
     */
    @Test
    public void listarYProbarSonDosDecisionesDistintas() {
        List<DramaResolucion.Opcion> utiles = DramaResolucion.utiles(URL_LS, BACKUP_BEIN1);
        assertFalse("el canal tiene utilices", utiles.isEmpty());
        assertEquals("al listar, la embebida va primero (para el selector de calidad)",
                DramaResolucion.Tipo.EMBEBIDA, utiles.get(0).tipo);

        // Pero el plan B, cuando la directa YA falló, avanza por las demás sin repetir.
        java.util.Set<String> probados = new java.util.LinkedHashSet<>();
        probados.add(DIRECTA);
        DramaResolucion.Opcion siguiente =
                DramaResolucion.siguiente(utiles, probados, DIRECTA, null);
        assertNotNull("tras fallar la directa tiene que quedar la embebida", siguiente);
        assertEquals(EMBED_ORDEN, siguiente.url);
    }

    /**
     * El escenario completo del log, con el arreglo:
     * glasbi da la directa → ExoPlayer (2 s) → si falla → embed → si falla → nada.
     * Sin repetir ninguna, y sin el bucle de 30 s que grabó el logcat.
     */
    @Test
    public void laSecuenciaDelLogYaNoRepiteNiTardaSesentaSegundos() {
        List<DramaResolucion.Opcion> utiles = DramaResolucion.utiles(URL_LS, BACKUP_BEIN1);

        // paso 1: lo que elige resolverVivo ahora (directa primero)
        assertTrue(DramaResolucion.clasificar(DIRECTA, false) == DramaResolucion.Tipo.DIRECTA);

        // paso 2: la directa no arranca -> plan B
        java.util.Set<String> probados = new java.util.LinkedHashSet<>();
        probados.add(DIRECTA);
        DramaResolucion.Opcion segundo = DramaResolucion.siguiente(utiles, probados, DIRECTA, null);
        assertNotNull(segundo);
        assertEquals("el segundo es el embed", EMBED_ORDEN, segundo.url);

        // paso 3: el embed tampoco -> se agota, y devuelve null (aviso honesto)
        probados.add(segundo.url);
        assertEquals("solo 2 útiles: ya no queda nada",
                2, utiles.size());
        assertEquals(null, DramaResolucion.siguiente(utiles, probados, null, null));
    }
}
