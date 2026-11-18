package dza.fut.paipo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * v7.39.0 — <b>la carrera de opciones: todas a la vez, gana la que responde primero.</b>
 *
 * <p><b>El defecto que arregla, medido en el logcat del usuario</b> (2026-11-18 01:09:19
 * → 01:11:02, canal {@code live_tv_aljeriaelheddaf}):</p>
 *
 * <pre>
 * 01:09:19  glasbi da el stream directo  -> live.eighty-eight.tv:7770/elheddaf1/index.m3u8
 * 01:09:30  la API ya tiene las 4 proxadas (1,7 s de reloj) — pero nadie las mira
 * 01:09:34  timeout 15 s   (host muerto a nivel TCP)
 * 01:09:49  timeout 15 s
 * 01:10:05  timeout 15 s
 * 01:10:22  timeout 15 s   -> ExoPlayer se rinde. 63 s tirados
 * 01:11:02  ...y solo entonces se prueba la proxada [0]
 * 01:11:03  la proxada [0] responde HTTP 200 en 1 s
 * </pre>
 *
 * <p>Un minuto perdido esperando <em>en serie</em> lo que en paralelo costaba 1 s. El
 * usuario lo dijo tal cual: «el resolver debe pasar todas las options en paralelo, la que
 * responde primero la pasas al player, así demora mucho».</p>
 *
 * <p><b>Y sí se puede.</b> Las opciones no compiten por nada: son URLs distintas en hosts
 * distintos. Lo único que hay que hacer es (a) lanzarlas todas, (b) quedarse con la
 * primera que devuelve un manifiesto de verdad, (c) cerrar las demás. Eso es esta clase.</p>
 *
 * <h3>Qué cuenta como «responde»</h3>
 *
 * <p>No basta un 200: el CDN de Drama sirve la tarjeta rusa «ОТСУТСТВУЕТ ПОДПИСКА» como
 * un {@code m3u8} perfectamente válido (visto en beIN Fr1, v7.37.17) y hay portales que
 * contestan 200 con HTML. Así que se exige, en este orden:</p>
 *
 * <ol>
 *   <li>HTTP 200-299 (403/404/410 = no rotundo, se descarta en el acto);</li>
 *   <li>que la url no sea un DASH cifrado en Widevine (imposible sin licencia);</li>
 *   <li>que el cuerpo EMPIECE por {@code #EXTM3U} — leído solo el primer trozo, no el
 *       archivo entero (un directo no termina nunca);</li>
 *   <li>y que ese {@code #EXTM3U} no sea la tarjeta de aviso ({@link #esAviso}).</li>
 * </ol>
 *
 * <p>Todo lo que decide es puro y está en métodos {@code static}: se prueba en JVM sin
 * dispositivo y sin red ({@code CarreraDeOpcionesTest}, {@code docs/repro-v3912.mjs}).</p>
 *
 * <h3>Lo que NO hace</h3>
 *
 * <p>No reproduce, no toca el reproductor, no sabe de Activities. La carrera solo contesta
 * a «¿cuál de estas N urls da vídeo ahora mismo?». Quien la lanza decide qué hacer con la
 * ganadora y recibe por {@link Oyente} los descartes, para poder decir en el log por qué
 * se cayó cada una.</p>
 */
public final class CarreraDeOpciones {

    /** UA de móvil: el proxy acepta cualquiera razonable, pero se pide como un móvil. */
    public static final String UA =
            "Mozilla/5.0 (Linux; Android 13; SM-A536B) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36";

    /** Tope de espera por opción. El proxy de Render a veces arranca en frío (~13 s). */
    public static final int TOPE_MS = 15000;

    /** Cuántos bytes se leen para ver si es un manifiesto. Un #EXTM3U cabe en 8. */
    static final int MUESTRA_BYTES = 4096;

    /** Cuántas opciones se lanzan a la vez. Un canal trae 3-8; el tope es por prudencia. */
    static final int MAX_A_LA_VEZ = 8;

    private CarreraDeOpciones() { }

    // ───────────────────────────── decisión (puro) ─────────────────────────────

    /**
     * v7.39.0 — <b>¿este cuerpo es un manifiesto HLS de verdad?</b>
     *
     * <p>Se mira solo el principio. Un {@code .mpd} (DASH) no es HLS y se rechaza aquí:
     * si es Widevine no hay nada que hacer sin licencia, y si no lo es, el que decide es
     * {@link #esDashCifrado} antes de llegar.</p>
     */
    public static boolean pareceManifiesto(@Nullable String cuerpo) {
        if (cuerpo == null) return false;
        String s = cuerpo.trim();
        if (s.isEmpty()) return false;
        // BOM de UTF-8 que algunos host meten delante.
        if (s.startsWith("\uFEFF")) s = s.substring(1).trim();
        if (!s.startsWith("#EXTM3U")) {
            // Un mp4/mkv directo no empieza por #EXTM3U pero TAMPOCO es basura: se
            // acepta por cabecera de contenido más abajo (Content-Type). Aquí, no.
            return false;
        }
        return !esAviso(s);
    }

    /**
     * v7.39.0 — <b>la tarjeta de aviso disfrazada de canal.</b>
     *
     * <p>Medido en beIN Fr1: el CDN sirve «ОТСУТСТВУЕТ ПОДПИСКА» (no hay suscripción) como
     * un {@code m3u8} válido, con su {@code #EXTM3U}, su {@code TARGETDURATION} y sus
     * segmentos. ExoPlayer llega a {@code STATE_READY} y el usuario ve el cartel. Si no se
     * detecta aquí, la carrera elegiría justo el servidor que no emite.</p>
     */
    public static boolean esAviso(@Nullable String cuerpo) {
        if (cuerpo == null || cuerpo.isEmpty()) return false;
        String l = cuerpo.toLowerCase(Locale.ROOT);
        return l.contains("отсутствует подписка")       // ruso: no hay suscripción
                || l.contains("no subscription")
                || l.contains("subscription required")
                || l.contains("channel is offline")
                || l.contains("/error/");                // tarjeta servida como segmento
    }

    /**
     * v7.39.0 — <b>los códigos que son un NO ROTUNDO.</b>
     *
     * <p>403/404/410 significan «esta opción no existe para ti», y reintentarla es tirar
     * el tiempo. Cualquier otro (200, 301, 500, o ninguno porque hubo timeout) NO descarta
     * nada: eso es lo aprendido en la v7.38.11 — un {@code http:null} de la sonda de la API
     * es «no lo sé», y el proxy lo resuelve igual (medido con beIN 2 y beIN 5).</p>
     */
    public static boolean esNoRotundo(int http) {
        return http == 403 || http == 404 || http == 410;
    }

    /**
     * v7.39.0 — ¿es un DASH cifrado en Widevine? Sin licencia de dispositivo no se puede
     * descifrar, así que no gasta un hueco en la carrera.
     *
     * <p>Medido: {@code starzplayarabia} ({@code _dash_enc}), {@code otte.*.aiv-cdn.net}
     * ({@code cenc.mpd}) y {@code uselector.cdn.intigral-ott.net/ADE.isml/manifest.mpd}
     * (http 299) traen su caja PSSH en el propio {@code .mpd}.</p>
     */
    public static boolean esDashCifrado(@Nullable String url, int http) {
        if (url == null) return false;
        String l = url.toLowerCase(Locale.ROOT);
        if (!l.contains(".mpd")) return false;
        if (http == 299) return true;                     // respuesta rara medida
        if (l.contains("starzplay") || l.contains("intigral")) return true;
        return l.contains("cenc") || l.contains("_enc") || l.contains("_drm")
                || l.contains("/dash/enc/") || l.contains("dash_enc");
    }

    /**
     * v7.39.0 — <b>¿merece esta url un hueco en la carrera?</b>
     *
     * <p>Se descartan sin gastar red: lo que no es http(s), lo que es un portal de vídeo
     * (YouTube y compañía, que nunca entregan el manifiesto a un WebView sin sesión) y el
     * DASH cifrado. Todo lo demás entra, <em>aunque la API lo diera por muerto</em>.</p>
     */
    public static boolean esCandidata(@Nullable DramaResolucion.Opcion o) {
        if (o == null || o.url == null) return false;
        String l = o.url.toLowerCase(Locale.ROOT);
        if (!l.startsWith("http")) return false;
        // v7.39.2 — los marcadores NO son urls: son órdenes para el redirector del sitio
        // («.LS.V2live_tv_custom_handler_…»). En el logcat del usuario cuatro de ellos
        // gastaron un hilo cada uno para morir con UnknownHostException en 1 ms. Y lo peor
        // no era el gasto: el plan B los ofrecía después como «servidor del canal».
        if (l.contains(".ls.") || l.contains("/.ls")) return false;
        if (DramaResolucion.esPaginaDePortal(o.url)) return false;
        if (esDashCifrado(o.url, 0)) return false;
        return true;
    }

    /**
     * Recorta la lista a las que merecen un hueco, sin repetir y respetando el orden que
     * ya traía (que es el de {@link DramaResolucion#planDeAtaque}: memoria → API → Drama).
     */
    @NonNull
    public static List<DramaResolucion.Opcion> candidatas(
            @Nullable List<DramaResolucion.Opcion> plan) {
        List<DramaResolucion.Opcion> out = new ArrayList<>();
        if (plan == null) return out;
        java.util.LinkedHashSet<String> vistos = new java.util.LinkedHashSet<>();
        for (DramaResolucion.Opcion o : plan) {
            if (!esCandidata(o)) continue;
            if (!vistos.add(o.url)) continue;
            out.add(o);
            if (out.size() >= MAX_A_LA_VEZ) break;
        }
        return out;
    }

    // ───────────────────────────── la sonda (red) ─────────────────────────────

    /** Lo que contestó una opción. Se devuelve también en los descartes, para el log. */
    public static final class Respuesta {
        public final DramaResolucion.Opcion opcion;
        public final int http;          // 0 = no hubo respuesta (timeout, DNS, TLS…)
        public final boolean gana;
        public final String motivo;
        public final long ms;

        Respuesta(DramaResolucion.Opcion opcion, int http, boolean gana, String motivo, long ms) {
            this.opcion = opcion;
            this.http = http;
            this.gana = gana;
            this.motivo = motivo;
            this.ms = ms;
        }

        @Override public String toString() {
            return (gana ? "GANA " : "no   ") + http + " " + ms + "ms " + motivo
                    + " → " + (opcion == null ? "?" : opcion.url);
        }
    }

    /** Quien lanza la carrera recibe aquí la ganadora y los descartes. */
    public interface Oyente {
        /** La primera que dio manifiesto. Llega UNA vez. En el hilo de la carrera. */
        void ganadora(@NonNull Respuesta r);
        /** Un descarte, con su motivo. Puede llegar después de {@link #ganadora}. */
        void descartada(@NonNull Respuesta r);
        /** No quedó ninguna. Llega solo si además no hubo ganadora. */
        void ninguna(@NonNull List<Respuesta> intentos);
    }

    /**
     * v7.39.0 — <b>lanza todas las opciones a la vez y devuelve la primera que responde.</b>
     *
     * <p>Bloquea al que la llama (llámala desde un hilo de fondo). Abre un hilo por opción,
     * se queda con la primera que da manifiesto y cierra el resto: las que sigan esperando
     * se salen solas al ver {@code cerrada} puesto, sin esperar su timeout.</p>
     *
     * @param candidatas ya filtradas por {@link #candidatas}
     * @param oyente     recibe ganadora y descartes (puede ser {@code null} si no interesa)
     * @return la ganadora, o {@code null} si no hubo ninguna
     */
    @Nullable
    public static Respuesta correr(@NonNull List<DramaResolucion.Opcion> candidatas,
                                   @Nullable final Oyente oyente) {
        final int n = candidatas.size();
        if (n == 0) {
            if (oyente != null) oyente.ninguna(new ArrayList<Respuesta>());
            return null;
        }
        final AtomicBoolean cerrada = new AtomicBoolean(false);
        final AtomicInteger quedan = new AtomicInteger(n);
        final Object candado = new Object();
        final Respuesta[] ganadora = new Respuesta[1];
        final List<Respuesta> intentos =
                java.util.Collections.synchronizedList(new ArrayList<Respuesta>());

        for (int i = 0; i < n; i++) {
            final DramaResolucion.Opcion op = candidatas.get(i);
            Thread h = new Thread(new Runnable() {
                @Override public void run() {
                    Respuesta r = sondear(op, cerrada);
                    intentos.add(r);
                    if (r.gana && cerrada.compareAndSet(false, true)) {
                        synchronized (candado) { ganadora[0] = r; candado.notifyAll(); }
                        if (oyente != null) oyente.ganadora(r);
                    } else if (!r.gana) {
                        if (oyente != null) oyente.descartada(r);
                    }
                    if (quedan.decrementAndGet() == 0) {
                        synchronized (candado) { candado.notifyAll(); }
                    }
                }
            }, "carrera-" + i);
            h.setDaemon(true);
            h.start();
        }

        // Se espera a que haya ganadora o a que todas hayan terminado. Sin sondeos
        // periódicos: un solo wait, con el tope de una opción más un margen.
        long limite = System.currentTimeMillis() + TOPE_MS + 2000L;
        synchronized (candado) {
            while (ganadora[0] == null && quedan.get() > 0) {
                long falta = limite - System.currentTimeMillis();
                if (falta <= 0) break;
                try { candado.wait(falta); } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        cerrada.set(true);                       // que las demás no esperen su timeout
        if (ganadora[0] == null && oyente != null) oyente.ninguna(intentos);
        return ganadora[0];
    }

    /**
     * Pide una url y dice si da manifiesto. Nunca lanza.
     *
     * <p>Lee solo {@link #MUESTRA_BYTES} del cuerpo: un directo no termina nunca y leerlo
     * entero era esperar para siempre. Con la cabecera basta.</p>
     */
    @NonNull
    static Respuesta sondear(@NonNull DramaResolucion.Opcion op,
                             @NonNull AtomicBoolean cerrada) {
        long t0 = System.currentTimeMillis();
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(op.url).openConnection();
            c.setConnectTimeout(TOPE_MS);
            c.setReadTimeout(TOPE_MS);
            c.setInstanceFollowRedirects(true);
            c.setRequestProperty("User-Agent", UA);
            c.setRequestProperty("Accept", "*/*");
            if (op.referer != null && !op.referer.isEmpty()) {
                c.setRequestProperty("Referer", op.referer);
            }
            if (cerrada.get()) {
                return new Respuesta(op, 0, false, "ya habia ganadora", 0);
            }
            int http = c.getResponseCode();
            long ms = System.currentTimeMillis() - t0;
            if (esNoRotundo(http)) {
                return new Respuesta(op, http, false, "no rotundo", ms);
            }
            if (http < 200 || http > 299) {
                return new Respuesta(op, http, false, "http " + http, ms);
            }
            String tipo = c.getContentType();
            if (tipo != null && tipo.toLowerCase(Locale.ROOT).startsWith("video/")) {
                // Un mp4 servido tal cual: no hay #EXTM3U que leer, pero es vídeo.
                return new Respuesta(op, http, true, "video directo", ms);
            }
            String cabeza = leerCabeza(c);
            if (pareceManifiesto(cabeza)) {
                return new Respuesta(op, http, true, "manifiesto #EXTM3U", ms);
            }
            if (esAviso(cabeza)) {
                return new Respuesta(op, http, false, "tarjeta de aviso", ms);
            }
            return new Respuesta(op, http, false, "no es manifiesto", ms);
        } catch (Throwable t) {
            String m = t.getClass().getSimpleName();
            if (t.getMessage() != null && t.getMessage().contains("timeout")) m = "timeout";
            return new Respuesta(op, 0, false, m, System.currentTimeMillis() - t0);
        } finally {
            if (c != null) {
                try { c.disconnect(); } catch (Throwable ignored) { }
            }
        }
    }

    /** Lee como mucho {@link #MUESTRA_BYTES} del cuerpo, en UTF-8, sin cerrar el socket
     *  a medias de forma brusca (el disconnect del finally se encarga). */
    @Nullable
    static String leerCabeza(@NonNull HttpURLConnection c) {
        InputStream in = null;
        try {
            in = c.getInputStream();
            byte[] buf = new byte[MUESTRA_BYTES];
            int leido = 0;
            while (leido < buf.length) {
                int r = in.read(buf, leido, buf.length - leido);
                if (r < 0) break;
                leido += r;
            }
            if (leido <= 0) return null;
            return new String(buf, 0, leido, "UTF-8");
        } catch (Throwable t) {
            return null;
        } finally {
            if (in != null) { try { in.close(); } catch (Throwable ignored) { } }
        }
    }
}
