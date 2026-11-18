package dza.fut.paipo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

/**
 * v7.38.7 — LA MEMORIA DE SERVIDORES por canal.
 *
 * <p>La app ya sabía cazar el playlist real dentro del embed
 * ({@code recordarPlaylistCazado} → {@code pasarANativoSiSePuede}) pero no se
 * acordaba de él: al reabrir el canal repetía los 2 POST y los 30 s de sondeo para
 * volver a encontrar la misma url. Estas pruebas fijan que lo aprendido sobrevive,
 * que ordena bien, y que <b>no</b> se aprende de la tarjeta de aviso.
 */
public class ServidoresDelCanalTest {

    private static final String PRIMETV =
            "http://primetv.cc.cd/beinfhd11/tracks-v1a1/mono.tbin.m3u8";
    private static final String OTRA =
            "http://cdn.ejemplo.tv/bein1/master.m3u8";

    // ─────────────────────────── serialización ───────────────────────────

    @Test
    public void loGuardadoSeRecuperaIgual() {
        List<ServidoresDelCanal.Conocido> l = new ArrayList<>();
        l.add(new ServidoresDelCanal.Conocido(PRIMETV, "Mozilla/5.0 (X11)",
                "https://ordernap.net/", 3, 1, 1700000000000L));

        List<ServidoresDelCanal.Conocido> vuelta =
                ServidoresDelCanal.deTexto(ServidoresDelCanal.aTexto(l));

        assertEquals(1, vuelta.size());
        ServidoresDelCanal.Conocido c = vuelta.get(0);
        assertEquals(PRIMETV, c.url);
        assertEquals("Mozilla/5.0 (X11)", c.ua);
        assertEquals("https://ordernap.net/", c.referer);
        assertEquals(3, c.exitos);
        assertEquals(1, c.fallos);
        assertEquals(1700000000000L, c.ultimoUso);
    }

    @Test
    public void unaEntradaRotaNoEnvenenaLaLista() {
        // basura en medio: la otra entrada tiene que sobrevivir
        String malo = "no-es-una-url\u0001x\u0001y\u00011\u00010\u0001123";
        String bueno = PRIMETV + "\u0001Mozilla\u0001https://a.b/\u00012\u00010\u0001456";
        List<ServidoresDelCanal.Conocido> l =
                ServidoresDelCanal.deTexto(malo + "\u0002" + bueno);

        assertEquals("solo la válida", 1, l.size());
        assertEquals(PRIMETV, l.get(0).url);
    }

    @Test
    public void textoVacioONuloNoRompeNada() {
        assertTrue(ServidoresDelCanal.deTexto(null).isEmpty());
        assertTrue(ServidoresDelCanal.deTexto("").isEmpty());
        assertTrue(ServidoresDelCanal.deTexto("   ").isEmpty());
        assertEquals("", ServidoresDelCanal.aTexto(new ArrayList<>()));
    }

    // ───────────────────────────── orden ─────────────────────────────

    @Test
    public void loQueFuncionaVaDelanteDeLoQueFalla() {
        long ahora = 1000L;
        List<ServidoresDelCanal.Conocido> l = new ArrayList<>();
        // el que falla, aunque usado hace poco, NO va primero
        l.add(new ServidoresDelCanal.Conocido(OTRA, null, null, 0, 3, ahora));
        l.add(new ServidoresDelCanal.Conocido(PRIMETV, "Mozilla", null, 5, 0, ahora - 100));

        List<ServidoresDelCanal.Conocido> orden = ServidoresDelCanal.ordenar(l);
        assertEquals("primero el fiable", PRIMETV, orden.get(0).url);
        assertTrue(orden.get(0).esFiable());
        assertFalse(orden.get(1).esFiable());
    }

    @Test
    public void aIgualdadGanaElMasReciente() {
        List<ServidoresDelCanal.Conocido> l = new ArrayList<>();
        l.add(new ServidoresDelCanal.Conocido(PRIMETV, null, null, 1, 0, 100L));
        l.add(new ServidoresDelCanal.Conocido(OTRA, null, null, 1, 0, 900L));

        assertEquals("el último que funcionó", OTRA,
                ServidoresDelCanal.ordenar(l).get(0).url);
    }

    // ─────────────────────────── aprendizaje ───────────────────────────

    @Test
    public void aprenderLaMismaUrlNoLaDuplicaYSusCabecerasSeActualizan() {
        List<ServidoresDelCanal.Conocido> l =
                ServidoresDelCanal.recordar(null, PRIMETV, "UA-viejo", "ref-viejo", 1L);
        l = ServidoresDelCanal.recordar(l, PRIMETV, "UA-nuevo", "ref-nuevo", 2L);

        assertEquals("una sola entrada", 1, l.size());
        assertEquals("UA-nuevo", l.get(0).ua);
        assertEquals("ref-nuevo", l.get(0).referer);
        assertEquals("dos éxitos", 2, l.get(0).exitos);
        assertEquals("y sigue siendo fiable", true, l.get(0).esFiable());
    }

    @Test
    public void unFalloNoBorraElAprendizajePeroLoBajaDePrioridad() {
        List<ServidoresDelCanal.Conocido> l =
                ServidoresDelCanal.recordar(null, PRIMETV, "UA", null, 1L);
        l = ServidoresDelCanal.recordar(l, OTRA, "UA2", null, 2L);
        assertEquals("el más reciente primero", OTRA, l.get(0).url);

        l = ServidoresDelCanal.fallar(l, OTRA, 3L);
        assertEquals("sigue estando (una url puede volver)", 2, l.size());
        assertEquals("pero el que funciona pasa delante", PRIMETV, l.get(0).url);
        for (ServidoresDelCanal.Conocido c : l) {
            if (c.url.equals(OTRA)) assertEquals(1, c.fallos);
        }
    }

    @Test
    public void unFalloDeAlgoDesconocidoSeApuntaConCeroExitos() {
        List<ServidoresDelCanal.Conocido> l = ServidoresDelCanal.fallar(null, OTRA, 5L);
        assertEquals(1, l.size());
        assertEquals(0, l.get(0).exitos);
        assertEquals(1, l.get(0).fallos);
        assertFalse("nunca funcionó: no es fiable", l.get(0).esFiable());
    }

    // ───────────────────── la fusión con lo que entrega Drama ─────────────────────

    /**
     * El caso beIN 1: Drama entrega 2 útiles (ordernap, primetv) y la app ya sabe
     * que primetv emite. El plan B tiene que empezar por primetv, no por el embed.
     */
    @Test
    public void loRecordadoSeIntentaAntesQueLoQueEntregaDrama() {
        String backup = "https://ordernap.net/e/z7iiiknxnuqjk -- redirect -;- "
                + PRIMETV + " -- Mozilla";

        List<DramaResolucion.Opcion> deDrama = DramaResolucion.utiles(null, backup);
        assertEquals(2, deDrama.size());
        assertEquals("sin memoria, Drama pone el embed delante",
                DramaResolucion.Tipo.EMBEBIDA, deDrama.get(0).tipo);

        List<DramaResolucion.Opcion> recordados = ServidoresDelCanal.opciones(
                ServidoresDelCanal.recordar(null, PRIMETV, "Mozilla", null, 1L));

        PlayerActivity activity = null;    // el selector es estático: no hace falta instancia
        DramaResolucion.Opcion primero =
                PlayerActivity.siguienteOpcionUtil(null, backup, new java.util.HashSet<>(), recordados);

        assertNotNull(primero);
        assertEquals("con memoria, primetv va primero", PRIMETV, primero.url);
        assertEquals(DramaResolucion.Tipo.DIRECTA, primero.tipo);
    }

    @Test
    public void unaUrlRecordadaNoSeDuplicaConLaQueEntregaDrama() {
        String backup = PRIMETV + " -- Mozilla";
        List<DramaResolucion.Opcion> recordados = ServidoresDelCanal.opciones(
                ServidoresDelCanal.recordar(null, PRIMETV, "Mozilla", null, 1L));

        List<DramaResolucion.Opcion> fusion =
                DramaResolucion.conRecordados(recordados, DramaResolucion.utiles(null, backup));

        assertEquals("una sola vez", 1, fusion.size());
        assertEquals("y con el UA recordado, no el que traiga Drama", "Mozilla", fusion.get(0).ua);
    }

    /** Lo recordado conserva sus cabeceras: el CDN contesta 403 con otro UA. */
    @Test
    public void loRecordadoMantieneSusCabecerasExactas() {
        List<ServidoresDelCanal.Conocido> l = ServidoresDelCanal.recordar(
                null, PRIMETV, "Mozilla/5.0 (Macintosh)", "https://ordernap.net/", 1L);
        DramaResolucion.Opcion o = ServidoresDelCanal.opciones(l).get(0);

        assertEquals("Mozilla/5.0 (Macintosh)", o.ua);
        assertEquals("https://ordernap.net/", o.referer);
        assertEquals(DramaResolucion.Tipo.DIRECTA, o.tipo);
    }

    /** Una url recordada que no es un stream no se trata como vídeo. */
    @Test
    public void loRecordadoQueNoEsStreamQuedaComoPagina() {
        DramaResolucion.Opcion o = DramaResolucion.directaCon(
                "https://www.youtube.com/@beINSPORTS/live", null, null);
        assertEquals(DramaResolucion.Tipo.PAGINA, o.tipo);
    }

    // ───────────────────────────── la llave ─────────────────────────────

    @Test
    public void laLlaveDelCanalSaleDelIdLiveYNoDeLaUrlEntera() {
        String a = ServidoresDelCanal.claveDeCanal(
                "dramalive://play?id_live=live_tv_beinsport1&u=1&a=all_streams_redirect");
        String b = ServidoresDelCanal.claveDeCanal(
                "dramalive://play?id_live=live_tv_beinsport1&u=OTRO&x=9");
        assertEquals("mismo canal, misma llave aunque cambien los parámetros", a, b);

        String c = ServidoresDelCanal.claveDeCanal(
                "dramalive://play?id_live=live_tv_beinsportnews&u=1");
        assertFalse("otro canal, otra llave", a.equals(c));
    }

    /** Sin id_live (canal ya curado a https) la llave sale de la url, estable. */
    @Test
    public void sinIdLiveLaLlaveEsLaUrlNormalizada() {
        String a = ServidoresDelCanal.claveDeCanal("https://cdn.ejemplo.tv/bein1/master.m3u8");
        String b = ServidoresDelCanal.claveDeCanal("https://cdn.ejemplo.tv/bein1/master.m3u8");
        assertEquals(a, b);
        assertNotNull(a);
    }

    /** El plan B con memoria y sin memoria no debe devolver null teniendo opciones. */
    @Test
    public void conMemoriaElPlanBSigueAgotandoLaListaYTerminaEnNull() {
        String backup = "https://ordernap.net/e/z7iiiknxnuqjk -- redirect -;- "
                + PRIMETV + " -- Mozilla";
        List<DramaResolucion.Opcion> recordados = ServidoresDelCanal.opciones(
                ServidoresDelCanal.recordar(null, PRIMETV, "Mozilla", null, 1L));

        java.util.Set<String> probados = new java.util.HashSet<>();
        List<String> recorrido = new ArrayList<>();
        DramaResolucion.Opcion o;
        while ((o = PlayerActivity.siguienteOpcionUtil(null, backup, probados, recordados)) != null) {
            probados.add(o.url);
            recorrido.add(o.url);
            if (recorrido.size() > 10) break;
        }
        assertEquals("las dos, sin repetir", 2, recorrido.size());
        assertEquals("empezando por la recordada", PRIMETV, recorrido.get(0));
        assertNull("y al final se agota de verdad",
                PlayerActivity.siguienteOpcionUtil(null, backup, probados, recordados));
    }
}
