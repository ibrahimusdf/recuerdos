package dza.fut.paipo;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * v7.37.19 — «¿está el embed pidiendo vídeo?».
 *
 * Medido 2026-11-17 en Chromium con el UA de móvil de la app, iframe 1280x720:
 *   ordernap (beIN 1) ......... 7 peticiones .ts  -> hay canal
 *   quotarevival (muerto) ..... 0 peticiones .ts  -> «domain protected»
 *
 * El <video> de dentro NO es accesible desde el padre (el embed lo mete en un
 * iframe suyo, otro origen): por eso `t` y `videoWidth` siempre salían 0 aunque el
 * vídeo se viera. La señal que sí llega a la app es la petición de los segmentos,
 * que pasa por WebViewClient.shouldInterceptRequest.
 */
public class SenalDeVideoTest {

    @Test
    public void reconoceSegmentosYPlaylists() {
        assertTrue(PlayerActivity.esSegmentoDeVideo(
                "https://k485k1.a737cozfwjmm.net:8443/hls/ho999dl3k9bvk-1276200000.ts"));
        assertTrue(PlayerActivity.esSegmentoDeVideo(
                "https://x.cdn/hls/a.m3u8?token=abc&t=1"));
        assertTrue(PlayerActivity.esSegmentoDeVideo("https://x.cdn/a.m4s"));
        assertTrue(PlayerActivity.esSegmentoDeVideo("https://x.cdn/v.mp4"));
        assertTrue(PlayerActivity.esSegmentoDeVideo("https://x.cdn/a.ts?x=1"));
        assertTrue("el CDN de Drama usa esta forma",
                PlayerActivity.esSegmentoDeVideo(
                        "https://ua102.online24.pm:8443/9999/tracks-v1/mono.m3u8"));
    }

    @Test
    public void noConfundePaginasNiScripts() {
        assertFalse(PlayerActivity.esSegmentoDeVideo("https://ordernap.net/e/z7iiiknxnuqjk"));
        assertFalse(PlayerActivity.esSegmentoDeVideo("https://cdn.jsdelivr.net/npm/x.js"));
        assertFalse(PlayerActivity.esSegmentoDeVideo("https://4.adsco.re/ads.js"));
        assertFalse(PlayerActivity.esSegmentoDeVideo("https://x.com/logo.png"));
        assertFalse(PlayerActivity.esSegmentoDeVideo(null));
        assertFalse(PlayerActivity.esSegmentoDeVideo(""));
        // ojo: «.tsx» o «.json» no son segmentos
        assertFalse(PlayerActivity.esSegmentoDeVideo("https://x.com/app.tsx"));
        assertFalse(PlayerActivity.esSegmentoDeVideo("https://x.com/datos.json"));
    }
}
