package dza.fut.paipo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

/** v3.2.42 — «cada canal en su lugar como hace Drama»: el orden de Drama manda. */
public class FusionRejillaTest {

    private static Canal canal(String titulo, String grupo) {
        Canal c = new Canal();
        c.title = titulo;
        c.category = grupo;
        Canal.OpcionCanal o = new Canal.OpcionCanal();
        o.url = "http://x/" + titulo;
        c.options = new ArrayList<>(Arrays.asList(o));
        return c;
    }

    private static List<String> titulos(List<Canal> l) {
        List<String> ts = new ArrayList<>();
        for (Canal c : l) ts.add(c.title);
        return ts;
    }

    /** v3.2.51 (captura del usuario «sale numero, no hay flags»): 974/961/966 son
     *  codigos telefonicos de pais; tenian que resolverse a nombre + bandera. */
    /** v3.2.52: el grupo "unsorted" del feed se muestra como lo llama Drama. */
    @Test
    public void losInOrdenadosTienenNombre() {
        assertEquals("Sin clasificar", FusionRejilla.etiquetaTopic("Unsorted"));
        assertEquals("Sin clasificar", FusionRejilla.etiquetaTopic("unsorted"));
    }

    /** v3.2.55: los topics-pais del glt llegan con nombre en ingles; la tabla los
     *  conoce ahi tambien y salen con bandera + nombre en castellano sin necesidad
     *  del CDN (fallback local cuando la imagen no llega). */
    @Test
    public void losNombresArabigosTienenAliasConBandera() {
        assertEquals("\uD83C\uDDF8\uD83C\uDDFE", FusionRejilla.banderaDeTopic("Syria"));
        // la bandera son 2 regional indicators = 4 unidades UTF-16: acabar en el nombre, sin contar prefijos
        assertTrue(FusionRejilla.etiquetaTopic("Syria").endsWith("Siria"));
        assertEquals("\uD83C\uDDF1\uD83C\uDDE7", FusionRejilla.banderaDeTopic("Lebanon"));
        assertEquals("\uD83C\uDDF6\uD83C\uDDE6", FusionRejilla.banderaDeTopic("Qatar"));
        assertEquals("\uD83C\uDDE6\uD83C\uDDEA", FusionRejilla.banderaDeTopic("United Arab Emirates"));
        assertTrue(FusionRejilla.esPais("saudi arabia"));
    }

    /** v3.2.56: la bandera se deriva del ISO embebido en la url de Drama. */
    @Test
    public void losIsoAlimentanLaBanderaEmbebida() {
        assertEquals("pe", FusionRejilla.isoDeUrlTopic(
                "http://logo.twoapistack.work/img/topics/ic_flag_pe.png"));
        assertEquals("", FusionRejilla.isoDeUrlTopic("http://x/hot_now.png"));
        assertEquals("", FusionRejilla.isoDeUrlTopic(null));
        assertEquals("pe", FusionRejilla.isoDeBanderaEmoji("\uD83C\uDDF5\uD83C\uDDEA"));
        assertEquals("", FusionRejilla.isoDeBanderaEmoji("\u25CE"));
        assertEquals("", FusionRejilla.isoDeBanderaEmoji(null));
    }

    @Test
    public void laBanderaSeDerivaDelIsoDeLaUrl() {
        assertEquals("\uD83C\uDDF5\uD83C\uDDEA",
                FusionRejilla.banderaDeUrlTopic("http://logo.twoapistack.work/img/topics/ic_flag_pe.png"));
        assertEquals("\uD83C\uDDEB\uD83C\uDDF7",
                FusionRejilla.banderaDeUrlTopic("http://x/ic_flag_fr.PNG"));
        assertEquals("", FusionRejilla.banderaDeUrlTopic("http://x/hot_now.png"));
        assertEquals("", FusionRejilla.banderaDeUrlTopic(null));
    }

    /** v3.2.61: el id del glt y el nombre del feed deben resolver al MISMO rotulo,
     *  si no, el filtro del panel dejaria el pais recien cargado sin canales. */
    @Test
    public void elIdTelefonicoYElNombreInglesSonElMismoTopic() {
        assertEquals(FusionRejilla.etiquetaTopic("Syria"),
                FusionRejilla.etiquetaTopic("963"));
        assertEquals(FusionRejilla.etiquetaTopic("Egypt"),
                FusionRejilla.etiquetaTopic("20"));
    }

    @Test
    public void losCodigosDeMarcadorSonPaisConBandera() {
        assertEquals("\uD83C\uDDF6\uD83C\uDDE6", FusionRejilla.banderaDeTopic("974"));   // Qatar
        assertEquals("\uD83C\uDDF1\uD83C\uDDE7", FusionRejilla.banderaDeTopic("live_tv_961")); // Libano
        assertTrue(FusionRejilla.esPais("966"));
        assertFalse(FusionRejilla.esPais("98765"));           // no es marcador -> no inventar
        assertEquals("1234", FusionRejilla.etiquetaTopic("1234")); // numerico suelto sigue num
        assertEquals("\uD83C\uDDF6\uD83C\uDDE6 Qatar", FusionRejilla.etiquetaTopic("974"));
        assertTrue(FusionRejilla.topicEsPais("51", "PE"));    // el pais del aparato se clava
        assertTrue(FusionRejilla.topicEsPais("live_tv_974", "QA"));
        assertFalse(FusionRejilla.topicEsPais("974", "PE"));  // Qatar no es Peru
    }

    /** v7.36 (pedido 44b): la rejilla entera se parte en Latinoamérica y resto del mundo. */
    @Test
    public void laRejillaPoneLatinoamericaAlPrincipio() {
        List<Canal> lista = new ArrayList<>(Arrays.asList(
                canal("Movistar Deportes", "live_tv_espana"),
                canal("ESPN", "live_tv_peru"),
                canal("Fox Sports", "live_tv_mexico"),
                canal("CNN", "live_tv_noticias"),
                canal("TyC Sports", "live_tv_argentina")));
        FusionRejilla.latinoamericaPrimero(lista);
        assertEquals(Arrays.asList("ESPN", "Fox Sports", "TyC Sports",
                        "Movistar Deportes", "CNN"), titulos(lista));
    }

    /** v7.36: dentro de cada bloque manda el orden de llegada del feed, no el alfabeto
     *  ni la población. Es la partición estable, no un sort. */
    @Test
    public void dentroDeCadaBloqueSeRespetaElOrdenDeLlegada() {
        List<Canal> lista = new ArrayList<>(Arrays.asList(
                canal("Zulia", "live_tv_venezuela"),
                canal("America TV", "live_tv_peru"),
                canal("Al Jazeera", "live_tv_qatar"),
                canal("Canal 13", "live_tv_chile")));
        FusionRejilla.latinoamericaPrimero(lista);
        assertEquals(Arrays.asList("Zulia", "America TV", "Canal 13", "Al Jazeera"),
                titulos(lista));
    }

    /** v7.36: sin ningún canal latino la lista no se toca. */
    @Test
    public void sinLatinosLaListaQuedaIgual() {
        List<Canal> lista = new ArrayList<>(Arrays.asList(
                canal("La 1", "live_tv_espana"), canal("CNN", "live_tv_noticias")));
        List<Canal> antes = new ArrayList<>(lista);
        FusionRejilla.latinoamericaPrimero(lista);
        assertEquals(titulos(antes), titulos(lista));
    }

    /** v7.36: no se pierde ni un canal por el camino (ni los null, que se descartan). */
    @Test
    public void noSePierdeNingunCanal() {
        List<Canal> lista = new ArrayList<>(Arrays.asList(
                canal("ESPN", "live_tv_peru"), null, canal("La 1", "live_tv_espana"),
                canal("Sin categoria", null)));
        int antes = 0;
        for (Canal c : lista) if (c != null) antes++;
        FusionRejilla.latinoamericaPrimero(lista);
        int despues = 0;
        for (Canal c : lista) if (c != null) despues++;
        assertEquals(antes, despues);
        assertEquals("ESPN", lista.get(0).title);      // el latino, primero
        assertEquals(3, despues);
    }

    /** v7.36 (pedido 44b): el bloque latinoamericano. */
    @Test
    public void latinoamericaSeReconocePorNombreYPorMarcador() {
        // por nombre de tópico
        for (String t : new String[]{"live_tv_peru", "live_tv_argentina", "live_tv_mexico",
                "live_tv_colombia", "live_tv_chile", "live_tv_venezuela", "live_tv_ecuador",
                "live_tv_bolivia", "live_tv_paraguay", "live_tv_uruguay", "live_tv_brasil",
                "live_tv_guatemala", "live_tv_honduras", "live_tv_el salvador",
                "live_tv_nicaragua", "live_tv_costa rica", "live_tv_panama",
                "live_tv_cuba", "live_tv_republica dominicana"}) {
            assertTrue("latino: " + t, FusionRejilla.esLatam(t));
        }
        // por marcador telefónico, como llegan varias fuentes
        assertEquals("pe", FusionRejilla.isoDeTopic("51"));
        assertEquals("mx", FusionRejilla.isoDeTopic("52"));
        assertEquals("ar", FusionRejilla.isoDeTopic("54"));
        assertEquals("cr", FusionRejilla.isoDeTopic("506"));
        assertEquals("gt", FusionRejilla.isoDeTopic("502"));
        assertTrue(FusionRejilla.esLatam("live_tv_502"));
    }

    /** v7.36: lo que NO es Latinoamérica no entra en el bloque. */
    @Test
    public void loQueNoEsLatinoamericaNoEntraEnElBloque() {
        assertFalse(FusionRejilla.esLatam("live_tv_espana"));
        assertFalse(FusionRejilla.esLatam("live_tv_usa"));
        assertFalse(FusionRejilla.esLatam("live_tv_qatar"));
        assertFalse(FusionRejilla.esLatam("974"));
        assertFalse(FusionRejilla.esLatam("live_tv_deportes"));   // categoría, no país
        assertFalse(FusionRejilla.esLatam("live_tv_noticias"));
        assertFalse(FusionRejilla.esLatam(null));
        assertFalse(FusionRejilla.esLatam(""));
    }

    /** v7.36: el ISO sale de la tabla sin depender de la fuente de emojis del teléfono. */
    @Test
    public void isoDeTopicDaElIsoAunqueLaFuenteDeEmojisFalle() {
        assertEquals("pe", FusionRejilla.isoDeTopic("live_tv_peru"));
        assertEquals("ve", FusionRejilla.isoDeTopic("live_tv_venezuela"));
        assertEquals("qa", FusionRejilla.isoDeTopic("live_tv_974"));
        assertEquals("", FusionRejilla.isoDeTopic("live_tv_deportes"));
        assertEquals("", FusionRejilla.isoDeTopic(null));
    }

    @Test
    public void elDramaMandaYEscribeEnSuOrden() {
        List<Canal> drama = Arrays.asList(canal("GOLPERU", "live_tv_peru"), canal("ESPN", "live_tv_deportes"));
        List<Canal> base = Arrays.asList(canal("Canal C", "general"), canal("Canal D", "general"));
        List<Canal> out = FusionRejilla.conDramaPrimero(drama, base);
        // Con el pegado-al-final de antes esto salía [C, D, GOLPERU, ESPN].
        assertEquals(Arrays.asList("GOLPERU", "ESPN", "Canal C", "Canal D"), titulos(out));
    }

    @Test
    public void nuncaPisaUnCanalDeDramaNiDejaDuplicadosPorCalidad() {
        List<Canal> drama = Arrays.asList(canal("ESPN (720p)", "live_tv_deportes"));
        List<Canal> base = Arrays.asList(canal("espn", "deportes"), canal("Nuevoplay", "x"));
        List<Canal> out = FusionRejilla.conDramaPrimero(drama, base);
        assertEquals(2, out.size());
        assertEquals("ESPN (720p)", out.get(0).title);   // la fila de Drama gana
        assertEquals("Nuevoplay", out.get(1).title);
    }

    @Test
    public void paisesConBanderaYHumanizacion() {
        assertEquals("\uD83C\uDDF5\uD83C\uDDEA Per\u00fa", FusionRejilla.etiquetaTopic("live_tv_peru"));
        assertEquals("\uD83C\uDDE6\uD83C\uDDF7 Argentina", FusionRejilla.etiquetaTopic("Live_TV_Argentina"));
        assertEquals("\uD83C\uDDFA\uD83C\uDDF8 USA", FusionRejilla.etiquetaTopic("USA Sports"));
        assertEquals("Deportes", FusionRejilla.etiquetaTopic("live_tv_deportes"));
        assertTrue(FusionRejilla.esPais("Live_TV_Chile"));
        assertFalse(FusionRejilla.esPais("live_tv_deportes"));
        // "usa" suelto no muerde dentro de otras palabras:
        assertFalse(FusionRejilla.esPais("Casas y platos"));
    }

    @Test
    public void dramaVacioDejaLaBaseIntacta() {
        List<Canal> base = Arrays.asList(canal("A", "g"), canal("B", "g"));
        assertEquals(2, FusionRejilla.conDramaPrimero(null, base).size());
        assertEquals(2, FusionRejilla.conDramaPrimero(new ArrayList<Canal>(), base).size());
    }

    @Test
    public void elGltAportaLosNombresHumanosDeLosTopicos() {
        String glt = "{\"result\":0,\"live_topics\":["
                + "{\"id_topic\":\"live_tv_peru\",\"topic_name\":\"Perú\"},"
                + "{\"id_topic\":\"live_tv_usa\",\"name\":\"USA\"}]}";
        java.util.Map<String, String> m = DramaSesion.nombresTopicosDe(glt);
        assertEquals("Perú", m.get("live_tv_peru"));
        assertEquals("USA", m.get("live_tv_usa"));
        assertTrue(DramaSesion.nombresTopicosDe("{\"live_topics\":[]}").isEmpty());
        assertTrue(DramaSesion.nombresTopicosDe("no json").isEmpty());
    }
    @org.junit.Test
    public void grupoSinMarcaSeMuestraEnVivo() {
        org.junit.Assert.assertEquals("En vivo", FusionRejilla.etiquetaTopic("DramaLive"));
    }
    @org.junit.Test
    public void topicosBloqueadosNoSeMuestran() {
        org.junit.Assert.assertTrue(FusionRejilla.topicBloqueado("Live Twitch"));
        org.junit.Assert.assertTrue(FusionRejilla.topicBloqueado("Israel"));
        org.junit.Assert.assertTrue(FusionRejilla.topicBloqueado("972"));
        org.junit.Assert.assertFalse(FusionRejilla.topicBloqueado("Peru"));
        org.junit.Assert.assertFalse(FusionRejilla.topicBloqueado("Live_TV_Peru"));
        org.junit.Assert.assertFalse(FusionRejilla.topicBloqueado(null));
    }


    // ---------- v7.12: claveNombre sin regex, mismo resultado ----------

    /** La version de referencia, con regex, tal como era antes de la v7.12. */
    private static String claveNombreConRegex(String nombre) {
        if (nombre == null) return "";
        return nombre.toLowerCase(java.util.Locale.ROOT)
                .replaceAll("\\(\\s*\\d{3,4}\\s*p\\s*\\)", " ")
                .replaceAll("[^a-z0-9 ]+", " ")
                .trim().replaceAll("\\s+", " ");
    }

    @org.junit.Test
    public void claveNombreNoCambia() {
        String[] muestras = {
                "Latina", "America TV", "America, el \"bueno\"", "  ESPN 2  ", "ESPN 2 (720p)",
                "Canal 1 (1080p)", "Canal 1 ( 720 p )", "Movistar+ Deportes HD", "beIN SPORTS ñ",
                "TNT Sports\tChile", "A-B_C.D", "  ", "", "(720p)", "x (720p) y (1080p)",
                "Panamericana 4K", "TV Peru\u00e9s", "willax\u00a0deportes", "RPP Noticias  (480P)",
                "Canal 12 (12p)", "Canal 12 (12345p)", "Canal 12 (12 p)", "gol tv \u2014 peru",
                "L1 MAX", "DSPORTS 2", "Directv Sports+", "Canal (720", "Canal 720p)", "0", "0000p",
        };
        for (String m : muestras) {
            assertEquals("clave distinta para: [" + m + "]",
                    claveNombreConRegex(m), FusionRejilla.claveNombre(m));
        }
        // Y sobre nombres reales del catalogo, que es donde se notaria un cambio.
        for (String m : new String[]{"ESPN 3", "GOLPERU", "liga 1max", "real madrid tv",
                "dazn 1 es", "Tigo Sports", "Win Sports+", "VTV", "Sony", "TELE SUR",
                "Cartoon Network", "animal_planet"}) {
            assertEquals("clave distinta para: [" + m + "]",
                    claveNombreConRegex(m), FusionRejilla.claveNombre(m));
        }
    }
}
