package dza.fut.paipo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Caché de resoluciones de stream, con caducidad.
 *
 * <p>Es el patrón de dzasports ({@code ResolutionCache} + {@code PlaybackOrchestrator}),
 * que es justo lo que aquí faltaba: cada vez que se abría un canal se volvía a
 * resolver el stream <b>desde cero</b> — carrera de 4 estrategias con WebViews
 * incluidos — aunque se acabara de abrir ese mismo canal. Con esto, la segunda
 * vez es instantánea.</p>
 *
 * <p>Vive <b>fuera</b> de {@code StreamResolver} y de las clases de Drama a
 * propósito: guarda un valor propio y plano ({@link Entrada}), así que no
 * arrastra Android ni OkHttp ni WebView, y se puede probar en JVM puro. Esa
 * separación es la que permite cambiar el resolvedor sin tocar la caché.</p>
 *
 * <p>Caduca a los 5 minutos: un m3u8 firmado o un token de sesión se mueren
 * antes o después, y servir uno podrido es peor que volver a resolver.</p>
 */
public final class ResolucionCache {

    private static final long TTL_MS = 5L * 60 * 1000;

    /** Una resolución ya hecha: lo mínimo para que ExoPlayer arranque. */
    public static final class Entrada {
        public final String url;
        public final String cookies;
        public final String referer;
        public final String origin;
        /** Copia defensiva; nunca null. */
        @NonNull public final Map<String, String> headers;

        public Entrada(String url, String cookies, String referer, String origin,
                       @Nullable Map<String, String> headers) {
            this.url = url;
            this.cookies = cookies == null ? "" : cookies;
            this.referer = referer == null ? "" : referer;
            this.origin = origin == null ? "" : origin;
            this.headers = headers == null ? new HashMap<>() : new HashMap<>(headers);
        }

        public boolean sirve() {
            return url != null && !url.isEmpty();
        }

        /** Copia defensiva: quien la recibe no puede corromper la caché. */
        Entrada copia() {
            return new Entrada(url, cookies, referer, origin, headers);
        }
    }

    private static final class Guardada {
        final Entrada entrada;
        final long cuando;

        Guardada(Entrada entrada, long cuando) {
            this.entrada = entrada;
            this.cuando = cuando;
        }

        boolean caducada(long ahora) {
            return ahora - cuando >= TTL_MS;
        }
    }

    private static final Map<String, Guardada> CACHE = new ConcurrentHashMap<>();

    private ResolucionCache() {}

    /** La resolución de {@code url}, o null si no está o ya caducó. */
    @Nullable
    public static Entrada get(@Nullable String url) {
        if (url == null || url.isEmpty()) return null;
        Guardada g = CACHE.get(url);
        if (g == null) return null;
        if (g.caducada(System.currentTimeMillis())) {
            CACHE.remove(url, g);
            return null;
        }
        // Copia: si el reproductor toca los headers, no envenena la caché.
        return g.entrada.copia();
    }

    /** Guarda una resolución. Las que no traen stream no se guardan. */
    public static void put(@Nullable String url, @Nullable Entrada entrada) {
        if (url == null || url.isEmpty() || entrada == null || !entrada.sirve()) return;
        CACHE.put(url, new Guardada(entrada, System.currentTimeMillis()));
    }

    /**
     * Igual que {@link #put}, pero con una edad artificial. Solo existe para que
     * la suite pueda comprobar la caducidad sin esperarse cinco minutos.
     */
    static void putConEdad(@Nullable String url, @Nullable Entrada entrada, long edadMs) {
        if (url == null || url.isEmpty() || entrada == null || !entrada.sirve()) return;
        CACHE.put(url, new Guardada(entrada, System.currentTimeMillis() - edadMs));
    }

    /** ¿Hay algo guardado y fresco para {@code url}? */
    public static boolean tiene(@Nullable String url) {
        return get(url) != null;
    }

    /** Tira todo. Se usa al salir del reproductor y al forzar recarga. */
    public static void clear() {
        CACHE.clear();
    }

    /** Cuántas entradas válidas hay ahora mismo (para pruebas y depuración). */
    public static int tamano() {
        long ahora = System.currentTimeMillis();
        int n = 0;
        for (Guardada g : CACHE.values()) if (!g.caducada(ahora)) n++;
        return n;
    }
}
