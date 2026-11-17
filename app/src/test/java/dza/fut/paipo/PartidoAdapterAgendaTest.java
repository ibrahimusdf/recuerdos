package dza.fut.paipo;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Test;

public class PartidoAdapterAgendaTest {

    @Test
    public void cadaPartidoPasaSusCanalesAlPlayerComoServidores() throws IOException {
        String source = leerFuente();
        int launchStart = source.indexOf("private void lanzarReproductor(Partido partido, Partido.EmbedData canalSeleccionado)");
        int launchEnd = source.indexOf("private static String normalizarUrlAgenda", launchStart);
        assertTrue("se encuentra el lanzamiento del reproductor", launchStart >= 0 && launchEnd > launchStart);

        String launch = source.substring(launchStart, launchEnd);
        assertTrue("las opciones se toman del evento tocado",
                launch.contains("partido.attributes.embeds.data"));
        assertTrue("se conserva el nombre de cada canal del evento",
                launch.contains("nombresServidores.add(embed.attributes.embed_name.trim())"));
        assertTrue("se conserva la URL de cada canal del evento",
                launch.contains("urlsServidores.add(normalizarUrlAgenda(embed.attributes.embed_iframe))"));
        assertTrue("se selecciona como inicial el canal tocado",
                launch.contains("if (embed == canalSeleccionado)")
                        && launch.contains("intent.putExtra(\"opcion_indice\", indiceSeleccionado)"));
        assertTrue("el reproductor recibe todas las etiquetas",
                launch.contains("intent.putExtra(\"opciones_labels\", nombresServidores.toArray(new String[0]))"));
        assertTrue("el reproductor recibe todas las URLs",
                launch.contains("intent.putExtra(\"opciones_urls\", urlsServidores.toArray(new String[0]))"));
        assertTrue("el primer stream es el seleccionado",
                launch.contains("intent.putExtra(\"url_iframe_inicial\", urlsServidores.get(indiceSeleccionado))"));
        assertTrue("la agenda identifica el zapeo aleatorio del evento",
                launch.contains("intent.putExtra(\"agenda_evento\", true)"));
    }

    @Test
    public void canalAgendaAbreReproductorConTodasLasOpcionesDeSuEvento() throws IOException {
        String source = leerFuente();
        assertTrue(source.contains("fila.setOnClickListener(v -> lanzarReproductor(p, embed))"));
        assertTrue(source.contains("normalizarUrlAgenda(embed.attributes.embed_iframe)"));
        assertTrue(source.contains("fila.setEnabled(false)"));
    }

    private static String leerFuente() throws IOException {
        Path path = Paths.get("app/src/main/java/dza/fut/paipo/PartidoAdapter.java");
        if (!Files.isRegularFile(path)) {
            path = Paths.get("src/main/java/dza/fut/paipo/PartidoAdapter.java");
        }
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
