package dza.fut.paipo;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Servidores de reproducción de películas y episodios.
 *
 * <p>Sustituye al resolver de cuevana3c.pro. La lista de servidores se
 * <b>construye con los ids que el catálogo ya trae</b>
 * ({@link PelisItem#playId}), así que no se hace ninguna petición de red para
 * montarla:</p>
 *
 * <pre>
 *   película : https://embed69.org/f/{imdb}/                             (LAT/ESP)
 *              https://vidsrc.sh/embed/movie?imdb={imdb}&amp;ds_lang=es
 *   episodio : https://vidsrc.sh/embed/tv?tmdb={tmdb}&amp;season={s}&amp;episode={e}&amp;ds_lang=es
 * </pre>
 *
 * <p>En el catálogo las películas llevan IMDb ({@code tt…}) y las series el id
 * de TMDB, igual que en la web. {@code embed69} solo entiende IMDb y con series
 * devuelve «No folders found», así que en episodios solo se ofrece VidSrc.</p>
 *
 * <p>Esta clase no usa nada de Android: el parseo es puro, para que
 * {@code PelisServidoresTest} pueda ejecutarlo en la JVM.</p>
 */
public final class PelisServidores {

    private PelisServidores() { }

    /** Referer de los embeds, para que el servidor no los rechace como hotlink. */
    public static final String REFERER = "https://embed69.org/";

    /** Nombre que se muestra de cada servidor, en orden de preferencia. */
    public static final String NOMBRE_EMBED69 = "Español · Embed69";
    public static final String NOMBRE_VIDSRC = "Español · VidSrc";

    /**
     * Lista de servidores de un título, en orden de preferencia.
     *
     * <p>El primero es el que antes daba el .m3u8 directamente y con audio en
     * español ({@link Embed69Resolver}); el segundo se resuelve excavando
     * iframes ({@link PelisStreamResolver}) y, si eso falla, se abre el embed
     * tal cual en el reproductor.</p>
     */
    public static class Servidores {
        /** nombre del servidor -> URL del embed, en orden de preferencia. */
        public final LinkedHashMap<String, String> opciones = new LinkedHashMap<>();
        /** nombre del servidor -> idioma. */
        public final LinkedHashMap<String, String> idiomas = new LinkedHashMap<>();

        public boolean hayServidores() {
            return !opciones.isEmpty();
        }

        /** Primera opción disponible. */
        public String mejorUrl() {
            for (Map.Entry<String, String> e : opciones.entrySet()) return e.getValue();
            return "";
        }

        public List<String> urlsOrdenadas() {
            return new ArrayList<>(opciones.values());
        }

        public List<String> idiomasDistintos() {
            Set<String> vistos = new LinkedHashSet<>(idiomas.values());
            return new ArrayList<>(vistos);
        }
    }

    /**
     * URL del embed de embed69 para una película.
     *
     * @return la URL, o {@code null} si el id no es un IMDb válido.
     */
    public static String urlEmbed69(String imdb) {
        if (!esImdb(imdb)) return null;
        return "https://embed69.org/f/" + imdb.trim() + "/";
    }

    /**
     * URL del embed de vidsrc para una película.
     *
     * @return la URL, o {@code null} si el id no es un IMDb válido.
     */
    public static String urlVidsrcPelicula(String imdb) {
        if (!esImdb(imdb)) return null;
        return "https://vidsrc.sh/embed/movie?imdb=" + imdb.trim() + "&ds_lang=es";
    }

    /**
     * URL del embed de vidsrc para un episodio.
     *
     * @return la URL, o {@code null} si el id no es un TMDB numérico.
     */
    public static String urlVidsrcEpisodio(String tmdb, String temporada, String episodio) {
        String t = limpio(tmdb);
        if (!t.matches("\\d+")) return null;
        String s = limpio(temporada);
        String e = limpio(episodio);
        if (s.isEmpty()) s = "1";
        if (e.isEmpty()) e = "1";
        return "https://vidsrc.sh/embed/tv?tmdb=" + t + "&season=" + s + "&episode=" + e + "&ds_lang=es";
    }

    /**
     * Construye la lista de servidores de un título del catálogo.
     *
     * <p>Si el título no tiene el id que hace falta, la lista queda vacía: no
     * se inventa ninguna URL.</p>
     *
     * @param item título del catálogo (con {@code playId} lleno).
     * @param se   temporada, o {@code null}/vacío para una película.
     * @param ep   episodio, o {@code null}/vacío para una película.
     */
    public static Servidores servidoresDe(PelisItem item, String se, String ep) {
        Servidores s = new Servidores();
        if (item == null) return s;

        boolean episodio = esEpisodio(se, ep);
        String playId = limpio(item.playId);

        if (episodio || item.esSerie()) {
            // Series: solo vidsrc entiende TMDB (embed69 no tiene series).
            String url = urlVidsrcEpisodio(playId, se, ep);
            if (url != null) poner(s, NOMBRE_VIDSRC, url, "Español");
            return s;
        }

        // Películas: embed69 primero (audio LAT/ESP), vidsrc de respaldo.
        String e69 = urlEmbed69(playId);
        if (e69 != null) poner(s, NOMBRE_EMBED69, e69, "Español");
        String vs = urlVidsrcPelicula(playId);
        if (vs != null) poner(s, NOMBRE_VIDSRC, vs, "Español");
        return s;
    }

    /** Etiqueta legible de cada servidor, para el diálogo de elegir servidor. */
    public static String etiqueta(String nombre) {
        return (nombre == null || nombre.trim().isEmpty()) ? "Servidor" : nombre.trim();
    }

    /**
     * Referer que hay que enviar con un embed: su propio origen, que es lo que
     * la página del player comprueba para no rechazarlo como hotlink.
     */
    public static String refererDe(String url) {
        if (url == null) return REFERER;
        int i = url.indexOf("://");
        if (i < 0) return REFERER;
        int j = url.indexOf('/', i + 3);
        return j < 0 ? url + "/" : url.substring(0, j + 1);
    }

    /** ¿Se ha pedido un episodio concreto y no la ficha entera de la serie? */
    private static boolean esEpisodio(String se, String ep) {
        return !limpio(se).isEmpty() && !limpio(ep).isEmpty();
    }

    private static void poner(Servidores s, String nombre, String url, String idioma) {
        if (s.opciones.containsKey(nombre)) return;
        s.opciones.put(nombre, url);
        s.idiomas.put(nombre, idioma);
    }

    private static boolean esImdb(String v) {
        return v != null && v.trim().matches("tt\\d+");
    }

    private static String limpio(String v) {
        return v == null ? "" : v.trim();
    }
}
