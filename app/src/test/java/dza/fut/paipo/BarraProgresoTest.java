package dza.fut.paipo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.Test;

/**
 * v7.33 — la barra de progreso del reproductor de películas.
 *
 * <p>El usuario la pidió «como en Netflix»: poder avanzar hasta donde uno
 * quiera arrastrando. El Java que la movía ya estaba escrito desde la v3.2 —
 * listener, {@code isSeeking}, {@code startSeekUpdate()} — pero los dos layouts
 * del reproductor habían perdido las vistas, así que {@code findViewById}
 * devolvía {@code null} y la barra nunca existía en pantalla. Es el mismo
 * patrón que el test de recursos: se rompe sin que el build ni lint digan nada.
 *
 * <p>Estos tests leen los layouts y el fuente igual que
 * {@link PlayerActivityTest}: comprueban que las vistas existen en los dos
 * layouts, que el Java las engancha y las muestra sólo cuando el contenido
 * admite búsqueda, y que el cálculo del arrastre aterriza donde se suelta.
 */
public class BarraProgresoTest {

    /** Los dos layouts del reproductor. Si uno se queda sin barra, el test lo dice. */
    private static final String[] LAYOUTS = {"activity_player.xml", "activity_player_portrait.xml"};

    private static String leerLayout(String nombre) throws Exception {
        File f = new File("app/src/main/res/layout/" + nombre);
        if (!f.isFile()) f = new File("src/main/res/layout/" + nombre);
        return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
    }

    private static String leerFuente() throws Exception {
        File f = new File("app/src/main/java/dza/fut/paipo/PlayerActivity.java");
        if (!f.isFile()) f = new File("src/main/java/dza/fut/paipo/PlayerActivity.java");
        return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
    }

    /** El trozo del layout que va desde un id hasta la etiqueta que lo cierra. */
    private static String bloque(String xml, String id) {
        int i = xml.indexOf(id);
        if (i < 0) return "";
        // El elemento se cierra en su propia linea, con la misma sangria que la abrio.
        int cierre = xml.indexOf("\n        </", i);
        return cierre < 0 ? xml.substring(i) : xml.substring(i, cierre);
    }

    @Test
    public void losDosLayoutsTienenLaBarraConSusTiempos() throws Exception {
        for (String nombre : LAYOUTS) {
            String xml = leerLayout(nombre);
            assertTrue(nombre + ": falta la fila de la barra", xml.contains("@+id/filaProgreso"));
            assertTrue(nombre + ": falta la barra", xml.contains("@+id/seekBar"));
            assertTrue(nombre + ": falta el tiempo actual", xml.contains("@+id/tvTiempoActual"));
            assertTrue(nombre + ": falta el tiempo total", xml.contains("@+id/tvTiempoTotal"));

            // Los tres dentro de la misma fila: si no, la barra sale descolgada.
            String fila = bloque(xml, "@+id/filaProgreso");
            assertTrue(nombre + ": la barra no esta dentro de la fila", fila.contains("@+id/seekBar"));
            assertTrue(nombre + ": el tiempo actual no esta dentro de la fila",
                    fila.contains("@+id/tvTiempoActual"));
            assertTrue(nombre + ": el tiempo total no esta dentro de la fila",
                    fila.contains("@+id/tvTiempoTotal"));

            // Empieza oculta: la decide aplicarVisibilidadExtra(), no el XML.
            assertTrue(nombre + ": la fila debe arrancar en gone",
                    bloque(xml, "@+id/filaProgreso").contains("android:visibility=\"gone\""));
        }
    }

    @Test
    public void laBarraSeArrastraYNoSeCuelaAlVideo() throws Exception {
        for (String nombre : LAYOUTS) {
            String xml = leerLayout(nombre);
            String fila = bloque(xml, "@+id/filaProgreso");
            // Sin clickable, el arrastre llegaria al PlayerView y en vez de
            // avanzar la pelicula alternaria los controles.
            assertTrue(nombre + ": la fila debe ser clickable para comerse el arrastre",
                    fila.contains("android:clickable=\"true\""));
            // La barra debe poder moverse a cualquier punto, no a saltos.
            assertTrue(nombre + ": la barra debe usar el progress dibujado fino",
                    fila.contains("android:progressDrawable=\"@drawable/seekbar_progress\""));
            assertTrue(nombre + ": la barra debe llevar el pomo propio",
                    fila.contains("android:thumb=\"@drawable/seekbar_thumb\""));
            assertTrue(nombre + ": la barra debe partirse en false (se rellena sola)",
                    fila.contains("android:splitTrack=\"false\""));
        }
    }

    @Test
    public void elJavaEnganchaLaFilaYLaMuestraSoloSiSePuedeBuscar() throws Exception {
        String src = leerFuente();
        assertTrue("la fila se busca por id", src.contains("filaProgreso = findViewById(R.id.filaProgreso);"));
        assertTrue("la fila se declara", src.contains("private android.view.View filaProgreso;"));

        // Con los controles, no durante la carga, y solo si el contenido se puede buscar.
        assertTrue("la visibilidad depende de sePuedeBuscar()",
                src.contains("boolean mostrarProgreso = fueraDePip && controlesVisibles && sePuedeBuscar();"));
        assertTrue("y se aplica a la fila",
                src.contains("if (filaProgreso != null) {")
                        && src.contains("filaProgreso.setVisibility(mostrarProgreso ? View.VISIBLE : View.GONE);"));

        // Sin la guarda de carga a proposito: durante la carga los controles estan
        // ocultos, asi que la barra debe esconderse tambien.
        assertFalse("la barra no debe saltarse la carga",
                src.contains("if (filaProgreso != null && (!cargaEnCurso || !fueraDePip))"));
    }

    @Test
    public void sePuedeBuscarDistinguePeliculaDeDirecto() throws Exception {
        String src = leerFuente();
        int i = src.indexOf("private boolean sePuedeBuscar()");
        int f = src.indexOf("\n    }", i);
        assertTrue("no encuentro sePuedeBuscar()", i >= 0 && f > i);
        String m = src.substring(i, f);
        assertTrue("sin reproductor no hay barra", m.contains("if (player == null) return false;"));

        // v7.34.1: el fallo que rompio el reproductor de canales en directo.
        // Un directo con ventana de DVR PUBLICa duracion y admite buscar dentro
        // de ella, asi que getDuration()>0 era cierto tambien para canales.
        // La comprobacion de directo tiene que ser explicita y la primera.
        assertTrue("en directo NUNCA hay barra, y se comprueba lo primero",
                m.contains("if (player.isCurrentMediaItemLive()) return false;"));
        assertTrue("ademas la linea de tiempo tiene que admitir posicionarse",
                m.contains("if (!player.isCurrentMediaItemSeekable()) return false;"));
        assertTrue("y la duracion tiene que ser conocida",
                m.contains("return player.getDuration() > 0;"));

        // Si vuelve el "o basta con", el reproductor de canales se rompe otra vez.
        assertFalse("no puede valer con que la duracion sea conocida: los directos con DVR tambien la publican",
                m.contains("player.isCurrentMediaItemSeekable() || player.getDuration() > 0"));
    }

    /**
     * v7.34.1 — con la barra oculta, el reproductor de canales en directo tiene
     * que quedar EXACTAMENTE como estaba. Si la fila ocupara sitio estando en
     * gone, o si la tira de canales perdiera alguno de sus atributos al
     * recolocarla, el estilo del directo se estropearia sin que ningun test de
     * la barra se diera cuenta.
     */
    @Test
    public void conLaBarraOcultaElDirectoQuedaIgual() throws Exception {
        String port = leerLayout("activity_player_portrait.xml");
        String land = leerLayout("activity_player.xml");

        for (String nombre : new String[]{"activity_player_portrait.xml", "activity_player.xml"}) {
            String xml = leerLayout(nombre);
            String fila = bloque(xml, "@+id/filaProgreso");
            // gone = no ocupa sitio, no desplaza nada.
            assertTrue(nombre + ": la fila arranca en gone", fila.contains("android:visibility=\"gone\""));
            // El contenedor que la envuelve (retrato) tambien colapsa.
            if (nombre.equals("activity_player_portrait.xml")) {
                assertTrue("retrato: el contenedor de abajo mide wrap_content, colapsa con la barra en gone",
                        port.contains("android:layout_height=\"wrap_content\"\n            android:layout_gravity=\"bottom\"\n"
                                + "            android:orientation=\"vertical\""));
            }
        }

        // La tira de canales conserva TODOS sus atributos originales en los dos.
        for (String xml : new String[]{land, port}) {
            String tira = bloque(xml, "@+id/tiraCanalesPlayer");
            assertTrue("la tira sigue midiendo su alto original",
                    tira.contains("115dp") || tira.contains("135dp"));
            assertTrue("la tira conserva su fondo", tira.contains("#80000000"));
            assertTrue("la tira sigue siendo clickable", tira.contains("android:clickable=\"true\""));
            assertTrue("la tira conserva su orientacion", tira.contains("android:orientation=\"vertical\""));
            assertTrue("la tira conserva su scroll", xml.contains("@+id/scrollCanalesPlayer"));
            assertTrue("la tira conserva su contenedor", xml.contains("@+id/contenedorCanalesPlayer"));
        }
        // Y en paisaje la tira sigue siendo la ultima que se ve abajo.
        assertTrue("paisaje: la tira sigue pegada al borde de abajo",
                land.indexOf("@+id/tiraCanalesPlayer") > land.indexOf("@+id/filaProgreso"));
    }

    @Test
    public void arrastrarBuscaEnElPuntoSoltado() {
        // Dos horas de pelicula: la mitad es la hora uno, no 3599999 ni 3600001.
        assertEquals(3_600_000L, PlayerActivity.posicionBuscada(7_200_000L, 500));
        assertEquals(0L, PlayerActivity.posicionBuscada(7_200_000L, 0));
        assertEquals(7_200_000L, PlayerActivity.posicionBuscada(7_200_000L, 1000));
        assertEquals(7_200_000L, PlayerActivity.posicionBuscada(7_200_000L, 1500));
        // Duracion desconocida (directo): nunca se sale del principio.
        assertEquals(0L, PlayerActivity.posicionBuscada(0L, 500));
        assertEquals(0L, PlayerActivity.posicionBuscada(-1L, 500));
        // Un corto de 90 segundos.
        assertEquals(45_000L, PlayerActivity.posicionBuscada(90_000L, 500));
        assertEquals(89_910L, PlayerActivity.posicionBuscada(90_000L, 999));
    }

    @Test
    public void elArrastreNoDejaLaBarraPeleandoseConElRefresco() throws Exception {
        String src = leerFuente();
        // Mientras se arrastra se congela el refresco; al soltar se busca y se reanuda.
        assertTrue("al empezar a arrastrar se marca isSeeking",
                src.contains("public void onStartTrackingTouch(SeekBar seekBar) {\n"
                        + "                isSeeking = true;"));
        assertTrue("y se para el refresco para que no pelee con el dedo",
                src.contains("isSeeking = true;\n                stopSeekUpdate();"));
        assertTrue("al soltar se busca la posicion calculada",
                src.contains("player.seekTo(posicionBuscada(duration, seekBar.getProgress()));"));
        assertTrue("y se reanuda el refresco",
                src.contains("isSeeking = false;\n                startSeekUpdate();"));
        assertTrue("el refresco respeta el arrastre",
                src.contains("if (player != null && !isSeeking && seekBar != null"));
    }
}
