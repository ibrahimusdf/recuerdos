package dza.fut.paipo;

/**
 * v7.20 — el «debug de la carga».
 *
 * <p>El usuario preguntó cómo mandarnos el debug de la carga. La respuesta honesta
 * era que no podía: la app escribía todo en logcat y en Android ninguna app puede
 * leer el logcat de otra (READ_LOGS es de nivel signature), así que sacar el log
 * obligaba a pegar un volcado entero del sistema a mano.</p>
 *
 * <p>Esta clase es la app tomando sus propias medidas: cada paso de la carga de la
 * rejilla y del arranque de un canal se apunta aquí con su milisegundo, y un botón
 * oculto (pulsación larga en la barra de la app) lo comparte por WhatsApp, correo,
 * etc. Sin etiquetas de versión ni nada visible en la interfaz normal.</p>
 *
 * <p>Pura a propósito: no toca nada de Android, así que se prueba con JUnit secano.</p>
 */
public final class RegistroCarga {

    private static final int MAX = 300;

    /** Las líneas, de la más antigua a la más nueva. */
    private static final java.util.ArrayDeque<String> LINEAS = new java.util.ArrayDeque<>();

    /** Origen (ms) de la medida en curso. */
    private static long marca;

    /** Qué se está midiendo. */
    private static String bloque;

    private RegistroCarga() { }

    /**
     * Empieza a medir una cosa. NO tira lo anterior: la rejilla y el arranque del
     * canal son dos medidas distintas y las dos interesan en el mismo volcado
     * (medido en el diagnóstico real del usuario: si se borra, al abrir un canal se
     * pierde lo que tardó la rejilla).
     */
    public static synchronized void abrir(@androidx.annotation.NonNull String que) {
        if (marca != 0) add("");          // separador entre bloques
        bloque = que;
        marca = System.currentTimeMillis();
        add("== " + que + " ==");
    }

    /** Apunta un paso con lo que lleva desde que se abrió el bloque. */
    public static synchronized void paso(@androidx.annotation.NonNull String que) {
        paso(que, null);
    }

    /** Igual, con un detalle al final («600 canales», «del disco», …). */
    public static synchronized void paso(@androidx.annotation.NonNull String que,
                                         @androidx.annotation.Nullable String extra) {
        long d = marca == 0 ? 0 : System.currentTimeMillis() - marca;
        String l = "  " + rellena(que, 34) + " " + rellenaIzq(d + " ms", 10);
        if (extra != null && !extra.trim().isEmpty()) l += "   " + extra.trim();
        LINEAS.addLast(l);
        while (LINEAS.size() > MAX) LINEAS.pollFirst();
    }

    /** Apunta una línea suelta. */
    public static synchronized void add(@androidx.annotation.NonNull String l) {
        LINEAS.addLast(l == null ? "" : l);   // ArrayDeque no admite null
        while (LINEAS.size() > MAX) LINEAS.pollFirst();
    }

    /**
     * v7.21 — Cierra el bloque en curso y apunta su total.
     *
     * <p>Sin esto, volcado() media hasta el momento en que el usuario pulsaba
     * compartir, que incluye volver atrás y buscar el botón: en el diagnóstico
     * real del usuario salían 10.324 ms cuando el canal había arrancado en
     * 3.568 ms. El total tenía que ser del bloque, no del viaje hasta el botón.</p>
     */
    public static synchronized void fin() {
        if (marca == 0) return;
        paso("TOTAL del bloque");
        add("");
        marca = 0;
        bloque = null;
    }

    /** Milisegundos desde que se abrió el bloque, sin apuntar nada. */
    public static synchronized long desdeApertura() {
        return marca == 0 ? 0 : System.currentTimeMillis() - marca;
    }

    /** Lo que se comparte. Sin APIs de Android a propósito. */
    @androidx.annotation.NonNull
    public static synchronized String volcado() {
        StringBuilder sb = new StringBuilder();
        sb.append("DIAGNOSTICO DE CARGA — PelisLatino\n");
        sb.append(sello()).append('\n');
        if (LINEAS.isEmpty()) {
            sb.append("\n(todavía sin medidas: abre la pestaña de Canales y toca un canal,")
              .append(" y después mantén pulsada la barra de arriba)\n");
            return sb.toString();
        }
        sb.append('\n');
        for (String l : LINEAS) sb.append(l).append('\n');
        sb.append("\n(compartido ").append(sello()).append(")\n");
        return sb.toString();
    }

    /** Número de líneas guardadas. Para los tests. */
    public static synchronized int lineas() {
        return LINEAS.size();
    }

    /** Tira todo. */
    public static synchronized void limpia() {
        LINEAS.clear();
        marca = 0;
        bloque = null;
    }

    /** El bloque abierto ahora mismo, o null. */
    @androidx.annotation.Nullable
    public static synchronized String bloque() {
        return bloque;
    }

    // ─────────────────────────────────────────── internos

    private static String rellena(String s, int n) {
        StringBuilder b = new StringBuilder(s == null ? "" : s);
        if (b.length() > n) {
            b.setLength(n - 1);
            b.append('…');
            return b.toString();
        }
        while (b.length() < n) b.append(' ');
        return b.toString();
    }

    private static String rellenaIzq(String s, int n) {
        StringBuilder b = new StringBuilder(s == null ? "" : s);
        while (b.length() < n) b.insert(0, ' ');
        return b.toString();
    }

    private static String sello() {
        return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss",
                java.util.Locale.US).format(new java.util.Date());
    }
}
