package dza.fut.paipo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * v7.39.3 — los cuatro defectos del logcat del 2026-11-18 (14:23, 14:25, 15:07).
 *
 * <p>Un test por defecto, con los números medidos en el logcat del usuario.</p>
 */
public class EnfriamientoYTopeTest {

    // ───────────────────────── 1. el tope por tipo de fuente ─────────────────────────

    private static final String PROXY =
            "https://dramalive-api-08th.onrender.com/p/232425/live_tv_beinsport1/0.m3u8";
    private static final String CDN =
            "http://primetv.cc.cd/beinfhd11/tracks-v1a1/mono.tbin.m3u8";

    @Test public void laProxadaDeLaCadenaTieneMasMargenQueUnCdn() {
        assertEquals(22000, CarreraDeOpciones.topePara(PROXY));
        assertEquals(15000, CarreraDeOpciones.topePara(CDN));
    }

    @Test public void losTimeoutsMedidosHabrianEntradoConElTopeNuevo() {
        // 14:25:25 -> 15007 ms y 14:26:31 -> 15084 ms: los dos descartados con tope 15 s.
        assertTrue(15007 < CarreraDeOpciones.topePara(PROXY));
        assertTrue(15084 < CarreraDeOpciones.topePara(PROXY));
    }

    @Test public void elTopeViejoNoCambiaParaLoQueNoEsProxy() {
        assertEquals(CarreraDeOpciones.TOPE_MS, CarreraDeOpciones.topePara(CDN));
        assertEquals(CarreraDeOpciones.TOPE_MS, CarreraDeOpciones.topePara(null));
    }

    @Test public void esDelProxyReconoceAmbasRutas() {
        assertTrue(CarreraDeOpciones.esDelProxy(PROXY));
        assertTrue(CarreraDeOpciones.esDelProxy("https://host/ps/232425/live_tv_x/0/seg.ts"));
        assertFalse(CarreraDeOpciones.esDelProxy("https://host/opciones/232425/live_tv_x"));
        assertFalse(CarreraDeOpciones.esDelProxy(CDN));
    }

    // ───────────────────────── 2. la API se enfría, no muere ─────────────────────────

    @Test public void unFalloNoMataLaApiParaSiempre() {
        ApiDrama.olvidarEstado();
        assertFalse("al principio se puede preguntar", ApiDrama.sesionSinApi());
        ApiDrama.marcarCaida();
        assertTrue("justo después del fallo, en enfriamiento", ApiDrama.sesionSinApi());
        // El enfriamiento es de un minuto, no infinito (la 93 no lo perdonaba nunca).
        assertEquals(60000L, ApiDrama.ENFRIAMIENTO_MS);
        ApiDrama.olvidarEstado();
        assertFalse("y para una prueba se puede limpiar", ApiDrama.sesionSinApi());
    }

    @Test public void elPrimerIntentoEsperaARender() {
        // La app de referencia usa 70 s en el primer intento; aquí 45 s, y solo cuando se
        // viene de un fallo. Con 12 s se perdían beIN 5 (13,3 s) y beIN 2 (13,1 s).
        assertTrue("paciencia de despertar > tope normal",
                ApiDrama.TIEMPO_DESPERTAR_MS > ApiDrama.TIEMPO_MS);
        assertTrue("y cubre los 13,3 s medidos en frío",
                ApiDrama.TIEMPO_DESPERTAR_MS > 13330);
    }

    // ───────────────────────── 3. lo descartado no se reintenta ─────────────────────────

    @Test public void el403ATiempoQuedaEnterradoYElTimeoutNo() {
        CarreraDeOpciones.Descartes d = new CarreraDeOpciones.Descartes();
        d.apuntar(CDN);                                    // 403, 175 ms
        d.apuntar("http://.LS.V2daddy_91/s");               // DNS
        assertTrue(d.estaMuerta(CDN));
        assertTrue(d.estaMuerta("http://.LS.V2daddy_91/s"));
        assertFalse("un timeout NO se entierra: el proxy lento contesta a la 2ª",
                d.estaMuerta(PROXY));
        assertFalse(d.estaMuerta(null));
        assertEquals(2, d.tamano());
        d.limpiar();
        assertFalse(d.estaMuerta(CDN));
        assertEquals(0, d.tamano());
    }

    @Test public void soloLoRotundoSeEntierra() {
        assertEquals(403, noRotundo(403));
        assertEquals(404, noRotundo(404));
        assertEquals(410, noRotundo(410));
        assertEquals(0, noRotundo(200));
        assertEquals(0, noRotundo(500));
        assertEquals(0, noRotundo(0));
    }

    private static int noRotundo(int http) {
        return CarreraDeOpciones.esNoRotundo(http) ? http : 0;
    }

    // ───────────────────────── 4. el guardián .LS. no mata el embed ─────────────────────────

    @Test public void unMarcadorEnLaListaNoImpideElEmbed() {
        // Los 7 marcadores de beIN 1 del logcat de las 13:49 conviven con 2 opciones buenas:
        // el canal TIENE que poder abrirse.
        assertTrue(DramaResolucion.esMarcadorSinClasificar("http://.LS.V2daddy_91/s"));
        assertFalse(DramaResolucion.esMarcadorSinClasificar("https://ordernap.net/e/z7iiiknxnuqjk"));
        assertFalse(DramaResolucion.esMarcadorSinClasificar(
                "https://dramalive-api-08th.onrender.com/p/232425/live_tv_beinsport1/2.m3u8"));
    }

    @Test public void laFuenteMarcadorSeSigueRechazando() {
        assertTrue(DramaResolucion.esMarcadorSinClasificar("http://.LS.V2LOAD_BALANCERlive_tv_beinsport1/s"));
        assertTrue(DramaResolucion.esMarcadorSinClasificar("http://.LS.V2live_tv_custom_handler_live_tv_x_description_HD_/s"));
        assertFalse(DramaResolucion.esMarcadorSinClasificar(null));
        assertFalse(DramaResolucion.esMarcadorSinClasificar(""));
    }

    @Test public void unArbolDeErroresSinOpcionesValidasNoEligeNada() {
        assertEquals(null, elegirValida(new String[] { "http://.LS.V2daddy_91/s" }));
        assertEquals(PROXY, elegirValida(new String[] { "http://.LS.V2daddy_91/s", PROXY }));
    }

    /** Lo mismo que hace onPlayerError desde la v7.39.3. */
    private static String elegirValida(String[] candidatas) {
        for (String u : candidatas) {
            if (DramaResolucion.esMarcadorSinClasificar(u)) continue;
            return u;
        }
        return null;
    }

    @Test public void laConstanteDeTopeNoSeBajaDeLosMedido() {
        // 13 300 ms medidos en frío: cualquier tope menor pierde el canal.
        assertTrue(CarreraDeOpciones.TOPE_MS >= 15000);
        assertTrue(CarreraDeOpciones.TOPE_PROXY_MS >= 15000);
        assertTrue(ApiDrama.TIEMPO_MS >= 12000);
    }

    @Test public void elDescarteSinHttpNoSeConfundeConUnRotundo() {
        // "timeout" y "SocketTimeoutException" llegan con http = 0 y NO entierran la url.
        assertFalse(CarreraDeOpciones.esNoRotundo(0));
        assertNull(elegirValida(new String[0]));
    }
}
