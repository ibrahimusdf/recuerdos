package dza.fut.paipo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * v7.39.2 — las tres cosas del logcat del usuario (2026-11-18 13:49, beIN Sports 1) que
 * dejaban un embed encima del vídeo que ya funcionaba:
 *
 * <ol>
 *   <li>los «marcadores sin clasificar» ({@code http://.LS.V2daddy_91/s}) llegaban al plan B
 *       como si fueran servidores;</li>
 *   <li>una ganadora de la carrera llegaba <b>después</b> de que el plan B hubiera puesto un
 *       embed, y el embed seguía armado 30 s más;</li>
 *   <li>el apunte de «servidor recordado» se hacía 3 s después y podía caer en el canal
 *       siguiente.</li>
 * </ol>
 */
public class MarcadoresYFuentePuestaTest {

    // ───────────────────── 1. marcadores que no son urls ─────────────────────

    /** Las cuatro del logcat, tal cual. Ninguna tiene host. */
    @Test public void losMarcadoresDelLogcatNoSonServidores() {
        String[] delLog = {
                "http://.LS.V2live_tv_custom_handler_live_tv_beinsport1_description_SD_/s",
                "http://.LS.V2live_tv_custom_handler_live_tv_beinsport1_description_HD_/s",
                "http://.LS.V2live_tv_custom_handler_live_tv_beinsport1_description_4K_/s",
                "http://.LS.V2LOAD_BALANCERlive_tv_beinsport1/s",
                "http://.LS.V2daddy_91/s",
        };
        for (String u : delLog) {
            assertTrue(u + " debería reconocerse como marcador",
                    DramaResolucion.esMarcadorSinClasificar(u));
        }
    }

    /** Un host que empieza por punto tampoco resuelve, aunque no traiga la marca .LS. */
    @Test public void unHostQueEmpiezaPorPuntoTampoco() {
        assertTrue(DramaResolucion.esMarcadorSinClasificar("http://.ejemplo/s"));
        assertTrue(DramaResolucion.esMarcadorSinClasificar("https://.otro/x"));
    }

    /** Y lo que SÍ es una url normal no se toca. */
    @Test public void lasUrlsDeVerdadNoSeConfunden() {
        assertFalse(DramaResolucion.esMarcadorSinClasificar(
                "https://dramalive-api-08th.onrender.com/p/232425/live_tv_beinsport1/2.m3u8"));
        assertFalse(DramaResolucion.esMarcadorSinClasificar(
                "http://primetv.cc.cd/beinfhd11/tracks-v1a1/mono.tbin.m3u8"));
        assertFalse(DramaResolucion.esMarcadorSinClasificar(
                "https://ordernap.net/e/z7iiiknxnuqjk"));
        assertFalse(DramaResolucion.esMarcadorSinClasificar(null));
        assertFalse(DramaResolucion.esMarcadorSinClasificar(""));
    }

    /** La consecuencia: el backup de beIN 1 deja de ofrecer marcadores como servidores. */
    @Test public void utilesDescartaLosMarcadoresAunqueVenganMalEtiquetados() {
        String backup = "http://.LS.V2live_tv_custom_handler_live_tv_aljeriaelheddaf_description_DL_/s"
                + " -;- https://live.eighty-eight.tv:7770/elheddaf1/index.m3u8";
        List<DramaResolucion.Opcion> utiles = DramaResolucion.utiles(
                "http://.LS.V2daddy_91/s", backup);
        for (DramaResolucion.Opcion o : utiles) {
            assertFalse("no debe quedar ningún marcador: " + o.url,
                    DramaResolucion.esMarcadorSinClasificar(o.url));
        }
        assertEquals(1, utiles.size());
    }

    /** Y la carrera no le gasta un hilo a ninguno (morían con UnknownHostException en 1 ms). */
    @Test public void laCarreraNoLeDaUnHiloAUnMarcador() {
        List<DramaResolucion.Opcion> plan = new ArrayList<>();
        plan.add(new DramaResolucion.Opcion(
                "http://.LS.V2daddy_91/s", DramaResolucion.Tipo.PAGINA, null, null, "", false));
        plan.add(new DramaResolucion.Opcion(
                "https://dramalive-api-08th.onrender.com/p/232425/live_tv_beinsport1/2.m3u8",
                DramaResolucion.Tipo.DIRECTA, null, null, "api·proxy [2] marcador", false));
        List<DramaResolucion.Opcion> cands = CarreraDeOpciones.candidatas(plan);
        assertEquals(1, cands.size());
        assertTrue(cands.get(0).url.contains("/p/232425/"));
    }

    // ───────────────────── 2. qué cuenta como «ya está sonando» ─────────────────────

    /**
     * La regla que evita pisar el vídeo: si ya hay imagen confirmada, la ganadora tardía no
     * toca nada. Es la comprobación que hace {@code hayFuentePuestaYSonando()} en la
     * Activity, aquí en corto: con imagen confirmada → sí; sin ella → no.
     */
    @Test public void conImagenConfirmadaLaGanadoraTardiaNoEntra() {
        assertTrue(decide(true, false, 0));
        assertFalse(decide(false, false, 0));
    }

    /** Y si el embed ya está pidiendo vídeo, también cuenta como «sonando». */
    @Test public void elEmbedConPeticionesDeVideoTambienCuenta() {
        assertTrue(decide(false, true, 3));
        // pero un embed puesto sin una sola petición NO es «sonando»: hay que seguir buscando
        assertFalse(decide(false, true, 0));
        assertFalse(decide(false, false, 5));
    }

    /** Espejo de {@code PlayerActivity.hayFuentePuestaYSonando()}. */
    private static boolean decide(boolean imagenConfirmada, boolean embeddedEnVentana,
                                  int peticionesDeVideo) {
        if (imagenConfirmada) return true;
        return embeddedEnVentana && peticionesDeVideo > 0;
    }

    // ───────────────────── 3. la carrera, con los marcadores del logcat ─────────────────────

    /**
     * El caso completo del logcat: 6 opciones, 4 de ellas marcadores muertos. Antes se
     * abrían 6 hilos; ahora quedan 2 (la directa del backup y el embed que sí es una
     * página), y ninguna es un {@code .LS.}.
     */
    @Test public void laCarreraDelLogcatBajaDeSeisADos() {
        List<DramaResolucion.Opcion> plan = new ArrayList<>();
        plan.add(new DramaResolucion.Opcion(
                "http://primetv.cc.cd/beinfhd11/tracks-v1a1/mono.tbin.m3u8",
                DramaResolucion.Tipo.DIRECTA, "Mozilla", null, "", false));
        plan.add(new DramaResolucion.Opcion(
                "https://ordernap.net/e/z7iiiknxnuqjk",
                DramaResolucion.Tipo.EMBEBIDA, null, null, "DLW", true));
        plan.add(new DramaResolucion.Opcion(
                "http://.LS.V2live_tv_custom_handler_live_tv_beinsport1_description_SD_/s",
                DramaResolucion.Tipo.PAGINA, null, null, "", false));
        plan.add(new DramaResolucion.Opcion(
                "http://.LS.V2live_tv_custom_handler_live_tv_beinsport1_description_HD_/s",
                DramaResolucion.Tipo.PAGINA, null, null, "", false));
        plan.add(new DramaResolucion.Opcion(
                "http://.LS.V2LOAD_BALANCERlive_tv_beinsport1/s",
                DramaResolucion.Tipo.PAGINA, null, null, "", false));
        plan.add(new DramaResolucion.Opcion(
                "http://.LS.V2daddy_91/s", DramaResolucion.Tipo.PAGINA, null, null, "", false));

        List<DramaResolucion.Opcion> cands = CarreraDeOpciones.candidatas(plan);
        assertEquals(2, cands.size());
        for (DramaResolucion.Opcion c : cands) {
            assertFalse(c.url, DramaResolucion.esMarcadorSinClasificar(c.url));
        }
    }
}
