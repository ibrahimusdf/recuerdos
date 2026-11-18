package dza.fut.paipo;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.Test;

public class MainFullscreenTest {

    @Test
    public void contenidoPrincipalNoAplicaInsetsQueDejenMargenEnPantalla() throws Exception {
        String layout = leerArchivo("app/src/main/res/layout/activity_main.xml",
                "src/main/res/layout/activity_main.xml");
        assertTrue(layout.contains("android:layout_width=\"match_parent\""));
        assertTrue(layout.contains("android:layout_height=\"match_parent\""));
        assertFalse("el drawer no debe agregar márgenes de barras del sistema",
                layout.contains("android:fitsSystemWindows=\"true\""));
    }

    @Test
    public void modoInmersivoSeRestauraAlRotarYVolverALaApp() throws Exception {
        String source = leerArchivo("app/src/main/java/dza/fut/paipo/Main.java",
                "src/main/java/dza/fut/paipo/Main.java");
        assertTrue(source.contains("WindowCompat.setDecorFitsSystemWindows(getWindow(), false);"));
        assertTrue(source.contains("public void onConfigurationChanged(@NonNull android.content.res.Configuration newConfig)"));
        assertTrue(source.contains("super.onConfigurationChanged(newConfig);\n        hideSystemUI();"));
        assertTrue(source.contains("protected void onResume() {\n        super.onResume();\n        hideSystemUI();"));
    }

    private static String leerArchivo(String appPath, String modulePath) throws Exception {
        File file = new File(appPath);
        if (!file.isFile()) file = new File(modulePath);
        assertTrue("no se encuentra " + appPath, file.isFile());
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }
}
