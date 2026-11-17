package dza.fut.paipo;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * v3.2.43 — la tira de canales como en Drama (capturas del usuario, 2026-10-17):
 *   ◎ Todos · ♥ Favoritos · ★ Los más vistos ·
 *   …primero los países de LATINOAMÉRICA… · …después el resto del mundo…
 *
 * v7.36 (pedido 44b): el usuario pidió «que se vean los canales de Latinoamérica como
 * ESPN primero». El bloque latinoamericano va entero por delante — sin destacar un país
 * sobre otro — y dentro de cada bloque se respeta el orden de llegada del feed, que es
 * el dato: Drama NUNCA reordena los grupos por población.
 *
 * v7.36 (pedido 44b): fuera el chip «🔥 Partidos en vivo». El usuario lo señaló como
 * «MATCHES» y pidió borrarlo: «no es necesario que se cargue».
 *
 * Clase pura: la tira se prueba en la suite sin Android.
 */
public final class TiraCanales {

    public static final String CLAVE_TODO = "";
    public static final String CLAVE_FAVORITOS = "\u0000fav";
    public static final String CLAVE_VISTOS = "\u0000top";
    public static final String CLAVE_VIVOS = "\u0000vivos";

    private TiraCanales() { }

    /**
     * [{clave, rótulo, cantidad}] en el orden de la tira. La `conteo` debe preservar el
     * orden de inserción (el fragmento usa LinkedHashMap: el orden de llegada de los
     * tópicos es el dato, no un adorno).
     */
    public static List<String[]> construir(Map<String, Integer> conteoTopicos,
                                           String paisDispositivoCc, int nFavoritos,
                                           int nConHistorial, boolean hayPartidosEnVivo) {
        List<String[]> out = new ArrayList<>();
        out.add(new String[]{CLAVE_TODO, "Todos", ""});
        if (nFavoritos > 0) out.add(new String[]{CLAVE_FAVORITOS, "\u2665 Favoritos", String.valueOf(nFavoritos)});
        if (nConHistorial > 0) out.add(new String[]{CLAVE_VISTOS, "\u2605 Los m\u00e1s vistos", String.valueOf(nConHistorial)});
        // v7.36: `paisDispositivoCc` ya no decide el orden — manda Latinoamérica. Se
        // conserva en la firma porque la llaman el fragmento y la suite.
        List<String[]> latam = new ArrayList<>();
        List<String[]> resto = new ArrayList<>();
        for (Map.Entry<String, Integer> e : conteoTopicos.entrySet()) {
            String[] fila = new String[]{e.getKey(), FusionRejilla.etiquetaTopic(e.getKey()), String.valueOf(e.getValue())};
            if (FusionRejilla.esLatam(e.getKey())) latam.add(fila);
            else resto.add(fila);
        }
        out.addAll(latam);        // el bloque latinoamericano entero, primero
        out.addAll(resto);        // y después el resto del mundo
        // v7.36 (pedido 44b): el chip «🔥 Partidos en vivo» está fuera.
        // `hayPartidosEnVivo` se sigue recibiendo para no romper a quien llama.
        return out;
    }

    /**
     * El marcador con el que el feed de Drama señala los partidos en directo (el chip
     * "Live Matches" de las capturas): las filas cuyo stream pasa por el handler custom.
     */
    public static boolean esPartidoEnVivo(String url) {
        return url != null && url.contains("live_tv_custom_handler");
    }

    /** El país del aparato según el locale (PE, AR, …) — en blanco si el SO no lo dice. */
    public static String paisDelAparato() {
        return Locale.getDefault().getCountry();
    }
}
