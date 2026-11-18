package dza.fut.paipo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

/** v7.18: el camino directo (JSON -> Canal) no pierde ni un canal. */
public class CatalogoDirectoTest {

    private static String f(String r) throws Exception {
        File x = new File("app/src/test/resources/" + r);
        if (!x.isFile()) x = new File("src/test/resources/" + r);
        return new String(Files.readAllBytes(x.toPath()), StandardCharsets.UTF_8);
    }

    @Test public void elCaminoDirectoNoPierdeNiUno() throws Exception {
        assertEquals(600, FuenteCanales.parsearCuerpo(f("scopes_corto.json")).size());
        assertEquals(2500, FuenteCanales.parsearCuerpo(f("scopes_latam.json")).size());
    }

    @Test public void ningunCanalRoto() throws Exception {
        for (String r : new String[]{"scopes_corto.json", "scopes_latam.json"}) {
            for (Canal c : FuenteCanales.parsearCuerpo(f(r))) {
                boolean tieneUrl = false;
                if (c.options != null) {
                    for (Canal.OpcionCanal o : c.options) {
                        if (o != null && o.url != null && !o.url.trim().isEmpty()) tieneUrl = true;
                    }
                }
                assertTrue("sin url: " + c.title, tieneUrl);
                assertTrue("sin titulo", c.title != null && !c.title.trim().isEmpty());
            }
        }
    }

    /** El bug de v7.18, demostrado: la vuelta JSON -> m3u -> JSON perdia los canales
     *  que solo se diferencian en la calidad. El camino directo NO los pierde. */
    @Test public void dosCanalesQueSoloDifierenEnLaCalidadSiguenSiendoDos() throws Exception {
        String json = "{\"scope\":\"prueba\",\"tope\":2,\"total\":2,\"shards\":[],\"canales\":["
                + "{\"title\":\"SEO TV 1 (1080p)\",\"category\":\"Peru\","
                + "\"options\":[{\"url\":\"http://a/1\"}]},"
                + "{\"title\":\"SEO TV 1 (720p)\",\"category\":\"Peru\","
                + "\"options\":[{\"url\":\"http://a/2\"}]}]}";
        List<Canal> directo = FuenteCanales.parsearCuerpo(json);
        assertEquals("el camino directo se los queda a los dos", 2, directo.size());

        // ...y la vuelta por el m3u (el camino viejo) SI los fusionaba: esa era la
        // perdida de 5 en corto, 44 en latam y 282 en global.
        String m3u = CatalogoJson.aM3uDeCanales(directo);
        assertEquals("por el m3u se perdia uno", 1, ListaPegada.desdeTexto(m3u).size());
    }

    /** La rejilla pide los canales YA parseados; el m3u queda solo de respaldo. */
    @Test public void laRejillaUsaElCaminoDirecto() throws Exception {
        File g = new File("app/src/main/java/dza/fut/paipo/FuenteCanales.java");
        if (!g.isFile()) g = new File("src/main/java/dza/fut/paipo/FuenteCanales.java");
        String src = new String(Files.readAllBytes(g.toPath()), StandardCharsets.UTF_8);
        int iDirecto = src.indexOf("CatalogoRemoto.aCanales(contexto)");
        int iM3u = src.indexOf("CatalogoRemoto.aM3u(contexto)");
        assertTrue("la rejilla tiene que pedir los canales ya parseados", iDirecto >= 0);
        assertTrue("el m3u sigue ahi, pero de respaldo", iM3u >= 0);
        assertTrue("el camino directo se intenta ANTES que el m3u", iDirecto < iM3u);
    }

    @Test public void losScopesDeclaranLoQueTraen() throws Exception {
        assertEquals(600, FuenteCanales.parsearCuerpo(f("scopes_corto.json")).size());
    }
}
