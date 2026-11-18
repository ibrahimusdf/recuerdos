package dza.fut.paipo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * v7.39.1 — el guardián contra «clico un canal y se queda en otro».
 *
 * <p>El reproductor es {@code singleTask}: el segundo canal se abre SOBRE la misma
 * instancia. Lo que queda vivo del primero (carrera, glasbi, WebView, vigilantes del
 * embed) volvía y remataba al segundo. La generación es lo que lo impide: cada respuesta
 * tardía lleva el número con el que nació y se descarta si ya no es el actual.</p>
 */
public class GeneracionDelCanalTest {

    @Test public void alNacerTodoLoQueVuelveEsDelCanalActual() {
        GeneracionDelCanal g = new GeneracionDelCanal();
        int canal1 = g.abrirCanal();
        assertTrue(g.vigente(canal1));
        assertEquals(GeneracionDelCanal.Decision.APLICAR, g.decidir(canal1));
    }

    /**
     * El caso exacto del usuario: abre el canal A, su carrera sigue sondeando; toca el
     * canal B; la ganadora de A llega 2 s después. Antes se le aplicaba a B (se veía A
     * con el título de B, o A directamente). Ahora se descarta.
     */
    @Test public void laGanadoraDelCanalAnteriorSeDescarta() {
        GeneracionDelCanal g = new GeneracionDelCanal();
        int canalA = g.abrirCanal();
        int canalB = g.abrirCanal();

        assertFalse(g.vigente(canalA));
        assertEquals(GeneracionDelCanal.Decision.DESCARTAR, g.decidir(canalA));
        assertTrue(g.vigente(canalB));
        assertEquals(GeneracionDelCanal.Decision.APLICAR, g.decidir(canalB));
    }

    /** Tres canales seguidos: solo el último manda; los dos anteriores quedan caducados. */
    @Test public void soloElUltimoCanalManda() {
        GeneracionDelCanal g = new GeneracionDelCanal();
        int a = g.abrirCanal();
        int b = g.abrirCanal();
        int c = g.abrirCanal();
        assertFalse(g.vigente(a));
        assertFalse(g.vigente(b));
        assertTrue(g.vigente(c));
    }

    /**
     * La generación no se reutiliza: abrir el MISMO canal otra vez también invalida lo
     * anterior. Reabrir el canal A no debe dejar entrar la respuesta de la primera vez.
     */
    @Test public void reabrirElMismoCanalTambienInvalida() {
        GeneracionDelCanal g = new GeneracionDelCanal();
        int primeraVez = g.abrirCanal();
        int segundaVez = g.abrirCanal();       // mismo canal, nueva apertura
        assertFalse(g.vigente(primeraVez));
        assertTrue(g.vigente(segundaVez));
    }

    /** Lo que llega del futuro (un número que aún no existe) tampoco entra. */
    @Test public void unaGeneracionQueNoExisteNoEsVigente() {
        GeneracionDelCanal g = new GeneracionDelCanal();
        g.abrirCanal();
        assertFalse(g.vigente(999));
        assertFalse(g.vigente(-1));
    }

    /**
     * Y el caso del logcat que originó todo esto: cuatro respuestas del canal viejo
     * llegando escalonadas después de cambiar. Ninguna entra.
     */
    @Test public void lasCuatroRespuestasDelCanalViejoLleganYSeTiranTodas() {
        GeneracionDelCanal g = new GeneracionDelCanal();
        int viejo = g.abrirCanal();
        int nuevo = g.abrirCanal();                 // el usuario ya cambió de canal
        int aplicadas = 0;
        for (int i = 0; i < 4; i++) {
            if (g.decidir(viejo) == GeneracionDelCanal.Decision.APLICAR) aplicadas++;
        }
        assertEquals(0, aplicadas);
        assertEquals(2, g.actual());
        assertTrue(g.vigente(nuevo));
    }
}
