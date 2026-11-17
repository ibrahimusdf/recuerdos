package dza.fut.paipo;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;

/** v7.37.15 — el JS del Captador va EMPAQUETADO en la APK (assets/drama_auto.js). */
public class DramaAutoJsHostTest {

    private static final String[] HOSTS_CAIDOS = {
        "li.highperformanceconnect.shop",
        "re.highperformanceconnect.shop",
        "sp.highperformanceconnect.shop",
    };

    private static String js() throws Exception {
        File f = new File("src/main/assets/drama_auto.js");
        assertTrue("no encuentro el JS del captador en " + f.getAbsolutePath(), f.isFile());
        byte[] b = new byte[(int) f.length()];
        java.io.InputStream in = new java.io.FileInputStream(f);
        try { int n = in.read(b); assertTrue(n > 1000); } finally { in.close(); }
        return new String(b, java.nio.charset.StandardCharsets.UTF_8);
    }

    @Test
    public void elJsDelCaptadorNoApuntaANingunHostCaido() throws Exception {
        String js = js();
        // se mira el CODIGO, no los comentarios: la doc explica por que se cambio el
        // host y tiene que poder nombrarlo.
        for (String h : HOSTS_CAIDOS)
            for (String linea : js.split("\n")) {
                String t = linea.trim();
                if (t.startsWith("//") || t.startsWith("*")) continue;
                assertFalse("hay codigo del captador apuntando a " + h
                        + " (medido 522 el 2026-11-16): " + t, linea.contains(h));
            }
    }

    @Test
    public void losRespaldosDelJsApuntanAlHostVivo() throws Exception {
        String js = js();
        String vivo = "https://main.fastconnections.online/api/live/livedrama/v13.0.0/";
        for (String ep : new String[]{ "glt", "glbr", "glasbi" })
            assertTrue("falta el respaldo de " + ep, js.contains(vivo + ep));
        for (String linea : js.split("\n")) {
            if (linea.trim().startsWith("//") || linea.trim().startsWith("*")) continue;
            assertFalse("hay codigo apuntando al /redirect/ antiguo: " + linea.trim(),
                    linea.contains("/redirect/"));
        }
    }

    @Test
    public void elPlayerDeclaraLaVersionEnCadaIntentoDrama() throws Exception {
        File f = new File("src/main/java/dza/fut/paipo/PlayerActivity.java");
        assertTrue(f.isFile());
        byte[] b = new byte[(int) f.length()];
        java.io.InputStream in = new java.io.FileInputStream(f);
        try { in.read(b); } finally { in.close(); }
        String src = new String(b, java.nio.charset.StandardCharsets.UTF_8);
        assertTrue("el player no imprime la version al resolver un canal Drama",
                src.contains("BuildConfig.VERSION_NAME"));
    }

    @Test
    public void elJsSigueTeniendoLaLogicaDeLaCosecha() throws Exception {
        String js = js();
        assertNotNull(js);
        assertTrue("el JS perdio la entrega a Android", js.contains("AndroidDrama.entregar"));
        assertTrue("el JS perdio la cosecha", js.contains("COSECHA"));
        assertTrue("el JS perdio el desalambrado", js.contains("decLibre"));
    }
}
