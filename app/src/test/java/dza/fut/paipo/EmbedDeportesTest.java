package dza.fut.paipo;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * v7.36.3 — el atajo de los embeds de deportes (ESPN y compañía).
 *
 * El log del usuario mostraba la cadena entera funcionando —mono.m3u8 y los
 * .ts descargándose con HTTP 200— y aun así «StreamResolver no devolvió
 * stream». La causa: resolverEmbedDeportes buscaba playlist.php?id=N&sig=...,
 * pero la web cambió y ahora el maestro .m3u8 va suelto en el HTML de
 * stream.php.
 *
 * Estas pruebas van contra la HTML REAL de
 * deportes.ksdjugfssddeports.com/stream.php, guardada tal cual.
 */
public class EmbedDeportesTest {

    private static String htmlReal() throws Exception {
        try (InputStream in = EmbedDeportesTest.class.getResourceAsStream("/deportes_stream.html")) {
            assertNotNull("falta el fichero de prueba deportes_stream.html", in);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    @Test
    public void laWebNuevaDaElM3u8Sueto() throws Exception {
        String p = StreamResolver.playlistDelReproductor(htmlReal());
        assertNotNull("no encontro el HLS en la HTML real", p);
        System.out.println("### HLS encontrado: " + p);
        assertTrue("no es .m3u8: " + p, p.contains(".m3u8"));
    }

    @Test
    public void elHostEsElDelStream() throws Exception {
        String p = StreamResolver.playlistDelReproductor(htmlReal());
        assertNotNull(p);
        assertTrue("host inesperado: " + p, p.startsWith("https://deportes.ksdjugfssddeports.com/"));
    }

    @Test
    public void elTokenViajaConLaUrl() throws Exception {
        String p = StreamResolver.playlistDelReproductor(htmlReal());
        assertNotNull(p);
        assertTrue("no lleva token: " + p, p.contains("token="));
    }

    /** La cadena vieja tenía que seguir funcionando donde exista. */
    @Test
    public void siHayPlaylistPhpSeUsaEse() {
        String html = "var x='https://cdn.ejemplo.com\\/playlist.php?id=13_&sig=abc123';";
        String p = StreamResolver.playlistDelReproductor(html);
        assertNotNull(p);
        assertTrue("tenia que salir el playlist.php: " + p, p.contains("playlist.php"));
        assertTrue(p.contains("id=13_"));
    }

    @Test
    public void sinNadaDevuelveNull() {
        assertNull(StreamResolver.playlistDelReproductor("<html>nada por aqui</html>"));
        assertNull(StreamResolver.playlistDelReproductor(null));
        assertNull(StreamResolver.playlistDelReproductor(""));
    }

    @Test
    public void losAmpersandEscapadosSeArreglan() {
        String html = "src='https://cdn.ejemplo.com\\/a\\/y.m3u8?a=1&amp;b=2'";
        String p = StreamResolver.playlistDelReproductor(html);
        assertNotNull(p);
        assertTrue("quedó el &amp;: " + p, !p.contains("&amp;"));
        assertTrue(p.contains("a=1&b=2"));
    }
}
