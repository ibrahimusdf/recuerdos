package dza.fut.paipo;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Caché LRU con presupuesto en <b>bytes</b>, no en número de entradas.
 *
 * <p><b>Por qué existe.</b> La losa de iniciales de {@link Logos} se dibujaba en
 * cada {@code onBindViewHolder}: un bitmap de 440×440 ARGB_8888 — <b>774 KB</b> —
 * más un degradado, dos {@code Paint} y un {@code Typeface}, todo en el hilo
 * principal y por cada tile cada vez que el RecyclerView lo reciclaba. Con la
 * rejilla de canales eso es lo que retrasaba la aparición de la cuadrícula.
 *
 * <p>El presupuesto se mide en bytes y no en entradas justo porque el contenido
 * de esta caché son bitmaps: con un tope de 12 MB caben ~15 losas, y si algún
 * día el tamaño cambia la caché se adapta sola en vez de quedarse corta o
 * desbocarse.
 *
 * <p>Es Java puro, sin nada de Android, para poder probar la política de desalojo
 * en la JVM — que es exactamente lo que hay que acertar.
 */
final class LruPeso<K, V> {

    /** Cuánto ocupa un valor. */
    interface Peso<V> { long de(V v); }

    private final long tope;
    private final Peso<V> peso;
    /** accessOrder=true: el orden de recorrido es el de uso, no el de inserción. */
    private final LinkedHashMap<K, V> mapa;
    private long usado;

    /**
     * @param topeBytes presupuesto máximo. Con 0 o negativo no se guarda nada
     *                  (la caché queda desactivada, que es el comportamiento
     *                  seguro si alguien pone un tope absurdo).
     */
    LruPeso(long topeBytes, @androidx.annotation.NonNull Peso<V> peso) {
        this.tope = Math.max(0L, topeBytes);
        this.peso = peso;
        this.mapa = new LinkedHashMap<>(64, 0.75f, true);
    }

    /** El valor guardado, o {@code null}. Contar como uso (LRU de verdad). */
    synchronized V get(K clave) {
        return mapa.get(clave);
    }

    /**
     * Guarda y desaloja por el principio mientras se pase del presupuesto.
     *
     * <p>La regla es <b>nunca pasarse del presupuesto</b>, así que si una entrada
     * sola pesa más que el tope se va ella también: la caché se queda vacía y esa
     * losa se vuelve a dibujar la próxima vez. Es el fallo seguro para una caché
     * de memoria — con losas de 774 KB y 12 MB de tope no puede ocurrir, y si
     * algún día los tamaños cambian, preferimos perder la caché a perder la app.
     */
    synchronized void put(K clave, V valor) {
        V previo = mapa.remove(clave);
        if (previo != null) usado -= Math.max(0L, peso.de(previo));
        mapa.put(clave, valor);
        usado += Math.max(0L, peso.de(valor));
        while (usado > tope && !mapa.isEmpty()) {
            Map.Entry<K, V> anciano = mapa.entrySet().iterator().next();
            mapa.remove(anciano.getKey());
            usado -= Math.max(0L, peso.de(anciano.getValue()));
        }
    }

    synchronized int tamano() { return mapa.size(); }

    synchronized long usado() { return usado; }

    /** Para los tests: vacía la caché sin reconstruirla. */
    synchronized void limpiar() { mapa.clear(); usado = 0L; }
}
