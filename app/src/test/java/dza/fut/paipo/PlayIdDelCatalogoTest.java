package dza.fut.paipo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

/**
 * El identificador con el que se resuelven los reproductores.
 *
 * <p>Usa una muestra del <b>catálogo real</b>
 * ({@code https://pelislatinohd.pages.dev/data/catalog.json}, 19 MB, 7.207
 * películas y 404 series) guardada en {@code test/resources}.</p>
 *
 * <p>Lo que se comprueba es justo lo que fallaba: el catálogo publica el
 * identificador en el campo {@code externalId} —IMDb ({@code tt…}) en
 * películas, id de TMDB en series— y si no se lee ese campo, {@code playId}
 * se rellena con {@code tmdb}, que es numérico siempre. Con un {@code tmdb}
 * donde debería ir un {@code tt…} ni embed69 ni vidsrc-película construyen
 * URL, y la ficha decía «este título no tiene identificadores».</p>
 */
public class PlayIdDelCatalogoTest {

    /** Muestra del catálogo real (películas, series y casos borde). */
    private static JsonObject catalogoReal() throws Exception {
        try (InputStream in = PlayIdDelCatalogoTest.class.getResourceAsStream(
                "/catalogo_real_muestra.json")) {
            assertNotNull("falta test/resources/catalogo_real_muestra.json", in);
            String txt = new Scanner(in, StandardCharsets.UTF_8.name())
                    .useDelimiter("\\A").next();
            return JsonParser.parseString(txt).getAsJsonObject();
        }
    }

    private static JsonArray array(JsonObject o, String clave) {
        JsonArray a = o.has(clave) && o.get(clave).isJsonArray() ? o.getAsJsonArray(clave) : null;
        return a == null ? new JsonArray() : a;
    }

    /** Valor en texto de un campo del catálogo ("" si no está). */
    private static String campo(JsonObject o, String clave) {
        return o.has(clave) && !o.get(clave).isJsonNull() ? o.get(clave).getAsString() : "";
    }

    /** Película de mentira, para los casos borde que el catálogo no tiene. */
    private static JsonObject obj(String title, String slug, String type,
                                  String externalId, String tmdb) {
        JsonObject o = new JsonObject();
        o.addProperty("title", title);
        o.addProperty("slug", slug);
        o.addProperty("type", type);
        if (externalId != null) o.addProperty("externalId", externalId);
        if (tmdb != null) o.addProperty("tmdb", tmdb);
        return o;
    }

    // --------------------------------------------------- películas: IMDb (tt…)

    @Test
    public void lasPeliculasDelCatalogoRealLlevanImdbEnPlayId() throws Exception {
        JsonArray movies = array(catalogoReal(), "movies");
        assertTrue("la muestra no tiene películas", movies.size() > 0);
        assertTrue("la muestra debería traer 500 películas, trae " + movies.size(),
                movies.size() >= 500);
        for (int i = 0; i < movies.size(); i++) {
            JsonObject o = movies.get(i).getAsJsonObject();
            PelisItem it = PelisApi.parseItem(o, "movie", null, false);
            assertNotNull(it);
            assertTrue("la película «" + campo(o, "title") + "» no dio item", it != null);
            assertTrue("playId de «" + it.titulo + "» no es un IMDb: " + it.playId,
                    it.playId.matches("tt\\d+"));
            // No debe colarse el tmdb numérico en el sitio del IMDb.
            assertFalse("playId de «" + it.titulo + "» es el tmdb: " + it.playId,
                    it.playId.equals(campo(o, "tmdb")));
        }
    }

    @Test
    public void unaPeliculaDelCatalogoRealConstruyeSusDosServidores() throws Exception {
        JsonArray movies = array(catalogoReal(), "movies");
        JsonObject o = movies.get(0).getAsJsonObject();
        PelisItem it = PelisApi.parseItem(o, "movie", null, false);

        PelisServidores.Servidores s = PelisServidores.servidoresDe(it, null, null);
        assertTrue("la película del catálogo real no dio ningún servidor", s.hayServidores());
        assertEquals(2, s.opciones.size());
        assertEquals("https://embed69.org/f/" + campo(o, "externalId") + "/",
                s.opciones.get(PelisServidores.NOMBRE_EMBED69));
        assertEquals("https://vidsrc.sh/embed/movie?imdb=" + campo(o, "externalId") + "&ds_lang=es",
                s.opciones.get(PelisServidores.NOMBRE_VIDSRC));
    }

    // ---------------------------------------------------- series: id de TMDB

    @Test
    public void lasSeriesDelCatalogoRealLlevanTmdbEnPlayId() throws Exception {
        JsonArray series = array(catalogoReal(), "series");
        assertTrue("la muestra no tiene series", series.size() > 0);
        // Las 404 series del catálogo real, no una muestra.
        assertTrue("la muestra debería traer las 404 series, trae " + series.size(),
                series.size() >= 400);
        for (int i = 0; i < series.size(); i++) {
            JsonObject o = series.get(i).getAsJsonObject();
            PelisItem it = PelisApi.parseItem(o, "series", null, false);
            assertNotNull(it);
            assertTrue("playId de la serie «" + it.titulo + "» no es numérico: " + it.playId,
                    it.playId.matches("\\d+"));
            assertEquals(campo(o, "tmdb"), it.playId);
        }
    }

    @Test
    public void unaSerieDelCatalogoRealConstruyeSuServidor() throws Exception {
        JsonArray series = array(catalogoReal(), "series");
        JsonObject o = series.get(0).getAsJsonObject();
        PelisItem it = PelisApi.parseItem(o, "series", null, false);

        PelisServidores.Servidores s = PelisServidores.servidoresDe(it, "1", "1");
        assertTrue("la serie del catálogo real no dio ningún servidor", s.hayServidores());
        // embed69 no tiene series: solo vidsrc.
        assertEquals(1, s.opciones.size());
        assertEquals("https://vidsrc.sh/embed/tv?tmdb=" + campo(o, "tmdb")
                        + "&season=1&episode=1&ds_lang=es",
                s.opciones.get(PelisServidores.NOMBRE_VIDSRC));
    }

    // --------------------------------------------------------- casos borde

    @Test
    public void sinExternalSeUsaElTmdbComoAntes() throws Exception {
        // Un título antiguo sin externalId: el tmdb sigue valiendo como
        // identificador, no se inventa nada.
        JsonObject o = obj("Sin external", "sin-external", "movie", null, "26200");
        PelisItem it = PelisApi.parseItem(o, "movie", null, false);
        assertEquals("26200", it.playId);
        // Con un tmdb donde debería ir un IMDb no hay servidor: no se miente.
        assertFalse(PelisServidores.servidoresDe(it, null, null).hayServidores());
    }

    @Test
    public void externalIdGanaATmdb() throws Exception {
        JsonObject o = obj("Los dos", "los-dos", "movie", "tt0081249", "26200");
        assertEquals("tt0081249", PelisApi.parseItem(o, "movie", null, false).playId);
    }
}
