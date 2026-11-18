package dza.fut.paipo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * v3.2.67 — la lista visible de la rejilla, retenida en memoria para zapear
 * de canal dentro del reproductor sin salir de él (el equivalente a nuestro
 * ChannelManager de dzasports, pero sin serializar cientos de canales por el
 * Intent, que tiene límite de tamaño).
 *
 * La siembran CanalAdapter al tocar un canal y PlayerActivity al abrir un evento
 * de agenda. Si el proceso muere y el reproductor se restaura, la lista queda
 * vacía y la tira de zapeo simplemente no aparece: degradación elegante.
 */
public final class ZapeoCanales {

    private ZapeoCanales() {}

    public static final List<Canal> canales = new ArrayList<>();
    public static int indiceActual = -1;

    /** Filtra solo los Canal de los items visibles y marca el elegido. */
    public static void fijar(List<?> items, Canal elegido) {
        canales.clear();
        indiceActual = -1;
        if (items != null) {
            for (Object o : items) {
                if (o instanceof Canal) {
                    if (o == elegido) indiceActual = canales.size();
                    canales.add((Canal) o);
                }
            }
        }
        if (indiceActual < 0 && elegido != null) {
            indiceActual = canales.size();
            canales.add(elegido);
        }
    }

    /**
     * Prepara la tira para un evento: el evento queda primero y se añaden hasta
     * {@code maxAleatorios} canales reproducibles del catálogo, en orden aleatorio.
     */
    public static List<Canal> seleccionarCanalesAgenda(
            Canal evento, List<Canal> catalogo, int maxAleatorios) {
        List<Canal> resultado = new ArrayList<>();
        Set<String> titulos = new HashSet<>();
        if (tieneOpcionValida(evento)) {
            resultado.add(evento);
            String tituloEvento = normalizarTitulo(evento.title);
            if (!tituloEvento.isEmpty()) titulos.add(tituloEvento);
        }
        if (catalogo == null || maxAleatorios <= 0) return resultado;

        List<Canal> candidatos = new ArrayList<>();
        for (Canal canal : catalogo) {
            if (!tieneOpcionValida(canal)) continue;
            String titulo = normalizarTitulo(canal.title);
            if (titulo.isEmpty() || titulos.contains(titulo)) continue;
            candidatos.add(canal);
        }
        Collections.shuffle(candidatos);
        for (Canal canal : candidatos) {
            String titulo = normalizarTitulo(canal.title);
            if (!titulos.add(titulo)) continue;
            resultado.add(canal);
            if (resultado.size() - (tieneOpcionValida(evento) ? 1 : 0) >= maxAleatorios) {
                break;
            }
        }
        return resultado;
    }

    private static boolean tieneOpcionValida(Canal canal) {
        if (canal == null || canal.options == null) return false;
        for (Canal.OpcionCanal opcion : canal.options) {
            if (opcion != null && opcion.url != null && !opcion.url.trim().isEmpty()) return true;
        }
        return false;
    }

    private static String normalizarTitulo(String titulo) {
        return titulo == null ? "" : titulo.trim().toLowerCase(Locale.ROOT);
    }
}
