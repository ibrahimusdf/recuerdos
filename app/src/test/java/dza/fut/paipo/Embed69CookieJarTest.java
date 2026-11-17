package dza.fut.paipo;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * La regresión que rompió la v7.37.2: **OkHttp no guarda cookies entre
 * peticiones**.
 *
 * <p>Anubis se canjea en una petición ({@code pass-challenge}) y la cookie que
 * devuelve hay que llevarla en la siguiente. Sin {@code CookieJar}, el servidor
 * volvía a mostrar la pantalla anti-bot y el resolvedor se quedaba ciego:
 * «embed69: sin dataLink». Yo no lo vi porque mi verificación manual usaba un
 * cookie jar de Python.</p>
 *
 * <p>Este test levanta un servidor local que imita a embed69 (pantalla Anubis
 * sin cookie, página real con ella) y comprueba que {@code pasarAnubis}
 * atraviesa el muro. Si alguien vuelve a quitar el {@code CookieJar}, este test
 * falla.</p>
 */
public class Embed69CookieJarTest {

    private static final String RANDOM = "408d6ba0d5a28e3f7e930605e00ad767"
            + "c4a11eaaedf4ae4495c9be83272ede40ae77f401c59167a0d2bdb68e2788c969";

    private static final String PAGINA_REAL = "<html><script>var dataLink = "
            + "[{\"video_language\":\"LAT\",\"sortedEmbeds\":["
            + "{\"servername\":\"vidhide\",\"link\":\"AAAA\"}]}]</script></html>";

    private static final String PANTALLA_ANUBIS = "<html><head><title>Making sure"
            + " you're not a bot!</title><script id=\"anubis_challenge\""
            + " type=\"application/json\">{\"rules\":{\"algorithm\":\"fast\","
            + "\"difficulty\":4},\"challenge\":{\"id\":\"01a1d5ef-1525-7dfb-a9ac"
            + "-605e36d450c6\",\"randomData\":\"" + RANDOM + "\",\"difficulty\":4,"
            + "\"method\":\"fast\"}}</script></head><body>...</body></html>";

    private ServerSocket server;
    private Thread hilo;
    private String base;

    @Before
    public void levantarServidor() throws Exception {
        server = new ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"));
        base = "http://127.0.0.1:" + server.getLocalPort();
        hilo = new Thread(this::servir, "servidor-prueba-embed69");
        hilo.setDaemon(true);
        hilo.start();
    }

    @After
    public void tirarServidor() throws Exception {
        if (server != null) server.close();
    }

    private void servir() {
        while (!server.isClosed()) {
            try (Socket s = server.accept()) {
                atender(s);
            } catch (Exception fin) {
                return;
            }
        }
    }

    private void atender(Socket s) throws Exception {
        BufferedReader in = new BufferedReader(new InputStreamReader(
                s.getInputStream(), StandardCharsets.ISO_8859_1));
        String linea = in.readLine();
        if (linea == null) return;
        String cookie = "";
        while (true) {
            String h = in.readLine();
            if (h == null || h.isEmpty()) break;
            if (h.toLowerCase().startsWith("cookie:")) cookie = h.substring(7).trim();
        }
        String[] partes = linea.split(" ");
        String ruta = partes.length > 1 ? partes[1] : "/";
        OutputStream os = s.getOutputStream();

        if (ruta.startsWith("/.within.website/")) {
            String nonce = param(ruta, "nonce");
            String hash = param(ruta, "response");
            if (nonceValido(nonce, hash)) {
                escribir(os, "302 Found", base + "/f/tt0111161/",
                        "anubis-auth=ok; Path=/", "");
            } else {
                escribir(os, "403 Forbidden", null, null, "reto invalido");
            }
        } else if (cookie.contains("anubis-auth=")) {
            escribir(os, "200 OK", null, null, PAGINA_REAL);
        } else {
            escribir(os, "200 OK", null, null, PANTALLA_ANUBIS);
        }
        os.flush();
        s.close();
    }

    // ------------------------------------------------------------------

    @Test
    public void pasarAnubisAtraviesaElMuroGraciasAlCookieJar() throws Exception {
        String url = base + "/f/tt0111161/";

        String dentro = Embed69Resolver.pasarAnubis(PANTALLA_ANUBIS, url);

        assertTrue("debería llegar a la página real, llegó: " + recorte(dentro),
                dentro != null && dentro.contains("dataLink"));
        assertFalse("no debería seguir siendo la pantalla de Anubis",
                Embed69Resolver.esAnubis(dentro));
    }

    @Test
    public void sinCookieElServidorLocalBloquea() throws Exception {
        // Garantía de que el test de arriba prueba algo: el mismo servidor,
        // pedido a pelo (sin cookie jar), sigue bloqueando.
        okhttp3.OkHttpClient sinCookies = new okhttp3.OkHttpClient();
        okhttp3.Request req = new okhttp3.Request.Builder()
                .url(base + "/f/tt0111161/").build();
        try (okhttp3.Response r = sinCookies.newCall(req).execute()) {
            assertTrue("el servidor local debería bloquear sin cookie",
                    Embed69Resolver.esAnubis(r.body().string()));
        }
    }

    @Test
    public void elCookieJarDelResolverLlevaLasCookiesAlSiguienteRequest()
            throws Exception {
        // El servidor local exige cookie para /f/. Con el cliente real del
        // resolver (que tiene CookieJar) basta una primera pasada para que las
        // siguientes peticiones ya vayan autorizadas.
        String dentro = Embed69Resolver.pasarAnubis(PANTALLA_ANUBIS,
                base + "/f/tt0111161/");
        assertTrue(dentro.contains("dataLink"));

        // Y una segunda llamada al mismo flujo sigue llegando (la cookie sigue
        // en el jar): si el jar se hubiera vaciado, volvería Anubis.
        String otraVez = Embed69Resolver.pasarAnubis(PANTALLA_ANUBIS,
                base + "/f/tt0111161/");
        assertTrue("la cookie debería seguir en el jar", otraVez.contains("dataLink"));
    }

    // ------------------------------------------------------------------

    private boolean nonceValido(String nonce, String hash) throws Exception {
        if (nonce == null || hash == null) return false;
        byte[] h = MessageDigest.getInstance("SHA-256")
                .digest((RANDOM + nonce).getBytes(StandardCharsets.UTF_8));
        return h[0] == 0 && h[1] == 0 && hex(h).equalsIgnoreCase(hash);
    }

    private static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format("%02x", x));
        return sb.toString();
    }

    private static String param(String ruta, String k) {
        int i = ruta.indexOf('?');
        if (i < 0) return null;
        for (String par : ruta.substring(i + 1).split("&")) {
            String[] kv = par.split("=", 2);
            if (kv.length == 2 && kv[0].equals(k)) return kv[1];
        }
        return null;
    }

    private static void escribir(OutputStream os, String estado, String location,
                                 String setCookie, String cuerpo) throws Exception {
        byte[] b = cuerpo.getBytes(StandardCharsets.UTF_8);
        StringBuilder h = new StringBuilder("HTTP/1.1 ").append(estado).append("\r\n");
        if (location != null) h.append("Location: ").append(location).append("\r\n");
        if (setCookie != null) h.append("Set-Cookie: ").append(setCookie).append("\r\n");
        h.append("Content-Type: text/html; charset=utf-8\r\n");
        h.append("Content-Length: ").append(b.length).append("\r\n");
        h.append("Connection: close\r\n\r\n");
        os.write(h.toString().getBytes(StandardCharsets.ISO_8859_1));
        os.write(b);
    }

    private static String recorte(String s) {
        return s == null ? "null" : s.substring(0, Math.min(120, s.length()));
    }
}
