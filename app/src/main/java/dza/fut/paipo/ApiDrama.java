package dza.fut.paipo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * v7.38.8 — <b>EL MISMO TRUCO QUE LA API DE DRAMA, DENTRO DE LA APP.</b>
 *
 * <p><b>Qué se descubrió (2026-11-18, medido contra
 * {@code dramalive-api-08th.onrender.com}).</b> Los marcadores {@code .LS.} que la
 * app descartaba SÍ se resuelven: el host del canal los publica como una página
 * {@code index.html} con un manifiesto HLS dentro, y ese manifiesto se puede
 * descargar. La respuesta de {@code /opciones/…/live_tv_beinsport1} de esa API lo
 * deja claro:</p>
 *
 * <pre>
 * ".LS.V2…description_SD_/s"  ->  https://spe6.speedsuperai.cfd/pages/alkoaqoohqze/index.html   emite:true
 * ".LS.V2…description_HD_/s"  ->  https://sup7.superdatanet.cfd/hd/azmyqsojuqbm/index.html      emite:true
 * ".LS.V2…description_4K_/s"  ->  https://ult11.ultraandroidsoftstarsfeedmoon.shop/fk/…/index.html emite:true
 * "primetv.cc.cd/…mono.tbin.m3u8" -> 403                                                          emite:false
 * "ordernap.net/e/…"            -> 403                                                          emite:false
 * "quotarevival.net/e/…"        -> 403                                                          emite:false
 * </pre>
 *
 * <p>O sea: en beIN 1 los que emiten son <b>justo los que la app tiraba</b>, y las dos
 * «útiles» que intentaba daban 403. Eso explica del todo el cuadro negro.</p>
 *
 * <p><b>Cómo se abre un marcador.</b> La app YA clasifica estas páginas como
 * {@code EMBEBIDA} cuando vienen de un trozo JSON (llevan {@code iframe}), y esa es
 * exactamente la vía correcta: se abren en el marco de la ventana, el JS de la página
 * pide su manifiesto, {@code recordarPlaylistCazado} lo caza y
 * {@code pasarANativoSiSePide} lo pasa a ExoPlayer. Lo que faltaba era <b>saber cuál
 * de las opciones emite</b> para no gastar 30 s por cada una.</p>
 *
 * <p><b>Qué NO hace esta clase.</b> No hay endpoint público que resuelva el marcador a
 * una URL final sin ejecutar la página: el redirector {@code glbr} solo contesta
 * dentro del navegador del sitio (medido, ver {@code docs/COMO_SESION_DRAMA.md}
 * v3.2.12). Así que la resolución sigue siendo por WebView — y está bien: es la única
 * que el sitio acepta.</p>
 *
 * <p>Esta clase es un <b>acelerador opcional</b>: si hay una API de la cadena
 * configurada, se le pregunta qué opción emite y se empieza por esa. Si no hay API, o
 * no contesta, todo sigue funcionando como en la v7.38.7 (memoria local + plan B).</p>
 */
final class ApiDrama {

    private ApiDrama() {}

    private static final String TAG = "BLABONGO_DEBUG";

    /**
     * Tiempo máximo de la consulta.
     *
     * <p><b>Por qué 12 s y no 2,5 s.</b> La v7.38.8 usaba 2,5 s para no frenar el
     * arranque. Medido el 2026-11-18: {@code /opciones} de beIN 5 tarda 13,3 s en UNA
     * sola opción (timeout interno de la API contra {@code rap8.rapidbackend.shop}) y
     * beIN 2 otros 13,1 s. Con 2,5 s la consulta caducaba SIEMPRE en esos canales y el
     * plan B se quedaba sin las opciones buenas — el canal en negro.</p>
     *
     * <p>Ahora no frena nada porque ya no está en el camino crítico: se lanza en paralelo
     * (ver {@code PlayerActivity.pedirOpcionesEnSegundoPlano}). Como nadie la espera,
     * vale la pena dejar que llegue.</p>
     */
    static final int TIEMPO_MS = 12000;

    /**
     * El host que se usa si el usuario no ha configurado otro.
     *
     * <p><b>Por qué hay uno por defecto.</b> El usuario pidió que la app haga «lo mismo
     * que la API». Con la casilla vacía eso no le funcionaría a nadie sin tocar ajustes.
     * Pero un servicio de terceros en plan gratuito duerme, así que no puede ser una
     * dependencia dura: ver {@link #sesionSinApi()} — si el host no contesta UNA vez,
     * se deja de preguntar en lo que queda de sesión y la app sigue como la v7.38.7.</p>
     */
    static final String BASE_POR_DEFECTO = "https://dramalive-api-08th.onrender.com";

    /**
     * Lo que la API dice de una opción del canal. {@code n} es el índice que usa esa
     * API para el proxy ({@code /p/{codigo}/{id}/{n}.m3u8}).
     */
    static final class OpcionApi {
        final int n;
        @NonNull final String tipo;          // marcador | directa | embed
        @NonNull final String origen;        // el trozo tal cual vino de Drama
        @NonNull final String url;           // a dónde apunta de verdad
        @Nullable final String ua;
        @Nullable final String referer;
        final boolean emite;
        final int http;

        OpcionApi(int n, @NonNull String tipo, @NonNull String origen, @NonNull String url,
                  @Nullable String ua, @Nullable String referer, boolean emite, int http) {
            this.n = n;
            this.tipo = tipo;
            this.origen = origen;
            this.url = url;
            this.ua = ua;
            this.referer = referer;
            this.emite = emite;
            this.http = http;
        }

        /** ¿Es una de las que la app puede reproducir de verdad? */
        boolean sirve() {
            if (!emite) return false;
            if (url.startsWith("http")) return true;
            return false;
        }

        /**
         * v7.38.9 — <b>¿la API se equivoca al decir que esta opción no emite?</b>
         *
         * <p>Medido el 2026-11-18: la API marca {@code emite:false} a canales que SÍ
         * emiten, porque solo sabe probar HLS. Dos casos reales:</p>
         *
         * <ul>
         *   <li>{@code live_tv_abudabyuae}: manifiesto MPEG-DASH con
         *       {@code http:200} y {@code content_type: application/dash+xml}, y la API
         *       lo da por muerto porque no es un {@code .m3u8}. Su propio proxy lo
         *       confirma: {@code /p/…} contesta «esa opción no es un manifiesto HLS (usa
         *       su URL directa)» y {@code /stream?n=0} sí redirige al {@code .mpd}.</li>
         *   <li>{@code live_tv_alkaas2}: {@code shoof.alkass.net} responde
         *       {@code http:200} con una página que reproduce de verdad.</li>
         * </ul>
         *
         * <p>Así que «no emite» solo se acepta cuando el fallo es de verdad: 403, 404,
         * timeout o un error explícito. Un 2xx con contenido, o un DASH/HLS que la API
         * no sabe leer, se conserva como plan B: es gratis probarlo, y tirar una url
         * buena es exactamente el fallo que dejaba canales en negro.</p>
         */
        boolean podriaEmitir() {
            if (sirve()) return true;
            if (url == null || !url.startsWith("http")) return false;
            if (http == 403 || http == 404 || http == 410) return false;
            // v7.38.10 — CORRECCIÓN de la v7.38.9. En la 7.38.9 rescataba cualquier
            // ".mpd" con 2xx. Estaba mal: medido el 2026-11-18, esos .mpd son
            // "cenc.mpd" / "..._dash_enc/index.mpd" y van cifrados en Widevine, que no
            // se puede abrir sin licencia. El propio diagnóstico del proyecto
            // (docs/DIAGNOSTICO-CANALES-DRAMA-v7.38.3.md) ya lo decía: "los de
            // starzplay son Widevine (imposible con clave suelta)". Meterlos en el plan
            // solo gasta turnos. Se quedan fuera.
            if (esDashCifrado()) return false;
            // Un 2xx con cuerpo que NO es un manifiesto cifrado: la API no supo leerlo
            // (p. ej. una página .html con el stream dentro). Eso sí vale la pena.
            return http >= 200 && http < 300;
        }

        /**
         * v7.38.10 — ¿este DASH va cifrado (y por tanto no lo podemos abrir)?
         *
         * <p>Dos señales, las dos medidas: el nombre ({@code cenc}, {@code _enc},
         * {@code _drm}) y que sea un {@code .mpd} pasando por un CDN que sirve CENC
         * (starzplay/intigral/otte). Ninguno de estos canales publica ClearKey: sin
         * licencia Widevine no hay nada que hacer, y fingir lo contrario es lo que
         * gastaba turnos en la 7.38.9.</p>
         */
        boolean esDashCifrado() {
            if (url == null) return false;
            String l = url.toLowerCase(Locale.ROOT);
            if (!l.contains(".mpd")) return false;
            // v7.38.10 — dos señales más, medidas en los .mpd reales de esta API:
            //  · `intigral-ott.net/…/manifest.mpd` es CENC de starzplay igual que los
            //    demás (misma plataforma, sin "cenc" en la ruta).
            //  · HTTP 299 no es un estado que use un CDN para un manifiesto válido; en
            //    esta API aparece justo en ese .mpd cifrado.
            if (http == 299) return true;
            if (l.contains("starzplay") || l.contains("intigral")) return true;
            return l.contains("cenc") || l.contains("_enc") || l.contains("_drm")
                    || l.contains("/dash/enc/") || l.contains("dash_enc");
        }
    }

    /** Respuesta completa de {@code /opciones}. */
    static final class Opciones {
        @NonNull final String codigo;
        @NonNull final String idLive;
        @NonNull final List<OpcionApi> opciones;
        final int emitiendo;

        Opciones(@NonNull String codigo, @NonNull String idLive,
                 @NonNull List<OpcionApi> opciones, int emitiendo) {
            this.codigo = codigo;
            this.idLive = idLive;
            this.opciones = opciones;
            this.emitiendo = emitiendo;
        }

        /**
         * Las que emiten <b>y</b> las que la API descarta por error (ver
         * {@link OpcionApi#podriaEmitir()}), en el orden en que la API las pone.
         *
         * <p>Primero las confirmadas y después las dudosas: si la API acertó, el orden
         * bueno no cambia; si se equivocó, hay una segunda oportunidad en vez de un
         * canal en negro.</p>
         */
        @NonNull
        List<OpcionApi> queEmiten() {
            List<OpcionApi> confirmadas = new ArrayList<>();
            List<OpcionApi> dudosas = new ArrayList<>();
            for (OpcionApi o : opciones) {
                if (o.sirve()) confirmadas.add(o);
                else if (o.podriaEmitir()) dudosas.add(o);
            }
            List<OpcionApi> out = new ArrayList<>(confirmadas);
            out.addAll(dudosas);
            return out;
        }

        /** Solo las confirmadas por la API. */
        @NonNull
        List<OpcionApi> confirmadas() {
            List<OpcionApi> out = new ArrayList<>();
            for (OpcionApi o : opciones) if (o.sirve()) out.add(o);
            return out;
        }
    }

    // ───────────────────────── parseo (puro y testeable) ─────────────────────────

    /**
     * Lee la respuesta de {@code /opciones/{codigo}/{id_live}}. Puro: no toca red.
     *
     * @return null si el JSON no tiene la forma esperada (una API caída, un portal
     *         cautivo o un cambio de esquema no deben romper nada)
     */
    @Nullable
    static Opciones leerOpciones(@Nullable String json) {
        if (json == null || json.trim().isEmpty()) return null;
        try {
            JsonElement raiz = JsonParser.parseString(json);
            if (raiz == null || !raiz.isJsonObject()) return null;
            JsonObject jo = raiz.getAsJsonObject();

            String codigo = texto(jo, "codigo");
            String idLive = null;
            JsonElement canal = jo.get("canal");
            if (canal != null && canal.isJsonObject()) {
                idLive = texto(canal.getAsJsonObject(), "id_live");
            }
            if (idLive == null) return null;

            int emitiendo = entero(jo, "emitiendo", 0);
            JsonElement arr = jo.get("opciones");
            if (arr == null || !arr.isJsonArray()) return null;

            List<OpcionApi> lista = new ArrayList<>();
            JsonArray a = arr.getAsJsonArray();
            for (JsonElement e : a) {
                if (e == null || !e.isJsonObject()) continue;
                JsonObject o = e.getAsJsonObject();
                String url = texto(o, "url");
                if (url == null || url.isEmpty()) continue;
                String tipo = texto(o, "tipo");
                String origen = texto(o, "origen");
                String ua = texto(o, "agent");
                String ref = null;
                JsonElement hs = o.get("headers");
                if (hs != null && hs.isJsonObject()) {
                    JsonObject h = hs.getAsJsonObject();
                    ref = texto(h, "Referer");
                    if (ref == null) ref = texto(h, "Origin");
                    if (ua == null) ua = texto(h, "User-Agent");
                }
                lista.add(new OpcionApi(
                        entero(o, "n", -1),
                        tipo == null ? "" : tipo,
                        origen == null ? url : origen,
                        url, ua, ref,
                        booleano(o, "emite", false),
                        entero(o, "http", 0)));
            }
            return new Opciones(codigo == null ? "" : codigo, idLive, lista, emitiendo);
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Convierte lo que dice la API en opciones para el plan B.
     *
     * <p><b>El orden importa.</b> Por cada opción que emite se ponen dos entradas: primero
     * la playlist ya proxada ({@code /p/…}, que ExoPlayer abre de forma nativa: sin
     * WebView, sin sniffer, sin CORS) y detrás la página original como {@code EMBEBIDA},
     * que es el plan B. Las que la API da por muertas pero traen contenido (DASH, o un
     * 200 que no sabe leer) se añaden al final con su URL directa: es gratis probarlas.</p>
     *
     * @param base   raíz de la API (puede ser null: entonces no hay proxada)
     * @param codigo código de país de Drama ({@code 232425})
     * @param idLive id del canal ({@code live_tv_beinsport1})
     */
    @NonNull
    static List<DramaResolucion.Opcion> aOpciones(@Nullable String base,
                                                  @Nullable String codigo,
                                                  @Nullable String idLive,
                                                  @Nullable Opciones o) {
        List<DramaResolucion.Opcion> out = new ArrayList<>();
        if (o == null) return out;
        String c = (codigo == null || codigo.trim().isEmpty()) ? o.codigo : codigo;
        String d = (idLive == null || idLive.trim().isEmpty()) ? o.idLive : idLive;

        for (OpcionApi x : o.queEmiten()) {
            // v7.38.9 — un portal (YouTube & cía) nunca entrega el canal a un WebView sin
            // sesión: no merece ocupar un turno del plan B. Se corta aquí, en origen.
            if (DramaResolucion.esPaginaDePortal(x.url)) continue;
            if (x.esDashCifrado()) {
                // Se deja constancia en el log: antes esto era un turno perdido.
                android.util.Log.d(TAG, "api drama: [" + x.n + "] DASH cifrado (Widevine), "
                        + "no se puede abrir sin licencia: " + x.url);
                continue;
            }
            boolean confirmada = x.sirve();
            // v7.38.9 — la proxada SOLO para lo que la API confirmó como HLS. Su proxy
            // contesta «esa opción no es un manifiesto HLS (usa su URL directa)» a todo
            // lo demás, y una url que se sabe que falla no debe ocupar un turno.
            String proxada = confirmada ? urlPlaylist(base, c, d, x.n) : null;
            if (proxada != null) {
                out.add(new DramaResolucion.Opcion(proxada, DramaResolucion.Tipo.DIRECTA,
                        null, null, "api·proxy [" + x.n + "] " + x.tipo, false));
            }
            DramaResolucion.Tipo t;
            if ("directa".equalsIgnoreCase(x.tipo) || DramaResolucion.esPlaylist(x.url)) {
                t = DramaResolucion.Tipo.DIRECTA;
            } else if ("marcador".equalsIgnoreCase(x.tipo) || "embed".equalsIgnoreCase(x.tipo)) {
                t = DramaResolucion.Tipo.EMBEBIDA;
            } else {
                t = DramaResolucion.Tipo.PAGINA;
            }
            // La proxada ya cubre el caso de "directa": no duplicamos el mismo stream.
            if (t == DramaResolucion.Tipo.DIRECTA && proxada != null) continue;
            out.add(DramaResolucion.deApi(x.url, t, x.ua, x.referer, x.n));
        }
        return out;
    }

    /** Atajo sin base: solo las páginas originales (para tests y para el modo sin API). */
    @NonNull
    static List<DramaResolucion.Opcion> aOpciones(@Nullable Opciones o) {
        return aOpciones(null, null, null, o);
    }

    // ───────────────────────── ajustes (nunca obligatorios) ─────────────────────────

    /** Preferencias propias: la app no mezcla esto con la fuente de canales. */
    private static final String PREFS = "api_drama";
    private static final String K_BASE = "base";

    /**
     * La raíz de la API configurada por el usuario, o {@code null} si no hay ninguna.
     *
     * <p><b>Por qué vacía por defecto.</b> Esta API es de un tercero y vive en un plan
     * gratuito que duerme. Una dependencia obligatoria convertiría un servicio externo
     * caído en «la app no abre canales». Sin API configurada, la v7.38.8 se comporta
     * exactamente como la v7.38.7: memoria local + plan B. Con API, además, empieza por
     * la playlist proxada.</p>
     */
    @Nullable
    static String baseConfigurada(@Nullable android.content.Context ctx) {
        if (ctx == null) return null;
        try {
            String v = ctx.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
                    .getString(K_BASE, null);
            if (v == null) return BASE_POR_DEFECTO;      // sin configurar: el de siempre
            if (v.trim().isEmpty()) return null;          // "" = el usuario la APAGÓ
            return recortar2(v);
        } catch (Throwable t) {
            return null;
        }
    }

    /** Guarda (o borra, con null o "") la raíz de la API. */
    static void guardarBase(@Nullable android.content.Context ctx, @Nullable String base) {
        if (ctx == null) return;
        try {
            ctx.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE).edit()
                    .putString(K_BASE, base == null ? "" : base.trim()).apply();
        } catch (Throwable ignored) { }
    }

    /** Estado de la API en esta sesión del proceso. */
    private static final int SIN_PROBAR = 0, VIVA = 1, MUERTA = 2;
    private static volatile int estado = SIN_PROBAR;

    /**
     * ¿Hay que seguir preguntando a la API en esta sesión?
     *
     * <p>Un plan gratuito que duerme puede tardar 30 s en despertar. No vamos a pagar eso
     * en cada canal: se prueba <b>una vez</b> y, si no contesta, se deja en paz hasta que
     * la app se reinicie. Así el peor caso es un único arranque 2,5 s más lento, y solo
     * si el host estaba dormido.</p>
     */
    static boolean sesionSinApi() {
        return estado == MUERTA;
    }

    /** Marca la API como caída para el resto de la sesión (lo llama un fallo real). */
    static void marcarCaida() {
        estado = MUERTA;
    }

    /** Solo para pruebas: vuelve a «sin probar». */
    static void olvidarEstado() {
        estado = SIN_PROBAR;
    }

    /**
     * ¿La API contesta ahora mismo? Se pregunta como mucho una vez por proceso (ver
     * {@link #sesionSinApi()}).
     */
    static boolean estaViva(@Nullable android.content.Context ctx) {
        if (estado == MUERTA) return false;
        if (estado == VIVA) return true;
        String b = baseConfigurada(ctx);
        if (b == null) { estado = MUERTA; return false; }
        try {
            String r = get(recortar(b) + "/salud");
            boolean ok = r != null && r.replace(" ", "").contains("\"ok\":true");
            estado = ok ? VIVA : MUERTA;
            return ok;
        } catch (Throwable t) {
            android.util.Log.d(TAG, "api drama: no responde (se sigue sin ella el resto de la sesion)");
            estado = MUERTA;
            return false;
        }
    }

    // ─────────────────── el proxy de playlist (puro y testeable) ───────────────────

    /**
     * El endpoint {@code /p/{codigo}/{id_live}/{n}.m3u8}: manifiesto HLS de UNA opción
     * concreta, con los segmentos ya reescritos a {@code /ps/…} (relativos).
     *
     * <p><b>Por qué esto es «lo mismo que la API, en Android».</b> ExoPlayer recibe una
     * url que ya lleva el UA y el Referer correctos inyectados por el proxy, así que no
     * hace falta WebView, ni cazar el manifiesto con un sniffer, ni pelearse con CORS.
     * Es la vía corta. Probado contra la API real (2026-11-18): devuelve {@code #EXTM3U}
     * con segmentos de 10 s.</p>
     *
     * @return null si faltan datos: preferimos el plan B local antes que una url rota.
     */
    @Nullable
    static String urlPlaylist(@Nullable String base, @Nullable String codigo,
                              @Nullable String idLive, int n) {
        if (n < 0) return null;
        String b = recortar2(base), c = limpio(codigo), d = limpio(idLive);
        if (b == null || c == null || d == null) return null;
        return b + "/p/" + c + "/" + d + "/" + n + ".m3u8";
    }

    /** El endpoint {@code /stream/…}: redirección directa a la url de la opción n. */
    @Nullable
    static String urlStream(@Nullable String base, @Nullable String codigo,
                            @Nullable String idLive, int n) {
        if (n < 0) return null;
        String b = recortar2(base), c = limpio(codigo), d = limpio(idLive);
        if (b == null || c == null || d == null) return null;
        return b + "/stream/" + c + "/" + d + "?n=" + n;
    }

    /**
     * Reescribe una lista de segmentos contra el proxy: {@code /ps/…} relativo → absoluto.
     * La API los emite relativos (medido), y ExoPlayer necesita absolutos si la playlist
     * se sirve desde un manifiesto en memoria.
     *
     * <p>Un {@code #EXT-X-KEY:URI="…"} también se reescribe: si el canal va cifrado en
     * ClearKey, la clave también pasa por el proxy.</p>
     */
    @NonNull
    static String reescribir(@NonNull String m3u8, @Nullable String base, @Nullable String codigo,
                             @Nullable String idLive, int n) {
        String b = recortar2(base), c = limpio(codigo), d = limpio(idLive);
        if (b == null || c == null || d == null || n < 0) return m3u8;
        String prefijo = b + "/ps/" + c + "/" + d + "/" + n + "/";
        StringBuilder out = new StringBuilder(m3u8.length() + 256);
        for (String linea : m3u8.split("\n", -1)) {
            String l = linea.replace("\r", "").trim();
            if (l.isEmpty() || l.startsWith("#")) {
                out.append(reescribirUri(l, prefijo)).append("\n");
            } else if (l.startsWith("http")) {
                out.append(l).append("\n");
            } else {
                // segmento relativo: unas veces viene como "/ps/…" y otras como "…js"
                String rel = l.startsWith("/") ? l.substring(1) : l;
                if (rel.startsWith("ps/")) out.append(b).append("/").append(rel).append("\n");
                else out.append(prefijo).append(rel).append("\n");
            }
        }
        return out.toString();
    }

    /** Reescribe el {@code URI="…"} de una etiqueta HLS (clave, init, mapa). */
    @NonNull
    private static String reescribirUri(@NonNull String linea, @NonNull String prefijo) {
        if (!linea.startsWith("#")) return linea;
        int i = linea.indexOf("URI=\"");
        if (i < 0) return linea;
        int ini = i + 5;
        int fin = linea.indexOf('"', ini);
        if (fin < 0) return linea;
        String uri = linea.substring(ini, fin);
        if (uri.startsWith("http")) return linea;
        String rel = uri.startsWith("/") ? uri.substring(1) : uri;
        String abs = rel.startsWith("ps/")
                ? prefijo.substring(0, prefijo.indexOf("/ps/") + 1) + rel
                : prefijo + rel;
        return linea.substring(0, ini) + abs + linea.substring(fin);
    }

    @Nullable
    private static String recortar2(@Nullable String s) {
        if (s == null) return null;
        String t = s.trim();
        while (t.endsWith("/")) t = t.substring(0, t.length() - 1);
        return t.isEmpty() ? null : t;
    }

    @Nullable
    private static String limpio(@Nullable String s) {
        if (s == null) return null;
        String t = s.trim();
        if (t.isEmpty()) return null;
        StringBuilder o = new StringBuilder(t.length());
        for (int i = 0; i < t.length(); i++) {
            char ch = t.charAt(i);
            if (Character.isLetterOrDigit(ch) || ch == '_' || ch == '-' || ch == '.') o.append(ch);
        }
        return o.length() == 0 ? null : o.toString();
    }

    // ─────────────────────────────── red ───────────────────────────────

    /**
     * Pregunta a la API qué opciones de este canal emiten. <b>Nunca lanza</b>: si algo
     * va mal devuelve null y el plan B sigue con lo que tiene.
     */
    @Nullable
    static Opciones pedirOpciones(@Nullable String base, @NonNull String codigo,
                                  @NonNull String idLive) {
        if (base == null || base.trim().isEmpty()) return null;
        if (sesionSinApi()) return null;              // ya se sabe que no contesta
        String url = recortar(base) + "/opciones/" + codigo + "/" + idLive;
        try {
            String cuerpo = get(url);
            Opciones o = leerOpciones(cuerpo);
            if (o == null) {
                android.util.Log.w(TAG, "api drama: respuesta no entendible de " + url);
                marcarCaida();
                return null;
            }
            estado = VIVA;
            android.util.Log.d(TAG, "api drama: " + o.opciones.size() + " opciones de "
                    + idLive + " · emitiendo " + o.emitiendo);
            for (OpcionApi x : o.opciones) {
                android.util.Log.d(TAG, "   · [" + x.n + "] " + x.tipo + " http=" + x.http
                        + " emite=" + x.emite + " -> " + x.url);
            }
            return o;
        } catch (Throwable t) {
            android.util.Log.w(TAG, "api drama: no se pudo consultar ("
                    + t.getClass().getSimpleName() + ") — no se vuelve a intentar esta sesion");
            marcarCaida();
            return null;
        }
    }

    @NonNull
    private static String get(@NonNull String url) throws Exception {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(TIEMPO_MS);
            c.setReadTimeout(TIEMPO_MS);
            c.setRequestProperty("Accept", "application/json");
            int code = c.getResponseCode();
            InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
            if (in == null) return "";
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                bo.write(buf, 0, n);
                if (bo.size() > 512 * 1024) break;      // tope: no es un catálogo
            }
            in.close();
            return new String(bo.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            if (c != null) c.disconnect();
        }
    }

    @NonNull
    private static String recortar(@NonNull String s) {
        String t = s.trim();
        while (t.endsWith("/")) t = t.substring(0, t.length() - 1);
        return t;
    }

    // ─────────────────────────── utilidades JSON ───────────────────────────

    @Nullable
    private static String texto(@NonNull JsonObject o, @NonNull String k) {
        JsonElement e = o.get(k);
        if (e == null || e.isJsonNull() || !e.isJsonPrimitive()) return null;
        try { return e.getAsString(); } catch (Throwable t) { return null; }
    }

    private static int entero(@NonNull JsonObject o, @NonNull String k, int def) {
        JsonElement e = o.get(k);
        if (e == null || e.isJsonNull() || !e.isJsonPrimitive()) return def;
        try { return e.getAsInt(); } catch (Throwable t) { return def; }
    }

    private static boolean booleano(@NonNull JsonObject o, @NonNull String k, boolean def) {
        JsonElement e = o.get(k);
        if (e == null || e.isJsonNull() || !e.isJsonPrimitive()) return def;
        try { return e.getAsBoolean(); } catch (Throwable t) { return def; }
    }
}
