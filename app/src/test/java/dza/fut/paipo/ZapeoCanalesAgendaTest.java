package dza.fut.paipo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Test;

public class ZapeoCanalesAgendaTest {

    @Test
    public void mantieneEventoPrimeroYAgregaHastaDiezCanalesValidosUnicos() {
        Canal evento = canal("Evento actual", "https://evento.test/stream.m3u8");
        List<Canal> catalogo = new ArrayList<>();
        catalogo.add(canal("EVENTO ACTUAL", "https://duplicado.test/stream.m3u8"));
        catalogo.add(canal("Sin URL", " "));
        for (int i = 0; i < 15; i++) {
            catalogo.add(canal("Canal " + i, "https://canal.test/" + i + ".m3u8"));
        }

        List<Canal> seleccionados =
                ZapeoCanales.seleccionarCanalesAgenda(evento, catalogo, 10);

        assertEquals(11, seleccionados.size());
        assertSame(evento, seleccionados.get(0));
        Set<String> titulos = new HashSet<>();
        for (int i = 1; i < seleccionados.size(); i++) {
            Canal canal = seleccionados.get(i);
            assertTrue(canal.options.get(0).url.startsWith("https://"));
            assertTrue(titulos.add(canal.title.toLowerCase(java.util.Locale.ROOT)));
            assertTrue(!"evento actual".equals(canal.title.toLowerCase(java.util.Locale.ROOT)));
        }
    }

    private static Canal canal(String titulo, String url) {
        Canal canal = new Canal();
        canal.title = titulo;
        canal.options = new ArrayList<>();
        Canal.OpcionCanal opcion = new Canal.OpcionCanal();
        opcion.label = "Principal";
        opcion.url = url;
        canal.options.add(opcion);
        return canal;
    }
}
