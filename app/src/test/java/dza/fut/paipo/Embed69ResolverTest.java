package dza.fut.paipo;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Scanner;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Pruebas del resolver de embed69 contra datos REALES capturados el
 * 13-Nov-2026 de {@code https://embed69.org/f/tt0111161/}.
 *
 * <p>Ese día embed69 cambió dos cosas y el resolver se quedó ciego:</p>
 * <ol>
 *   <li>Delante del reproductor puso <b>Anubis</b> (anti-bot v1.28.2): la
 *       primera respuesta es la pantalla "Making sure you're not a bot!" y el
 *       reto PoW que buscábamos vive en la página que hay detrás. De ahí el
 *       error «sin reto PoW en la página».</li>
 *   <li>La clave AES dejó de depender del nonce: ahora es fija,
 *       {@code sha256(LINK_CHALLENGE + LINK_SALT)}, con los dos valores
 *       declarados en el HTML.</li>
 * </ol>
 *
 * <p>Estas pruebas fijan el algoritmo de Anubis (nonce) y el descifrado AES,
 * que son las dos piezas que se pueden comprobar sin red. El canje real del
 * reto contra el servidor se verificó a mano el mismo día.</p>
 */
public class Embed69ResolverTest {

    private static final String CH_REAL = "aac8c4de6c6c683473fe80ceca05356e";
    private static final String SA_REAL = "6d07665b9850a0db";

    private static JsonObject real() throws Exception {
        try (InputStream in = Embed69ResolverTest.class.getResourceAsStream(
                "/embed69_real.json")) {
            assertNotNull("falta test/resources/embed69_real.json", in);
            String txt = new Scanner(in, StandardCharsets.UTF_8.name())
                    .useDelimiter("\\A").next();
            return JsonParser.parseString(txt).getAsJsonObject();
        }
    }

    // ------------------------------------------------------------------
    //  1. Anubis
    // ------------------------------------------------------------------

    @Test
    public void detectaLaPantallaDeAnubis() throws Exception {
        String html = "<html><head><title>Making sure you're not a bot!</title>"
                + "<script id=\"anubis_challenge\" type=\"application/json\">"
                + real().getAsJsonObject("anubis_challenge")
                + "</script></head><body>...</body></html>";
        assertTrue(Embed69Resolver.esAnubis(html));
        assertFalse(Embed69Resolver.esAnubis(
                "<html><script>var dataLink = []</script></html>"));
    }

    @Test
    public void elNonceDeAnubisCumpleLaReglaDelWorker() throws Exception {
        // El worker sha256.mjs de Anubis: con difficulty=4 exige 2 bytes a cero.
        JsonObject ch = real().getAsJsonObject("anubis_challenge")
                .getAsJsonObject("challenge");
        String randomData = ch.get("randomData").getAsString();
        int dificultad = 4;

        long nonce = Embed69Resolver.nonceAnubis(randomData, dificultad);

        byte[] h = sha256(randomData + nonce);
        assertEquals("primer byte", 0, h[0] & 0xFF);
        assertEquals("segundo byte", 0, h[1] & 0xFF);
        // Y el nonce anterior NO cumple: si no, el test no probaría nada.
        byte[] anterior = sha256(randomData + (nonce - 1));
        assertTrue("el nonce anterior no debería cumplir",
                !(anterior[0] == 0 && anterior[1] == 0));
    }

    @Test
    public void elHashDeAnubisEmpiezaPorLosBytesACero() throws Exception {
        JsonObject ch = real().getAsJsonObject("anubis_challenge")
                .getAsJsonObject("challenge");
        String randomData = ch.get("randomData").getAsString();
        long nonce = Embed69Resolver.nonceAnubis(randomData, 4);

        byte[] h = sha256(randomData + nonce);
        assertEquals("0000", hex(h).substring(0, 4));
    }

    @Test
    public void laClaveDeEnlacesEsSha256DeChallengeMasSalt() throws Exception {
        String html = "<script>const LINK_CHALLENGE = \"" + CH_REAL + "\";"
                + "const LINK_SALT = \"" + SA_REAL + "\";</script>";
        byte[] clave = Embed69Resolver.claveDeEnlaces(html);
        assertArrayEquals(sha256(CH_REAL + SA_REAL), clave);
        // 32 bytes = AES-256
        assertEquals(32, clave.length);
    }

    @Test
    public void laClaveCaeEnLosValoresPorDefectoSiElHtmlNoLosDeclara()
            throws Exception {
        byte[] clave = Embed69Resolver.claveDeEnlaces("<html>sin constantes</html>");
        assertArrayEquals(sha256(CH_REAL + SA_REAL), clave);
    }

    // ------------------------------------------------------------------
    //  2. dataLink real: estructura y descifrado
    // ------------------------------------------------------------------

    @Test
    public void elDataLinkRealTraeLosTresIdiomas() throws Exception {
        JsonArray dl = real().getAsJsonArray("dataLink");
        assertEquals(3, dl.size());
        assertEquals("LAT", dl.get(0).getAsJsonObject().get("video_language").getAsString());
        assertEquals("ESP", dl.get(1).getAsJsonObject().get("video_language").getAsString());
        assertEquals("SUB", dl.get(2).getAsJsonObject().get("video_language").getAsString());
        for (int i = 0; i < dl.size(); i++) {
            JsonArray emb = dl.get(i).getAsJsonObject().getAsJsonArray("sortedEmbeds");
            assertTrue("idioma " + i + " sin servidores", emb.size() > 0);
            for (int j = 0; j < emb.size(); j++) {
                JsonObject e = emb.get(j).getAsJsonObject();
                assertTrue("falta servername", e.has("servername"));
                assertTrue("falta link", e.has("link"));
            }
        }
    }

    @Test
    public void descifraElEnlaceRealDeVidhide() throws Exception {
        // Este es el enlace que devolvió embed69 el 13-Nov-2026 para The
        // Shawshank Redemption en LAT, servidor vidhide.
        String cifrado = real().getAsJsonArray("dataLink").get(0).getAsJsonObject()
                .getAsJsonArray("sortedEmbeds").get(2).getAsJsonObject()
                .get("link").getAsString();
        assertEquals("vidhide", real().getAsJsonArray("dataLink").get(0).getAsJsonObject()
                .getAsJsonArray("sortedEmbeds").get(2).getAsJsonObject()
                .get("servername").getAsString());

        String claro = descifrarComoElResolver(cifrado, sha256(CH_REAL + SA_REAL));

        assertNotNull(claro);
        assertTrue("debería ser una URL http: " + claro, claro.startsWith("http"));
        assertTrue("debería apuntar a morencius: " + claro, claro.contains("morencius"));
    }

    @Test
    public void descifraTodosLosEnlacesReales() throws Exception {
        byte[] clave = sha256(CH_REAL + SA_REAL);
        JsonArray dl = real().getAsJsonArray("dataLink");
        int total = 0;
        for (int i = 0; i < dl.size(); i++) {
            JsonObject bloque = dl.get(i).getAsJsonObject();
            String lang = bloque.get("video_language").getAsString();
            JsonArray emb = bloque.getAsJsonArray("sortedEmbeds");
            for (int j = 0; j < emb.size(); j++) {
                JsonObject e = emb.get(j).getAsJsonObject();
                String claro = descifrarComoElResolver(e.get("link").getAsString(), clave);
                assertNotNull(lang + "/" + e.get("servername").getAsString(), claro);
                assertTrue(lang + "/" + e.get("servername").getAsString() + " -> " + claro,
                        claro.startsWith("http"));
                total++;
            }
        }
        assertEquals(9, total);
    }

    @Test
    public void elDataLinkRealEsParseablePorOrgJson() throws Exception {
        // El resolver usa org.json, no Gson: comprobamos que el array real
        // sobrevive al round-trip por la librería de la app.
        String json = real().getAsJsonArray("dataLink").toString();
        JSONArray arr = new JSONArray(json);
        assertEquals(3, arr.length());
        JSONObject lat = arr.getJSONObject(0);
        assertEquals("LAT", lat.getString("video_language"));
        assertTrue(lat.getJSONArray("sortedEmbeds").length() >= 3);
    }

    // ------------------------------------------------------------------
    //  utilidades
    // ------------------------------------------------------------------

    /** Mismo descifrado que Embed69Resolver.descifrar: base64 → iv(16) + ct → AES-CBC. */
    private static String descifrarComoElResolver(String base64, byte[] aesKey)
            throws Exception {
        byte[] raw = Base64.getDecoder().decode(base64);
        if (raw.length <= 16) return null;
        Cipher c = Cipher.getInstance("AES/CBC/PKCS5Padding");
        c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(aesKey, 0, 32, "AES"),
                new IvParameterSpec(raw, 0, 16));
        byte[] claro = c.doFinal(raw, 16, raw.length - 16);
        return new String(claro, StandardCharsets.UTF_8).replace("`", "").trim();
    }

    private static byte[] sha256(String s) throws Exception {
        return java.security.MessageDigest.getInstance("SHA-256")
                .digest(s.getBytes(StandardCharsets.UTF_8));
    }

    private static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format("%02x", x));
        return sb.toString();
    }
}
