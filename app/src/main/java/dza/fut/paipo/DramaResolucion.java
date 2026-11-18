package dza.fut.paipo;

import androidx.annotation.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/**
 * v7.38.0 — TODOS los servidores de un canal de Drama, sin perder ninguno.
 *
 * <p><b>Lo que estaba mal (medido 2026-11-17 contra el servidor real).</b> El
 * campo {@code backup} de glasbi es una lista separada por {@code " -;- "} en la
 * que cada entrada es UNA de estas tres cosas:
 *
 * <pre>
 *   http://.LS.V2live_tv_custom_handler_…_SD_/s        -- redirect
 *   {"url":"https://ordernap.net/e/…","agent":"Mozilla…","iframe":"…",
 *    "headers":{"Referer":"…"},"description":"DLW"}  -- double_redirect
 *   http://primetv.cc.cd/beinfhd11/…/mono.tbin.m3u8    -- Mozilla
 * </pre>
 *
 * La app se quedaba con la PRIMERA «no marcador» y tiraba el resto. En beIN eso
 * significaba quedarse justo con la directa que da 403 y perder las embebidas
 * que sí emiten (ocho servidores reducidos a uno). Además se ignoraba el
 * {@code agent}/{@code headers} que el propio proveedor manda para cada opción:
 * sin ellos el CDN contesta 403.
 *
 * <p>Esta clase solo ordena y clasifica: no abre red. Se prueba en JVM.
 */
public final class DramaResolucion {

    /** De qué clase es cada servidor. */
    public enum Tipo {
        /** Marcador {@code .LS.V2…}: solo lo resuelve el glbr del sitio, que hoy da 404. */
        MARCADOR,
        /** Página de embed (iframe con su propio reproductor). Es la que emite. */
        EMBEBIDA,
        /** Playlist/stream directo (m3u8, mp4…). */
        DIRECTA,
        /** Página normal (YouTube, un portal…): se puede abrir, pero no es un stream. */
        PAGINA
    }

    /** Un servidor del canal, con TODO lo que el proveedor manda para él. */
    public static final class Opcion {
        public final String url;
        public final Tipo tipo;
        public final String ua;         // User-Agent que exige ESTE servidor
        public final String referer;    // Referer/Origin que exige ESTE servidor
        public final String descripcion;
        public final boolean iframe;    // el proveedor pide meterlo en un iframe
        /** v7.38.1 — «KID:CLAVE» en hexadecimal si el canal va cifrado en ClearKey. */
        public final String clearKey;

        Opcion(String url, Tipo tipo, String ua, String referer,
               String descripcion, boolean iframe) {
            this(url, tipo, ua, referer, descripcion, iframe, null);
        }

        Opcion(String url, Tipo tipo, String ua, String referer,
               String descripcion, boolean iframe, String clearKey) {
            this.url = url;
            this.tipo = tipo;
            this.ua = ua;
            this.referer = referer;
            this.descripcion = descripcion == null ? "" : descripcion;
            this.iframe = iframe;
            this.clearKey = clearKey;
        }

        /** Texto de una línea para el log y para el chip de servidores. */
        public String etiqueta() {
            String d = descripcion.isEmpty() ? "" : (" " + descripcion);
            return tipo + d + " → " + url;
        }

        @Override public String toString() { return etiqueta(); }
    }

    private DramaResolucion() { }

    /**
     * Todos los servidores del canal, en orden de utilidad y sin repetir.
     *
     * @param url     {@code live.url} (suele ser un marcador)
     * @param backup  {@code live.backup} en crudo
     */
    public static List<Opcion> opciones(@Nullable String url, @Nullable String backup) {
        List<Opcion> todas = new ArrayList<>();
        if (url != null && !url.trim().isEmpty()) todas.add(deTexto(url.trim(), null, null));
        // v7.38.4: el troceado del backup vive en un solo sitio (DramaSesion), para
        // que el chip de calidad y el plan B no vean listas distintas del mismo canal.
        for (String trozo : DramaSesion.trozosDeBackup(backup)) {
            Opcion o = deTrozo(trozo);
            if (o != null) todas.add(o);
        }
        return ordenarSinRepetir(todas);
    }

    /** Las que merecen un intento de verdad (nada de marcadores). */
    public static List<Opcion> utiles(@Nullable String url, @Nullable String backup) {
        List<Opcion> out = new ArrayList<>();
        for (Opcion o : opciones(url, backup)) {
            if (o.tipo == Tipo.MARCADOR) continue;
            if (esEmbedMuerto(o.url)) continue;
            out.add(o);
        }
        return out;
    }

    /** Todas las embebidas, incluidas las que hoy sabemos muertas (para el log). */
    public static List<Opcion> embebidas(@Nullable String url, @Nullable String backup) {
        List<Opcion> out = new ArrayList<>();
        for (Opcion o : opciones(url, backup)) if (o.tipo == Tipo.EMBEBIDA) out.add(o);
        return out;
    }

    /** La primera embebida que queda (la que emite en los canales de beIN). */
    @Nullable public static Opcion mejorEmbebida(@Nullable String url, @Nullable String backup) {
        for (Opcion o : utiles(url, backup)) if (o.tipo == Tipo.EMBEBIDA) return o;
        return null;
    }

    /** La siguiente embebida después de {@code actual} (para no repetir al fallar). */
    @Nullable public static Opcion siguienteEmbebida(@Nullable String url, @Nullable String backup,
                                                     @Nullable String actual) {
        boolean pasado = (actual == null);
        for (Opcion o : utiles(url, backup)) {
            if (o.tipo != Tipo.EMBEBIDA) continue;
            if (actual != null && actual.equals(o.url)) { pasado = true; continue; }
            if (!pasado) continue;
            return o;
        }
        return null;
    }

    /**
     * v7.38.4 — EL SIGUIENTE SERVIDOR DE VERDAD: salta los ya probados y devuelve
     * el primero que quede, en el orden de {@link #utiles}.
     *
     * <p><b>Qué arregla.</b> Esta decisión vivía en {@code PlayerActivity} con un
     * {@code return null} dentro del bucle: al encontrarse un servidor ya probado
     * <em>abortaba la lista completa</em> en vez de saltarlo. Con la lista ordenada
     * por {@code utiles} — que pone las EMBEBIDAS primero — el canal se quedaba en
     * cero después de intentar <b>solo el primer</b> servidor, y el log decía «se
     * han probado TODOS los servidores» con siete/ocho sin abrir. Ese síntoma es
     * «pulso el canal y no reproduce».
     *
     * <p>Se trae aquí porque es aritmética pura y se prueba en JVM
     * ({@code PlanBTest}); una Activity de 4 000 líneas no es sitio para una regla
     * que decide si un canal se ve o no.
     *
     * @param utiles      los servidores del canal, ya ordenados ({@link #utiles})
     * @param yaProbados  urls que este reproductor ya intentó (no se repiten)
     * @param actual      url que está sonando ahora mismo (no se repite)
     * @param enCurso     la embebida que se está mostrando (no se repite)
     * @return el primero no descartado, o {@code null} si de verdad no queda ninguno
     */
    @Nullable
    public static Opcion siguiente(@Nullable List<Opcion> utiles,
                                   @Nullable java.util.Collection<String> yaProbados,
                                   @Nullable String actual,
                                   @Nullable String enCurso) {
        if (utiles == null || utiles.isEmpty()) return null;
        for (Opcion o : utiles) {
            if (o == null || o.url == null) continue;
            if (actual != null && actual.equals(o.url)) continue;
            if (enCurso != null && enCurso.equals(o.url)) continue;
            if (yaProbados != null && yaProbados.contains(o.url)) continue;   // saltar, NO abortar
            return o;
        }
        return null;
    }

    /** Embeds que hoy sabemos muertos. Medido 2026-11-17: quotarevival pinta
     *  «NOT ALLOWED · domain protected» y no pide ni un segmento. */
    public static boolean esEmbedMuerto(@Nullable String url) {
        if (url == null) return false;
        return url.toLowerCase(Locale.ROOT).contains("quotarevival.");
    }

    /** ¿Es este enlace una página de embed (no un stream)? Puro. */
    public static boolean esEmbebida(@Nullable String u) {
        if (u == null) return false;
        String l = u.toLowerCase(Locale.ROOT);
        if (l.contains("youtube.com") || l.contains("youtu.be")) return false;
        return l.contains("ordernap.") || l.contains("quotarevival.") || l.contains("/e/")
                || l.contains("/embed/") || l.contains("embed.") || l.contains("player.");
    }

    /** ¿Es un playlist/stream que ExoPlayer puede abrir? Puro. */
    public static boolean esPlaylist(@Nullable String u) {
        if (u == null) return false;
        String l = u.toLowerCase(Locale.ROOT);
        return l.contains(".m3u8") || l.contains(".mpd") || l.contains(".mp4")
                || l.contains(".m4v") || l.contains("/hls/") || l.contains("tracks-v1");
    }

    // ── parseo ───────────────────────────────────────────────────────────────

    /** Un trozo del backup: puede ser JSON (iframe) o «url -- agente -- tipo». */
    @Nullable
    static Opcion deTrozo(@Nullable String trozo) {
        if (trozo == null) return null;
        String t = trozo.trim();
        if (t.isEmpty()) return null;

        if (t.startsWith("{")) {
            // {"url":"…","agent":"…","iframe":"…","headers":{…},"description":"DLW"} -- double_redirect
            // OJO: el tipo viene DETRÁS del JSON («...} -- double_redirect»), así que hay
            // que cortar en la última llave antes de parsear. (Fallo real: al parsear el
            // trozo entero, el JSON no cerraba y se perdían las embebidas.)
            int fin = t.lastIndexOf('}');
            String json = (fin > 0) ? t.substring(0, fin + 1) : t;
            boolean pideIframe = t.substring(Math.max(0, fin)).contains("double_redirect");
            try {
                JsonElement e = JsonParser.parseString(json);
                if (!e.isJsonObject()) return null;
                JsonObject o = e.getAsJsonObject();
                String u = texto(o, "url");
                if (u == null || !u.startsWith("http")) return null;
                String ua = texto(o, "agent");
                String desc = texto(o, "description");
                String ref = null;
                JsonElement h = o.get("headers");
                if (h != null && h.isJsonObject()) {
                    JsonObject ho = h.getAsJsonObject();
                    ref = texto(ho, "Referer");
                    if (ref == null) ref = texto(ho, "Origin");
                    if (ua == null) ua = texto(ho, "User-Agent");
                }
                boolean enIframe = o.has("iframe") || pideIframe
                        || "double_redirect".equalsIgnoreCase(texto(o, "type"));
                return new Opcion(u, clasificar(u, enIframe), ua, ref, desc, enIframe,
                        claveClearKey(o));
            } catch (Exception ignore) {
                return null;
            }
        }

        // «url -- agente -- tipo»  (el tipo final, p.ej. «redirect», no se usa: manda la url)
        String[] partes = t.split("\\s*--\\s*");
        String url = partes.length > 0 ? partes[0].trim() : "";
        String ua = partes.length > 1 ? partes[1].trim() : null;
        // el segundo trozo a veces NO es un UA sino el tipo de agente («redirect»,
        // «double_redirect»…): eso no es un User-Agent y no debe mandarse.
        if (ua != null && !pareceUnUa(ua)) ua = null;
        if (url.isEmpty()) return null;
        return deTexto(url, ua, null);
    }

    /**
     * v7.38.1 — saca «KID:CLAVE» del bloque {@code drm} del proveedor. Puro.
     *
     * <p>Medido con Abu Dhabi Al Emarat: {@code "drm":{"uuid":"CLEARKEY_UUID",
     * "clearkey":"HLefmCmE7OkZMgMKS7f6qg:4uUPr6kjguc1vA6KofZ7IA"}}. Es base64url
     * sin relleno y corresponde a un KID y una clave de 16 bytes.
     */
    @Nullable
    static String claveClearKey(@Nullable JsonObject o) {
        if (o == null) return null;
        JsonElement d = o.get("drm");
        if (d == null || !d.isJsonObject()) return null;
        JsonObject drm = d.getAsJsonObject();
        String ck = texto(drm, "clearkey");
        if (ck == null) ck = texto(drm, "clear_key");
        if (ck == null) return null;
        String[] par = ck.split(":");
        if (par.length != 2) return null;
        String kid = b64aHex(par[0]);
        String key = b64aHex(par[1]);
        if (kid == null || key == null) return null;
        return kid + ":" + key;
    }

    /**
     * base64url (sin relleno, como lo manda el proveedor) → hexadecimal.
     * «HLefmCmE7OkZMgMKS7f6qg» → «1cb79f982984ece91932030a4bb7faaa». Puro.
     */
    @Nullable
    static String b64aHex(@Nullable String b64) {
        if (b64 == null) return null;
        // v7.38.1: decodificador propio, sin android.util.Base64. Ese método es un
        // stub en los tests de JUnit (devolvía null y todo el DRM se perdía en
        // silencio), y así el cálculo queda cubierto por tests de verdad.
        String t = b64.trim().replace('-', '+').replace('_', '/');
        while (t.length() % 4 != 0) t = t + "=";
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int buffer = 0, bits = 0;
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (c == '=') break;
            int v;
            if (c >= 'A' && c <= 'Z') v = c - 'A';
            else if (c >= 'a' && c <= 'z') v = c - 'a' + 26;
            else if (c >= '0' && c <= '9') v = c - '0' + 52;
            else if (c == '+') v = 62;
            else if (c == '/') v = 63;
            else if (c == '\n' || c == '\r' || c == ' ' || c == '\t') continue;
            else return null;                      // carácter imposible: se descarta
            buffer = (buffer << 6) | v;
            bits += 6;
            if (bits >= 8) {
                bits -= 8;
                out.write((buffer >> bits) & 0xFF);
            }
        }
        byte[] bytes = out.toByteArray();
        if (bytes.length == 0) return null;
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte x : bytes) sb.append(String.format("%02x", x));
        return sb.toString();
    }

    /** ¿Esto es un User-Agent o el nombre del agente del sitio? Puro. */
    static boolean pareceUnUa(@Nullable String s) {
        if (s == null) return false;
        String t = s.trim();
        if (t.isEmpty() || t.equalsIgnoreCase("Mozilla")) return false;
        String l = t.toLowerCase(Locale.ROOT);
        if (l.equals("redirect") || l.equals("double_redirect") || l.equals("advanced")
                || l.equals("web-player") || l.equals("all_streams_redirect")) return false;
        return l.contains("mozilla/") || (l.contains("applewebkit") || l.contains("safari")
                || l.contains("chrome") || l.contains("okhttp"));
    }

    private static Opcion deTexto(String url, String ua, String referer) {
        return deTexto(url, ua, referer, null, false);
    }

    private static Opcion deTexto(String url, String ua, String referer,
                                  String descripcion, boolean iframe) {
        return new Opcion(url, clasificar(url, iframe), ua, referer, descripcion, iframe);
    }

    /** Clasifica sin red. Puro. */
    public static Tipo clasificar(@Nullable String url, boolean iframe) {
        if (url == null || url.isEmpty()) return Tipo.MARCADOR;
        String u = url.trim();
        if (u.contains(".LS.") || u.startsWith(".LS.")) return Tipo.MARCADOR;
        if (!u.startsWith("http")) return Tipo.MARCADOR;
        if (esPlaylist(u)) return Tipo.DIRECTA;
        if (iframe || esEmbebida(u)) return Tipo.EMBEBIDA;
        return Tipo.PAGINA;
    }

    /**
     * Orden: primero lo que de verdad emite.
     * <ol>
     *   <li>EMBEBIDA (es la que tiene el canal; beIN 1,2,3,5,6,News,Max)</li>
     *   <li>DIRECTA (m3u8; puede ser el cartel ruso: se comprueba al abrir)</li>
     *   <li>PAGINA (YouTube y demás: último recurso visible)</li>
     *   <li>MARCADOR (hoy no resuelve: se deja al final, no se borra)</li>
     * </ol>
     * Se quitan repetidas conservando la primera aparición.
     */
    static List<Opcion> ordenarSinRepetir(List<Opcion> todas) {
        int[] peso = new int[]{2, 0, 1, 3};   // EMBEBIDA, DIRECTA, PAGINA, MARCADOR
        List<Opcion> orden = new ArrayList<>();
        LinkedHashSet<String> vistas = new LinkedHashSet<>();
        for (int p = 0; p < 4; p++) {
            for (Opcion o : todas) {
                if (peso[o.tipo.ordinal()] != p) continue;
                if (!vistas.add(clave(o.url))) continue;
                orden.add(o);
            }
        }
        return orden;
    }

    /** Clave para deduplicar: sin esquema, sin www, sin barra final. */
    static String clave(@Nullable String url) {
        if (url == null) return "";
        String u = url.trim().toLowerCase(Locale.ROOT);
        u = u.replaceFirst("^https?://", "").replaceFirst("^www\\.", "");
        while (u.endsWith("/")) u = u.substring(0, u.length() - 1);
        return u;
    }

    @Nullable
    private static String texto(JsonObject o, String campo) {
        try {
            JsonElement e = o.get(campo);
            if (e == null || e.isJsonNull() || !e.isJsonPrimitive()) return null;
            String s = e.getAsString();
            return (s == null || s.trim().isEmpty()) ? null : s.trim();
        } catch (Exception ignore) {
            return null;
        }
    }
}
