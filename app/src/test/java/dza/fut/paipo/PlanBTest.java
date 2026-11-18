package dza.fut.paipo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.junit.Test;

/**
 * v7.38.4 — EL PLAN B. Las tres cosas que hacían que un canal se quedara en negro
 * con sus ocho servidores sin abrir.
 *
 * <p><b>Por qué existe este fichero.</b> La suite tenía 174 pruebas verdes y el
 * usuario no podía ver un canal. La razón: se probaban las funciones que no se
 * ejecutan ({@code DramaResolucion.embebidas/mejorEmbebida/siguienteEmbebida} solo
 * las llama el test) y no la decisión que sí decide si el canal se ve. Aquí se
 * prueban las tres reglas que estaban mal, con el backup REAL de beIN 1 copiado
 * del servidor el 2026-11-17.
 *
 * <p>Los tres casos están probados en ROJO contra el código de la 7.38.3 (ver
 * {@code docs/repro-planb.mjs}, que ejecuta la misma lógica en node).
 */
public class PlanBTest {

    private static final String EMBED_BEIN1 = "https://ordernap.net/e/z7iiiknxnuqjk";
    private static final String EMBED_MUERTO = "https://quotarevival.net/e/yxtmusy4xj8tj";

    /** live_tv_beinsport1 tal cual lo devolvió glasbi. */
    private static final String URL_BEIN1 =
            "http://.LS.V2live_tv_custom_handler_live_tv_mainevent_description_DL_/s";

    private static final String BACKUP_BEIN1 =
            "http://.LS.V2live_tv_custom_handler_live_tv_beinsport1_description_SD_/s -- redirect"
            + " -;- http://.LS.V2live_tv_custom_handler_live_tv_beinsport1_description_HD_/s -- redirect"
            + " -;- http://.LS.V2live_tv_custom_handler_live_tv_beinsport1_description_4K_/s -- redirect"
            + " -;- {\"url\":\"https:\\/\\/ordernap.net\\/e\\/z7iiiknxnuqjk\","
            + "\"agent\":\"Mozilla\\/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit\\/537.36 "
            + "(KHTML, like Gecko) Chrome\\/156.0.0.0 Safari\\/537.36\","
            + "\"iframe\":\"https:\\/\\/ordernap.net\\/e\\/z7iiiknxnuqjk\","
            + "\"headers\":{\"User-Agent\":\"Mozilla\\/5.0 (Macintosh; Intel Mac OS X 10_15_7) \""
            + "AppleWebKit\\/537.36 (KHTML, like Gecko) Chrome\\/156.0.0.0 Safari\\/537.36\"},"
            + "\"description\":\"DLW\",\"data\":\" \"} -- double_redirect"
            + " -;- http://.LS.V2LOAD_BALANCERlive_tv_beinsport1/s -- redirect"
            + " -;- .LS.V2redirectV2_koora10_064762e6357527bc79dfba693c3490df_1_serv:GeneralCrawler; -- redirect"
            + " -;- .LS.V2redirectV2_livekora_064762e6357527bc79dfba693c3490df_1_serv:GeneralCrawler; -- redirect"
            + " -;- http://.LS.V2daddy_91/s -- redirect"
            + " -;- {\"url\":\"https:\\/\\/quotarevival.net\\/e\\/yxtmusy4xj8tj\","
            + "\"agent\":\"Mozilla\\/5.0 (Macintosh) Chrome\\/156.0.0.0\","
            + "\"headers\":{\"Referer\":\"https:\\/\\/new.aflam4you.net\\/\","
            + "\"Origin\":\"https:\\/\\/new.aflam4you.net\"},\"description\":\"SD\",\"data\":\" \"}"
            + " -- double_redirect"
            + " -;- http://primetv.cc.cd/beinfhd11/tracks-v1a1/mono.tbin.m3u8 -- Mozilla"
            + " -;- http://.LS.V2daddy_91/s -- redirect -;- ";

    // ───────────────────── BUG 1: el `return null` que abortaba la lista ─────

    /**
     * CALIBRACIÓN HONESTA (medido con docs/repro-planb.mjs): el {@code return null}
     * del bucle de la 7.38.3 NO alcanza a un canal con dos servidores útiles — que
     * es justo el caso del backup real de beIN 1 aquí copiado (1 embebida + 1
     * directa; los otros 8 trozos son marcadores {@code .LS.} y {@code utiles()} los
     * filtra). Con solo dos, cuando se pide el siguiente desde el primero, los
     * guards de {@code actual}/{@code enCurso} ya lo descartan antes de llegar al
     * bucle de probados y la respuesta es correcta.
     *
     * <p>Se deja como test porque fija el límite: el bug necesita TRES servidores
     * útiles para dispararse (ver el siguiente test). Sin esta calibración el
     * informe habría exagerado su alcance.
     */
    @Test
    public void conDosServidoresUtilesElBugNoLlegaADispararse() {
        List<DramaResolucion.Opcion> utiles = DramaResolucion.utiles(URL_BEIN1, BACKUP_BEIN1);
        assertEquals("el backup real de beIN 1 deja 2 útiles: embed + directa", 2, utiles.size());

        String primero = utiles.get(0).url;
        Set<String> probados = new LinkedHashSet<>();
        probados.add(primero);
        DramaResolucion.Opcion siguiente =
                DramaResolucion.siguiente(utiles, probados, null, primero);
        assertNotNull(siguiente);
        assertFalse(primero.equals(siguiente.url));
    }

    /**
     * EL BUG BLOQUEANTE, con su condición real: TRES servidores útiles y dos ya
     * probados. En la 7.38.3 el bucle ABORTABA al toparse con la url ya vista (el
     * {@code return null} estaba dentro del {@code for}), así que el tercero no se
     * intentaba nunca y el log decía «se han probado TODOS los servidores».
     *
     * <p>Los canales que sí traen tres o más servidores usables son los que se
     * quedaban en negro; los de dos se salvaban por los guards.
     */
    @Test
    public void conTresServidoresElTerceroSiSeIntenta() {
        List<DramaResolucion.Opcion> tres = DramaResolucion.utiles(
                "https://embed1.example/e/aaa",
                "https://embed2.example/e/bbb -;- "
                        + "http://primetv.cc.cd/beinfhd11/tracks-v1a1/mono.tbin.m3u8 -- Mozilla");
        assertTrue("la muestra tiene que tener 3 útiles", tres.size() >= 3);

        Set<String> probados = new LinkedHashSet<>();
        probados.add(tres.get(0).url);
        probados.add(tres.get(1).url);

        DramaResolucion.Opcion siguiente =
                DramaResolucion.siguiente(tres, probados, tres.get(1).url, null);
        assertNotNull("con uno sin probar NO se puede devolver null", siguiente);
        assertEquals("tiene que devolver el tercero", tres.get(2).url, siguiente.url);
    }

    /** Recorre TODOS los servidores uno a uno: el plan B no se salta ninguno. */
    @Test
    public void seRecorrenTodosLosServidoresYSeAcabaEnNull() {
        List<DramaResolucion.Opcion> utiles = DramaResolucion.utiles(URL_BEIN1, BACKUP_BEIN1);
        Set<String> probados = new LinkedHashSet<>();
        String actual = null;
        List<String> recorrido = new ArrayList<>();

        for (int i = 0; i < 50; i++) {                       // tope de seguridad
            DramaResolucion.Opcion o = DramaResolucion.siguiente(utiles, probados, actual, null);
            if (o == null) break;
            recorrido.add(o.url);
            probados.add(o.url);
            actual = null;                                   // tras fallar deja de "sonar"
        }

        assertEquals("se prueban TODOS los útiles, en su orden", utiles.size(), recorrido.size());
        assertEquals("sin repetir ninguno", utiles.size(), new LinkedHashSet<>(recorrido).size());
        // y cuando ya se probaron todos, sí: null (ahí el aviso es honesto)
        assertNull(DramaResolucion.siguiente(utiles, probados, null, null));
    }

    /** Ninguna embebida viva se descarta; la muerta (quotarevival) no se abre. */
    @Test
    public void elPlanBSalteaElEmbedMuertoYNoElBueno() {
        List<DramaResolucion.Opcion> utiles = DramaResolucion.utiles(URL_BEIN1, BACKUP_BEIN1);
        for (DramaResolucion.Opcion o : utiles) {
            assertFalse("un embed muerto no debe estar en la lista de útiles",
                    EMBED_MUERTO.equals(o.url));
        }
        boolean tieneElBueno = false;
        for (DramaResolucion.Opcion o : utiles) if (EMBED_BEIN1.equals(o.url)) tieneElBueno = true;
        assertTrue("ordernap sí está", tieneElBueno);
    }

    // ─────────────── BUG 4: primeraEmbebida con el dominio clavado ───────────

    /**
     * En la 7.38.3 sólo se aceptaba {@code ordernap.}. Un embed de otro dominio
     * (que es lo que pasa cada vez que el proveedor rota) se ignoraba aunque el
     * canal no tuviera otra vía.
     */
    @Test
    public void cualquierEmbedVivoValeNoSoloOrdernap() {
        String opciones = "[{\"n\":\"SD\",\"u\":\"https://embeddesconocido.example/e/abc123\"}]";
        assertEquals("un embed nuevo ya no se descarta",
                "https://embeddesconocido.example/e/abc123",
                DramaSesion.primeraEmbebida(opciones));
    }

    /** Y el que sabemos muerto sigue fuera, para no perder 30 s en él. */
    @Test
    public void elEmbedMuertoSigueEstandoExcluido() {
        String opciones = "[{\"n\":\"SD\",\"u\":\"" + EMBED_MUERTO + "\"}]";
        assertNull(DramaSesion.primeraEmbebida(opciones));
    }

    /** Una directa (m3u8) no es un embed: no debe colarse como tal. */
    @Test
    public void unaDirectaNoSeConfundeConEmbed() {
        String opciones =
                "[{\"n\":\"HD\",\"u\":\"http://primetv.cc.cd/beinfhd11/tracks-v1a1/mono.tbin.m3u8\"}]";
        assertNull(DramaSesion.primeraEmbebida(opciones));
    }

    // ───────────── BUG 9: el backup partido de dos maneras distintas ─────────

    /**
     * El mismo backup tenía que dar lo mismo al chip de calidad y al plan B. Con el
     * parseador estricto ({@code split(" -;- ")}) un separador con dos espacios o un
     * salto de línea colapsaba varios servidores en uno.
     */
    @Test
    public void elBackupSeParteIgualConEspaciosRaros() {
        String conEspaciosRaros =
                "http://a.example/1.m3u8 -- Mozilla   -;-\n   http://b.example/2.m3u8 -- Mozilla";
        List<String> trozos = DramaSesion.trozosDeBackup(conEspaciosRaros);
        assertEquals("dos servidores, no uno", 2, trozos.size());

        // y el plan B ve los mismos dos
        List<DramaResolucion.Opcion> ops = DramaResolucion.opciones(null, conEspaciosRaros);
        assertEquals(2, ops.size());
    }

    /** Un backup vacío o nulo no rompe nada (llegaba en canales sin alternativa). */
    @Test
    public void backupVacioNoRompeNada() {
        assertTrue(DramaSesion.trozosDeBackup(null).isEmpty());
        assertTrue(DramaSesion.trozosDeBackup("").isEmpty());
        assertTrue(DramaSesion.trozosDeBackup("   ").isEmpty());
        assertNotNull(DramaResolucion.opciones(null, null));   // sin excepción: eso es lo que importa
        assertTrue(DramaResolucion.opciones(null, "").isEmpty());
        assertTrue(DramaResolucion.utiles(null, null).isEmpty());
    }

    // ─────────────────────────── red de seguridad ────────────────────────────

    /** Las funciones que el bug dejó sin cubrir siguen siendo puras y no explotan. */
    @Test
    public void siguienteAguantaListasVaciasYBasura() {
        assertNull(DramaResolucion.siguiente(null, null, null, null));
        assertNull(DramaResolucion.siguiente(Collections.emptyList(), null, null, null));
        assertNull(DramaResolucion.siguiente(
                DramaResolucion.utiles(null, null), null, null, null));
        assertNull(DramaResolucion.siguiente(
                Arrays.asList((DramaResolucion.Opcion) null), null, null, null));
    }
}
