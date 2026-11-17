package dza.fut.paipo;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * v7.36.2 — el MIME de las URLs sin extensión.
 *
 * mediaItemPara() forzaba APPLICATION_M3U8 en toda URL que no acabase en .m3u8.
 * Eso hacía falta para las páginas de player (.php/.html) que sirven un playlist
 * sin llevarlo en el nombre, pero era FALSO para una URL como /play/a07j, que
 * es TS binario en crudo: mandarla al parser de playlists HLS la rompe.
 *
 * Estas son las URLs reales del catálogo.
 */
public class MimeUrlTest {

    @Test
    public void phpEsPaginaDePlayer() {
        assertTrue(PlayerActivity.parecePaginaPlayer(
                "https://embed.saohgdasregions.fun/embed2/espn.php"));
    }

    @Test
    public void phpConQueryEsPaginaDePlayer() {
        assertTrue(PlayerActivity.parecePaginaPlayer(
                "https://tvf90.com/online.php?stream=espn"));
    }

    @Test
    public void htmlConUrlEnBase64EsPaginaDePlayer() {
        assertTrue(PlayerActivity.parecePaginaPlayer(
                "https://alilus.github.io/lobinnnnnnnsnsnsn/m3u8player.html?url=aHR0cHM6Ly96dG5y"));
    }

    @Test
    public void tsBinarioSinExtensionNoEsPaginaDePlayer() {
        assertFalse(PlayerActivity.parecePaginaPlayer(
                "http://181.205.130.194:4000/play/a07j"));
    }

    @Test
    public void rastraSinExtensionNoEsPaginaDePlayer() {
        assertFalse(PlayerActivity.parecePaginaPlayer(
                "http://200.10.30.241:8000/play/a07i"));
    }

    @Test
    public void m3u8ConQueryDaTruePeroNoLlegaAUsarse() {
        // Lleva ?DVR0, asi que parecePaginaPlayer dice true. No importa:
        // mediaItemPara mira .m3u8 ANTES, asi que a esta URL nunca se le
        // consulta. El test existe para que quede dicho.
        assertTrue(PlayerActivity.parecePaginaPlayer(
                "https://imagenuniversaltv.net:3771/hybrid/play.m3u8?DVR0"));
    }

    @Test
    public void nullNoEsPaginaDePlayer() {
        assertFalse(PlayerActivity.parecePaginaPlayer(null));
    }

    @Test
    public void mayusculasNoEngañan() {
        assertTrue(PlayerActivity.parecePaginaPlayer(
                "http://ejemplo.com/EMBED/ESPN.PHP"));
        assertFalse(PlayerActivity.parecePaginaPlayer(
                "http://ejemplo.com/PLAY/A07J"));
    }
}
