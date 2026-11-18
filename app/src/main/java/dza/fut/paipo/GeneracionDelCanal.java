package dza.fut.paipo;

import androidx.annotation.NonNull;

/**
 * v7.39.1 — <b>«¿sigue siendo este mi canal?»: el guardián de las respuestas tardías.</b>
 *
 * <p><b>El síntoma que arregla, en palabras del usuario:</b> «a veces clic en un canal y se
 * queda en otro».</p>
 *
 * <p>El reproductor es {@code singleTask}: cuando se toca otro canal, Android
 * <em>reutiliza la misma Activity</em> y avisa por {@code onNewIntent}. La instancia seguía
 * con todo esto vivo del canal anterior:</p>
 *
 * <ul>
 *   <li>las hebras de la carrera de opciones ({@link CarreraDeOpciones}), sondeando el
 *       canal viejo;</li>
 *   <li>el POST de glasbi/glbr (2-13 s);</li>
 *   <li>el WebView oculto buscando el manifiesto;</li>
 *   <li>los vigilantes del embed (15 s y 30 s);</li>
 *   <li>la memoria de servidores del canal viejo.</li>
 * </ul>
 *
 * <p>Cada uno de esos, al volver, remataba al canal <b>nuevo</b>: le aplicaba su ganadora,
 * o su «no hay nada» disparaba el plan B del canal anterior. El usuario veía que el canal
 * elegido no abría y que seguía el otro.</p>
 *
 * <p>La solución es la de siempre en concurrencia: <b>un número de generación</b>. Al abrir
 * un canal se sube; lo asíncrono guarda el que tenía al nacer y, al volver, pregunta. Si ya
 * no es el actual, se descarta en silencio. Esta clase es solo eso, puro, para poder
 * probarlo sin móvil ({@code GeneracionDelCanalTest}).</p>
 */
public final class GeneracionDelCanal {

    private int actual;

    /** Generación vigente. */
    public synchronized int actual() {
        return actual;
    }

    /** Abre un canal nuevo: todo lo anterior queda caducado. Devuelve la nueva. */
    public synchronized int abrirCanal() {
        return ++actual;
    }

    /** ¿La respuesta que nació en {@code generacion} sigue siendo de este canal? */
    public synchronized boolean vigente(int generacion) {
        return generacion == actual;
    }

    /**
     * <b>La regla, con nombre:</b> qué hacer con una respuesta tardía. Es lo que evita que
     * una vuelta de tuerca futura se olvide de preguntar.
     */
    @NonNull
    public synchronized Decision decidir(int generacion) {
        return generacion == actual ? Decision.APLICAR : Decision.DESCARTAR;
    }

    /** Aplicar la respuesta al canal que se está viendo, o tirarla por vieja. */
    public enum Decision { APLICAR, DESCARTAR }
}
