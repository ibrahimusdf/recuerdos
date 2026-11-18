package dza.fut.paipo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

/**
 * v7.38.8 — el orden en que se prueban los servidores de un canal.
 *
 * <p>Antes de esta versión el plan B solo sabía dos cosas: lo que Drama entregaba y lo
 * que la app recordaba de ese canal. Ahora hay una tercera fuente (lo que la API de la
 * cadena probó que emite) y el orden entre las tres es una decisión de producto, no un
 * detalle: si se equivoca, el usuario mira 30 s de negro antes de que algo abra.</p>
 *
 * <p>Todo puro: corre en CI sin red ni dispositivo.</p>
 */
public class PlanDeAtaqueTest {

    private static final String BASE = "https://dramalive-api-08th.onrender.com";
    private static final String CODIGO = "232425";
    private static final String ID = "live_tv_beinsport1";

    private static DramaResolucion.Opcion op(String url, DramaResolucion.Tipo t) {
        return new DramaResolucion.Opcion(url, t, null, null, "", false);
    }

    private static DramaResolucion.Opcion proxy(int n) {
        return op(BASE + "/p/" + CODIGO + "/" + ID + "/" + n + ".m3u8",
                DramaResolucion.Tipo.DIRECTA);
    }

    /** Las 6 que la API entrega para beIN 1: proxy y página, intercaladas. */
    private static List<DramaResolucion.Opcion> api() {
        return new ArrayList<>(Arrays.asList(
                proxy(1), op("https://sup7.superdatanet.cfd/hd/azmyqsojuqbm/index.html",
                        DramaResolucion.Tipo.EMBEBIDA),
                proxy(2), op("https://ult11.ultraandroidsoftstarsfeedmoon.shop/fk/xfwurjrfwhim/index.html",
                        DramaResolucion.Tipo.EMBEBIDA),
                proxy(0), op("https://spe6.speedsuperai.cfd/pages/alkoaqoohqze/index.html",
                        DramaResolucion.Tipo.EMBEBIDA)));
    }

    private static List<DramaResolucion.Opcion> drama() {
        return new ArrayList<>(Arrays.asList(
                op("https://ordernap.net/e/z7iiiknxnuqjk", DramaResolucion.Tipo.EMBEBIDA),
                op("https://quotarevival.net/e/yxtmusy4xj8tj", DramaResolucion.Tipo.EMBEBIDA)));
    }

    private static List<String> urls(List<DramaResolucion.Opcion> l) {
        List<String> o = new ArrayList<>();
        for (DramaResolucion.Opcion x : l) o.add(x.url);
        return o;
    }

    // ────────────────────────── el orden ──────────────────────────

    @Test
    public void laMemoriaDelCanalVaSiemprePrimero() {
        List<DramaResolucion.Opcion> mem = new ArrayList<>();
        mem.add(op("https://spe6.speedsuperai.cfd/pages/alkoaqoohqze/index.html",
                DramaResolucion.Tipo.EMBEBIDA));
        List<DramaResolucion.Opcion> p =
                DramaResolucion.planDeAtaque(mem, api(), drama());
        assertEquals("https://spe6.speedsuperai.cfd/pages/alkoaqoohqze/index.html", p.get(0).url);
        assertEquals(1, p.get(0).tipo == DramaResolucion.Tipo.EMBEBIDA ? 1 : 0);
    }

    @Test
    public void laPlaylistProxadaVaDelanteDeSuPagina() {
        List<DramaResolucion.Opcion> p =
                DramaResolucion.planDeAtaque(null, api(), null);
        assertEquals(6, p.size());
        for (int i = 0; i < 6; i += 2) {
            assertEquals(DramaResolucion.Tipo.DIRECTA, p.get(i).tipo);
            assertTrue("debe ser del proxy: " + p.get(i).url, p.get(i).url.contains("/p/"));
            assertTrue("debe terminar en .m3u8", p.get(i).url.endsWith(".m3u8"));
            assertEquals(DramaResolucion.Tipo.EMBEBIDA, p.get(i + 1).tipo);
        }
    }

    @Test
    public void loQueTraeDramaVaAlFinal() {
        List<DramaResolucion.Opcion> p =
                DramaResolucion.planDeAtaque(null, api(), drama());
        assertEquals(8, p.size());
        assertTrue(p.get(6).url.contains("ordernap"));
        assertTrue(p.get(7).url.contains("quotarevival"));
    }

    /**
     * El fallo que este método evita: {@code conRecordados} pasa las nuevas por
     * {@code ordenarSinRepetir}, que pone TODAS las EMBEBIDA antes que las DIRECTA. Con
     * eso la página lenta se colaría delante de la playlist proxada, al revés de lo que
     * interesa.
     */
    @Test
    public void elPlanNoReordenaLoQueDiceLaApi() {
        List<DramaResolucion.Opcion> p = DramaResolucion.planDeAtaque(null, api(), null);
        assertEquals(urls(api()), urls(p));
        // y el atajo antiguo SÍ habría reordenado:
        List<DramaResolucion.Opcion> conElViejo = DramaResolucion.conRecordados(null, api());
        assertTrue("ordenarSinRepetir sube las embebidas",
                conElViejo.get(0).tipo == DramaResolucion.Tipo.EMBEBIDA);
    }

    // ────────────────────────── sin duplicados ──────────────────────────

    @Test
    public void noRepiteLaMismaUrlEntreLasTresFuentes() {
        List<DramaResolucion.Opcion> mem = new ArrayList<>();
        mem.add(op("https://spe6.speedsuperai.cfd/pages/alkoaqoohqze/index.html",
                DramaResolucion.Tipo.EMBEBIDA));
        List<DramaResolucion.Opcion> p =
                DramaResolucion.planDeAtaque(mem, api(), drama());
        HashSet<String> v = new HashSet<>();
        for (DramaResolucion.Opcion o : p) {
            assertTrue("repetida: " + o.url, v.add(DramaResolucion.clave(o.url)));
        }
        assertEquals(8, p.size());   // 1 memoria + 6 API (una coincide con la memoria) + 2 Drama
    }

    @Test
    public void ignoraMarcadoresYNulos() {
        List<DramaResolucion.Opcion> conBasura = new ArrayList<>();
        conBasura.add(null);
        conBasura.add(op("http://.LS.V2lo_que_sea/s", DramaResolucion.Tipo.MARCADOR));
        conBasura.add(proxy(3));
        List<DramaResolucion.Opcion> p =
                DramaResolucion.planDeAtaque(conBasura, null, null);
        assertEquals(1, p.size());
        assertTrue(p.get(0).url.endsWith("/3.m3u8"));
    }

    // ─────────────────────── casos degradados ───────────────────────

    @Test
    public void sinApiEsExactamenteLaV387() {
        List<DramaResolucion.Opcion> p = DramaResolucion.planDeAtaque(null, null, drama());
        assertEquals(urls(drama()), urls(p));
        assertTrue(DramaResolucion.planDeAtaque(null, new ArrayList<>(), drama()).size() == 2);
    }

    @Test
    public void todoVacioONuloNoExplota() {
        assertTrue(DramaResolucion.planDeAtaque(null, null, null).isEmpty());
        assertTrue(DramaResolucion.planDeAtaque(new ArrayList<>(), new ArrayList<>(),
                new ArrayList<>()).isEmpty());
    }

    // ─────────────── el selector completo (5 argumentos) ───────────────

    @Test
    public void elSelectorDeCincoRespetaElPlanYSeSaltaLoProbado() {
        List<DramaResolucion.Opcion> api = api();
        HashSet<String> probados = new HashSet<>();

        DramaResolucion.Opcion a =
                PlayerActivity.siguienteOpcionUtil(null, null, probados, null, api);
        assertEquals(api.get(0).url, a.url);
        probados.add(a.url);

        DramaResolucion.Opcion b =
                PlayerActivity.siguienteOpcionUtil(null, null, probados, null, api);
        assertEquals(api.get(1).url, b.url);
        probados.add(b.url);

        // Se marcan todas: ya no queda ninguna
        for (DramaResolucion.Opcion o : api) probados.add(o.url);
        assertNull(PlayerActivity.siguienteOpcionUtil(null, null, probados, null, api));
    }

    @Test
    public void elSelectorSigueFuncionandoSinApiYSinMemoria() {
        // Formato real del backup de Drama: trozos separados por "-;-"
        String backup = "https://ordernap.net/e/aaa -- Mozilla -;- "
                + "http://.LS.V2marcador/s -- redirect";
        DramaResolucion.Opcion o =
                PlayerActivity.siguienteOpcionUtil(null, backup, new HashSet<>(), null, null);
        assertTrue(o.url.contains("ordernap"));
    }

    /** La sobrecarga de 4 (v7.38.7) sigue viva: hay código y tests que la usan. */
    @Test
    public void laSobrecargaDeCuatroSigueExistiendo() {
        String backup = "https://ordernap.net/e/aaa -- Mozilla -;- "
                + "http://.LS.V2marcador/s -- redirect";
        assertTrue(PlayerActivity.siguienteOpcionUtil(null, backup,
                new HashSet<>(), null) != null);
        assertTrue(PlayerActivity.siguienteOpcionUtil(null, backup,
                new HashSet<>()) != null);
    }
}
