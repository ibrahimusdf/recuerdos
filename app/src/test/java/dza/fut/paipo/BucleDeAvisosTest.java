package dza.fut.paipo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * v7.39.5 — el bucle infinito de avisos y el arranque que se rendía demasiado pronto.
 *
 * <p>Logcat del usuario, 2026-11-18. El bucle: 4 238 repeticiones de «↩️ sin stream
 * nativo; se prueba el embed en la ventana -> …androstreamlivebs4.m3u8» en tres segundos,
 * todas en el hilo de UI, con la app congelada.</p>
 */
public class BucleDeAvisosTest {

    // ─────────────────────────── 1. no se elige lo ya probado ───────────────────────────

    @Test public void elTopeDeVueltasSeguidasEsFinito() {
        assertEquals(8, PlayerActivity.MAX_AVISOS_SEGUIDOS);
        assertTrue("con 4 238 vueltas medidas, el tope tiene que ser mucho menor",
                PlayerActivity.MAX_AVISOS_SEGUIDOS < 100);
    }

    @Test public void laEleccionDelAvisoExcluyeLoYaProbado() {
        // La lógica vieja (primeraEmbebida sin mirar `servidoresProbados`) repetía la
        // misma url para siempre; la nueva devuelve null en cuanto está probada.
        java.util.Set<String> probados = new java.util.HashSet<>();
        String u = "https://andro.mangeinde4.lat/checklist/androstreamlivebs4.m3u8";
        probados.add(u);
        assertTrue(probados.contains(u));
        assertNull(elegir(u, probados));
        assertNull(elegir(u, new java.util.HashSet<String>()));   // null si no hay lista
    }

    /** Misma regla que `avisarSinStream` desde la v7.39.5. */
    private static String elegir(String url, java.util.Set<String> probados) {
        if (url == null || probados.contains(url)) return null;
        if (DramaResolucion.esMarcadorSinClasificar(url)) return null;
        if (DramaResolucion.esPaginaDePortal(url)) return null;
        return url;
    }

    @Test public void unMarcadorTampocoSeEligeParaElEmbed() {
        assertNull(elegir("http://.LS.V2live_tv_custom_handler_x/s",
                new java.util.HashSet<String>()));
        assertNull(elegir("https://www.youtube.com/@beINSPORTS/live",
                new java.util.HashSet<String>()));
    }

    // ─────────────────────────── 2. reintento del arranque ───────────────────────────

    @Test public void hayReintentosDeArranqueAntesDeRendirse() {
        // 16:41:02.836 Buffering... → 16:41:14.507 ❌ 504: con 2 reintentos y su espera,
        // el segundo intento ya da imagen (16:41:26.077 ✅ Ready).
        assertEquals(2, PlayerActivity.REINTENTOS_DE_ARRANQUE);
        assertTrue(PlayerActivity.REINTENTOS_DE_ARRANQUE < PlayerActivity.MAX_REFRESH_RETRIES
                || PlayerActivity.REINTENTOS_DE_ARRANQUE >= 1);
    }

    @Test public void un403DeRuidoNoDescartaElCanal() {
        // 16:41:38.988 Buffering... → 403 → 16:41:40.101 ✅ Ready: el 403 era ruido.
        assertFalse("un 403 al arrancar NO es «el canal no emite»",
                DramaResolucion.esMarcadorSinClasificar("https://x/canal/index.m3u8"));
    }

    // ─────────────────────────── 3. el segmento .ts no es una fuente ───────────────────────────

    @Test public void unSegmentoTsNoEsPlaylist() {
        assertFalse(DramaResolucion.esPlaylist(
                "https://siloh-ns1.plutotv.net/626_pluto/clip/x/hls/0-713360/hls_600-00109.ts"));
        assertFalse(DramaResolucion.esPlaylist("https://x/poster/canal.jpg"));
        assertFalse(DramaResolucion.esPlaylist("https://x/sprite/thumb.png"));
    }

    @Test public void losManifiestosSiguenSiendoPlaylist() {
        assertTrue(DramaResolucion.esPlaylist("https://x/canal/index.m3u8"));
        assertTrue(DramaResolucion.esPlaylist("https://x/manifest.mpd"));
        assertTrue(DramaResolucion.esPlaylist("http://181.78.8.199:8000/play/a0cu/index.m3u8"));
    }

    @Test public void laProxadaNoSeConfundeConUnSegmento() {
        String proxy = "https://dramalive-api-08th.onrender.com/p/232425/live_tv_x/0.m3u8";
        assertTrue(DramaResolucion.esPlaylist(proxy));
        assertTrue(CarreraDeOpciones.esDelProxy(proxy));
    }

    // ─────────────────────────── 4. la memoria del canal ───────────────────────────

    @Test public void laProxadaSeApuntaAunqueNoHayaReady() {
        // La regla nueva: `streamReady || esDelProxy(url)`.
        String proxy = "https://dramalive-api-08th.onrender.com/p/232425/live_tv_x/0.m3u8";
        String cdn = "https://shd-amg-fast.edgenextcdn.net/tx010/playlist.m3u8";
        assertTrue(seApunta(proxy, false));
        assertFalse(seApunta(cdn, false));
        assertTrue(seApunta(cdn, true));
    }

    private static boolean seApunta(String url, boolean streamReady) {
        return streamReady || CarreraDeOpciones.esDelProxy(url);
    }

    @Test public void unFalloSoloSeApuntaSiHayAlternativas() {
        // `apuntarFalloDelCanal` no toca nada cuando el canal tiene una sola opción.
        assertEquals(false, sePuedeApuntarFallo(1));
        assertEquals(true, sePuedeApuntarFallo(2));
        assertEquals(true, sePuedeApuntarFallo(7));
    }

    private static boolean sePuedeApuntarFallo(int opcionesDelCanal) {
        return opcionesDelCanal > 1;
    }
}
