package dza.fut.paipo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

/**
 * v7.27 — la caché de resoluciones, que es lo que hacía falta para no volver a
 * resolver el mismo stream cada vez que se abre un canal (dzasports:
 * {@code ResolutionCache}).
 */
public class ResolucionCacheTest {

    private static final String URL = "https://cdn.example.com/pages/9/index.m3u8";

    @Before
    @After
    public void limpia() {
        ResolucionCache.clear();
    }

    private static ResolucionCache.Entrada entrada(String url) {
        return new ResolucionCache.Entrada(url, "sesion=1", "https://wrapper.example/",
                "https://cdn.example.com", null);
    }

    @Test
    public void guardaYDevuelveLoMismo() {
        ResolucionCache.Entrada e = entrada(URL);
        ResolucionCache.put(URL, e);

        ResolucionCache.Entrada fuera = ResolucionCache.get(URL);
        assertNotNull(fuera);
        assertEquals(URL, fuera.url);
        assertEquals("sesion=1", fuera.cookies);
        assertEquals("https://wrapper.example/", fuera.referer);
        assertEquals("https://cdn.example.com", fuera.origin);
        assertTrue(ResolucionCache.tiene(URL));
    }

    @Test
    public void loQueNoEstaDevuelveNull() {
        assertNull(ResolucionCache.get("https://otra.example/x.m3u8"));
        assertFalse(ResolucionCache.tiene("https://otra.example/x.m3u8"));
    }

    @Test
    public void noGuardaNadaSinStream() {
        ResolucionCache.put(URL, null);
        ResolucionCache.put(URL, new ResolucionCache.Entrada("", "", "", "", null));
        ResolucionCache.put(null, entrada(URL));
        ResolucionCache.put("", entrada(URL));
        assertEquals(0, ResolucionCache.tamano());
        assertFalse(ResolucionCache.tiene(URL));
    }

    @Test
    public void caducaALosCincoMinutos() {
        ResolucionCache.put(URL, entrada(URL));
        assertTrue("fresca debe servir", ResolucionCache.tiene(URL));

        // Casi caducada: sigue sirviendo.
        ResolucionCache.putConEdad(URL, entrada(URL), 4 * 60 * 1000);
        assertTrue("a los 4 min todavía sirve", ResolucionCache.tiene(URL));

        // Pasada: se tira sola y devuelve null.
        ResolucionCache.putConEdad(URL, entrada(URL), 6 * 60 * 1000);
        assertFalse("a los 6 min ya no", ResolucionCache.tiene(URL));
        assertNull(ResolucionCache.get(URL));
        assertEquals("la entrada caducada se borra al consultarla", 0, ResolucionCache.tamano());
    }

    @Test
    public void tamanoSoloCuentaLasFrescas() {
        ResolucionCache.put("https://a.example/1.m3u8", entrada("https://a.example/1.m3u8"));
        ResolucionCache.put("https://a.example/2.m3u8", entrada("https://a.example/2.m3u8"));
        assertEquals(2, ResolucionCache.tamano());

        ResolucionCache.putConEdad("https://a.example/2.m3u8",
                entrada("https://a.example/2.m3u8"), 10 * 60 * 1000);
        assertEquals("la caducada no cuenta", 1, ResolucionCache.tamano());
    }

    @Test
    public void clearLoVaciaTodo() {
        ResolucionCache.put(URL, entrada(URL));
        assertEquals(1, ResolucionCache.tamano());
        ResolucionCache.clear();
        assertEquals(0, ResolucionCache.tamano());
        assertNull(ResolucionCache.get(URL));
    }

    @Test
    public void losHeadersSeCopianNoSeComparten() {
        Map<String, String> h = new HashMap<>();
        h.put("User-Agent", "escritorio");
        ResolucionCache.Entrada e = new ResolucionCache.Entrada(URL, "", "", "", h);
        ResolucionCache.put(URL, e);

        // Si el llamante mutase su mapa, la cache no debe enterarse.
        h.put("Referer", "https://el-que-miente.example/");
        assertEquals("escritorio", ResolucionCache.get(URL).headers.get("User-Agent"));
        assertFalse(ResolucionCache.get(URL).headers.containsKey("Referer"));

        // Y al revés: mutar lo que devuelve get() no debe tocar lo guardado.
        ResolucionCache.get(URL).headers.put("Origin", "https://rata.example/");
        assertFalse(ResolucionCache.get(URL).headers.containsKey("Origin"));
    }

    @Test
    public void nulosSeVuelvenCadenasVacias() {
        ResolucionCache.put(URL, new ResolucionCache.Entrada(URL, null, null, null, null));
        ResolucionCache.Entrada e = ResolucionCache.get(URL);
        assertEquals("", e.cookies);
        assertEquals("", e.referer);
        assertEquals("", e.origin);
        assertNotNull(e.headers);
        assertTrue(e.headers.isEmpty());
    }

    @Test
    public void getEsInmuneANull() {
        assertNull(ResolucionCache.get(null));
        assertNull(ResolucionCache.get(""));
        assertFalse(ResolucionCache.tiene(null));
        assertSame(null, ResolucionCache.get(null));
    }
}
