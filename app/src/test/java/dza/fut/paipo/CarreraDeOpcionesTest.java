package dza.fut.paipo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * v7.39.0 — la carrera de opciones, probada sin red y sin móvil.
 *
 * <p>El caso que manda es el del logcat del usuario (2026-11-18,
 * {@code live_tv_aljeriaelheddaf}): cuatro opciones, la primera un host muerto a nivel
 * TCP que costaba 4 × 15 s, y una proxada que contestaba en 1 s. Estos tests fijan las
 * reglas puras: quién entra en la carrera, qué cuenta como «responde» y qué se descarta
 * sin gastar un turno.</p>
 */
public class CarreraDeOpcionesTest {

    private static DramaResolucion.Opcion op(String url) {
        return DramaResolucion.directaCon(url, null, null);
    }

    // ───────────────────── qué cuenta como manifiesto (el núcleo) ─────────────────────

    @Test public void unExtM3uEsUnManifiesto() {
        assertTrue(CarreraDeOpciones.pareceManifiesto(
                "#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-TARGETDURATION:10\n"));
    }

    @Test public void elBomNoRompelaDeteccion() {
        assertTrue(CarreraDeOpciones.pareceManifiesto("\uFEFF#EXTM3U\n#EXT-X-VERSION:3\n"));
    }

    @Test public void elHtmlNoEsUnManifiesto() {
        assertFalse(CarreraDeOpciones.pareceManifiesto(
                "<!DOCTYPE html><html><body>player</body></html>"));
        assertFalse(CarreraDeOpciones.pareceManifiesto("{\"detail\":\"no es un manifiesto\"}"));
        assertFalse(CarreraDeOpciones.pareceManifiesto(null));
        assertFalse(CarreraDeOpciones.pareceManifiesto(""));
    }

    /**
     * Medido en beIN Fr1: el CDN sirve «ОТСУТСТВУЕТ ПОДПИСКА» con su #EXTM3U y sus
     * segmentos. Si la carrera se lo traga, elige justo el servidor que NO emite.
     */
    @Test public void laTarjetaDeAvisoNoGanaAunqueTraigaExtM3u() {
        String cartel = "#EXTM3U\n#EXT-X-TARGETDURATION:10\n"
                + "#EXTINF:10,\n/error/otsutstvuet-podpiska.ts\n";
        assertTrue(CarreraDeOpciones.esAviso(cartel));
        assertFalse(CarreraDeOpciones.pareceManifiesto(cartel));
    }

    // ───────────────────────────── qué descarta y qué no ─────────────────────────────

    /**
     * La regla aprendida en la v7.38.11: solo un NO rotundo descarta. Un timeout (0) o un
     * 500 son «no lo sé» — el proxy de la cadena los resuelve igual (medido con beIN 2 y
     * beIN 5: {@code emite:false}, {@code http:null}, y el {@code /p/…} devuelve #EXTM3U).
     */
    @Test public void soloElNoRotundoDescarta() {
        assertTrue(CarreraDeOpciones.esNoRotundo(403));
        assertTrue(CarreraDeOpciones.esNoRotundo(404));
        assertTrue(CarreraDeOpciones.esNoRotundo(410));
        assertFalse(CarreraDeOpciones.esNoRotundo(0));      // timeout / DNS / TLS
        assertFalse(CarreraDeOpciones.esNoRotundo(200));
        assertFalse(CarreraDeOpciones.esNoRotundo(301));
        assertFalse(CarreraDeOpciones.esNoRotundo(500));
    }

    @Test public void elDashCifradoNoGastaHueco() {
        assertTrue(CarreraDeOpciones.esDashCifrado(
                "https://otte.example.aiv-cdn.net/cenc.mpd", 200));
        assertTrue(CarreraDeOpciones.esDashCifrado(
                "https://uselector.cdn.intigral-ott.net/ADE.isml/manifest.mpd", 299));
        assertTrue(CarreraDeOpciones.esDashCifrado(
                "https://x/starzplayarabia_dash_enc/manifest.mpd", 200));
        // un m3u8 normal no es DASH cifrado, y un .mpd claro tampoco
        assertFalse(CarreraDeOpciones.esDashCifrado("https://x/canal/index.m3u8", 200));
        assertFalse(CarreraDeOpciones.esDashCifrado("https://x/limpio/manifest.mpd", 200));
    }

    @Test public void losPortalesNoEntranPeroElHostDeLaCadenaSi() {
        assertFalse(CarreraDeOpciones.esCandidata(op("https://www.youtube.com/@beINSPORTS/live")));
        assertFalse(CarreraDeOpciones.esCandidata(op("http://.LS.V2live_tv_custom_handler_x/s")));
        // el index.html de la cadena SÍ: ahí vive el manifiesto (medido, beIN Max 1)
        assertTrue(CarreraDeOpciones.esCandidata(op(
                "https://max10.maxwordsandroidios.shop/pages/abc/index.html")));
        // y la proxada de la API también, aunque la sonda la diera por muerta
        assertTrue(CarreraDeOpciones.esCandidata(op(
                "https://dramalive-api-08th.onrender.com/p/232425/live_tv_aljeriaelheddaf/0.m3u8")));
    }

    /**
     * v7.39.4 — un EMBED no es un manifiesto y no compite en la carrera.
     *
     * <p>Logcat del usuario, 2026-11-18 16:01: la carrera del arranque se quedó con
     * {@code ordernap.net/e/b11wbtzmon53m} y gastó 1 444 ms en descartarlo («no es
     * manifiesto») mientras la proxada buena respondía 200 en 752 ms.</p>
     */
    @Test public void losEmbedsNoEntranEnLaCarrera() {
        assertFalse(CarreraDeOpciones.esCandidata(op("https://ordernap.net/e/b11wbtzmon53m")));
        assertFalse(CarreraDeOpciones.esCandidata(op("https://quotarevival.net/e/vg4he6f2wmrj3")));
    }

    // ─────────────────────────── el caso del logcat, entero ───────────────────────────

    /**
     * Las cuatro opciones del canal del logcat, en el orden del plan de ataque: la memoria
     * del canal y el glasbi delante, la API detrás. Todas entran (ninguna es portal ni
     * Widevine), incluida la del host muerto: no se descarta por adelantado, simplemente
     * pierde la carrera.
     */
    @Test public void lasCuatroDelLogcatEntranALaCarrera() {
        List<DramaResolucion.Opcion> plan = new ArrayList<>();
        plan.add(op("https://live.eighty-eight.tv:7770/elheddaf1/index.m3u8"));      // muerta
        plan.add(op("https://dramalive-api-08th.onrender.com/p/232425/x/0.m3u8"));   // gana
        plan.add(op("https://dramalive-api-08th.onrender.com/p/232425/x/1.m3u8"));
        plan.add(op("https://dramalive-api-08th.onrender.com/p/232425/x/2.m3u8"));

        List<DramaResolucion.Opcion> cands = CarreraDeOpciones.candidatas(plan);
        assertEquals(4, cands.size());
        // el orden NO se toca: la carrera los lanza todos a la vez, el orden solo decide
        // quién sale antes del hilo, no quién gana
        assertEquals("https://live.eighty-eight.tv:7770/elheddaf1/index.m3u8",
                cands.get(0).url);
    }

    @Test public void noSeRepiteLaMismaUrlDosVecesEnLaCarrera() {
        List<DramaResolucion.Opcion> plan = new ArrayList<>();
        plan.add(op("https://a/canal.m3u8"));
        plan.add(op("https://a/canal.m3u8"));            // misma url, otro objeto
        plan.add(op("https://b/canal.m3u8"));
        assertEquals(2, CarreraDeOpciones.candidatas(plan).size());
    }

    @Test public void elTopeDeOchoEvitaAbrirVeinteSockets() {
        List<DramaResolucion.Opcion> plan = new ArrayList<>();
        for (int i = 0; i < 20; i++) plan.add(op("https://x/canal" + i + "/index.m3u8"));
        assertEquals(8, CarreraDeOpciones.candidatas(plan).size());
    }

    @Test public void sinCandidatasLaCarreraLoDiceYNoSeCuelga() {
        final List<Integer> llamadas = new ArrayList<>();
        CarreraDeOpciones.Respuesta r = CarreraDeOpciones.correr(
                new ArrayList<DramaResolucion.Opcion>(),
                new CarreraDeOpciones.Oyente() {
                    @Override public void ganadora(CarreraDeOpciones.Respuesta x) { llamadas.add(1); }
                    @Override public void descartada(CarreraDeOpciones.Respuesta x) { llamadas.add(2); }
                    @Override public void ninguna(List<CarreraDeOpciones.Respuesta> i) { llamadas.add(3); }
                });
        assertNull(r);
        assertEquals(1, llamadas.size());
        assertEquals(Integer.valueOf(3), llamadas.get(0));   // solo «ninguna», una vez
        assertNotNull(CarreraDeOpciones.UA);
    }

    // ─────────────────────── qué NO se pone a sonar sin comprobar ───────────────────────

    /**
     * Lo que trae Drama sí arranca directo (es lo que hacía la app de referencia); lo que
     * promete la API o la memoria entra en la carrera. Y el marcador {@code .LS.} del
     * glasbi NO es una promesa que valga la pena esperar.
     */
    @Test public void unaPromesaSinComprobarNoFrenaLaCarrera() {
        assertTrue(PlayerActivity.esPromesaSinComprobar(DramaResolucion.deApi(
                "https://dramalive-api-08th.onrender.com/p/232425/x/0.m3u8",
                DramaResolucion.Tipo.DIRECTA, null, null, 0)));
        assertTrue(PlayerActivity.esPromesaSinComprobar(DramaResolucion.directaCon(
                "https://a/canal.m3u8", null, null)));        // «recordado»
        assertTrue(PlayerActivity.esPromesaSinComprobar(null));
        // una directa normal de Drama no es «promesa»: esa sí se pone a sonar
        DramaResolucion.Opcion deDrama = new DramaResolucion.Opcion(
                "https://live.eighty-eight.tv:7770/elheddaf1/index.m3u8",
                DramaResolucion.Tipo.DIRECTA, null, null, "", false);
        assertFalse(PlayerActivity.esPromesaSinComprobar(deDrama));
    }
}
