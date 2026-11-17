package dza.fut.paipo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import org.junit.Test;

/** v3.2.43 — la tira de la rejilla, clon de Drama (capturas 2026-10-17). */
public class TiraCanalesTest {

    private static LinkedHashMap<String, Integer> conteo(String... kv) {
        LinkedHashMap<String, Integer> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put(kv[i], Integer.parseInt(kv[i + 1]));
        return m;
    }

    private static List<String> claves(List<String[]> tiras) {
        List<String> out = new ArrayList<>();
        for (String[] t : tiras) out.add(t[0]);
        return out;
    }

    /** v7.36 (pedido 44b): reescribe el test que fijaba «el país del aparato clavado».
     *  Ahora manda Latinoamérica: perú y argentina pasan por delante de deportes pese a
     *  llegar después y a ser menos poblados, y el chip de vivos ya no se emite. */
    @Test
    public void latinoamericaVaPrimeroYElChipDeVivosDesaparece() {
        List<String[]> t = TiraCanales.construir(
                conteo("live_tv_deportes", "9", "live_tv_peru", "3", "live_tv_argentina", "2"),
                "PE", 2, 5, true);
        assertEquals(Arrays.asList("", TiraCanales.CLAVE_FAVORITOS, TiraCanales.CLAVE_VISTOS,
                        "live_tv_peru", "live_tv_argentina", "live_tv_deportes"), claves(t));
        // dentro del bloque latinoamericano se respeta el orden de llegada: perú antes
        // que argentina, aunque argentina llegue después en el mapa y tenga menos canales
        assertEquals("\uD83C\uDDF5\uD83C\uDDEA Per\u00fa", t.get(3)[1]);
        for (String[] fila : t) {
            assertFalse("el chip de partidos en vivo está fuera (pedido 44b)",
                    TiraCanales.CLAVE_VIVOS.equals(fila[0]));
        }
    }

    /** v7.36: el mundo no latino va entero detrás, aunque llegue primero en el feed. */
    @Test
    public void elRestoDelMundoVaDespuesDeLatinoamerica() {
        List<String[]> t = TiraCanales.construir(
                conteo("live_tv_espana", "9", "live_tv_usa", "8", "live_tv_mexico", "3",
                       "live_tv_chile", "2"),
                "PE", 0, 0, false);
        assertEquals(Arrays.asList("", "live_tv_mexico", "live_tv_chile",
                        "live_tv_espana", "live_tv_usa"), claves(t));
    }

    /** v7.36: un país latino que la tabla no conocía (Guatemala) también entra en el
     *  bloque, con su bandera, y por delante del resto del mundo. */
    @Test
    public void losPaisesLatinosQueLaTablaNoConociaTambienVanPrimero() {
        assertTrue(FusionRejilla.esLatam("live_tv_guatemala"));
        assertTrue(FusionRejilla.esLatam("502"));            // por marcador telefónico
        List<String[]> t = TiraCanales.construir(
                conteo("live_tv_guatemala", "4", "live_tv_espana", "9"), "PE", 0, 0, false);
        assertEquals(Arrays.asList("", "live_tv_guatemala", "live_tv_espana"), claves(t));
    }

    /** v7.36: sin ningún tópico latino, la tira se queda como estaba. */
    @Test
    public void sinNingunLatinoLaTiraNoCambia() {
        List<String[]> t = TiraCanales.construir(
                conteo("live_tv_espana", "9", "live_tv_usa", "8"), "PE", 0, 0, false);
        assertEquals(Arrays.asList("", "live_tv_espana", "live_tv_usa"), claves(t));
    }

    @Test
    public void sinHistorialYNadaGuardadoNoHayChipsEspeciales() {
        List<String[]> t = TiraCanales.construir(
                conteo("live_tv_argentina", "2", "live_tv_deportes", "9"), "PE", 0, 0, false);
        assertEquals(Arrays.asList("", "live_tv_argentina", "live_tv_deportes"), claves(t));
        // orden de llegada, NO de población: argentina(2) antes que deportes(9)
    }

    @Test
    public void paisDelAparatoDesconocidoNoInventaChip() {
        List<String[]> t = TiraCanales.construir(
                conteo("live_tv_chile", "4", "live_tv_peru", "3"), "XX", 0, 0, false);
        // v7.36: chile y perú son los dos Latinoamérica, así que el orden de llegada
        // ya no lo rompe ningún país clavado a mano.
        assertEquals(Arrays.asList("", "live_tv_chile", "live_tv_peru"), claves(t));
        List<String[]> v = TiraCanales.construir(
                conteo("live_tv_peru", "3"), "", 0, 0, false);
        assertEquals(Arrays.asList("", "live_tv_peru"), claves(v));
    }

    @Test
    public void elPaisPropioNoSeDuplica() {
        List<String[]> t = TiraCanales.construir(
                conteo("live_tv_peru", "3"), "PE", 1, 1, false);
        int n = 0;
        for (String[] fila : t) if ("live_tv_peru".equals(fila[0])) n++;
        assertEquals(1, n);
    }

    @Test
    public void vivoDetectaElMarcadorDelFeed() {
        assertTrue(TiraCanales.esPartidoEnVivo(
                "http://.LS.V2live_tv_custom_handler_12_description_HD_/s"));
        assertTrue(TiraCanales.esPartidoEnVivo("dramalive://play?id_live=x&u=http%3A%2F%2Fx%2Flive_tv_custom_handler_9_z"));
        assertFalse(TiraCanales.esPartidoEnVivo("https://cdn.example/x.m3u8"));
        assertFalse(TiraCanales.esPartidoEnVivo(null));
    }

    @Test
    public void lasVistasViajanSerializadasYEltopMandaPorCantidad() {
        LinkedHashMap<String, Integer> v = new LinkedHashMap<>();
        v.put("s:a", 1);
        v.put("s:b", 4);
        v.put("s:c", 2);
        String s = UsoCanales.serializarVistas(v);
        assertEquals(v, UsoCanales.deserializarVistas(s));
        assertEquals(Arrays.asList("s:b", "s:c"), UsoCanales.topVistas(v, 2));
        assertTrue(UsoCanales.deserializarVistas("").isEmpty());
        assertTrue(UsoCanales.deserializarVistas("basura\ns:x\tnum").isEmpty());
    }

    @Test
    public void laClaveEstabilizaElCanal() {
        Canal c = new Canal();
        c.title = "ESPN (720p)";
        assertEquals("s:ESPN".replace("ESPN", "espn"), UsoCanales.claveDe(c)); // sin id: slug del título
        Canal.OpcionCanal o = new Canal.OpcionCanal();
        o.url = "dramalive://play?id_live=abc123&u=1";
        c.options = new ArrayList<>(Arrays.asList(o));
        assertEquals("i:abc123", UsoCanales.claveDe(c));   // con id_live manda el id
        assertTrue(UsoCanales.claveDe(null).isEmpty());
    }
}
