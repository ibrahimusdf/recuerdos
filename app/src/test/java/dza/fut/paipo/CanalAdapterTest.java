package dza.fut.paipo;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

/** v3.2.5: el normalizador del tap no debe corromper el esquema propio del app. */
public class CanalAdapterTest {

    @Test
    public void normalizar_dejaIntactoElEsquemaDramaLive() {
        String u = "dramalive://play?id_live=x&u=1&a=all_streams_redirect&t=hot_now";
        assertEquals(u, CanalAdapter.normalizarUrlCanal(u, null));
        // y sigue normalizando lo de siempre
        assertEquals("https://example.com/intro.m3u8",
                CanalAdapter.normalizarUrlCanal("/p/x.html?r=https://example.com/intro.m3u8", null));
        assertNull(CanalAdapter.normalizarUrlCanal(null, null));
    }

    // ---------------------------------------------------------------
    // v7.27: el tap en la rejilla va DIRECTAMENTE al reproductor, con
    // todos los servidores ya empaquetados (como en dzasports). Antes
    // pasaba por una hoja intermedia que los volvía a listar.
    // ---------------------------------------------------------------

    private static Canal canal(String titulo, String... etiquetaUrl) {
        Canal c = new Canal();
        c.title = titulo;
        c.options = new java.util.ArrayList<>();   // los productores siempre la crean
        for (int i = 0; i + 1 < etiquetaUrl.length; i += 2) {
            Canal.OpcionCanal o = new Canal.OpcionCanal();
            o.label = etiquetaUrl[i];
            o.url = etiquetaUrl[i + 1];
            c.options.add(o);
        }
        return c;
    }

    @Test
    public void elTapLlevaTodosLosServidoresNoSoloElPrimero() {
        Canal c = canal("AMC HD", "1", "https://a.example/amc.m3u8",
                "M3U", "https://b.example/amc.m3u8",
                "Embed", "https://c.example/amc.html");

        CanalAdapter.CargaCanal carga = CanalAdapter.cargaDe(c, null);

        assertEquals("AMC HD", carga.titulo);
        assertEquals(3, carga.urls.length);
        assertEquals(3, carga.etiquetas.length);
        assertArrayEquals(new String[]{"1", "M3U", "Embed"}, carga.etiquetas);
        assertArrayEquals(new String[]{"https://a.example/amc.m3u8",
                "https://b.example/amc.m3u8", "https://c.example/amc.html"}, carga.urls);
        // Arranca por el primero, pero lleva los demás para poder cambiar.
        assertEquals(0, carga.indice);
        assertEquals("https://a.example/amc.m3u8", carga.urlInicial);
    }

    @Test
    public void siLaPrimeraOpcionEstaVaciaArrancaConLaSiguiente() {
        Canal c = canal("Fox", "1", "",
                "2", "https://b.example/fox.m3u8");

        CanalAdapter.CargaCanal carga = CanalAdapter.cargaDe(c, null);

        assertEquals(1, carga.indice);
        assertEquals("https://b.example/fox.m3u8", carga.urlInicial);
        // ...y las dos etiquetas siguen viajando, para que el chip exista.
        assertEquals(2, carga.urls.length);
        assertEquals("", carga.urls[0]);
    }

    @Test
    public void lasOpcionesSeNormalizan() {
        Canal c = canal("TNT", "1", "/p/x.html?r=https://cdn.example/tnt.m3u8",
                "2", "dramalive://play?id_live=1&u=1");

        CanalAdapter.CargaCanal carga = CanalAdapter.cargaDe(c, null);

        assertEquals("https://cdn.example/tnt.m3u8", carga.urls[0]);
        assertEquals("dramalive://play?id_live=1&u=1", carga.urls[1]);
    }

    @Test
    public void sinOpcionesNoHayNadaQueAbrir() {
        assertNull(CanalAdapter.cargaDe(null, null));

        Canal vacio = new Canal();
        vacio.title = "Nada";
        assertNull("sin opciones", CanalAdapter.cargaDe(vacio, null));

        Canal soloHuecos = canal("Huecos", "1", "", "2", "   ");
        assertNull("si ninguna opción trae URL", CanalAdapter.cargaDe(soloHuecos, null));
    }

    @Test
    public void etiquetasVaciasNoRompenNada() {
        Canal c = canal("Sin etiquetas", null, "https://a.example/x.m3u8");
        CanalAdapter.CargaCanal carga = CanalAdapter.cargaDe(c, null);
        assertEquals("", carga.etiquetas[0]);
        assertEquals("https://a.example/x.m3u8", carga.urlInicial);
    }

    @Test
    public void cargaDeNoDependeDelOrdenDeLasListas() {
        // El mismo canal por dos caminos debe dar la misma carga: es lo que
        // impedirá que la rejilla y el reproductor se peleen por el índice.
        Canal c = canal("ESPN", "HD", "https://a.example/espn.m3u8",
                "SD", "https://b.example/espn.m3u8");
        CanalAdapter.CargaCanal uno = CanalAdapter.cargaDe(c, null);
        CanalAdapter.CargaCanal dos = CanalAdapter.cargaDe(c, null);

        assertArrayEquals(uno.urls, dos.urls);
        assertArrayEquals(uno.etiquetas, dos.etiquetas);
        assertEquals(uno.indice, dos.indice);
        assertEquals(uno.urlInicial, dos.urlInicial);

        List<String> esperado = Arrays.asList("https://a.example/espn.m3u8",
                "https://b.example/espn.m3u8");
        assertEquals(esperado, Arrays.asList(uno.urls));
    }
}

