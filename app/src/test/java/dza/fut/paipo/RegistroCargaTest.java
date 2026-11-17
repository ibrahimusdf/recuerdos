package dza.fut.paipo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

/** v7.20 — el registro de carga: lo que se comparte cuando el usuario pulsa largo
 *  en la barra. Puro (sin Android), así que se prueba con JUnit secano. */
public class RegistroCargaTest {

    @Before public void limpia() { RegistroCarga.limpia(); }

    @Test public void vacioNoRevienta() {
        assertEquals(0, RegistroCarga.lineas());
        String v = RegistroCarga.volcado();
        assertTrue("tiene que decir que no hay nada medido", v.contains("sin medidas"));
        assertTrue(v.contains("DIAGNOSTICO DE CARGA"));
        assertNull("sin bloque abierto", RegistroCarga.bloque());
        assertEquals(0, RegistroCarga.desdeApertura());
    }

    @Test public void abrirPoneElBloqueYLaCabecera() {
        RegistroCarga.abrir("arranque de canal");
        assertEquals("arranque de canal", RegistroCarga.bloque());
        assertTrue(RegistroCarga.lineas() >= 1);
        assertTrue(RegistroCarga.volcado().contains("arranque de canal"));
    }

    @Test public void losPasosLlevanMilisegundos() throws Exception {
        RegistroCarga.abrir("prueba");
        Thread.sleep(30);
        RegistroCarga.paso("paso lento");
        String v = RegistroCarga.volcado();
        assertTrue("tiene que aparecer el paso", v.contains("paso lento"));
        assertTrue("tiene que llevar ms: " + v, v.contains("ms"));
        assertTrue("el tiempo acumulado tiene que ser >= lo dormido: "
                + RegistroCarga.desdeApertura(), RegistroCarga.desdeApertura() >= 25);
    }

    @Test public void elDetalleSePegaAlFinal() {
        RegistroCarga.abrir("rejilla");
        RegistroCarga.paso("catálogo remoto", "2500 canales");
        String v = RegistroCarga.volcado();
        assertTrue(v.contains("2500 canales"));
        int iNombre = v.indexOf("catálogo remoto");
        int iMs = v.indexOf("ms", iNombre);
        int iDet = v.indexOf("2500 canales");
        assertTrue("el orden es nombre, ms, detalle", iNombre < iMs && iMs < iDet);
    }

    @Test public void limpiaTiraTodo() {
        RegistroCarga.abrir("algo");
        RegistroCarga.paso("un paso", "x");
        assertTrue(RegistroCarga.lineas() > 0);
        RegistroCarga.limpia();
        assertEquals(0, RegistroCarga.lineas());
        assertNull(RegistroCarga.bloque());
        assertEquals(0, RegistroCarga.desdeApertura());
    }

    /** Medido en el diagnóstico real: si `abrir` borrara, al tocar un canal se
     *  perdía lo que había tardado la rejilla. Los dos bloques tienen que convivir. */
    @Test public void dosBloquesConvivenEnElMismoVolcado() {
        RegistroCarga.abrir("REJILLA de canales");
        RegistroCarga.paso("catálogo remoto", "2500 canales");
        RegistroCarga.abrir("arranque de canal");
        RegistroCarga.paso("resolver canal (2 POST)");
        String v = RegistroCarga.volcado();
        assertTrue("falta la rejilla", v.contains("REJILLA de canales"));
        assertTrue("falta el catálogo remoto", v.contains("2500 canales"));
        assertTrue("falta el canal", v.contains("arranque de canal"));
        assertTrue("falta la resolucion", v.contains("resolver canal (2 POST)"));
        assertEquals("el bloque vigente es el último", "arranque de canal",
                RegistroCarga.bloque());
    }

    @Test public void noCreceSinTope() {
        RegistroCarga.abrir("mucho");
        for (int i = 0; i < 500; i++) RegistroCarga.paso("paso " + i);
        assertTrue("se queda con las ultimas: " + RegistroCarga.lineas(),
                RegistroCarga.lineas() <= 300);
        assertTrue(RegistroCarga.volcado().contains("paso 499"));
    }

    @Test public void elVolcadoLlevaSelloDeHora() {
        RegistroCarga.abrir("algo");
        assertTrue("el volcado tiene que llevar fecha y hora",
                java.util.regex.Pattern
                        .compile("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}")
                        .matcher(RegistroCarga.volcado()).find());
    }

    @Test public void pasoConNullNoRevienta() {
        RegistroCarga.abrir("algo");
        RegistroCarga.paso(null);
        RegistroCarga.paso("con null", null);
        RegistroCarga.add(null);
        assertTrue(RegistroCarga.lineas() >= 3);
    }

    /**
     * v7.21 — el error de medida que hizo que el canal pareciera tardar 10.324 ms
     * cuando en realidad había arrancado en 3.568 ms: el «total» no se cerraba al
     * terminar, así que seguía corriendo mientras el usuario volvía atrás y buscaba
     * el botón de compartir. Estos tres fijan que el total es del bloque.
     */
    @Test public void finApuntaElTotalYParaElReloj() throws Exception {
        RegistroCarga.abrir("arranque de canal");
        RegistroCarga.paso("resolver canal");
        Thread.sleep(12);
        RegistroCarga.fin();
        String v = RegistroCarga.volcado();
        assertTrue("tiene que aparecer el total", v.contains("TOTAL del bloque"));
        long d1 = RegistroCarga.desdeApertura();
        Thread.sleep(25);
        assertEquals("con el bloque cerrado el reloj tiene que estar parado",
                d1, RegistroCarga.desdeApertura());
        assertNull("cerrar el bloque lo deja sin bloque abierto", RegistroCarga.bloque());
    }

    @Test public void finDosVecesNoApuntaDosTotales() throws Exception {
        RegistroCarga.abrir("arranque de canal");
        RegistroCarga.paso("resolver canal");
        RegistroCarga.fin();
        int antes = RegistroCarga.lineas();
        RegistroCarga.fin();
        assertEquals("fin() sobre un bloque cerrado no puede escribir nada",
                antes, RegistroCarga.lineas());
    }

    @Test public void finSinBloqueAbiertoNoRevienta() {
        RegistroCarga.fin();
        assertEquals(0, RegistroCarga.lineas());
        assertTrue(RegistroCarga.volcado().contains("sin medidas"));
    }

    @Test public void elVolcadoNoArrastraElTotalDelBloqueAnterior() {
        RegistroCarga.abrir("rejilla");
        RegistroCarga.paso("catálogo remoto");
        RegistroCarga.fin();
        String v = RegistroCarga.volcado();
        assertFalse("el pie no puede prometer un tiempo que no está midiendo",
                v.contains("ms desde que se abrió"));
        assertTrue("pero tiene que seguir llevando la hora", v.contains("(compartido "));
    }
}
