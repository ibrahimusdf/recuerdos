package dza.fut.paipo;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * v7.8 — EL CATÁLOGO BAJADO DE GITHUB.
 *
 * <p><b>Qué sustituye.</b> La app abría conectando a DramaLive y extrayendo los 265
 * tópicos del índice para tener la rejilla: medido, <b>21 segundos</b>, 265 peticiones
 * en paralelo, más datos y batería — y era el camino que se comía el ANR de la tira
 * de pestañas. Con el catálogo servido desde GitHub es <b>un solo GET</b>.
 *
 * <p><b>De dónde sale.</b> {@code canales-compacto.json}, el fichero que ya está
 * publicado en el repo. Es el mismo esquema que {@code belkaperu/belkafut/data2.json}:
 * {@code categories[]} + {@code channels[]} con {@code servers[]}, y
 * {@code servers[].url} es un {@code dramalive://} que {@link PlayerActivity} ya sabe
 * resolver. El JSON no cambia el reproductor.
 *
 * <p><b>Cómo encaja.</b> Tres capas, de más rápida a más fresca:
 * <ol>
 *   <li><b>assets</b> — el catálogo empaquetado en el APK (v7.7). Aparece al
 *       instante y funciona sin red. Sigue siendo la semilla.</li>
 *   <li><b>disco</b> — la última copia bajada de GitHub. Sobrevive al reinicio.</li>
 *   <li><b>red</b> — un GET al repo. Refresca en segundo plano y pisa la 2.</li>
 * </ol>
 * Si la red falla no se pierde nada: se queda lo que haya y la extracción en vivo
 * de siempre entra como último recurso (ver {@link DramaFondo}).
 *
 * <p><b>Por qué se cachea el JSON y no el m3u.</b> El JSON es lo que se ha bajado y
 * se puede validar tal cual; el m3u se deriva con {@link CatalogoJson#aM3uUnico},
 * que es puro y está en la suite. Así un fichero corrupto se detecta en el mismo
 * momento de bajarlo y no envenena la caché.
 */
public final class CatalogoRemoto {

    private CatalogoRemoto() {}

    private static final String TAG = "CatalogoRemoto";

    /** v7.17 - el backend ESTÁTICO de Cloudflare Pages. El catálogo está partido
     *  en shards y servido como ficheros JSON, SIN Worker: se baja solo el
     *  alcance que toca (corto 146 KB, latam 555 KB, global 2,7 MB) en vez de
     *  los 3,4 MB de siempre. Medido contra el servidor en vivo. */
    public static final String BASE = "https://pelislatino-canales.pages.dev";

    /** El fichero de UN alcance. {@link AutoCanales#alcance} da el que toca. */
    @NonNull
    public static String urlScope(@NonNull String alcance) {
        return BASE + "/scopes/" + alcance + ".json";
    }

    /** La huella de la fuente: 512 B. Si no cambió, no se baja el catálogo. */
    @NonNull
    public static String urlVersion() {
        return BASE + "/version.json";
    }

    /** Dónde se guarda la última huella conocida y para qué alcance. */
    private static final String ARCHIVO_META = "catalogo_meta.txt";

    /** Copia en {@code filesDir} del último JSON que bajó bien. */
    private static final String ARCHIVO = "catalogo_remoto.json";

    /** El m3u derivado, una sola vez por proceso (son ~4 MB de texto). */
    private static volatile String cacheM3u;

    /** v7.18 - los canales ya parseados, una sola vez por proceso. Es el
     *  camino bueno: no pasa por el texto del m3u. */
    private static volatile java.util.List<Canal> cacheCanales;

    private static volatile OkHttpClient cliente;

    private static OkHttpClient cliente() {
        OkHttpClient c = cliente;
        if (c == null) {
            synchronized (CatalogoRemoto.class) {
                if (cliente == null) {
                    cliente = new OkHttpClient.Builder()
                            .connectTimeout(10, TimeUnit.SECONDS)
                            .readTimeout(30, TimeUnit.SECONDS)
                            .callTimeout(45, TimeUnit.SECONDS)
                            .retryOnConnectionFailure(true)
                            .build();
                }
                c = cliente;
            }
        }
        return c;
    }

    /** ¿Hay una copia de GitHub guardada en disco? */
    public static boolean enDisco(@NonNull Context ctx) {
        return jsonDisco(ctx) != null;
    }

    /**
     * El m3u de la copia guardada en disco, o "" si no hay.
     * Llamar desde un hilo en segundo plano.
     *
     * OJO: se mantiene por compatibilidad, pero para la rejilla usa
     * {@link #aCanales(Context)}. La vuelta por el m3u PIERDE canales
     * (medido en v7.18: 282 de 12.000 en global), porque el título sale sin
     * la calidad y el parser dedupe por título.
     */
    @NonNull
    public static String aM3u(@NonNull Context ctx) {
        String c = cacheM3u;
        if (c != null) return c;
        synchronized (CatalogoRemoto.class) {
            if (cacheM3u != null) return cacheM3u;
            String json = jsonDisco(ctx);
            String m3u = json == null ? "" : aM3uDe(json);
            cacheM3u = m3u;
            return m3u;
        }
    }

    /**
     * v7.18 - los canales de la copia guardada en disco, YA parseados.
     *
     * <p>Sin la vuelta por el m3u. Aquella vuelta era la que perdía canales:
     * el emisor escribía el título sin la calidad ({@code (1080p)}), que
     * {@code M3uParser.deRotulo} se había guardado aparte, y al volver a
     * parsear el texto el dedupe por título fusionaba
     * {@code SEO TV 1 (1080p)} con {@code SEO TV 1 (720p)}.
     *
     * <p>Medido con los JSON del backend, antes y después:
     * <pre>
     *   alcance   por el m3u   directo
     *   corto        595        600
     *   latam       2456       2500
     *   global     11718      12000
     * </pre>
     * Y de paso se quitan 29-77 ms de trabajo por carga.
     *
     * @return los canales, o lista vacía si no hay copia en disco
     */
    @NonNull
    public static java.util.List<Canal> aCanales(@NonNull Context ctx) {
        java.util.List<Canal> c = cacheCanales;
        if (c != null) return c;
        synchronized (CatalogoRemoto.class) {
            if (cacheCanales != null) return cacheCanales;
            String json = jsonDisco(ctx);
            java.util.List<Canal> canales = json == null
                    ? new java.util.ArrayList<>()
                    : FuenteCanales.parsearCuerpo(json);
            cacheCanales = canales;
            return canales;
        }
    }

    /** Tira la copia en memoria (tras publicar o tras un fallo). */
    public static void invalidar() {
        synchronized (CatalogoRemoto.class) {
            cacheM3u = null;
            cacheCanales = null;
        }
    }

    /**
     * Baja el catálogo y, si sirve, lo guarda en disco.
     *
     * @return el m3u, o {@code null} si no se pudo (sin red, HTTP malo, o el texto
     *         no daba ni un canal). En ese caso NO se toca lo que hubiera en disco.
     */
    @Nullable
    public static String refrescar(@NonNull Context ctx) {
        // v7.17: PRIMERO la huella. Si la fuente no cambió desde la última vez y
        // el alcance es el mismo, no se baja NADA: se usa lo que hay en disco.
        // Medido: 512 B en vez de 146 KB - 2,7 MB. Sin esto, el alcance «global»
        // bajaba 2,7 MB en cada arranque aunque no hubiera canales nuevos.
        String alcance = AutoCanales.alcance(ctx);
        String remota = versionRemota();
        if (remota != null) {
            String[] meta = meta(ctx);
            if (remota.equals(meta[0]) && alcance.equals(meta[1])) {
                String guardado = aM3u(ctx);
                if (!guardado.isEmpty()) {
                    Log.i(TAG, "catálogo sin cambios (" + remota + ", " + alcance
                            + "): se usa el de disco");
                    return guardado;
                }
            }
        }

        String json = descargar(urlScope(alcance));
        if (json == null) return null;
        java.util.List<Canal> frescos = FuenteCanales.parsearCuerpo(json);
        if (frescos.isEmpty()) {
            Log.w(TAG, "el JSON del backend no dio ni un canal (" + json.length() + " bytes)");
            return null;
        }
        if (!guardarDisco(ctx, json)) return null;
        if (remota != null) guardarMeta(ctx, remota, alcance);
        String m3u = aM3uDe(json);
        synchronized (CatalogoRemoto.class) {
            cacheM3u = m3u;
            cacheCanales = frescos;
        }
        Log.i(TAG, "catálogo remoto (" + alcance + "): " + frescos.size() + " canales");
        return m3u;
    }

    /**
     * v7.17 - la huella de la fuente, o {@code null} si no se pudo leer.
     *
     * <p>Son 512 B: es la consulta más barata que hay, y la que permite no
     * bajar el catálogo cuando no ha cambiado nada.
     */
    @Nullable
    private static String versionRemota() {
        try {
            String texto = descargar(urlVersion());
            if (texto == null || texto.isEmpty()) return null;
            int i = texto.indexOf("\"version\"");
            if (i < 0) return null;
            int a = texto.indexOf('"', i + 9);
            int b = a < 0 ? -1 : texto.indexOf('"', a + 1);
            if (a < 0 || b < 0) return null;
            String v = texto.substring(a + 1, b).trim();
            return v.isEmpty() ? null : v;
        } catch (Exception e) {
            return null;   // sin huella se baja el catálogo: es lo seguro
        }
    }

    /** La última huella conocida y el alcance con el que se bajó. */
    @NonNull
    private static String[] meta(@NonNull Context ctx) {
        try {
            File f = new File(ctx.getFilesDir(), ARCHIVO_META);
            if (!f.isFile() || f.length() == 0) return new String[]{"", ""};
            String t = new String(java.nio.file.Files.readAllBytes(f.toPath()),
                    StandardCharsets.UTF_8).trim();
            int c = t.indexOf('|');
            if (c < 0) return new String[]{t, ""};
            return new String[]{t.substring(0, c), t.substring(c + 1)};
        } catch (Exception e) {
            return new String[]{"", ""};
        }
    }

    private static void guardarMeta(@NonNull Context ctx, @NonNull String version,
                                     @NonNull String alcance) {
        try (FileOutputStream fos = new FileOutputStream(
                new File(ctx.getFilesDir(), ARCHIVO_META))) {
            fos.write((version + "|" + alcance).getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            Log.w(TAG, "guardando la huella: " + e);
        }
    }

    /**
     * Un GET. Bloqueante: llamar desde un hilo en segundo plano.
     *
     * @return el texto, o {@code null} si no se pudo
     */
    @Nullable
    public static String descargar(@NonNull String url) {
        Request req = new Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .build();
        try (Response r = cliente().newCall(req).execute()) {
            if (!r.isSuccessful()) {
                Log.w(TAG, "HTTP " + r.code() + " al bajar el catálogo");
                return null;
            }
            ResponseBody b = r.body();
            if (b == null) return null;
            String s = b.string();
            if (s == null) return null;
            s = s.trim();
            // Un portal cautivo o un proxy pueden contestar 200 con HTML: si no
            // empieza por '{' no es JSON y no se guarda.
            if (s.isEmpty() || s.charAt(0) != '{') {
                Log.w(TAG, "la respuesta no es JSON (" + s.length() + " bytes)");
                return null;
            }
            return s;
        } catch (IOException e) {
            Log.w(TAG, "bajando el catálogo: " + e);
            return null;
        } catch (Exception e) {
            Log.w(TAG, "bajando el catálogo (inesperado): " + e);
            return null;
        }
    }

    /**
     * v7.16 - el m3u de un JSON de catálogo, admitiendo los DOS esquemas.
     *
     * <p>El worker de Cloudflare devuelve el esquema NATIVO de la app
     * ({@code {"canales":[{title,image,category,options[]}]}}), que no es el
     * compacto de GitHub ({@code categories[]} + {@code channels[]} con
     * {@code servers[]}). Se distinguen por {@code options} y se convierte:
     * sin esto, {@link CatalogoJson#aM3uUnico} no sacaba ni un canal del
     * worker y el catálogo grande nunca se refrescaba.
     */
    @NonNull
    private static String aM3uDe(@NonNull String json) {
        if (esNativo(json)) {
            java.util.List<Canal> canales = FuenteCanales.parsearCuerpo(json);
            return canales.isEmpty() ? "" : CatalogoJson.aM3uDeCanales(canales);
        }
        return CatalogoJson.aM3uUnico(json);
    }

    /** ¿El JSON trae el esquema nativo (canales con {@code options})? */
    private static boolean esNativo(@NonNull String json) {
        try {
            com.google.gson.JsonElement raiz = com.google.gson.JsonParser.parseString(json);
            if (raiz == null || !raiz.isJsonObject()) return false;
            com.google.gson.JsonArray cs = raiz.getAsJsonObject().getAsJsonArray("canales");
            if (cs == null || cs.size() == 0) return false;
            com.google.gson.JsonObject primero = cs.get(0).getAsJsonObject();
            return primero != null && primero.has("options");
        } catch (Exception e) {
            return false;
        }
    }

    // ─────────────────────────── disco ───────────────────────────

    @Nullable
    private static String jsonDisco(@NonNull Context ctx) {
        try {
            File f = new File(ctx.getFilesDir(), ARCHIVO);
            if (!f.isFile() || f.length() == 0) return null;
            byte[] b = java.nio.file.Files.readAllBytes(f.toPath());
            if (b.length == 0) return null;
            return new String(b, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean guardarDisco(@NonNull Context ctx, @NonNull String json) {
        try {
            File f = new File(ctx.getFilesDir(), ARCHIVO);
            File tmp = new File(f.getPath() + ".tmp");
            try (FileOutputStream fos = new FileOutputStream(tmp)) {
                fos.write(json.getBytes(StandardCharsets.UTF_8));
            }
            if (!tmp.renameTo(f)) {
                try (FileOutputStream fos = new FileOutputStream(f)) {
                    fos.write(json.getBytes(StandardCharsets.UTF_8));
                }
                tmp.delete();
            }
            return true;
        } catch (Exception e) {
            Log.w(TAG, "guardando el catálogo: " + e);
            return false;
        }
    }
}
