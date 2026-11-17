package dza.fut.paipo;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.Test;

/**
 * v7.35 — por qué la rejilla de canales tardaba en aparecer, y lo que lo fija.
 *
 * <p>Dos causas, ninguna visible en la pantalla hasta que se mide:
 *
 * <p><b>1. La losa de iniciales se dibujaba en cada bind.</b>
 * {@link Logos#avatar} alojaba un bitmap de 440×440 ARGB_8888 —774 KB— más un
 * degradado, dos {@code Paint} y un {@code Typeface}, en el hilo principal, por
 * cada tile cada vez que el RecyclerView lo reciclaba. Medido sobre la lista
 * rápida ({@code pequena.json}, 462 canales): los 462 pasan por ahí, tengan logo
 * o no. Ahora el bitmap sale de una caché con presupuesto en bytes.
 *
 * <p><b>2. Dos saltos al pool en serie antes de pintar.</b> Con la rejilla recién
 * cargada no hay tópico ni búsqueda, así que filtrar devuelve la lista tal cual —
 * pero se copiaba entera y se daba otra vuelta al pool para descubrirlo.
 */
public class RejillaRapidaTest {

    private static String fuente(String ruta) throws Exception {
        File f = new File("app/src/main/java/dza/fut/paipo/" + ruta);
        if (!f.isFile()) f = new File("src/main/java/dza/fut/paipo/" + ruta);
        return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
    }

    @Test
    public void laLosaSeDibujaUnaVezPorNombre() throws Exception {
        String logos = fuente("Logos.java");
        assertTrue("la losa tiene que salir de una cache", logos.contains("LOSAS.get(clave)"));
        assertTrue("y guardarse al dibujarla", logos.contains("LOSAS.put(clave, fresco)"));
        assertTrue("el dibujo de verdad esta en su propio metodo",
                logos.contains("private static android.graphics.Bitmap dibujarLosa(String nombre)"));
        assertTrue("avatar solo envuelve el bitmap, no lo dibuja",
                logos.contains("return new android.graphics.drawable.BitmapDrawable(ctx.getResources(), losa(nombre));"));

        // El createBitmap solo puede aparecer DONDE se dibuja, no en el camino caliente.
        int enDibujar = logos.indexOf("private static android.graphics.Bitmap dibujarLosa");
        int finDibujar = logos.indexOf("\n    }", enDibujar);
        assertTrue("se encuentra el metodo de dibujo", enDibujar >= 0 && finDibujar > enDibujar);
        String dibujo = logos.substring(enDibujar, finDibujar);
        assertTrue("ahi si se aloja el bitmap", dibujo.contains("Bitmap.createBitmap"));
        String caminoCaliente = logos.substring(0, enDibujar)
                + logos.substring(finDibujar);
        assertFalse("fuera del dibujo NO se puede alojar ningun bitmap de 774 KB",
                caminoCaliente.contains("Bitmap.createBitmap"));
    }

    @Test
    public void laCacheTienePresupuestoEnBytes() throws Exception {
        String logos = fuente("Logos.java");
        assertTrue("el presupuesto se mide en bytes, no en entradas",
                logos.contains("new LruPeso<>(PRESUPUESTO_LOSAS, android.graphics.Bitmap::getByteCount)"));
        // 440*440*4 = 774.400 B. Con 12 MB caben ~15 losas: mas de una pantalla.
        assertTrue("12 MB de presupuesto", logos.contains("12L * 1024 * 1024"));
    }

    @Test
    public void sinFiltroSePublicaSinDarOtraVueltaAlPool() throws Exception {
        String frag = fuente("CanalesEnVivoFragment.java");
        int i = frag.indexOf("private void aplicarFiltroInterno()");
        assertTrue("no encuentro aplicarFiltroInterno", i >= 0);
        String m = frag.substring(i, frag.indexOf("\n    }", i));
        // El camino rapido: sin topico y sin busqueda, publicar aqui mismo.
        assertTrue("si no hay topico ni busqueda se sale por el camino rapido",
                m.contains("if (topicoActivo.isEmpty() && busqueda.isEmpty()) {"));
        assertTrue("publicando la lista tal cual",
                m.contains("publicarFiltro(new ArrayList<Object>(canales),"));
        // El camino lento (copia + pool) sigue existiendo para cuando SI hay filtro.
        assertTrue("con filtro se sigue yendo al pool",
                m.contains("dza.fut.paipo.core.AppExecutors.enSerie("));
    }

    @Test
    public void laRejillaSeConfiguraParaNoRebindearDeMas() throws Exception {
        String frag = fuente("CanalesEnVivoFragment.java");
        assertTrue("la rejilla no depende del adapter para medirse",
                frag.contains("recyclerView.setHasFixedSize(true);"));
        assertTrue("y guarda mas tiles ya medidos (de 2 a 6)",
                frag.contains("recyclerView.setItemViewCacheSize(6);"));
    }

    @Test
    public void losLogosNoSePidenPorElProxySinQuerer() throws Exception {
        // El proxy de weserv rompio las banderas (v3.2.55) y viene apagado a
        // proposito. La optimizacion NO puede colarselo por la puerta de atras.
        String adapter = fuente("CanalAdapter.java");
        assertTrue("el proxy sigue detras de su interruptor",
                adapter.contains("reducirLogos ? Logos.reducido(canal.image, Logos.ANCHO_TILE) : canal.image"));
        assertFalse("nadie lo ha encendido por defecto",
                adapter.contains("private boolean reducirLogos = true"));
    }
}
