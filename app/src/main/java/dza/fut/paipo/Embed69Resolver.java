package dza.fut.paipo;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import java.util.Arrays;
import java.util.Base64;


import okhttp3.ConnectionSpec;
import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Resolver del servidor <b>embed69</b> (opción "Multi"), que es el que más
 * títulos tiene disponibles con audio en español: expone copias
 * <b>LAT</b> (latino), <b>ESP</b> (castellano) y <b>SUB</b> (subtitulada).
 *
 * <p>La página protege los enlaces con un reto Proof-of-Work + AES-CBC, pero el
 * propio HTML trae el algoritmo completo, así que se resuelve sin WebView:</p>
 * <ol>
 *   <li>Se leen del HTML {@code POW_CHALLENGE}, {@code POW_DIFFICULTY},
 *       {@code POW_SALT} y el array {@code dataLink} (con los embeds cifrados
 *       clasificados por idioma).</li>
 *   <li>Se busca un {@code nonce} tal que
 *       {@code sha256(challenge + nonce)} empiece por N ceros.</li>
 *   <li>La clave es {@code aesKey = sha256(challenge + nonce + salt)}; cada
 *       enlace se descifra con AES-CBC (los 16 primeros bytes son el IV).</li>
 *   <li>Se elige primero el idioma LAT, luego ESP, y dentro de cada idioma el
 *       servidor {@code vidhide}, cuyo player va ofuscado con el mismo packer
 *       que ya resolvemos en {@link JsUnpacker} → master.m3u8.</li>
 * </ol>
 *
 * Verificado en vivo 2026-09-29 (The Matrix, Shawshank... títulos que vimeus
 * no tiene).
 */
public final class Embed69Resolver {

    private static final String TAG = "Embed69Resolver";
    private static final String UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";

    private static final Pattern P_CHALLENGE =
            Pattern.compile("POW_CHALLENGE\\s*=\\s*'([^']+)'");
    private static final Pattern P_DIFFICULTY =
            Pattern.compile("POW_DIFFICULTY\\s*=\\s*(\\d+)");
    private static final Pattern P_SALT =
            Pattern.compile("POW_SALT\\s*=\\s*'([^']+)'");
    private static final Pattern M3U8 =
            Pattern.compile("https?://[^\"'\\ ]+\\.m3u8[^\"'\\ ]*");

    // ---- Anubis (anti-bot), añadido por embed69 en noviembre de 2026 ----
    // La primera respuesta ya NO es la página del reproductor: es la pantalla
    // "Making sure you're not a bot!" de Anubis v1.28. Sin resolverla no hay
    // cookie y el servidor devuelve siempre lo mismo (de ahí el error
    // "sin reto PoW en la página": el reto que buscábamos vive en la página
    // que está detrás de Anubis).
    private static final Pattern P_ANUBIS =
            Pattern.compile("id=\"anubis_challenge\"[^>]*>(.*?)</script>",
                    Pattern.DOTALL);
    private static final Pattern P_LINK_CH =
            Pattern.compile("LINK_CHALLENGE\\s*=\\s*[\"']([^\"']+)[\"']");
    private static final Pattern P_LINK_SA =
            Pattern.compile("LINK_SALT\\s*=\\s*[\"']([^\"']+)[\"']");
    // Valores por si algún día el HTML deja de declararlos: se leen de la
    // página, pero tener el respaldo evita quedarse sin clave.
    private static final String LINK_CH_DEFECTO = "aac8c4de6c6c683473fe80ceca05356e";
    private static final String LINK_SA_DEFECTO = "6d07665b9850a0db";

    private static final long MAX_NONCE = 4_000_000L;

    private static final OkHttpClient http = new OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            // Algunos CDNs de vídeo siguen usando TLS "clásico": sin esto habría
            // fallos de handshake en según qué móvil.
            .connectionSpecs(Arrays.asList(ConnectionSpec.MODERN_TLS,
                    ConnectionSpec.COMPATIBLE_TLS, ConnectionSpec.CLEARTEXT))
            // IMPRESCINDIBLE desde que embed69 puso Anubis delante: el reto se
            // canjea en una petición y la cookie hay que llevarla en la
            // siguiente. OkHttp no guarda cookies por defecto, así que sin
            // esto el servidor volvía a mostrar la pantalla anti-bot y el
            // resolver se quedaba ciego («sin dataLink»). Verificado el
            // 13-Nov-2026: sin cookie jar el pass-challenge devuelve Anubis
            // otra vez; con él, devuelve la página real.
            .cookieJar(new CookieJarEnMemoria())
            .build();

    /**
     * Cookie jar en memoria. No vale el de OkHttp por defecto (NO_COOKIES):
     * desde que embed69 puso Anubis delante, el reto se canjea en una
     * petición y la cookie hay que llevarla en la siguiente. Sin esto el
     * servidor vuelve a mostrar la pantalla anti-bot y el resolver se queda
     * ciego («sin dataLink»).
     *
     * <p>{@code okhttp3.JavaNetCookieJar} no está en okhttp 4, así que va
     * implementado aquí. Vive lo que vive el proceso, que es exactamente lo
     * que hace falta.</p>
     */
    private static final class CookieJarEnMemoria implements CookieJar {
        private final Map<HttpUrl, List<Cookie>> almacen = new HashMap<>();

        @Override public void saveFromResponse(HttpUrl url, List<Cookie> cookies) {
            if (cookies == null || cookies.isEmpty()) return;
            List<Cookie> previas = almacen.get(url);
            if (previas == null) {
                almacen.put(url, new ArrayList<>(cookies));
                return;
            }
            for (Cookie nueva : cookies) {
                boolean puesta = false;
                for (int i = 0; i < previas.size(); i++) {
                    Cookie vieja = previas.get(i);
                    if (vieja.name().equals(nueva.name())
                            && vieja.domain().equals(nueva.domain())
                            && vieja.path().equals(nueva.path())) {
                        previas.set(i, nueva);
                        puesta = true;
                        break;
                    }
                }
                if (!puesta) previas.add(nueva);
            }
        }

        @Override public List<Cookie> loadForRequest(HttpUrl url) {
            List<Cookie> fuera = new ArrayList<>();
            for (Map.Entry<HttpUrl, List<Cookie>> e : almacen.entrySet()) {
                for (Cookie c : e.getValue()) {
                    if (c.matches(url)) fuera.add(c);
                }
            }
            return fuera;
        }
    }

    private Embed69Resolver() { }

    public static boolean isEmbed69(String url) {
        return url != null && url.toLowerCase(Locale.US).contains("embed69.org");
    }

    /**
     * @param url la URL tipo {@code https://embed69.org/f/tt0133093/}
     */
    public static PelisStreamResolver.StreamResult resolve(android.content.Context ctx, String url)
            throws Exception {
        long t0 = System.currentTimeMillis();

        // ---- 1) Anubis: si el servidor contesta con la pantalla anti-bot,
        //         hay que resolver su reto para que suelte la cookie y
        //         devuelva la página real del reproductor. ----
        String html = get(url, "https://playpaste.link/player/");
        if (esAnubis(html)) {
            Log.d(TAG, "Anubis detectado, resolviendo reto...");
            html = pasarAnubis(html, url);
            Log.d(TAG, "Anubis superado en " + (System.currentTimeMillis() - t0)
                    + "ms, pagina real=" + html.length() + "B");
        }

        // ---- 2) constantes del reto (formato viejo) + enlaces cifrados ----
        Matcher mc = P_CHALLENGE.matcher(html);
        Matcher md = P_DIFFICULTY.matcher(html);
        Matcher ms = P_SALT.matcher(html);
        boolean formatoViejo = mc.find() && md.find() && ms.find();
        String challenge = formatoViejo ? mc.group(1) : null;
        int dificultad = formatoViejo ? Integer.parseInt(md.group(1)) : 0;
        String salt = formatoViejo ? ms.group(1) : null;

        String json = extraerDataLink(html);
        if (json == null) throw new Exception("embed69: sin dataLink (HTML=" + html.length() + "B)");
        JSONArray dataLink = new JSONArray(json);
        if (dataLink.length() == 0) throw new Exception("embed69: dataLink vacío (extraido="
                + json.length() + "B, HTML=" + html.length() + "B)");
        Log.d(TAG, "dataLink con " + dataLink.length() + " idiomas"
                + (formatoViejo ? ", dificultad=" + dificultad
                                : " (clave LINK_CHALLENGE+LINK_SALT)"));

        // ---- 3) derivar la clave AES ----
        // Formato nuevo (nov-2026): la clave es FIJA,
        //     aesKey = sha256(LINK_CHALLENGE + LINK_SALT)
        // y LINK_CHALLENGE/LINK_SALT vienen en el propio HTML. El reto que hay
        // que resolver es el de Anubis, no uno por título.
        byte[] aesKey;
        if (formatoViejo) {
            String prefijo = new String(new char[dificultad]).replace('\0', '0');
            long nonce = -1;
            for (long n = 0; n < MAX_NONCE; n++) {
                if (sha256Hex(challenge + n).startsWith(prefijo)) { nonce = n; break; }
            }
            if (nonce < 0) throw new Exception("embed69: PoW no resuelto");
            aesKey = sha256(challenge + nonce + salt);
            Log.d(TAG, "PoW resuelto nonce=" + nonce + " en " + (System.currentTimeMillis() - t0) + "ms");
        } else {
            aesKey = claveDeEnlaces(html);
        }

        // ---- 3) idiomas ordenados: LAT -> ESP -> otro -> SUB ----
        List<JSONObject> idiomas = new ArrayList<>();
        for (int i = 0; i < dataLink.length(); i++) {
            JSONObject o = dataLink.optJSONObject(i);
            if (o != null) idiomas.add(o);
        }
        idiomas.sort((a, b) -> puntos(a) - puntos(b));

        // ---- 4) para cada idioma, probar servidores hasta dar con un m3u8 ----
        Exception ultimo = null;
        for (JSONObject idioma : idiomas) {
            String lang = idioma.optString("video_language", "");
            JSONArray servidores = servidoresOrdenados(idioma.optJSONArray("sortedEmbeds"));
            for (int s = 0; s < servidores.length(); s++) {
                JSONObject emb = servidores.optJSONObject(s);
                if (emb == null) continue;
                String nombre = emb.optString("servername", "");
                String cifrado = emb.optString("link", "");
                if (cifrado.isEmpty()) continue;
                String enlace;
                try {
                    enlace = descifrar(cifrado, aesKey);
                } catch (Exception e) {
                    ultimo = e;
                    continue;
                }
                if (enlace == null || !enlace.startsWith("http")) {
                    Log.d(TAG, "  " + lang + "/" + nombre + " -> enlace no usable: " + enlace);
                    continue;
                }
                Log.d(TAG, "  " + lang + "/" + nombre + " -> " + enlace);
                try {
                    String m3u8 = m3u8DelEmbed(enlace);
                    if (m3u8 == null) continue;
                    Map<String, String> headers = new HashMap<>();
                    headers.put("User-Agent", UA);
                    headers.put("Referer", origen(enlace));
                    headers.put("Origin", origen(enlace).substring(0, origen(enlace).length() - 1));
                    headers.put("Accept", "*/*");
                    headers.put("Accept-Language", "es-ES,es;q=0.9");
                    Log.d(TAG, "OK idioma=" + lang + " servidor=" + nombre + " ("
                            + (System.currentTimeMillis() - t0) + "ms)");
                    return new PelisStreamResolver.StreamResult(
                            m3u8, "", origen(enlace), sinBarra(origen(enlace)), headers);
                } catch (Exception e) {
                    Log.w(TAG, "  falló " + lang + "/" + nombre + ": " + e.getMessage());
                    ultimo = e;
                }
            }
        }
        throw new Exception("embed69: ningún enlace sirvió"
                + (ultimo != null ? " (" + ultimo.getMessage() + ")" : ""));
    }

    // ------------------------------------------------------------------
    //  Anubis (anti-bot)
    // ------------------------------------------------------------------

    /** ¿La respuesta es la pantalla "Making sure you're not a bot!"? */
    static boolean esAnubis(String html) {
        return html != null && html.contains("anubis_challenge");
    }

    /**
     * Resuelve el reto de Anubis y devuelve la página que hay detrás.
     *
     * <p>Flujo real de Anubis v1.28.2 (sacado de {@code main.mjs} y del worker
     * {@code sha256.mjs}, verificado en vivo el 13-Nov-2026):</p>
     * <ol>
     *   <li>La página trae {@code <script id="anubis_challenge">} con
     *       {@code {rules:{algorithm,difficulty}, challenge:{id,randomData,...}}}.</li>
     *   <li>Se busca un {@code nonce} tal que
     *       {@code sha256(randomData + nonce)} empiece por
     *       {@code floor(dificultad/2)} bytes a cero (y, si la dificultad es
     *       impar, el nibble alto del byte siguiente también a cero).</li>
     *   <li>Se pide {@code /.../api/pass-challenge?id=..&response=<hash>&nonce=..&redir=..}
     *       , que pone la cookie y redirige al reproductor.</li>
     * </ol>
     */
    static String pasarAnubis(String html, String urlOriginal) throws Exception {
        Matcher ma = P_ANUBIS.matcher(html);
        if (!ma.find()) throw new Exception("embed69: Anubis sin reto");
        JSONObject bloque = new JSONObject(ma.group(1));
        JSONObject rules = bloque.optJSONObject("rules");
        JSONObject ch = bloque.optJSONObject("challenge");
        if (ch == null) throw new Exception("embed69: Anubis sin challenge");
        String id = ch.optString("id", "");
        String randomData = ch.optString("randomData", "");
        int dificultad = rules != null ? rules.optInt("difficulty", 4)
                : ch.optInt("difficulty", 4);
        if (id.isEmpty() || randomData.isEmpty()) {
            throw new Exception("embed69: Anubis incompleto");
        }

        long t0 = System.currentTimeMillis();
        long nonce = nonceAnubis(randomData, dificultad);
        String hash = sha256Hex(randomData + nonce);
        long ms = Math.max(1, System.currentTimeMillis() - t0);

        // La base sale de la propia URL para que el mismo código sirva contra
        // embed69 y contra el servidor local de las pruebas.
        String base = urlOriginal.substring(0,
                urlOriginal.indexOf('/', urlOriginal.indexOf("://") + 3));
        String pase = base + "/.within.website/x/cmd/anubis/api/pass-challenge"
                + "?id=" + urlenc(id)
                + "&response=" + hash
                + "&nonce=" + nonce
                + "&redir=" + urlenc(urlOriginal)
                + "&elapsedTime=" + ms;
        String dentro = get(pase, urlOriginal);
        if (dentro == null || dentro.isEmpty()) {
            throw new Exception("embed69: Anubis no canjeo el reto");
        }
        if (esAnubis(dentro)) {
            throw new Exception("embed69: Anubis no soltó la cookie (reto canjeado"
                    + " pero la página sigue bloqueada)");
        }
        return dentro;
    }

    /**
     * Nonce de Anubis: {@code sha256(randomData + nonce)} con
     * {@code floor(dificultad/2)} bytes a cero y, si la dificultad es impar,
     * el nibble alto del byte siguiente también a cero.
     *
     * <p>Es lo que hace el worker {@code sha256.mjs} de Anubis; con la
     * dificultad 4 que usa embed69 hoy son 2 bytes = 16 bits, del orden de
     * 65.536 intentos (milisegundos en el móvil).</p>
     */
    static long nonceAnubis(String randomData, int dificultad) throws Exception {
        int bytes = Math.max(1, dificultad / 2);
        boolean impar = dificultad % 2 != 0;
        for (long n = 0; n < MAX_NONCE; n++) {
            byte[] h = sha256(randomData + n);
            boolean ok = true;
            for (int i = 0; i < bytes; i++) {
                if (h[i] != 0) { ok = false; break; }
            }
            if (ok && impar && (h[bytes] >> 4) != 0) ok = false;
            if (ok) return n;
        }
        throw new Exception("embed69: nonce de Anubis no encontrado");
    }

    /** Clave AES del formato nuevo: {@code sha256(LINK_CHALLENGE + LINK_SALT)}. */
    static byte[] claveDeEnlaces(String html) throws Exception {
        String ch = grupo(P_LINK_CH, html);
        String sa = grupo(P_LINK_SA, html);
        if (ch == null) ch = LINK_CH_DEFECTO;
        if (sa == null) sa = LINK_SA_DEFECTO;
        return sha256(ch + sa);
    }

    private static String grupo(Pattern p, String s) {
        Matcher m = p.matcher(s);
        return m.find() ? m.group(1) : null;
    }

    private static String urlenc(String s) throws Exception {
        return java.net.URLEncoder.encode(s, "UTF-8");
    }

    /** Descifra el embed y saca el master.m3u8 validándolo. */
    private static String m3u8DelEmbed(String embedUrl) throws Exception {
        String html = get(embedUrl, "https://embed69.org/");
        String m3u8 = null;
        if (JsUnpacker.isPacked(html)) {
            String packed = JsUnpacker.extractPackedBlock(html);
            String js = packed == null ? null : JsUnpacker.unpack(packed);
            if (js != null) {
                Matcher m = M3U8.matcher(js);
                if (m.find()) m3u8 = m.group();
            }
        }
        if (m3u8 == null) {
            Matcher m = M3U8.matcher(html);
            if (m.find()) m3u8 = m.group();
        }
        if (m3u8 == null) throw new Exception("sin m3u8 en el embed");

        String master = get(m3u8, origen(embedUrl));
        if (!master.contains("#EXTM3U")) throw new Exception("master inválido");
        Log.d(TAG, "      master OK (" + master.length() + "B, "
                + (master.split("#EXT-X-STREAM-INF", -1).length - 1) + " variantes)");
        return m3u8;
    }

    /** AES-CBC, IV = 16 primeros bytes del binario descodificado en base64. */
    private static String descifrar(String base64, byte[] aesKey) throws Exception {
        // java.util.Base64 y no android.util.Base64: funciona igual en el
        // móvil (minSdk 30, API 26+) y en la JVM, así el resolver se puede
        // probar fuera del emulador.
        byte[] raw = Base64.getDecoder().decode(base64);
        if (raw.length <= 16) return null;
        Cipher c = Cipher.getInstance("AES/CBC/PKCS5Padding");
        c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(aesKey, 0, 32, "AES"),
                new IvParameterSpec(raw, 0, 16));
        byte[] claro = c.doFinal(raw, 16, raw.length - 16);
        return new String(claro, StandardCharsets.UTF_8).trim();
    }

    /** Extrae el array JSON <code>dataLink = [ ... ]</code> respetando anidamientos. */
    private static String extraerDataLink(String html) {
        int i = html.indexOf("dataLink");
        if (i < 0) return null;
        int start = html.indexOf('[', i);
        if (start < 0) return null;
        int depth = 0;
        boolean enCadena = false, escapado = false;
        for (int j = start; j < html.length(); j++) {
            char c = html.charAt(j);
            if (enCadena) {
                if (escapado) escapado = false;
                else if (c == '\\') escapado = true;
                else if (c == '"') enCadena = false;
                continue;
            }
            if (c == '"') enCadena = true;
            else if (c == '[' || c == '{') depth++;
            else if (c == ']' || c == '}') {
                depth--;
                if (depth == 0) return html.substring(start, j + 1);
            }
        }
        return null;
    }

    /** Primero vidhide (se descomprime con JsUnpacker), luego voe, después el resto. */
    private static JSONArray servidoresOrdenados(JSONArray arr) {
        JSONArray out = new JSONArray();
        if (arr == null) return out;
        String[] preferencia = {"vidhide", "voe", "streamwish", "doodstream", "mixdrop", "filemoon"};
        for (String p : preferencia) {
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o != null && p.equals(o.optString("servername", "").toLowerCase(Locale.US))) {
                    out.put(o);
                }
            }
        }
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            String n = o.optString("servername", "").toLowerCase(Locale.US);
            boolean ya = false;
            for (String p : preferencia) if (p.equals(n)) ya = true;
            if (!ya) out.put(o);
        }
        return out;
    }

    /** 0 = LAT, 1 = ESP, 2 = otro, 3 = SUB. */
    private static int puntos(JSONObject o) {
        String l = o.optString("video_language", "").toUpperCase(Locale.US);
        if (l.contains("LAT")) return 0;
        if (l.contains("ESP") || l.contains("CAST")) return 1;
        if (l.contains("SUB")) return 3;
        return 2;
    }

    private static String origen(String url) {
        try {
            java.net.URL u = new java.net.URL(url);
            return u.getProtocol() + "://" + u.getHost() + "/";
        } catch (Exception e) {
            return "https://embed69.org/";
        }
    }

    private static String sinBarra(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    private static byte[] sha256(String s) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
    }

    private static String sha256Hex(String s) throws Exception {
        byte[] d = sha256(s);
        StringBuilder sb = new StringBuilder(d.length * 2);
        for (byte b : d) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private static String get(String url, String referer) throws Exception {
        Request req = new Request.Builder()
                .url(url)
                .addHeader("User-Agent", UA)
                .addHeader("Referer", referer)
                .addHeader("Accept", "text/html,application/xhtml+xml,application/json;q=0.9,*/*;q=0.8")
                .addHeader("Accept-Language", "es-ES,es;q=0.9")
                .build();
        try (Response resp = http.newCall(req).execute()) {
            if (!resp.isSuccessful() || resp.body() == null) {
                throw new Exception("HTTP " + resp.code() + " en " + url);
            }
            return resp.body().string();
        }
    }
}
