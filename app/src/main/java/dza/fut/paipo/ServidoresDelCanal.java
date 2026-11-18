package dza.fut.paipo;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * v7.38.7 — <b>LA MEMORIA DE SERVIDORES DE CADA CANAL.</b>
 *
 * <p><b>El problema que resuelve.</b> La app ya sabía hacer lo más difícil: cuando el
 * embed de un canal arranca, su JS pide el playlist real y {@code PlayerActivity} lo
 * caza ({@code recordarPlaylistCazado}) y se lo pasa a ExoPlayer
 * ({@code pasarANativoSiSePuede}). Lo que <b>no</b> hacía era acordarse. Al volver a
 * abrir el mismo canal, todo empezaba de cero: los 2 POST, el embed, los 30 s de
 * sondeo… y otra vez a cazar el mismo playlist que ya había encontrado ayer.</p>
 *
 * <p>Eso es exactamente lo que Drama hace con sus servidores: los entrega por canal
 * (su {@code live.url} + {@code live.backup}) y su cliente los reutiliza. Aquí se
 * guarda lo mismo que la app ya descubre, con sus cabeceras exactas, y el plan B
 * empieza por ahí.</p>
 *
 * <p><b>Qué se guarda por canal:</b> la url del playlist, el User-Agent y el Referer
 * con los que el CDN la aceptó. Nada más. No se guardan cookies de sesión.</p>
 *
 * <p><b>Qué NO es:</b> no reimplementa el protocolo de Drama (medido y descartado:
 * su {@code glbr} solo responde dentro de su propio navegador, ver
 * {@code docs/COMO_SESION_DRAMA.md} v3.2.12). Esto no pide nada a nadie: reutiliza
 * lo que la propia app ya consiguió abriendo el canal como lo abre siempre.</p>
 *
 * <p><b>Por qué no vive en el .m3u de la rejilla.</b> {@code FuenteCanales.fijarUrlResuelta}
 * mete UNA url por canal dentro del M3U y la reescribe entera — es un listado, no un
 * perfil. Aquí hace falta por canal: varias urls ordenadas por cuántas veces
 * funcionaron, con su UA y su Referer, y marcar las que dejaron de servir. Eso en un
 * M3U no cabe sin ensuciarlo.</p>
 */
final class ServidoresDelCanal {

    private ServidoresDelCanal() {}

    private static final String PREFS = "servidores_canal_v2";
    private static final String SEP = "\u0001";       // separador que no aparece en una URL
    private static final String SEP_ITEM = "\u0002";

    /** Un servidor que ya se vio funcionar en este canal. */
    static final class Conocido {
        @NonNull final String url;
        @Nullable final String ua;
        @Nullable final String referer;
        /** Veces que se comprobó que reproduce. */
        final int exitos;
        /** Veces que se comprobó que NO reproduce (después de haber funcionado). */
        final int fallos;
        final long ultimoUso;

        Conocido(@NonNull String url, @Nullable String ua, @Nullable String referer,
                 int exitos, int fallos, long ultimoUso) {
            this.url = url;
            this.ua = ua;
            this.referer = referer;
            this.exitos = exitos;
            this.fallos = fallos;
            this.ultimoUso = ultimoUso;
        }

        /** ¿Merece la pena intentarlo primero? */
        boolean esFiable() {
            return exitos > 0 && exitos >= fallos;
        }

        @NonNull
        DramaResolucion.Opcion aOpcion() {
            return DramaResolucion.directaCon(url, ua, referer);
        }
    }

    // ─────────────────────────── E/S (puro + prefs) ───────────────────────────

    /** Serializa. Puro y testeable. Formato: url SE P UA SE P REF SE P ex SE P fa SE P ts. */
    @NonNull
    static String aTexto(@NonNull List<Conocido> lista) {
        StringBuilder sb = new StringBuilder();
        for (Conocido c : lista) {
            if (sb.length() > 0) sb.append(SEP_ITEM);
            sb.append(nz(c.url)).append(SEP)
              .append(nz(c.ua)).append(SEP)
              .append(nz(c.referer)).append(SEP)
              .append(c.exitos).append(SEP)
              .append(c.fallos).append(SEP)
              .append(c.ultimoUso);
        }
        return sb.toString();
    }

    /** Deserializa, tolerante a basura. Puro y testeable. */
    @NonNull
    static List<Conocido> deTexto(@Nullable String texto) {
        List<Conocido> out = new ArrayList<>();
        if (texto == null || texto.trim().isEmpty()) return out;
        for (String item : texto.split(SEP_ITEM)) {
            if (item == null || item.isEmpty()) continue;
            String[] p = item.split(SEP, -1);
            if (p.length < 6) continue;
            String url = p[0].trim();
            if (!url.startsWith("http")) continue;      // una entrada rota no envenena la lista
            int ex = num(p[3]), fa = num(p[4]);
            long ts = largo(p[5]);
            out.add(new Conocido(url, vacio(p[1]) ? null : p[1],
                    vacio(p[2]) ? null : p[2], ex, fa, ts));
        }
        return ordenar(out);
    }

    /**
     * Orden de intento: primero lo que funcionó y no ha fallado, luego lo que
     * funcionó, luego el resto; a igualdad, lo usado más recientemente.
     */
    @NonNull
    static List<Conocido> ordenar(@Nullable List<Conocido> lista) {
        List<Conocido> out = lista == null ? new ArrayList<>() : new ArrayList<>(lista);
        Collections.sort(out, (a, b) -> {
            int pa = a.esFiable() ? 0 : (a.exitos > 0 ? 1 : 2);
            int pb = b.esFiable() ? 0 : (b.exitos > 0 ? 1 : 2);
            if (pa != pb) return pa - pb;
            if (a.fallos != b.fallos) return a.fallos - b.fallos;
            return Long.compare(b.ultimoUso, a.ultimoUso);   // lo más reciente primero
        });
        return out;
    }

    /** Mete la url (o actualiza sus cabeceras) al principio. Puro. */
    @NonNull
    static List<Conocido> recordar(@Nullable List<Conocido> lista, @NonNull String url,
                                   @Nullable String ua, @Nullable String referer, long ahora) {
        List<Conocido> out = new ArrayList<>();
        if (url == null || url.isEmpty()) return lista == null ? out : new ArrayList<>(lista);
        int exitosPrevios = 0;
        if (lista != null) {
            for (Conocido c : lista) {
                if (!c.url.equals(url)) out.add(c);
                else exitosPrevios = c.exitos;
            }
        }
        out.add(0, new Conocido(url, ua, referer, exitosPrevios + 1, 0, ahora));
        return ordenar(out);
    }

    /** Suma un fallo a esa url (si no estaba, la apunta con 0 éxitos). Puro. */
    @NonNull
    static List<Conocido> fallar(@Nullable List<Conocido> lista, @NonNull String url, long ahora) {
        List<Conocido> out = new ArrayList<>();
        boolean visto = false;
        if (lista != null) {
            for (Conocido c : lista) {
                if (!c.url.equals(url)) { out.add(c); continue; }
                visto = true;
                out.add(new Conocido(c.url, c.ua, c.referer, c.exitos, c.fallos + 1, ahora));
            }
        }
        if (!visto && url.startsWith("http"))
            out.add(new Conocido(url, null, null, 0, 1, ahora));
        return ordenar(out);
    }

    /** Todas las urls conocidas de un canal, como opciones para el plan B. Puro. */
    @NonNull
    static List<DramaResolucion.Opcion> opciones(@Nullable List<Conocido> lista) {
        List<DramaResolucion.Opcion> out = new ArrayList<>();
        for (Conocido c : ordenar(lista)) out.add(c.aOpcion());
        return out;
    }

    // ─────────────────────────────── prefs ───────────────────────────────

    private static final Map<String, List<Conocido>> CACHE = new ConcurrentHashMap<>();

    @NonNull
    private static SharedPreferences prefs(@NonNull Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    @NonNull
    static List<Conocido> leer(@NonNull Context ctx, @Nullable String canalId) {
        if (canalId == null || canalId.isEmpty()) return new ArrayList<>();
        List<Conocido> mem = CACHE.get(canalId);
        if (mem != null) return new ArrayList<>(mem);
        List<Conocido> lista = deTexto(prefs(ctx).getString(clave(canalId), null));
        CACHE.put(canalId, lista);
        return new ArrayList<>(lista);
    }

    static void guardar(@NonNull Context ctx, @Nullable String canalId, List<Conocido> lista) {
        if (canalId == null || canalId.isEmpty()) return;
        CACHE.put(canalId, lista == null ? new ArrayList<>() : new ArrayList<>(lista));
        prefs(ctx).edit().putString(clave(canalId), aTexto(lista)).apply();
    }

    /** Se vio reproducir: se recuerda al frente con sus cabeceras. */
    static void apuntarExito(@NonNull Context ctx, @Nullable String canalId,
                             @NonNull String url, @Nullable String ua, @Nullable String referer) {
        List<Conocido> l = leer(ctx, canalId);
        List<Conocido> nuevo = recordar(l, url, ua, referer, System.currentTimeMillis());
        guardar(ctx, canalId, nuevo);
        android.util.Log.d(DramaSesion.TAG, "servidores: recordado para " + canalId
                + " (nº " + nuevo.size() + ") · " + url);
    }

    /** Se comprobó que no reproduce. */
    static void apuntarFallo(@NonNull Context ctx, @Nullable String canalId, @NonNull String url) {
        guardar(ctx, canalId, fallar(leer(ctx, canalId), url, System.currentTimeMillis()));
    }

    /** Borra lo aprendido de un canal (para el botón de «olvidar» o un canal que cambió). */
    static void olvidar(@NonNull Context ctx, @Nullable String canalId) {
        if (canalId == null) return;
        CACHE.remove(canalId);
        prefs(ctx).edit().remove(clave(canalId)).apply();
    }

    /** El canal de una fila: {@code id_live} si está, o la propia url como llave. */
    @NonNull
    static String claveDeCanal(@Nullable String canalUrl) {
        String id = DramaSesion.idDeCanal(canalUrl);
        if (id != null && !id.isEmpty()) return "id:" + id;
        return "url:" + (canalUrl == null ? "" : canalUrl.replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT));
    }

    @NonNull
    private static String clave(@NonNull String canalId) {
        return "c_" + canalId.replaceAll("[^A-Za-z0-9_:.-]", "_");
    }

    private static boolean vacio(@Nullable String s) { return s == null || s.isEmpty(); }

    private static int num(@Nullable String s) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return 0; }
    }

    private static long largo(@Nullable String s) {
        try { return Long.parseLong(s.trim()); } catch (Exception e) { return 0L; }
    }

    @NonNull
    private static String nz(@Nullable String s) { return s == null ? "" : s.replace(SEP, " ").replace(SEP_ITEM, " "); }
}
