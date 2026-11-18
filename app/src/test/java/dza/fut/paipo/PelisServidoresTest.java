package dza.fut.paipo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

/**
 * Servidores de reproducción de películas y episodios.
 *
 * <p>Puro, sin Android y sin red: solo se comprueba cómo se construyen las URLs
 * a partir de los ids que el catálogo ya trae ({@code PelisItem.playId}).</p>
 */
public class PelisServidoresTest {

    /** Película con IMDb, como las del catálogo. */
    private static PelisItem peli(String slug, String playId) {
        PelisItem it = new PelisItem();
        it.tipo = PelisItem.TIPO_PELICULA;
        it.slug = slug;
        it.titulo = "Titulo";
        it.playId = playId;
        return it;
    }

    /** Serie: en el catálogo playId es el id de TMDB, no un IMDb. */
    private static PelisItem serie(String slug, String playId) {
        PelisItem it = new PelisItem();
        it.tipo = PelisItem.TIPO_SERIE;
        it.slug = slug;
        it.titulo = "Serie";
        it.playId = playId;
        return it;
    }

    private static PelisItem.Episodio ep(String temporada, String episodio) {
        PelisItem.Episodio e = new PelisItem.Episodio();
        e.temporada = temporada;
        e.episodio = episodio;
        return e;
    }

    // -------------------------------------------------------------- urlEmbed69

    @Test
    public void peliculaConImdbDaUrlDeEmbed69() {
        assertEquals("https://embed69.org/f/tt0064757/",
                PelisServidores.urlEmbed69("tt0064757"));
    }

    @Test
    public void elImdbSeRecorta() {
        assertEquals("https://embed69.org/f/tt0133093/",
                PelisServidores.urlEmbed69("  tt0133093  "));
    }

    @Test
    public void sinImdbNoHayUrlDeEmbed69() {
        // Lo que llega en series (id de TMDB) y lo que no es un id válido.
        assertNull(PelisServidores.urlEmbed69("71886"));
        assertNull(PelisServidores.urlEmbed69("1396"));
        assertNull(PelisServidores.urlEmbed69(null));
        assertNull(PelisServidores.urlEmbed69(""));
        assertNull(PelisServidores.urlEmbed69("   "));
        assertNull(PelisServidores.urlEmbed69("tt"));
        assertNull(PelisServidores.urlEmbed69("ttABC"));
        assertNull(PelisServidores.urlEmbed69("0064757"));
    }

    // ---------------------------------------------------------- urlVidsrc*

    @Test
    public void vidsrcDePelicula() {
        assertEquals("https://vidsrc.sh/embed/movie?imdb=tt0111161&ds_lang=es",
                PelisServidores.urlVidsrcPelicula("tt0111161"));
    }

    @Test
    public void vidsrcDePeliculaExigeImdb() {
        assertNull(PelisServidores.urlVidsrcPelicula("71886"));
        assertNull(PelisServidores.urlVidsrcPelicula(null));
        assertNull(PelisServidores.urlVidsrcPelicula("tt"));
    }

    @Test
    public void vidsrcDeEpisodio() {
        assertEquals("https://vidsrc.sh/embed/tv?tmdb=1396&season=1&episode=3&ds_lang=es",
                PelisServidores.urlVidsrcEpisodio("1396", "1", "3"));
    }

    @Test
    public void vidsrcDeEpisodioCompletaTemporadaYEpisodio() {
        assertEquals("https://vidsrc.sh/embed/tv?tmdb=1396&season=1&episode=1&ds_lang=es",
                PelisServidores.urlVidsrcEpisodio("1396", null, null));
        assertEquals("https://vidsrc.sh/embed/tv?tmdb=1396&season=1&episode=1&ds_lang=es",
                PelisServidores.urlVidsrcEpisodio("1396", "", ""));
        assertEquals("https://vidsrc.sh/embed/tv?tmdb=1396&season=2&episode=1&ds_lang=es",
                PelisServidores.urlVidsrcEpisodio("  1396  ", "2", ""));
    }

    @Test
    public void vidsrcDeEpisodioExigeTmdb() {
        assertNull(PelisServidores.urlVidsrcEpisodio("tt0111161", "1", "1"));
        assertNull(PelisServidores.urlVidsrcEpisodio("", "1", "1"));
        assertNull(PelisServidores.urlVidsrcEpisodio(null, "1", "1"));
        assertNull(PelisServidores.urlVidsrcEpisodio("abc", "1", "1"));
    }

    // --------------------------------------------------------- servidoresDe

    @Test
    public void peliculaOfreceEmbed69YVidsrcEnEseOrden() {
        PelisServidores.Servidores s = PelisServidores.servidoresDe(peli("x", "tt0111161"), null, null);
        assertEquals(Arrays.asList(
                        "https://embed69.org/f/tt0111161/",
                        "https://vidsrc.sh/embed/movie?imdb=tt0111161&ds_lang=es"),
                s.urlsOrdenadas());
        assertEquals(Arrays.asList(
                        PelisServidores.NOMBRE_EMBED69,
                        PelisServidores.NOMBRE_VIDSRC),
                Arrays.asList(s.opciones.keySet().toArray(new String[0])));
    }

    @Test
    public void serieOfreceSoloVidsrc() {
        // embed69 no tiene series: con ellas devuelve "No folders found".
        PelisServidores.Servidores s = PelisServidores.servidoresDe(serie("s", "1396"), null, null);
        assertEquals(Arrays.asList("https://vidsrc.sh/embed/tv?tmdb=1396&season=1&episode=1&ds_lang=es"),
                s.urlsOrdenadas());
    }

    @Test
    public void episodioConcretoUsaTemporadaYEpisodio() {
        PelisServidores.Servidores s = PelisServidores.servidoresDe(serie("s", "1396"), "2", "5");
        assertEquals(Arrays.asList("https://vidsrc.sh/embed/tv?tmdb=1396&season=2&episode=5&ds_lang=es"),
                s.urlsOrdenadas());
    }

    @Test
    public void peliculaConIdDeSerieNoOfreceNada() {
        // Si el catálogo trajera un id numérico en una película, no se inventa
        // ninguna URL: embed69 y vidsrc-película exigen un IMDb.
        PelisServidores.Servidores s = PelisServidores.servidoresDe(peli("x", "71886"), null, null);
        assertFalse(s.hayServidores());
        assertTrue(s.urlsOrdenadas().isEmpty());
    }

    @Test
    public void sinPlayIdNoHayServidores() {
        assertFalse(PelisServidores.servidoresDe(peli("x", ""), null, null).hayServidores());
        assertFalse(PelisServidores.servidoresDe(peli("x", null), null, null).hayServidores());
        assertFalse(PelisServidores.servidoresDe(null, null, null).hayServidores());
    }

    @Test
    public void serieSinTmdbNoHayServidores() {
        // Una serie a la que el catálogo le puso un IMDb en playId: vidsrc-tv
        // pide TMDB numérico, así que no se puede construir nada.
        assertFalse(PelisServidores.servidoresDe(serie("s", "tt0111161"), "1", "1").hayServidores());
    }

    @Test
    public void mejorUrlEsElPrimero() {
        assertEquals("https://embed69.org/f/tt0111161/",
                PelisServidores.servidoresDe(peli("x", "tt0111161"), null, null).mejorUrl());
        assertEquals("", PelisServidores.servidoresDe(null, null, null).mejorUrl());
    }

    // --------------------------------------------------------------- etiqueta

    @Test
    public void etiquetasLegibles() {
        assertEquals(PelisServidores.NOMBRE_EMBED69, PelisServidores.etiqueta(PelisServidores.NOMBRE_EMBED69));
        assertEquals("Servidor", PelisServidores.etiqueta(null));
        assertEquals("Servidor", PelisServidores.etiqueta("   "));
    }

    // --------------------------------------------------------------- refererDe

    @Test
    public void refererDelPropioEmbed() {
        assertEquals("https://embed69.org/", PelisServidores.refererDe("https://embed69.org/f/tt1/"));
        assertEquals("https://vidsrc.sh/", PelisServidores.refererDe("https://vidsrc.sh/embed/movie?imdb=tt1"));
        assertEquals("http://181.205.130.194:4000/", PelisServidores.refererDe("http://181.205.130.194:4000/play/a07j"));
    }

    @Test
    public void refererPorDefecto() {
        assertEquals(PelisServidores.REFERER, PelisServidores.refererDe(null));
        assertEquals(PelisServidores.REFERER, PelisServidores.refererDe("sin-esquema"));
    }

    // --------------------------------------------------------------- utilidades

    @Test
    public void idiomasDistintos() {
        List<String> idiomas = PelisServidores.servidoresDe(peli("x", "tt0111161"), null, null).idiomasDistintos();
        assertEquals(Arrays.asList("Español"), idiomas);
    }
}
