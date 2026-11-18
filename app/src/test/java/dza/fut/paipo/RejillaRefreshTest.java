package dza.fut.paipo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * v7.28 — la rejilla al volver del reproductor y al girar el teléfono.
 *
 * El usuario preguntó por las dos y tenían respuesta contraria a la que
 * esperaba: girar SÍ reconstruía todo (y por eso «demoraba»), volver del
 * reproductor NO refrescaba nunca. Estos tests fijan las dos.
 */
public class RejillaRefreshTest {

    private static String fuente(String archivo) {
        String r = "dza/fut/paipo/" + archivo;
        try (InputStream in = RejillaRefreshTest.class.getClassLoader()
                .getResourceAsStream(r)) {
            if (in != null) return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (java.io.IOException ignore) { }
        java.io.File f = new java.io.File("src/main/java/dza/fut/paipo/" + archivo);
        if (!f.isFile()) f = new java.io.File("app/src/main/java/dza/fut/paipo/" + archivo);
        try {
            return new String(java.nio.file.Files.readAllBytes(f.toPath()),
                    StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            throw new AssertionError("no encuentro la fuente de " + archivo + ": " + e);
        }
    }

    // ───────────── la decisión pura de onResume ─────────────

    /** Sin cambios y sin venir del reproductor: no se toca nada. */
    @Test
    public void siNoCambioNadaNoSeTocaLaRejilla() {
        assertEquals(CanalesEnVivoFragment.Accion.NADA,
                CanalesEnVivoFragment.queHacerAlVolver(1000, 1000, false));
    }

    /** Lo que faltaba: venir del reproductor SÍ tiene que refrescar. */
    @Test
    public void volverDelReproductorRefresca() {
        assertEquals(CanalesEnVivoFragment.Accion.REFRESCAR_CACHE,
                CanalesEnVivoFragment.queHacerAlVolver(1000, 1000, true));
    }

    /** Si además cambió la marca de Drama, manda la recarga completa. */
    @Test
    public void siCambioLaMarcaMandaLaRecarga() {
        assertEquals(CanalesEnVivoFragment.Accion.RECARGAR_RED,
                CanalesEnVivoFragment.queHacerAlVolver(2000, 1000, false));
        // Aunque también vengamos del reproductor: la marca es más fuerte.
        assertEquals(CanalesEnVivoFragment.Accion.RECARGAR_RED,
                CanalesEnVivoFragment.queHacerAlVolver(2000, 1000, true));
    }

    /** Marca a cero (nunca se refrescó): no hay nada que recargar de la red,
     *  pero venirse del reproductor sigue refrescando la lista. */
    @Test
    public void sinMarcaDeDramaNoHayRecarga() {
        assertEquals(CanalesEnVivoFragment.Accion.NADA,
                CanalesEnVivoFragment.queHacerAlVolver(0, 0, false));
        assertEquals(CanalesEnVivoFragment.Accion.REFRESCAR_CACHE,
                CanalesEnVivoFragment.queHacerAlVolver(0, 0, true));
    }

    /** Al arrancar (visto = 0, marca existente) se recarga, como siempre. */
    @Test
    public void alArrancarSeRecarga() {
        assertEquals(CanalesEnVivoFragment.Accion.RECARGAR_RED,
                CanalesEnVivoFragment.queHacerAlVolver(1000, 0, false));
    }

    // ───────────── que el refresco no toque la red ─────────────

    /**
     * El refresco de la vuelta tiene que ser {@code prepararRejillaAsync}, que
     * re-fusiona con lo que hay en disco. Si alguien lo cambia por
     * {@code cargarCanales(...)} se vuelve a encender el indicador de carga y se
     * puede acabar en red, que es justo lo que se quería evitar.
     */
    @Test
    public void elRefrescoDeLaVueltaNoUsaLaRed() {
        String f = fuente("CanalesEnVivoFragment.java");
        int i = f.indexOf("case REFRESCAR_CACHE:");
        assertTrue("tiene que existir el caso REFRESCAR_CACHE", i > 0);
        // El cuerpo acaba en el "default:" del switch, no en otro "case " (no
        // hay mas). Cortar por "default:" es lo que delimita el bloque de verdad.
        int fin = f.indexOf("default:", i);
        assertTrue("el switch debe tener default", fin > i);
        String cuerpo = f.substring(i, fin);
        assertTrue("el refresco debe re-fusionar sin red",
                cuerpo.contains("prepararRejillaAsync()"));
        assertFalse("el refresco de la vuelta NO debe llamar a cargarCanales",
                cuerpo.contains("cargarCanales("));
        assertFalse("ni debe forzar red", cuerpo.contains("forzarRed"));
    }

    // ───────────── el adaptador marca la salida ─────────────

    @Test
    public void elAdaptadorMarcaLaSalidaAntesDeAbrir() {
        String a = fuente("CanalAdapter.java");
        assertTrue("el adaptador debe avisar de que salimos al reproductor",
                a.contains("CanalesEnVivoFragment.marcarSalidaAlPlayer()"));
        // El aviso tiene que ir ANTES del startActivity: si no, onResume podría
        // consumir el flag antes de que se pusiera.
        int marca = a.indexOf("CanalesEnVivoFragment.marcarSalidaAlPlayer()");
        int abre = a.indexOf("startActivity(intent)", marca);
        assertTrue("el aviso debe llegar antes de abrir el reproductor",
                marca > 0 && abre > marca);
    }

    // ───────────── el giro ─────────────

    @Test
    public void mainNoSeRecreaAlGirar() {
        String man = leerManifest();
        int i = man.indexOf("android:name=\".Main\"");
        assertTrue("tiene que estar la actividad Main", i > 0);
        String trozo = man.substring(i, man.indexOf('>', i));
        assertTrue("Main debe declarar configChanges o se recreará al girar",
                trozo.contains("android:configChanges"));
        assertTrue("debe cubrir la orientación", trozo.contains("orientation"));
        assertTrue("debe cubrir el tamaño de pantalla", trozo.contains("screenSize"));
    }

    private static String leerManifest() {
        for (String p : new String[]{
                "src/main/AndroidManifest.xml", "app/src/main/AndroidManifest.xml"}) {
            java.io.File f = new java.io.File(p);
            if (f.isFile()) {
                try {
                    return new String(java.nio.file.Files.readAllBytes(f.toPath()),
                            StandardCharsets.UTF_8);
                } catch (java.io.IOException e) {
                    throw new AssertionError(e);
                }
            }
        }
        throw new AssertionError("no encuentro AndroidManifest.xml");
    }

    /** Con la actividad no recreada, alguien tiene que recolocar las columnas. */
    @Test
    public void alGirarSeRecolocanLasColumnas() {
        String f = fuente("CanalesEnVivoFragment.java");
        assertTrue("el fragmento debe override onConfigurationChanged",
                f.contains("public void onConfigurationChanged("));
        assertTrue("debe recolocar el spanCount",
                f.contains("setSpanCount("));
        assertTrue("recalculando las columnas del contexto nuevo",
                f.contains("calcularNumeroDeColumnas(requireContext())"));
        assertFalse("no debe recrear la vista a mano",
                f.contains("recyclerView.setAdapter(null)"));
    }

    /** Las columnas siguen siendo las de antes: 2..4 según el ancho. */
    @Test
    public void lasColumnasSiguenSiendoLasDeSiempre() {
        assertEquals(2, CanalesEnVivoFragment.columnasPara(150f));
        assertEquals(2, CanalesEnVivoFragment.columnasPara(299f));
        assertEquals(2, CanalesEnVivoFragment.columnasPara(300f));   // 300/150 = 2 justo
        assertEquals(3, CanalesEnVivoFragment.columnasPara(450f));
        assertEquals(4, CanalesEnVivoFragment.columnasPara(800f));
        assertEquals(4, CanalesEnVivoFragment.columnasPara(5000f));
        assertEquals(2, CanalesEnVivoFragment.columnasPara(0f));
    }
}
