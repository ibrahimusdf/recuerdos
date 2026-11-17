package dza.fut.paipo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * v7.13 — contrato de la fuente PRIMERA: {@code data_unificado.json}, del repo
 * belkaperu/belkafut. Cubre las dos cosas que el usuario pidió:
 *
 *   1. que ese fichero se cargue EN PRIMERO (y que iptv-org ya no esté en la cadena);
 *   2. que su esquema —que no es ninguno de los que la app ya comía— se convierta
 *      bien a {@link Canal}.
 */
public class BelkafutTest {

    /** El fichero publicado, tal cual (301 KB). */
    private static String real() throws Exception {
        File f = new File("src/test/resources/data_unificado.json");
        if (!f.isFile()) f = new File("app/src/test/resources/data_unificado.json");
        return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
    }

    // ───────────────────────── la cadena ─────────────────────────

    @Test public void belkafutEsLaPrimeraFuentePublica() {
        List<AutoCanales.Fuente> cadena = AutoCanales.cadenaPublica();
        assertTrue("la cadena no puede estar vacía", !cadena.isEmpty());
        assertEquals("Belkafut", cadena.get(0).etiqueta);
        assertEquals(
                "https://pelislatino-canales.pages.dev/canales/pequena.json",
                AutoCanales.UNIFICADO);
        assertEquals(AutoCanales.UNIFICADO, cadena.get(0).url);
    }

    @Test public void iptvOrgNoEstaEnLaCadena() {
        for (AutoCanales.Fuente f : AutoCanales.cadenaPublica()) {
            assertFalse("iptv-org sigue en la cadena: " + f.url,
                    f.url.toLowerCase(Locale.ROOT).contains("iptv-org"));
        }
    }

    /** v7.14: el lider lleva timeouts cortos y UN intento. Si raw.githubusercontent
     *  no contesta, la cadena sigue en vez de dejar al usuario esperando. */
    @Test public void elLiderEsRapidoYElRestoNo() {
        List<AutoCanales.Fuente> cadena = AutoCanales.cadenaPublica();
        assertTrue("el lider tiene que ser rapido", cadena.get(0).rapida);
        for (int i = 1; i < cadena.size(); i++) {
            assertFalse("no deberia ser rapida: " + cadena.get(i).url,
                    cadena.get(i).rapida);
        }
    }
    // ---------- v7.16: el worker de Cloudflare ----------

    /** El lider sale del borde de Cloudflare, y el esquema que devuelve es el
     *  NATIVO de la app: si no, parsearCuerpo no daria ni un canal. */
    @Test public void elLiderSaleDelWorkerYEnElEsquemaNativo() {
        assertTrue("el lider tiene que salir del worker: " + AutoCanales.UNIFICADO,
                AutoCanales.UNIFICADO.startsWith("https://pelislatino-canales.pages.dev/canales"));
        assertTrue("la ETAPA 1 pide solo la lista pequena",
                AutoCanales.UNIFICADO.endsWith("/canales/pequena.json"));
    }

    /** Si el worker se cae, la cadena sigue por el fichero de GitHub: el usuario
     *  no se queda sin la lista pequena. Y NO es rapida (va detras). */
    @Test public void elWorkerTieneRespaldoEnGitHub() {
        boolean respaldo = false;
        for (int i = 1; i < AutoCanales.cadenaPublica().size(); i++) {
            AutoCanales.Fuente f = AutoCanales.cadenaPublica().get(i);
            if (f.url.contains("belkaperu/belkafut")) {
                respaldo = true;
                assertFalse("el respaldo no puede ser rapido", f.rapida);
            }
        }
        assertTrue("sin respaldo en GitHub, si el worker cae no hay lista pequena", respaldo);
    }

    /** v7.17 - el catálogo GRANDE se baja POR ALCANCE, del backend estático:
     *  corto 146 KB, latam 555 KB, global 2,7 MB. Antes: 3,4 MB siempre. */
    @Test public void elCatalogoGrandeSeBajaPorAlcance() {
        assertEquals("https://pelislatino-canales.pages.dev", CatalogoRemoto.BASE);
        assertEquals("https://pelislatino-canales.pages.dev/scopes/corto.json",
                CatalogoRemoto.urlScope(AutoCanales.ALCANCE_CORTO));
        assertEquals("https://pelislatino-canales.pages.dev/scopes/latam.json",
                CatalogoRemoto.urlScope(AutoCanales.ALCANCE_LATAM));
        assertEquals("https://pelislatino-canales.pages.dev/scopes/global.json",
                CatalogoRemoto.urlScope(AutoCanales.ALCANCE_GLOBAL));
    }

    /** Los tres alcances de la app tienen fichero: si falta uno, ese alcance se
     *  quedaría sin catálogo. */
    @Test public void losTresAlcancesTienenFichero() {
        for (String a : new String[]{AutoCanales.ALCANCE_CORTO,
                AutoCanales.ALCANCE_LATAM, AutoCanales.ALCANCE_GLOBAL}) {
            String u = CatalogoRemoto.urlScope(a);
            assertTrue("sin fichero para " + a + ": " + u, u.endsWith("/scopes/" + a + ".json"));
        }
    }

    /** v7.17 - antes de bajar el catálogo se mira la HUELLA (512 B). Si no cambió
     *  y el alcance es el mismo, no se baja nada. Es lo que hace que el alcance
     *  «global» deje de bajar 2,7 MB en cada arranque. */
    @Test public void antesDeBajarSeMiraLaHuella() throws Exception {
        String src = fuente("CatalogoRemoto.java");
        assertTrue("tiene que consultar la huella", src.contains("versionRemota()"));
        assertTrue("y compararla con la guardada",
                src.contains("remota.equals(meta[0]) && alcance.equals(meta[1])"));
        assertTrue("si coincide, usa el disco y no baja",
                src.contains("se usa el de disco"));
        assertTrue("la huella se guarda junto al alcance",
                src.contains("guardarMeta(ctx, remota, alcance)"));
    }

    /** CatalogoRemoto tiene que entender el esquema nativo del worker. Sin esto,
     *  aM3uUnico no sacaba ni un canal y el catalogo grande nunca se refrescaba. */
    @Test public void elCatalogoRemotoEntiendeElEsquemaNativo() throws Exception {
        String src = fuente("CatalogoRemoto.java");
        assertTrue("tiene que elegir el conversor por esquema", src.contains("aM3uDe(json)"));
        assertTrue("tiene que detectar el esquema nativo", src.contains("esNativo(json)"));
        assertTrue("y convertir con el emisor de canales",
                src.contains("CatalogoJson.aM3uDeCanales(canales)"));
    }

    /** La respuesta REAL del worker (bajada hoy) tiene que entrar entera: 462
     *  canales, 6 grupos, y el m3u que se deriva se vuelve a parsear igual. */
    @Test public void laRespuestaRealDelWorkerSeConvierteAM3u() throws Exception {
        File f = new File("app/src/test/resources/worker_pequena.json");
        if (!f.isFile()) f = new File("src/test/resources/worker_pequena.json");
        String json = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);

        List<Canal> canales = FuenteCanales.parsearCuerpo(json);
        assertEquals("el worker da 462 canales jugables", 462, canales.size());
        for (Canal c : canales) {
            assertNotNull("sin opciones: " + c.title, c.options);
            assertFalse("sin opciones: " + c.title, c.options.isEmpty());
        }

        String m3u = CatalogoJson.aM3uDeCanales(canales);
        assertTrue("el m3u tiene que empezar por #EXTM3U", m3u.startsWith("#EXTM3U"));
        List<Canal> vuelta = ListaPegada.desdeTexto(m3u);
        assertEquals("la vuelta por el m3u no puede perder canales", 462, vuelta.size());
        Set<String> grupos = new LinkedHashSet<>();
        for (Canal c : vuelta) grupos.add(c.category);
        assertEquals(6, grupos.size());
    }

    // ---------- v7.15: la carga en DOS ETAPAS ----------

    private static String fuente(String ruta) throws Exception {
        File f = new File("app/src/main/java/dza/fut/paipo/" + ruta);
        if (!f.isFile()) f = new File("src/main/java/dza/fut/paipo/" + ruta);
        return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
    }

    /** ETAPA 1: la lista pequeña se pinta TAL CUAL, sin fusionar con el catálogo
     *  grande. Es lo que la hace aparecer en ~65 ms en vez de 317 ms. */
    @Test public void laEtapaUnoPintaLaListaPequenaSinFusionar() throws Exception {
        String frag = fuente("CanalesEnVivoFragment.java");
        assertTrue("la ETAPA 1 tiene que leer el cache de Belkafut",
                frag.contains("AutoCanales.desdeCache(contexto, AutoCanales.UNIFICADO)"));
        assertTrue("la ETAPA 1 se pinta sin Drama (sinDrama = true)",
                frag.contains("aplicarCanales(pequena, \"Belkafut · caché\", true)"));
        assertTrue("tiene que existir el modo sin fusionar",
                frag.contains("private volatile boolean ultimaSinDrama"));
        assertTrue("prepararRejillaAsync respeta la marca",
                frag.contains("if (sinDrama) {"));
    }

    /** ETAPA 2: sin la marca, se fusiona con el catálogo grande como siempre. */
    @Test public void laEtapaDosSiFusionaConElCatalogoGrande() throws Exception {
        String frag = fuente("CanalesEnVivoFragment.java");
        assertTrue(frag.contains("fusion = conLaListaDrama(base);"));
        // y el camino normal (red) sigue llegando por aplicarCanales de 2 argumentos
        assertTrue(frag.contains("aplicarCanales(nuevos, recortar(fuente.comoTexto()));"));
    }
    // ───────────────────────── el fichero real ─────────────────────────

    @Test public void elFicheroPublicadoParsea() throws Exception {
        // 517 entradas en el fichero; 462 se pueden abrir. Las otras 55 solo traen
        // `options` relativas ("/p/repro1noticias.html?r=…"), que son paginas web,
        // no streams: se quedan fuera en vez de dejar 55 tiles que fallan al abrirse.
        List<Canal> canales = FuenteCanales.parsearCuerpo(real());
        assertEquals(462, canales.size());
    }

    @Test public void losGruposQuedanComoLosPoneElPropioFichero() throws Exception {
        List<Canal> canales = FuenteCanales.parsearCuerpo(real());
        Set<String> grupos = new LinkedHashSet<>();
        for (Canal c : canales) grupos.add(c.category);
        // Ni "all"/"general" crudos, ni "deportes" junto a "Deportes", ni "peliculas"
        // junto a "Películas": los 14 grupos del fichero colapsan en sus 6 rótulos.
        java.util.List<String> orden = new java.util.ArrayList<>(grupos);
        java.util.Collections.sort(orden);
        assertEquals(
                "[Canales Internacionales, Contenido Infantil, Deportes en Vivo, Entretenimiento, Noticias, Películas]",
                orden.toString());
    }

    @Test public void todosLosCanalesTienenUrlJugable() throws Exception {
        for (Canal c : FuenteCanales.parsearCuerpo(real())) {
            assertNotNull("sin opciones: " + c.title, c.options);
            assertFalse("sin opciones: " + c.title, c.options.isEmpty());
            for (Canal.OpcionCanal o : c.options) {
                assertNotNull("url nula en " + c.title, o.url);
                assertTrue("url que no es stream en " + c.title + ": " + o.url,
                        o.url.startsWith("http"));
                assertFalse("etiqueta vacía en " + c.title,
                        o.label == null || o.label.trim().isEmpty());
            }
        }
    }

    @Test public void losTitulosNoSeRepiten() throws Exception {
        List<Canal> canales = FuenteCanales.parsearCuerpo(real());
        Set<String> titulos = new LinkedHashSet<>();
        for (Canal c : canales) {
            assertTrue("titulo repetido: " + c.title, titulos.add(c.title));
        }
    }

    @Test public void lasUrlsRelativasDeOptionsSeDescartan() throws Exception {
        // Una de las 206 entradas con `options` apunta a /p/repro1noticias.html?...,
        // que es una página, no un stream. Se queda solo con lo servible.
        String mini = "{\"categories\":[{\"display_name\":\"Noticias\",\"name\":\"Noticias\"}],"
                + "\"channels\":[{\"name\":\"13\",\"title\":\"13\",\"category\":\"noticias\","
                + "\"imageUrl\":\"https://img/13.png\",\"servers\":[],"
                + "\"options\":[{\"label\":\"13\",\"url\":\"/p/repro1noticias.html?r=https://x\"},"
                + "{\"label\":\"M3U\",\"url\":\"http://s/13/index.m3u8\"}]}]}";
        List<Canal> canales = FuenteCanales.parsearCuerpo(mini);
        assertEquals(1, canales.size());
        assertEquals("Noticias", canales.get(0).category);
        assertEquals(1, canales.get(0).options.size());
        assertEquals("M3U", canales.get(0).options.get(0).label);
        assertEquals("https://img/13.png", canales.get(0).image);
    }

    @Test public void unCanalSinNingunaUrlSeCae() {
        String mini = "{\"categories\":[],\"channels\":[{\"name\":\"Nada\",\"title\":\"Nada\","
                + "\"category\":\"all\",\"servers\":[{\"name\":\"1\",\"url\":\"/relativo\"}],"
                + "\"options\":[]}]}";
        assertTrue(FuenteCanales.parsearCuerpo(mini).isEmpty());
    }

    @Test public void dosServidoresDelMismoCanalQuedanComoDosOpciones() {
        String mini = "{\"categories\":[],\"channels\":[{\"name\":\"ESPN\",\"title\":\"ESPN\","
                + "\"category\":\"general\",\"servers\":[{\"name\":\"1\",\"url\":\"http://a/1.m3u8\"},"
                + "{\"name\":\"Embed\",\"url\":\"https://b/2.m3u8\"}]}]}";
        List<Canal> canales = FuenteCanales.parsearCuerpo(mini);
        assertEquals(1, canales.size());
        assertEquals("Deportes en Vivo", canales.get(0).category);
        assertEquals(2, canales.get(0).options.size());
        assertEquals("1", canales.get(0).options.get(0).label);
        assertEquals("Embed", canales.get(0).options.get(1).label);
    }

    @Test public void elMismoTituloDosVecesSumaServidores() {
        String mini = "{\"categories\":[],\"channels\":["
                + "{\"name\":\"ESPN\",\"title\":\"ESPN\",\"category\":\"deportes\","
                + "\"servers\":[{\"name\":\"1\",\"url\":\"http://a/1.m3u8\"}]},"
                + "{\"name\":\"ESPN\",\"title\":\"ESPN\",\"category\":\"general\","
                + "\"servers\":[{\"name\":\"2\",\"url\":\"http://b/2.m3u8\"}]}]}";
        List<Canal> canales = FuenteCanales.parsearCuerpo(mini);
        assertEquals(1, canales.size());
        assertEquals(2, canales.get(0).options.size());
        assertEquals("Deportes en Vivo", canales.get(0).category);
    }

    @Test public void niM3uNiElJsonDeLatinosSeTocan() {
        // El conversor nuevo solo entra cuando la raíz trae `channels[]` suelto.
        String latinos = "{\"categorias\":[\"Deportes\"],\"total\":1,\"canales\":["
                + "{\"title\":\"TUDN\",\"image\":\"\",\"category\":\"Deportes\",\"options\":["
                + "{\"label\":\"1\",\"url\":\"http://190.14.10.19:16000/play/a09v/index.m3u8\"}]}]}";
        List<Canal> canales = FuenteCanales.parsearCuerpo(latinos);
        assertEquals(1, canales.size());
        assertEquals("TUDN", canales.get(0).title);
        assertEquals("Deportes", canales.get(0).category);
    }

    // ─────────────── v7.26: los servidores por la ruta remota ───────────────

    /**
     * EL test del bug. El catálogo remoto no se usa en JSON: CatalogoRemoto lo
     * convierte a M3U y M3uParser lo re-parsea. Si esa conversión se lleva solo el
     * primer servidor de cada canal, el menú sale vacío aunque el canal los tenga.
     *
     * <p>Medido sobre el fichero real: por la ruta del M3U se perdían 251
     * servidores y ningún canal se quedaba con más de uno.</p>
     */
    @Test public void lasDosRutasDanLosMismosServidores() throws Exception {
        String json = real();

        List<Canal> directo = FuenteCanales.parsearUnificado(json);
        List<Canal> viaM3u = M3uParser.parsear(CatalogoJson.aM3uUnico(json));

        int od = 0, om = 0, vd = 0, vm = 0;
        for (Canal c : directo) if (c.options != null) {
            od += c.options.size();
            if (c.options.size() > 1) vd++;
        }
        for (Canal c : viaM3u) if (c.options != null) {
            om += c.options.size();
            if (c.options.size() > 1) vm++;
        }

        assertEquals("la ruta remota tiene que dar los mismos canales",
                directo.size(), viaM3u.size());
        assertEquals("y las mismas opciones en total", od, om);
        assertEquals("y los mismos canales con varios servidores", vd, vm);
        assertTrue("tiene que haber canales con varios servidores de verdad", vd > 50);
    }

    /** Una línea #EXTINF por servidor, no una por canal. */
    @Test public void elM3uLlevaUnaLineaPorServidor() {
        String mini = "{\"categories\":[],\"channels\":[{\"name\":\"ESPN\",\"title\":\"ESPN\","
                + "\"category\":\"general\",\"servers\":[{\"name\":\"1\",\"url\":\"http://a/1.m3u8\"},"
                + "{\"name\":\"M3U\",\"url\":\"https://b/2.m3u8\"},"
                + "{\"name\":\"Embed\",\"url\":\"https://c/3.m3u8\"}]}]}";
        String m3u = CatalogoJson.aM3uUnico(mini);
        assertEquals("tres servidores, tres líneas", 3, m3u.split("#EXTINF").length - 1);
        for (String u : new String[]{"http://a/1.m3u8", "https://b/2.m3u8", "https://c/3.m3u8"})
            assertTrue("falta " + u, m3u.contains(u));
    }

    /**
     * El nombre del servidor NO puede ir en el rótulo: M3uParser dedupea por rótulo,
     * así que «ESPN (1)» y «ESPN (M3U)» no se fusionan y cada servidor se convierte
     * en un canal suelto. Viaja en su propio atributo server-name.
     */
    @Test public void elNombreDelServidorNoSeCuelaEnElRotulo() {
        String mini = "{\"categories\":[],\"channels\":[{\"name\":\"ESPN\",\"title\":\"ESPN\","
                + "\"category\":\"general\",\"servers\":[{\"name\":\"1\",\"url\":\"http://a/1.m3u8\"},"
                + "{\"name\":\"M3U\",\"url\":\"https://b/2.m3u8\"}]}]}";
        String m3u = CatalogoJson.aM3uUnico(mini);
        assertTrue("el nombre va en su atributo", m3u.contains("server-name=\"1\""));
        assertTrue("y el otro también", m3u.contains("server-name=\"M3U\""));
        assertFalse("el rótulo se queda limpio", m3u.contains(",ESPN (1)"));
        assertFalse("ni con el otro", m3u.contains(",ESPN (M3U)"));
        // y al parsearlo, se fusionan en un canal con dos opciones
        List<Canal> cs = M3uParser.parsear(m3u);
        assertEquals(1, cs.size());
        assertEquals(2, cs.get(0).options.size());
        assertEquals("ESPN", cs.get(0).title);
        assertEquals("1", cs.get(0).options.get(0).label);
        assertEquals("M3U", cs.get(0).options.get(1).label);
    }

    /** Las options[] jugables también son servidores; las relativas, no. */
    @Test public void lasOptionsJugablesTambienSalen() {
        String mini = "{\"categories\":[],\"channels\":[{\"name\":\"X\",\"title\":\"X\","
                + "\"category\":\"general\",\"servers\":[{\"name\":\"1\",\"url\":\"http://a/1.m3u8\"}],"
                + "\"options\":[{\"label\":\"Alt\",\"url\":\"https://b/2.m3u8\"},"
                + "{\"label\":\"Pag\",\"url\":\"/p/repro/xyz\"}]}]}";
        String m3u = CatalogoJson.aM3uUnico(mini);
        assertTrue(m3u.contains("https://b/2.m3u8"));
        assertFalse("una ruta relativa es una página, no un stream",
                m3u.contains("/p/repro/xyz"));
        assertEquals(2, M3uParser.parsear(m3u).get(0).options.size());
    }

    /** Un M3U cualquiera (sin server-name) sigue funcionando como siempre. */
    @Test public void unM3uNormalNoCambia() {
        String m3u = "#EXTM3U\n#EXTINF:-1 tvg-name=\"Directv Sports\" group-title=\"Futbol\","
                + "Directv Sports\nhttp://x/1.m3u8\n"
                + "#EXTINF:-1 tvg-name=\"ESPN (720p)\" group-title=\"Futbol\",ESPN (720p)\n"
                + "http://y/2.m3u8\n";
        List<Canal> cs = M3uParser.parsear(m3u);
        assertEquals(2, cs.size());
        assertEquals("Directv Sports", cs.get(0).title);
        assertEquals("720p", cs.get(1).options.get(0).label);
    }
}
