package dza.fut.paipo;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.Request;
import okhttp3.Response;

/**
 * Carga de canales SIN configuración del usuario.
 *
 * La idea es la contraria a "pegar una URL": se recorre una cadena de fuentes
 * públicas hasta que una responda, se recuerda cuál fue la que funcionó para que el
 * próximo arranque empiece por ahí, y se guarda el cuerpo en el caché de la app para
 * que la rejilla se pinte aunque no haya red (o el proveedor esté caído).
 *
 * Qué hay en la cadena, y por qué:
 *   1. `data_unificado.json` (repo belkafut): el catálogo de la casa. 301 KB y 517
 *      canales con todos sus servidores, así que contesta en un visto y es lo
 *      PRIMERO que se pide (pedido del usuario: «cargamos este en primero»).
 *   2. El catálogo latino de la app (`canales.json`), que es el que se fusiona con
 *      la rejilla de Drama.
 *   3. La lista con la que la app ya venía (deportes; es la que reproduce en el player).
 *   4. Free-TV/IPTV, que explícitamente es televisión abierta.
 *
 * v7.13: iptv-org FUERA de la cadena, por pedido explícito del usuario («no quiero el
 * iptv org»). Era el eslabón más lento: 507 KB de LATAM y 2,5 MB de global, con
 * timeouts de 30 s de conexión, y encima sus entradas eran las que más fallaban
 * (403/404 por geobloqueo o señal muerta). Con `data_unificado.json` delante, la
 * cadena resuelve en el primer salto.
 */
public final class AutoCanales {

    public static final String CLAVE_ACTIVO = "auto_activado";
    public static final String CLAVE_ALCANCE = "auto_alcance";

    /** Qué tan grande es el catálogo que se baja. Ver {@link #candidatos}. */
    public static final String ALCANCE_CORTO = "corto";
    public static final String ALCANCE_LATAM = "latam";
    public static final String ALCANCE_GLOBAL = "global";
    private static final String CLAVE_GANADOR = "auto_ganador_url";
    private static final String CLAVE_GANADOR_ETIQUETA = "auto_ganador_etiqueta";

    private static final String PREFS = "fuente_canales";
    /** v7.13 (pedido del usuario): LA PRIMERA fuente, la lista pequena de la ETAPA 1.
     * 462 canales, 108 KB, un solo viaje.
     *
     * v7.16: sale del WORKER de Cloudflare del usuario
     * (pelislatino-canales.pages.dev), que ya devuelve el esquema nativo de la
     * app.
     *
     * v7.17: ahora es un JSON ESTÁTICO del mismo backend (sin Worker, sin
     * CPU, sin límite de peticiones): 105 KB servidos por el borde.
     * Package-visible para el test de contrato. */
    static final String UNIFICADO =
            "https://pelislatino-canales.pages.dev/canales/pequena.json";
    /** v7.16 - RESPALDO de {@link #UNIFICADO}: el fichero de siempre en GitHub.
     *  Si el worker se cae, la cadena sigue por aqui y el usuario no se queda
     *  sin la lista pequena. NO es rapida: va detras del worker. */
    private static final String UNIFICADO_RESPALDO =
            "https://raw.githubusercontent.com/belkaperu/belkafut/refs/heads/main/data_unificado.json";
    private static final String FREE_TV = "https://raw.githubusercontent.com/Free-TV/IPTV/master/playlist.m3u8";
    /** v3.2.65: catálogo propio de canales latinos (repo del usuario, 479 canales
     *  con todos sus servidores en el JSON histórico de la app). Package-visible
     *  para que el test de contrato lo verifique. */
    static final String LATINOS_JSON =
            "https://raw.githubusercontent.com/youcefyakhoo/cesopppplop/main/canales.json";

    private static final long TTL_CACHE = TimeUnit.HOURS.toMillis(12);
    private static final long MAX_CACHE = 16L * 1024 * 1024;
    public static final int TOPE_CORTO = 600;
    public static final int TOPE_LATAM = 2500;
    public static final int TOPE_GLOBAL = 12000;
    private static final int MAX_FALLOS_EN_RESUMEN = 4;
    private static final String TAG = "AutoCanales";
    private static final Object CERROJO_PRECARGA = new Object();
    @Nullable private static List<Canal> canalesPrecargados;
    @Nullable private static String origenPrecargado;
    @Nullable private static String clavePrecargada;
    @Nullable private static String clavePrecargaEnCurso;

    public static final class Precarga {
        @NonNull public final List<Canal> canales;
        @NonNull public final String origen;

        private Precarga(@NonNull List<Canal> canales, @NonNull String origen) {
            this.canales = canales;
            this.origen = origen;
        }
    }

    public static class Fuente {
        public final String etiqueta;
        public final String url;
        /** Pesada = solo se intenta si el usuario la activó explícitamente. */
        public final boolean optativa;
        /** Panel Xtream si esta fuente es un `player_api.php`; null si es lista normal. */
        @Nullable
        public final Xtream.Panel panel;
        /** Texto pegado por el usuario (M3U, JSON o URLs de stream): no se descarga nada. */
        @Nullable
        public final String pegada;

        /** Rapida = timeouts cortos y UN solo intento. Es el lider de la cadena: si
         *  tarda, se pasa a la siguiente fuente en vez de dejar al usuario esperando
         *  (v7.14, «demora mas»). */
        final boolean rapida;

        Fuente(String etiqueta, String url, boolean optativa) {
            this(etiqueta, url, optativa, null, null);
        }

        Fuente(String etiqueta, String url, boolean optativa, @Nullable Xtream.Panel panel) {
            this(etiqueta, url, optativa, panel, null);
        }

        Fuente(String etiqueta, @Nullable String url, boolean optativa,
               @Nullable Xtream.Panel panel, @Nullable String pegada) {
            this(etiqueta, url, optativa, panel, pegada, false);
        }

        Fuente(String etiqueta, @Nullable String url, boolean optativa, boolean rapida) {
            this(etiqueta, url, optativa, null, null, rapida);
        }

        Fuente(String etiqueta, @Nullable String url, boolean optativa,
               @Nullable Xtream.Panel panel, @Nullable String pegada, boolean rapida) {
            this.etiqueta = etiqueta;
            this.url = url == null ? "" : url;
            this.optativa = optativa;
            this.panel = panel;
            this.pegada = pegada;
            this.rapida = rapida;
        }
    }

    public interface Oyente {
        /** `desdeCache` sirve para no anunciar "actualizado" cuando es datos viejos. */
        void onCanales(@NonNull List<Canal> canales, @NonNull String etiqueta, boolean desdeCache);

        void onError(@NonNull String resumen);
    }

    // v7.4: el pool de red lo lleva AppExecutors.red() (ver core/AppExecutors).

    private AutoCanales() {}

    // ─────────────────────────── preferencias ───────────────────────────

    public static boolean estaActivado(@NonNull Context contexto) {
        SharedPreferences prefs = contexto.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        // Sin preferencia guardada el automático va encendido: es lo que se espera de la app.
        return prefs.getBoolean(CLAVE_ACTIVO, true);
    }

    public static void setActivado(@NonNull Context contexto, boolean activado) {
        contexto.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putBoolean(CLAVE_ACTIVO, activado).apply();
    }

    /**
     * Tamaño del catálogo. En un dispositivo en español el defecto es LATAM: 2.422
     * canales por 507 KB es "un montón" sin bajar 2,5 MB por datos móviles.
     */
    @NonNull
    public static String alcance(@NonNull Context contexto) {
        String guardado = contexto.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(CLAVE_ALCANCE, "");
        if (ALCANCE_CORTO.equals(guardado) || ALCANCE_LATAM.equals(guardado) || ALCANCE_GLOBAL.equals(guardado)) {
            return guardado;
        }
        return esEspanol() ? ALCANCE_LATAM : ALCANCE_CORTO;
    }

    public static void setAlcance(@NonNull Context contexto, @NonNull String alcance) {
        contexto.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(CLAVE_ALCANCE, alcance).apply();
    }

    private static boolean esEspanol() {
        return "es".equalsIgnoreCase(Locale.getDefault().getLanguage());
    }

    @NonNull
    public static String etiquetaAlcance(@NonNull String alcance) {
        if (ALCANCE_GLOBAL.equals(alcance)) return "Global";
        if (ALCANCE_LATAM.equals(alcance)) return "LATAM";
        return "Corto";
    }

    public static int tope(@NonNull String alcance) {
        if (ALCANCE_GLOBAL.equals(alcance)) return TOPE_GLOBAL;
        if (ALCANCE_LATAM.equals(alcance)) return TOPE_LATAM;
        return TOPE_CORTO;
    }

    /** Etiqueta de la fuente que ganó la última vez, para mostrarla en pantalla. */
    @NonNull
    public static String ultimoGanador(@NonNull Context contexto) {
        return contexto.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(CLAVE_GANADOR_ETIQUETA, "");
    }

    @Nullable
    public static String paisDispositivo() {
        String cc = Locale.getDefault().getCountry().toLowerCase(Locale.ROOT);
        return TextUtils.isEmpty(cc) || cc.length() != 2 ? null : cc;
    }

    /**
     * v7.13 — las fuentes PÚBLICAS, en el orden en que se prueban. Sin {@link Context}
     * para que el test de contrato pueda verificarla sin Robolectric.
     *
     * <p>«Belkafut» ({@link #UNIFICADO}) va PRIMERA en cualquier alcance, que es lo que
     * pidió el usuario: 301 KB y 517 canales, contesta en un visto. Si el repo está
     * caído se sigue con «Latinos» y la lista de la app.
     *
     * <p>iptv-org quedó FUERA por pedido explícito («no quiero el iptv org»): era el
     * eslabón lento (507 KB de LATAM, 2,5 MB de global) y el que más fallaba.
     */
    @NonNull
    static List<Fuente> cadenaPublica() {
        List<Fuente> lista = new ArrayList<>();
        lista.add(new Fuente("Belkafut", UNIFICADO, false, true));
        lista.add(new Fuente("Belkafut · respaldo", UNIFICADO_RESPALDO, false));
        lista.add(new Fuente("Latinos", LATINOS_JSON, false));
        lista.add(new Fuente("Deportes (lista de la app)", FuenteCanales.URL_POR_DEFECTO, false));
        lista.add(new Fuente("TDT · Free-TV", FREE_TV, false));
        return lista;
    }

    @NonNull
    public static List<Fuente> candidatos(@NonNull Context contexto) {
        List<Fuente> lista = new ArrayList<>(cadenaPublica());

        // El ganador anterior pasa al principio: así el segundo arranque no vuelve a
        // probar toda la cadena.
        // v7.13: al estrenarse «Belkafut» se olvida UNA sola vez el ganador público
        // anterior, para que la cadena pruebe primero la fuente nueva.
        SharedPreferences prefC = contexto.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (!prefC.getBoolean("belkafut_v1", false)) {
            prefC.edit().remove(CLAVE_GANADOR).putBoolean("belkafut_v1", true).apply();
        }
        // v7.16: al estrenar el worker se olvida UNA sola vez el ganador
        // anterior, que apuntaba a la URL vieja de GitHub.
        if (!prefC.getBoolean("worker_v1", false)) {
            prefC.edit().remove(CLAVE_GANADOR).putBoolean("worker_v1", true).apply();
        }
        String ganador = prefC.getString(CLAVE_GANADOR, "");
        if (!TextUtils.isEmpty(ganador)) {
            for (int i = 0; i < lista.size(); i++) {
                if (lista.get(i).url.equals(ganador)) {
                    lista.add(0, lista.remove(i));
                    break;
                }
            }
        }
        // Un panel configurado manda sobre todo lo público: es lo que el usuario pidió, y si
        // falla se sigue con «Latinos»/Free-TV para no dejar la pestaña vacía.
        Xtream.Panel panel = Xtream.siActivo(contexto);
        if (panel != null) {
            lista.add(0, new Fuente("Panel Xtream · " + panel.etiqueta(), "xtream:" + panel.base,
                    false, panel));
        }
        // La fuente que el usuario escribió (código FG o URL) manda sobre todo lo demás, pero
        // DENTRO de la cadena: así su lista se cachea y, si el servidor de códigos está caído
        // (fgcode.store responde 500 con su MySQL rechazando conexiones), la pestaña no se
        // queda vacía: sigue con LATAM/Global.
        FuenteCanales manual = FuenteCanales.desdePreferencias(contexto);
        if (!manual.pegada.isEmpty()) {
            lista.add(0, new Fuente("Fuente: " + manual.comoTexto(), null, false, null, manual.pegada));
        }
        if (manual.esPersonalizada() && manual.pegada.isEmpty()) {
            String resuelta = manual.urlResuelta();
            if (!TextUtils.isEmpty(resuelta)) {
                lista.add(0, new Fuente("Fuente: " + manual.comoTexto(), resuelta, false));
            }
        }
        return lista;
    }

    // ─────────────────────────── recorrido ───────────────────────────

    public static void cargarAsync(@NonNull final Context contexto, boolean forzarRed,
                                   @NonNull final Oyente oyente) {
        final List<Fuente> pendientes = candidatos(contexto);
        final Context app = contexto.getApplicationContext();
        ejecutor().execute(() -> recorrer(app, pendientes, forzarRed, oyente));
    }

    /** Calienta la fuente de canales mientras el usuario está en la agenda. */
    public static void precargarAsync(@NonNull Context contexto) {
        final Context app = contexto.getApplicationContext();
        final String clave = clavePrecarga(app);
        synchronized (CERROJO_PRECARGA) {
            if (clave.equals(clavePrecargada) && canalesPrecargados != null) return;
            if (clave.equals(clavePrecargaEnCurso)) return;
            clavePrecargaEnCurso = clave;
        }

        if (estaActivado(app)) {
            cargarAsync(app, false, new Oyente() {
                @Override public void onCanales(@NonNull List<Canal> canales,
                                                @NonNull String etiqueta,
                                                boolean desdeCache) {
                    guardarPrecarga(clave, canales, etiqueta);
                }

                @Override public void onError(@NonNull String resumen) {
                    fallarPrecarga(clave, resumen);
                }
            });
        } else {
            FuenteCanales.desdePreferencias(app).cargarAsync(new FuenteCanales.Oyente() {
                @Override public void onCanales(@NonNull List<Canal> canales) {
                    guardarPrecarga(clave, canales, "Fuente configurada");
                }

                @Override public void onError(@NonNull String mensaje,
                                              boolean esHttp, int codigoHttp) {
                    fallarPrecarga(clave, mensaje);
                }
            });
        }
    }

    @Nullable
    public static Precarga precargaDisponible(@NonNull Context contexto) {
        String clave = clavePrecarga(contexto.getApplicationContext());
        synchronized (CERROJO_PRECARGA) {
            if (!clave.equals(clavePrecargada) || canalesPrecargados == null
                    || origenPrecargado == null) return null;
            return new Precarga(new ArrayList<>(canalesPrecargados), origenPrecargado);
        }
    }

    @NonNull
    private static String clavePrecarga(@NonNull Context contexto) {
        FuenteCanales fuente = FuenteCanales.desdePreferencias(contexto);
        Xtream.Panel panel = Xtream.siActivo(contexto);
        return estaActivado(contexto) + "|" + alcance(contexto) + "|"
                + fuente.codigo + "|" + fuente.url + "|" + fuente.endpoint + "|"
                + fuente.pegada + "|" + fuente.cabeceras + "|" + fuente.dramaKey + "|"
                + (panel == null ? "" : panel.base + "|" + panel.usuario + "|" + panel.clave);
    }

    private static void guardarPrecarga(@NonNull String clave, @NonNull List<Canal> canales,
                                        @NonNull String origen) {
        synchronized (CERROJO_PRECARGA) {
            if (!clave.equals(clavePrecargaEnCurso)) return;
            canalesPrecargados = new ArrayList<>(canales);
            origenPrecargado = origen;
            clavePrecargada = clave;
            clavePrecargaEnCurso = null;
        }
        Log.i(TAG, "precarga lista: " + canales.size() + " canales (" + origen + ")");
    }

    private static void fallarPrecarga(@NonNull String clave, @NonNull String motivo) {
        synchronized (CERROJO_PRECARGA) {
            if (!clave.equals(clavePrecargaEnCurso)) return;
            clavePrecargaEnCurso = null;
        }
        Log.w(TAG, "precarga de canales falló: " + motivo);
    }

    private static void recorrer(@NonNull Context app, @NonNull List<Fuente> fuentes,
                                 boolean forzarRed, @NonNull Oyente oyente) {
        List<String> fallos = new ArrayList<>();
        for (Fuente fuente : fuentes) {
            if (fuente.pegada != null) {
                List<Canal> pegados = ListaPegada.desdeTexto(fuente.pegada);
                if (pegados != null && !pegados.isEmpty()) {
                    recordarGanador(app, fuente);
                    limitar(pegados, tope(alcance(app)));
                    final List<Canal> finales = pegados;
                    FuenteCanales.enMain(() -> oyente.onCanales(finales, fuente.etiqueta, false));
                    return;
                }
                fallos.add(fuente.etiqueta + ": el texto pegado no es M3U, JSON ni una URL de stream");
                continue;
            }
            if (fuente.panel != null) {
                try {
                    List<Canal> delPanel = Xtream.cargar(fuente.panel);
                    if (!delPanel.isEmpty()) {
                        recordarGanador(app, fuente);
                        limitar(delPanel, Math.max(tope(alcance(app)), TOPE_GLOBAL));
                        final List<Canal> finales = delPanel;
                        FuenteCanales.enMain(() -> oyente.onCanales(finales, fuente.etiqueta, false));
                        return;
                    }
                    fallos.add(fuente.etiqueta + ": 0 canales");
                } catch (Exception e) {
                    fallos.add(fuente.etiqueta + ": " + mensajeDe(e));
                    Log.w(TAG, "panel xtream -> " + mensajeDe(e));
                }
                continue;
            }
            File cache = archivoCache(app, fuente.url);

            if (cache.exists() && !forzarRed && System.currentTimeMillis() - cache.lastModified() < TTL_CACHE) {
                List<Canal> delCache = leerCache(cache);
                if (!delCache.isEmpty()) {
                    limitar(delCache, tope(alcance(app)));
                    FuenteCanales.enMain(() -> oyente.onCanales(delCache, fuente.etiqueta + " · caché", true));
                    return;
                }
            }

            String cuerpo = null;
            try {
                cuerpo = descargar(app, fuente.url, fuente.rapida);
            } catch (Exception e) {
                fallos.add(fuente.etiqueta + ": " + mensajeDe(e));
                Log.w(TAG, fuente.url + " -> " + mensajeDe(e));
            }

            if (cuerpo != null) {
                try {
                    List<Canal> canales = FuenteCanales.parsearCuerpo(cuerpo);
                    if (!canales.isEmpty()) {
                        escribirCache(cache, cuerpo);
                        recordarGanador(app, fuente);
                        limitar(canales, tope(alcance(app)));
                        final List<Canal> finales = canales;
                        FuenteCanales.enMain(() -> oyente.onCanales(finales, fuente.etiqueta, false));
                        return;
                    }
                    fallos.add(fuente.etiqueta + ": sin canales");
                } catch (Exception e) {
                    fallos.add(fuente.etiqueta + ": " + mensajeDe(e));
                }
            }

            // Ni la red ni un cuerpo útil: si hay caché viejo, mejor eso que la pantalla vacía.
            List<Canal> rancio = leerCache(cache);
            if (!rancio.isEmpty()) {
                limitar(rancio, tope(alcance(app)));
                final List<Canal> rescatados = rancio;
                FuenteCanales.enMain(() -> oyente.onCanales(rescatados, fuente.etiqueta + " · caché", true));
                return;
            }
        }

        final String resumen = resumir(fallos);
        FuenteCanales.enMain(() -> oyente.onError(resumen));
    }

    @NonNull
    private static String descargar(@NonNull Context app, @NonNull String url) throws IOException {
        return descargar(app, url, false);
    }

    private static String descargar(@NonNull Context app, @NonNull String url,
                                   boolean rapida) throws IOException {
        try {
            return descargar1(app, url, rapida);
        } catch (IOException e) {
            if (FuenteCanales.esFalloDeConexion(e)) {
                String alterno = FuenteCanales.otroEsquema(url);
                if (alterno != null) {
                    try {
                        return descargar1(app, alterno, rapida);
                    } catch (IOException e2) {
                        throw new IOException(mensajeDe(e) + " (ni por el otro esquema: "
                                + mensajeDe(e2) + ")");
                    }
                }
            }
            throw e;
        }
    }

    /**
     * v7.15 — los canales que hay en el caché de una URL, SIN tocar la red.
     *
     * <p>Es la ETAPA 1 de la carga (pedido del usuario: «cargar una lista pequeña
     * [data_unificado.json] y después rellenarla con la grande en segundo plano»).
     * La lista pequeña está cacheada, así que aparece aunque el proveedor esté caído
     * o el teléfono sin red. Vacía si todavía no se ha bajado nunca.
     */
    @NonNull
    public static List<Canal> desdeCache(@NonNull Context contexto, @NonNull String url) {
        return leerCache(archivoCache(contexto.getApplicationContext(), url));
    }

    @NonNull
    private static String descargar1(@NonNull Context app, @NonNull String url) throws IOException {
        return descargar1(app, url, false);
    }

    /**
     * v7.14 — la fuente RAPIDA (el lider de la cadena) lleva timeouts cortos y UN
     * solo intento. Antes todas compartian la escalera de 3 intentos con siestas de
     * 5 s y 15 s: si el lider no contestaba, el usuario esperaba hasta 2 minutos
     * antes de ver la cadena seguir («demora mas»). Con la rejilla local ya pintada,
     * esperar eso no compra nada: se pasa a la fuente siguiente.
     */
    @NonNull
    private static String descargar1(@NonNull Context app, @NonNull String url,
                                    boolean rapida) throws IOException {
        Request peticion = FuenteCanales.conCabecerasDelUsuario(new Request.Builder().url(url)
                        .header("User-Agent", "Mozilla/5.0 (Linux; Android) Blabongo"),
                // las listas públicas no van a necesitarlas, pero un .m3u del proveedor
                // tras un CDN con control de acceso sí: misma fuente de cabeceras que el reproductor.
                FuenteCanales.desdePreferencias(app).encabezados().para(url));
        okhttp3.OkHttpClient cliente = rapida ? clienteRapido() : FuenteCanales.cliente();
        int intentosMax = rapida ? 1 : 3;
        IOException ultimo = null;
        for (int intento = 1; intento <= intentosMax; intento++) {
            try (Response response = cliente.newCall(peticion).execute()) {
                String cuerpo = response.body() != null ? response.body().string() : "";
                if (!response.isSuccessful()) {
                    IOException e5 = new IOException("HTTP " + response.code());
                    if (response.code() >= 500 && intento < 3) { ultimo = e5; }
                    else throw e5;
                } else {
                    return cuerpo;
                }
            } catch (IOException e) {
                if (ultimo == null || e.getMessage() == null || !e.getMessage().startsWith("HTTP ")) ultimo = e;
                if (intento >= 3) throw ultimo != null ? ultimo : e;
            }
            try { Thread.sleep(rapida ? 0L : (intento == 1 ? 2_000L : 6_000L)); }
            catch (InterruptedException ie) { Thread.currentThread().interrupt(); throw new IOException("cancelado"); }
        }
        throw ultimo != null ? ultimo : new IOException("descarga fallo");
    }

    /** 8 s de conexion / 15 s de lectura: de sobra para 300 KB, y si no contesta se
      *  sigue con la fuente siguiente en vez de dejar al usuario esperando. */
    private static volatile okhttp3.OkHttpClient clienteRapido;

    private static synchronized okhttp3.OkHttpClient clienteRapido() {
        if (clienteRapido == null) {
            clienteRapido = new okhttp3.OkHttpClient.Builder()
                    .connectTimeout(8, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)
                    .followRedirects(true)
                    .build();
        }
        return clienteRapido;
    }

    /** Recorta la lista al tope del catálogo elegido. */
    private static void limitar(@NonNull List<Canal> canales, int tope) {
        while (canales.size() > tope) {
            canales.remove(canales.size() - 1);
        }
    }

    /**
     * Los N grupos con más canales, para las pestañas. Con 2.400 canales hay decenas de
     * `group-title`; si se toman los primeros por orden de aparición salen "Legislative"
     * o "Religious" y se pierden General/Music/News, que son los gordos.
     * Los empates conservan el orden de la lista (más estable que ordenar por nombre).
     */
    @NonNull
    public static List<Map.Entry<String, Integer>> gruposPrincipales(
            @NonNull Map<String, Integer> conteo, int max) {
        List<Map.Entry<String, Integer>> todo = new ArrayList<>(conteo.entrySet());
        todo.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        return todo.size() > max ? new ArrayList<>(todo.subList(0, max)) : todo;
    }

    // ─────────────────────────── caché en disco ───────────────────────────

    @NonNull
    private static File carpetaCache(@NonNull Context app) {
        File carpeta = new File(app.getCacheDir(), "fuentes");
        if (!carpeta.exists()) carpeta.mkdirs();
        return carpeta;
    }

    @NonNull
    private static File archivoCache(@NonNull Context app, @NonNull String url) {
        // El hash del nombre no es criptográfico: solo hay que evitar colisiones triviales.
        String nombre = String.format(Locale.ROOT, "%08x.txt", url.hashCode());
        return new File(carpetaCache(app), nombre);
    }

    @NonNull
    private static List<Canal> leerCache(@NonNull File archivo) {
        try {
            if (!archivo.exists() || archivo.length() > MAX_CACHE) return new ArrayList<>();
            String cuerpo = new String(Files.readAllBytes(archivo.toPath()), StandardCharsets.UTF_8);
            return FuenteCanales.parsearCuerpo(cuerpo);
        } catch (IOException | RuntimeException e) {
            Log.w(TAG, "caché ilegible: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    private static void escribirCache(@NonNull File archivo, @NonNull String cuerpo) {
        if (cuerpo.length() > MAX_CACHE) return;
        try {
            Files.write(archivo.toPath(), cuerpo.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            Log.w(TAG, "no se pudo guardar el caché: " + e.getMessage());
        }
    }

    public static void vaciarCache(@NonNull Context app) {
        File carpeta = carpetaCache(app);
        File[] archivos = carpeta.listFiles();
        if (archivos != null) {
            for (File a : archivos) {
                //noinspection ResultOfMethodCallIgnored
                a.delete();
            }
        }
    }

    @NonNull
    public static String estadoCache(@NonNull Context app) {
        File[] archivos = carpetaCache(app).listFiles();
        if (archivos == null || archivos.length == 0) return "";
        long vieja = Long.MAX_VALUE;
        for (File a : archivos) vieja = Math.min(vieja, a.lastModified());
        long hace = System.currentTimeMillis() - vieja;
        if (hace < TimeUnit.MINUTES.toMillis(1)) return "hace un momento";
        if (hace < TimeUnit.HOURS.toMillis(1)) return "hace " + TimeUnit.MILLISECONDS.toMinutes(hace) + " min";
        if (hace < TimeUnit.DAYS.toMillis(1)) {
            return "hace " + TimeUnit.MILLISECONDS.toMinutes(hace) / 60 + " h";
        }
        return "hace " + TimeUnit.MILLISECONDS.toDays(hace) + " d";
    }

    private static void recordarGanador(@NonNull Context app, @NonNull Fuente fuente) {
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(CLAVE_GANADOR, fuente.url)
                .putString(CLAVE_GANADOR_ETIQUETA, fuente.etiqueta)
                .apply();
    }

    @NonNull
    private static String mensajeDe(@NonNull Throwable e) {
        String m = e.getMessage();
        return TextUtils.isEmpty(m) ? e.getClass().getSimpleName() : m;
    }

    @NonNull
    private static String resumir(@NonNull List<String> fallos) {
        if (fallos.isEmpty()) return "Ninguna fuente respondió.";
        StringBuilder sb = new StringBuilder();
        int n = Math.min(fallos.size(), MAX_FALLOS_EN_RESUMEN);
        for (int i = 0; i < n; i++) {
            if (i > 0) sb.append(" · ");
            sb.append(fallos.get(i));
        }
        if (fallos.size() > n) sb.append(" · +").append(fallos.size() - n);
        return sb.toString();
    }

    /**
     * Pool de red. Antes era un ejecutor de UN hilo pedido a un sitio que podia
     * romperse; ahora es el pool acotado y bien configurado de AppExecutors.
     */
    @NonNull
    private static ExecutorService ejecutor() {
        return dza.fut.paipo.core.AppExecutors.red();
    }
}
