package dza.fut.paipo;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * v7.36.1 — el arreglo del «en la app solo se oye el audio».
 *
 * Un canal cuyo vídeo es MPEG-2 NO da error en ExoPlayer: el AAC se decodifica,
 * la pista de vídeo no se selecciona y el reproductor llega a READY. La app se
 * quedaba quieta en esa opción. HayVideoUtil es la decisión pura que detecta el
 * caso y hace saltar al servidor siguiente.
 */
public class SinVideoTest {

    private static boolean llamado(boolean... esVideo) {
        boolean[] sop = new boolean[esVideo.length];
        java.util.Arrays.fill(sop, true);
        return PlayerActivity.hayVideoUtil(esVideo, sop);
    }

    @Test
    public void videoNormal_seQueda() {
        assertTrue(llamado(true));
    }

    @Test
    public void videoConAudio_seQueda() {
        assertTrue(llamado(true, false));
    }

    @Test
    public void audioSolo_salta() {
        assertFalse(llamado(false));
    }

    @Test
    public void soloAudioAunqueHayaVariasPistasDeAudio_salta() {
        assertFalse(llamado(false, false, false));
    }

    /** El caso real: hay pista de vídeo, pero el móvil no sabe decodificarla. */
    @Test
    public void videoQueNoSePuedeDecodificar_salta() {
        assertFalse(PlayerActivity.hayVideoUtil(new boolean[]{true}, new boolean[]{false}));
    }

    /** Vídeo no soportado acompañado de audio soportado: sigue sin verse nada. */
    @Test
    public void videoMuertoConAudioVivo_salta() {
        assertFalse(PlayerActivity.hayVideoUtil(
                new boolean[]{false, true}, new boolean[]{true, false}));
    }

    /** Si hay dos pistas de vídeo y una sí va, esa se usa. */
    @Test
    public void conUnaVarianteDeVideoQueSiVa_seQueda() {
        assertTrue(PlayerActivity.hayVideoUtil(
                new boolean[]{true, true}, new boolean[]{false, true}));
    }

    /** Sin pistas todavía: no se inventa un fallo y no se salta. */
    @Test
    public void sinPistas_noSalta() {
        assertTrue(PlayerActivity.hayVideoUtil(new boolean[0], new boolean[0]));
    }
}
