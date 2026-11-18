package dza.fut.paipo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * v7.35 — la política de desalojo de la caché de losas.
 *
 * <p>Es Java puro a propósito: si la caché se pasa de presupuesto, el que paga es
 * el usuario con un cierre por memoria; si se queda corta, la rejilla vuelve a ir
 * lenta. Las dos cosas se deciden aquí y ninguna se ve en la pantalla hasta que
 * es tarde.
 */
public class LruPesoTest {

    /** Peso de mentira: 1 KB por letra del VALOR (el peso se aplica al valor). */
    private static final LruPeso.Peso<String> PESO = s -> (long) s.length() * 1024L;

    /** Valores de 1 KB, para que las cuentas salgan redondas. */
    private static final String UN_KB = "x";

    private LruPeso<String, String> cache(long tope) {
        return new LruPeso<>(tope, PESO);
    }

    @Test
    public void guardaYDevuelve() {
        LruPeso<String, String> c = cache(1024 * 1024);
        c.put("amc", UN_KB);
        assertEquals(UN_KB, c.get("amc"));
        assertEquals(1, c.tamano());
    }

    @Test
    public void respetaElPresupuesto() {
        // Cuatro entradas de 1 KB con un tope de 4 KB justos: caben las cuatro.
        LruPeso<String, String> c = cache(4 * 1024L);
        c.put("aaaa", UN_KB);
        c.put("bbbb", UN_KB);
        c.put("cccc", UN_KB);
        c.put("dddd", UN_KB);
        assertTrue("no puede pasarse del tope: usado=" + c.usado(), c.usado() <= 4 * 1024L);
        assertEquals(4, c.tamano());
    }

    @Test
    public void desalojaElMenosUsadoNoElMasNuevo() {
        // Tres de 1 KB con tope de 3 KB. La cuarta desborda y desaloja una.
        LruPeso<String, String> c = cache(3 * 1024L);
        c.put("aaaa", UN_KB);
        c.put("bbbb", UN_KB);
        c.put("cccc", UN_KB);
        // "aaaa" se acaba de usar, así que pasa al final del orden de desalojo.
        assertNotNull(c.get("aaaa"));
        c.put("dddd", UN_KB);
        assertNull("el menos usado tiene que caer", c.get("bbbb"));
        assertNotNull("el recien usado se queda", c.get("aaaa"));
        assertNotNull(c.get("cccc"));
        assertNotNull(c.get("dddd"));
    }

    @Test
    public void unaEntradaMayorQueElTopeNoSeQueda() {
        // La regla es no pasarse NUNCA del presupuesto: si una entrada sola pesa
        // mas que el tope, se va ella. Para una cache de memoria es el fallo
        // seguro (con losas de 774 KB y 12 MB de tope no puede ocurrir).
        LruPeso<String, String> c = cache(1024L);
        c.put("nombre-larguisimo-que-no-cabe", "xxx");   // 3 KB > 1 KB de tope
        assertNull(c.get("nombre-larguisimo-que-no-cabe"));
        assertEquals(0, c.tamano());
        assertTrue("y el presupuesto queda intacto", c.usado() <= 1024L);
    }

    @Test
    public void topeNegativoDejaLaCacheApagada() {
        LruPeso<String, String> c = cache(-1L);
        c.put("aaaa", UN_KB);
        assertEquals(0, c.tamano());
    }

    @Test
    public void actualizarUnaClaveNoCuentaDosVeces() {
        LruPeso<String, String> c = cache(1024 * 1024);
        c.put("aaaa", UN_KB);
        c.put("aaaa", UN_KB);
        assertEquals("una sola entrada", 1, c.tamano());
        assertEquals("y su peso contado una vez", 1024L, c.usado());
    }

    @Test
    public void limpiarVaciaYTambienElCuento() {
        LruPeso<String, String> c = cache(1024 * 1024);
        c.put("aaaa", UN_KB);
        c.put("bbbb", UN_KB);
        c.limpiar();
        assertEquals(0, c.tamano());
        assertEquals(0L, c.usado());
    }

    /**
     * El caso real: losas de 774 KB (440×440×4) con un presupuesto de 12 MB.
     * Tiene que cabir más de una pantalla de rejilla, y no desbordar nunca.
     */
    @Test
    public void conElTamanoRealDeLosaCabeMasDeUnaPantalla() {
        final long losa = 440L * 440L * 4L;   // 774.400 B
        LruPeso<Integer, String> c = new LruPeso<>(Logos.PRESUPUESTO_LOSAS, v -> losa);
        for (int i = 0; i < 40; i++) c.put(i, "losa" + i);
        assertTrue("con 12 MB tienen que cabir al menos 15 losas, caben " + c.tamano(),
                c.tamano() >= 15);
        assertTrue("y nunca pasarse del presupuesto", c.usado() <= Logos.PRESUPUESTO_LOSAS);
        assertEquals("la ultima usada tiene que seguir ahi", "losa39", c.get(39));
    }
}
