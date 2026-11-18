package dza.fut.paipo;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * El fixture es el playlist REAL medido el 2026-10-08 en
 * `https://fas4.faststorageapi.shop/malasp/fgmxweigdavh/index.html` (canal vivo
 * del usuario; los `k9x_*` daban 404 crudos y 200 tras el swap, con fotograma
 * 852x480 decodificado por drama-live.py). Este test fija ese comportamiento.
 */
public class SwapHlsTest {

    private static final String PLAYLIST = String.join("\n",
            "#EXTM3U",
            "",
            "#EXT-X-VERSION:3",
            "",
            "#EXT-X-TARGETDURATION:10",
            "",
            "#EXT-X-MEDIA-SEQUENCE:1791437170",
            "",
            "#EXTINF:10.000000,",
            "",
            "k9x_1791436683-1791437170.js",
            "",
            "#EXTINF:5.000000,",
            "",
            "k9x_1791436683-1791437171.js",
            "",
            "#EXTINF:5.000000,",
            "",
            "k9x_1791436683-1791437172.js");

    @Test
    public void paresExplicitosQuitanElPrefijo() {
        List<String[]> swaps = new ArrayList<>();
        swaps.add(new String[]{"k9x_", ""});
        String out = SwapHls.aplicar(PLAYLIST, swaps, false);
        assertTrue(out.contains("\n1791436683-1791437170.js"));
        assertTrue(out.contains("\n1791436683-1791437172.js"));
        assertFalse(out.contains("k9x_"));
        // las lineas de comentario y el media-sequence viajan intactos
        assertTrue(out.contains("#EXT-X-MEDIA-SEQUENCE:1791437170"));
        assertTrue(out.contains("#EXTINF:10.000000,"));
    }

    @Test
    public void autoDetectaPrefijoComun() {
        String out = SwapHls.aplicar(PLAYLIST, Collections.emptyList(), true);
        assertEquals(SwapHls.aplicar(PLAYLIST, Collections.singletonList(new String[]{"k9x_", ""}), false), out);
    }

    @Test
    public void sinUnanimidadNoTocaNada() {
        String mezcla = "#EXTM3U\nk9x_1-a.js\notro_2-b.js\n";
        assertEquals(mezcla, SwapHls.aplicar(mezcla, Collections.emptyList(), true));
    }

    @Test
    public void playlistsNormalesQuedanIgual() {
        String normal = "#EXTM3U\n#EXT-X-VERSION:3\nfileSequence2157.ts\nfileSequence2158.ts\n";
        assertEquals(normal, SwapHls.aplicar(normal, Collections.emptyList(), true));
        // y con swaps explicitos que no pegan, tampoco
        assertEquals(normal, SwapHls.aplicar(normal, Collections.singletonList(new String[]{"k9x_", ""}), true));
    }

    @Test
    public void prefijoComunDetectaSoloLaFormaPanel() {
        assertNull(SwapHls.prefijoComun(Arrays.asList("fileSequence2157.ts")));
        assertEquals("k9x_", SwapHls.prefijoComun(Arrays.asList("k9x_1-2.js", "k9x_1-3.js")));
        // prefijo que no va seguido de digito (p.ej. master de variants) NO es swap
        assertNull(SwapHls.prefijoComun(Arrays.asList("var_hd/index.m3u8", "var_sd/index.m3u8")));
    }

    @Test
    public void esPlaylistToleraBasuraYCrlf() {
        assertTrue(SwapHls.esPlaylist("#EXTM3U\nx".getBytes(StandardCharsets.UTF_8)));
        assertTrue(SwapHls.esPlaylist(("\uFEFF\r\n  #EXTM3U\n").getBytes(StandardCharsets.UTF_8)));
        assertTrue(SwapHls.esPlaylist("#EXTM3U\r\n#EXTINF\r\ns.ts".getBytes(StandardCharsets.UTF_8)));
        assertFalse(SwapHls.esPlaylist("<html>no</html>".getBytes(StandardCharsets.UTF_8)));
        byte[] ts = new byte[188];
        ts[0] = 0x47;
        assertFalse(SwapHls.esPlaylist(ts));
    }

    @Test
    public void paresSwapDelTextoDescifrado() {
        String texto = "{\"url\":\"https://x/pages/y/index.html\",\"mediatype\":\"hls\",\"swap\":{\"k9x_\":\"\"}}";
        List<String[]> pares = EnlaceDrama.paresSwap(texto);
        assertEquals(1, pares.size());
        assertArrayEquals(new String[]{"k9x_", ""}, pares.get(0));
        // sin swap no hay nada que aplicar
        assertTrue(EnlaceDrama.paresSwap("{\"url\":\"https://x\"}").isEmpty());
        assertTrue(EnlaceDrama.paresSwap(null).isEmpty());
    }

    /**
     * La otra mitad de la suposición: si el panel ya sirve la playlist con el
     * cuerpo HLS de verdad, DramaSwapDataSource la reconoce como playlist (y no
     * como HTML) y se la pasa a ExoPlayer sin que el relay tenga que intermediar.
     */
    @Test
    public void laPlaylistDelPanelEsPlaylist() {
        assertTrue(SwapHls.esPlaylist(("#EXTM3U\n#EXTINF:10,\nk9x_1.js\n")
                .getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    /**
     * v7.22 — LA suposición de la que depende quitar el relay.
     *
     * El relay bajaba la playlist, le aplicaba el swap y la dejaba en un fichero
     * local con extensión .m3u8. Costaba 1.068 ms (medido en el diagnóstico real
     * del usuario). Se quita porque DramaSwapDataSource hace el MISMO swap dentro
     * del pipeline de ExoPlayer. Este test fija que ese swap funciona sobre la
     * playlist TAL Y COMO la sirve el panel: nombres de segmento relativos y con
     * el prefijo k9x_.
     */
    @Test
    public void elSwapFuncionaSobreLaPlaylistCrudaDelPanel() {
        String cruda = "#EXTM3U\n"
                + "#EXT-X-VERSION:3\n"
                + "#EXT-X-TARGETDURATION:10\n"
                + "#EXT-X-MEDIA-SEQUENCE:1791437170\n"
                + "#EXTINF:10.000000,\n"
                + "k9x_1793308141-1793310209.js\n"
                + "#EXTINF:10.000000,\n"
                + "k9x_1793308141-1793310210.js\n";
        String out = SwapHls.aplicar(cruda, Collections.<String[]>emptyList(), true);
        assertTrue(out.contains("1793308141-1793310209.js"));
        assertTrue(out.contains("1793308141-1793310210.js"));
        assertFalse("no puede quedar el prefijo que da 404", out.contains("k9x_"));
        assertEquals(cruda.replace("k9x_", ""), out);
        for (String l : out.split("\n")) {
            if (l.isEmpty() || l.charAt(0) == '#') continue;
            assertFalse("no debe absolutizar: " + l, l.startsWith("http"));
        }
    }
}
