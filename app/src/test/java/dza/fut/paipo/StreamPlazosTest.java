package dza.fut.paipo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * v7.29 — los plazos de resolucion, con el metodo de dzasports.
 *
 * El usuario: «demora mucho resolver o play los servidores de m3u o php o html».
 * Eran dos cosas y ninguna se veia a simple vista:
 *
 *   1. la sonda del camino rapido (`esHlsPorContenido`) usaba el cliente HTTP
 *      general (12+15 s) y el patron PARECE_STREAM incluye `\.php\?`, asi que
 *      cualquier `...php?...` se bajaba la pagina ENTERA antes de resolver;
 *   2. la carrera no tenia plazo: `invokeAny` espera a que fallen las cuatro
 *      estrategias, y con topes de 120 s eso eran ~2 minutos.
 */
public class StreamPlazosTest {

    private static String fuente(String archivo) {
        String r = "dza/fut/paipo/" + archivo;
        try (InputStream in = StreamPlazosTest.class.getClassLoader()
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

    // ───────────── los tramos del plazo ─────────────

    /** Recien empezada: corren las cuatro estrategias. */
    @Test
    public void alPrincipioCorrenLasCuatro() {
        assertEquals(StreamResolver.Tramo.HTTP, StreamResolver.tramoDe(0, 0));
        assertEquals(StreamResolver.Tramo.HTTP, StreamResolver.tramoDe(7999, 0));
    }

    /** Pasado el plazo corto, las HTTP se apagan y siguen los WebView. */
    @Test
    public void pasadoElPlazoCortoSiguenLosWebView() {
        assertEquals(StreamResolver.Tramo.SOLO_WEBVIEW, StreamResolver.tramoDe(8000, 0));
        assertEquals(StreamResolver.Tramo.SOLO_WEBVIEW, StreamResolver.tramoDe(15000, 0));
    }

    /** Pasado el plazo total, se rinde. Es el techo: 20 s, no 120. */
    @Test
    public void pasadoElPlazoTotalSeRinde() {
        assertEquals(StreamResolver.Tramo.AGOTADO, StreamResolver.tramoDe(20000, 0));
        assertEquals(StreamResolver.Tramo.AGOTADO, StreamResolver.tramoDe(120000, 0));
    }

    /** El tramo largo existe para las paginas tipo Clappr, pero tiene techo. */
    @Test
    public void elTramoLargoTieneTecho() {
        long inicio = 0;
        assertTrue("a los 20 s justos ya no queda nada",
                StreamResolver.msHastaElFin(20000, inicio) <= 0);
        assertTrue("a los 19 s todavia queda algo",
                StreamResolver.msHastaElFin(19000, inicio) > 0);
        long a = StreamResolver.msHastaElFin(9000, inicio);
        long b = StreamResolver.msHastaElFin(9001, inicio);
        assertTrue("el plazo debe ir bajando", b < a);
    }

    // ───────────── que la carrera ya no es invokeAny ─────────────

    /**
     * `invokeAny` es el corazon del problema: devuelve en cuanto una acierta,
     * pero si fallan todas espera a que fallen TODAS. Con la cola de terminados
     * y `poll(plazo)` eso ya no puede pasar.
     */
    @Test
    public void laCarreraYaNoEsInvokeAny() {
        String s = fuente("StreamResolver.java");
        int i = s.indexOf("Iniciando carrera de 4 estrategias");
        assertTrue("tiene que estar la carrera", i > 0);
        int fin = s.indexOf("private static StreamResult resolveWithWebViewIframe", i);
        assertTrue(fin > i);
        String cuerpo = s.substring(i, fin);

        assertFalse("la carrera NO debe usar invokeAny (espera a que fallen todas)",
                cuerpo.contains("invokeAny("));
        assertTrue("debe esperar con poll(plazo)", cuerpo.contains("cola.poll("));
        assertTrue("debe usar ExecutorCompletionService",
                cuerpo.contains("ExecutorCompletionService"));
        assertTrue("debe cancelar todas las tareas al salir",
                cuerpo.contains("for (Future<StreamResult> f : todos) f.cancel(true);"));
        assertTrue("debe apagar el executor", cuerpo.contains("shutdownNow()"));
    }

    /** Las dos HTTP son las primeras: son las que se pueden apagar. */
    @Test
    public void lasHttpSonLasQueSeApagan() {
        String s = fuente("StreamResolver.java");
        int i = s.indexOf("case SOLO_WEBVIEW:");
        assertTrue("tiene que existir el caso SOLO_WEBVIEW", i > 0);
        int fin = s.indexOf("default:", i);
        String cuerpo = s.substring(i, fin > i ? fin : s.length());
        assertTrue("se cancelan las dos primeras tareas (cadena y okHttp)",
                cuerpo.contains("todos.get(0).cancel(true)")
                        && cuerpo.contains("todos.get(1).cancel(true)"));
        assertFalse("no se debe apagar el WebView-iframe",
                cuerpo.contains("todos.get(2).cancel(true)"));
        assertFalse("no se debe apagar el WebView-inyeccion",
                cuerpo.contains("todos.get(3).cancel(true)"));
    }

    // ───────────── la sonda del camino rapido ─────────────

    /** La sonda no puede usar el cliente general: eso era el problema 1. */
    @Test
    public void laSondaUsaUnClienteCorto() {
        String s = fuente("StreamResolver.java");
        assertTrue("tiene que existir el cliente de sondeo", s.contains("clienteSonda"));
        int i = s.indexOf("private static boolean esHlsPorContenido(");
        assertTrue(i > 0);
        int fin = s.indexOf("private static boolean esPlaylistHls(", i);
        String cuerpo = s.substring(i, fin > i ? fin : s.length());
        assertTrue("la sonda debe usar clienteSonda", cuerpo.contains("clienteSonda.newCall"));
        assertFalse("la sonda NO debe usar el cliente general",
                cuerpo.contains("httpClient.newCall"));
        int j = s.indexOf("private static final OkHttpClient clienteSonda");
        assertTrue(j > 0);
        String decl = s.substring(j, s.indexOf("build();", j));
        assertTrue("conexion corta", decl.contains("connectTimeout(3,"));
        assertTrue("lectura corta", decl.contains("readTimeout(4,"));
    }

    /** La cadena respeta el plazo exterior que le pasa la carrera. */
    @Test
    public void laCadenaRespetaElPlazoExterior() {
        String s = fuente("StreamResolver.java");
        assertTrue("debe existir la sobrecarga con plazo",
                s.contains("static StreamResult resolverEnCadena(String url, long caducaEn)"));
        assertTrue("y debe quedarse con el menor de los dos plazos",
                s.contains("Math.min(System.currentTimeMillis() + TIEMPO_MAX_CADENA_MS, caducaEn)"));
        assertTrue("la carrera debe pasarle su plazo",
                s.contains("resolverEnCadena(realUrl, plazoTotal)"));
    }
}
