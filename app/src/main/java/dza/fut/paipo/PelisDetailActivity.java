package dza.fut.paipo;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Detalle de una película o serie, con acceso a la reproducción.
 *
 * Flujo de reproducción:
 *   1) La lista de servidores se construye con los ids que el catálogo ya trae
 *      (PelisItem.playId): embed69 para películas, y vidsrc para películas y
 *      episodios. No se le pide nada a ningún sitio (PelisServidores).
 *   2) Cada embed va a su resolver: Embed69Resolver (PoW + AES-CBC, da el .m3u8
 *      directamente) o PelisStreamResolver, que hace extracción profunda de
 *      iframes (OkHttp recursivo + WebView interna) para localizar el .m3u8.
 *   3) Si la extracción falla, se abre el reproductor nativo en modo WebView
 *      cargando el propio embed como "url_iframe_inicial", de forma que el
 *      usuario vea el player real en pantalla completa.
 */
public class PelisDetailActivity extends AppCompatActivity {

    private static final String TAG = "PelisDetalle";

    public static final String EXTRA_ITEM = "pelis_item";

    private PelisItem item;
    private PelisEpisodioAdapter episodioAdapter;

    private ImageView imgBackdrop, imgPoster;
    private TextView txtTitulo, txtMeta, txtTipo, txtSinopsis, txtEstado;
    private Button btnReproducir;
    /** Momento en que arrancó la búsqueda del mejor servidor (cadena). */
    private long inicioCadena = 0L;
    /**
     * Número de cadena en curso. Al cambiar de película se incrementa, así una
     * resolución VIEJA que termine tarde no puede abrir el stream anterior y
     * pisar la película nueva.
     */
    private int idCadena = 0;
    private ProgressBar progress;
    private LinearLayout contenedorEpisodios;
    private Spinner spinnerTemporadas;
    private RecyclerView recyclerEpisodios;

    private final Map<String, List<PelisItem.Episodio>> porTemporada = new LinkedHashMap<>();
    private final List<String> temporadas = new ArrayList<>();
    private String temporadaActual = "";

    /** Pool para resolución profunda de streams (OkHttp + WebView interna). */
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pelis_detalle);

        item = (PelisItem) getIntent().getSerializableExtra(EXTRA_ITEM);
        if (item == null) {
            Toast.makeText(this, "No se pudo abrir el título", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        imgBackdrop = findViewById(R.id.imgBackdropDetalle);
        imgPoster = findViewById(R.id.imgPosterDetalle);
        txtTitulo = findViewById(R.id.txtTituloDetalle);
        txtMeta = findViewById(R.id.txtMetaDetalle);
        txtTipo = findViewById(R.id.txtTipoDetalle);
        txtSinopsis = findViewById(R.id.txtSinopsisDetalle);
        txtEstado = findViewById(R.id.txtEstadoDetalle);
        btnReproducir = findViewById(R.id.btnReproducirDetalle);
        progress = findViewById(R.id.progressDetalle);
        contenedorEpisodios = findViewById(R.id.contenedorEpisodios);
        spinnerTemporadas = findViewById(R.id.spinnerTemporadas);
        recyclerEpisodios = findViewById(R.id.recyclerEpisodios);

        pintarCabecera();
        txtEstado.setText("Pulsa Reproducir para resolver el servidor…");

        episodioAdapter = new PelisEpisodioAdapter(this, this::reproducirEpisodio);
        recyclerEpisodios.setLayoutManager(new LinearLayoutManager(this));
        recyclerEpisodios.setAdapter(episodioAdapter);

        btnReproducir.setOnClickListener(v -> reproducirPrincipal());

        if (item.esSerie()) {
            contenedorEpisodios.setVisibility(View.VISIBLE);
            cargarEpisodios();
        }

        cargarDetalle();
    }

    private void pintarCabecera() {
        txtTitulo.setText(item.titulo);
        txtMeta.setText(item.subtitulo());
        String tipo = item.esSerie() ? "Serie" : "Película";
        String generos = item.generosTexto();
        txtTipo.setText(generos.isEmpty() ? tipo : tipo + " · " + generos);

        String fondo = item.backdrop.isEmpty() ? item.poster : item.backdrop;
        Glide.with(this).load(fondo).centerCrop().into(imgBackdrop);
        Glide.with(this)
                .load(item.poster.isEmpty() ? item.backdrop : item.poster)
                .placeholder(R.drawable.bg_pelis_poster_placeholder)
                .error(R.drawable.bg_pelis_poster_placeholder)
                .centerCrop()
                .into(imgPoster);

        if (!item.sinopsis.isEmpty()) txtSinopsis.setText(item.sinopsis);
    }

    // ─────────────────────────── CARGA DE DATOS ───────────────────────────

    private void cargarDetalle() {
        progress.setVisibility(View.VISIBLE);

        PelisApi.detalle(item, new PelisApi.Callback<PelisItem>() {
            @Override
            public void onOk(PelisItem detalle) {
                progress.setVisibility(View.GONE);
                if (detalle == null || isFinishing()) return;
                // Refresca sinopsis/póster si el catálogo no los traía completos.
                if (!detalle.sinopsis.isEmpty()) {
                    item.sinopsis = detalle.sinopsis;
                    txtSinopsis.setText(detalle.sinopsis);
                }
                if (!detalle.backdrop.isEmpty()) {
                    item.backdrop = detalle.backdrop;
                    Glide.with(PelisDetailActivity.this).load(detalle.backdrop).centerCrop().into(imgBackdrop);
                }
            }

            @Override
            public void onError(String mensaje) {
                progress.setVisibility(View.GONE);
                Log.w(TAG, "No se pudo cargar el detalle: " + mensaje);
            }
        });
    }

    private void cargarEpisodios() {
        porTemporada.clear();
        temporadas.clear();

        for (PelisItem.Episodio ep : item.episodios) {
            String t = ep.temporada == null || ep.temporada.isEmpty() ? "1" : ep.temporada;
            List<PelisItem.Episodio> lista = porTemporada.get(t);
            if (lista == null) {
                lista = new ArrayList<>();
                porTemporada.put(t, lista);
                temporadas.add(t);
            }
            lista.add(ep);
        }

        if (temporadas.isEmpty()) {
            contenedorEpisodios.setVisibility(View.GONE);
            return;
        }

        ArrayAdapter<String> adaptador = new ArrayAdapter<>(this,
                R.layout.item_spinner_temporada, temporadas);
        adaptador.setDropDownViewResource(R.layout.item_spinner_temporada_dropdown);
        spinnerTemporadas.setAdapter(adaptador);

        spinnerTemporadas.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                temporadaActual = temporadas.get(position);
                refrescarEpisodios();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) { }
        });

        temporadaActual = temporadas.get(0);
        refrescarEpisodios();
    }

    private void refrescarEpisodios() {
        List<PelisItem.Episodio> lista = porTemporada.get(temporadaActual);
        episodioAdapter.mostrar(lista != null ? lista : new ArrayList<>());
    }

    // ─────────────────────────── REPRODUCCIÓN ───────────────────────────

    private void reproducirPrincipal() {
        if (item.esSerie()) {
            List<PelisItem.Episodio> lista = porTemporada.get(temporadaActual);
            if (lista != null && !lista.isEmpty()) {
                reproducirEpisodio(lista.get(0));
                return;
            }
        }
        buscarServidoresRespaldo(null, null);
    }

    private void reproducirEpisodio(PelisItem.Episodio episodio) {
        String se = episodio.temporada == null ? "1" : episodio.temporada;
        String ep = episodio.episodio == null ? "1" : episodio.episodio;
        buscarServidoresRespaldo(se, ep);
    }

    private void buscarServidoresRespaldo(final String se, final String numeroEp) {
        btnReproducir.setEnabled(false);
        txtEstado.setText("Preparando servidores…");
        // Los servidores se construyen con los ids que el catálogo ya trae
        // (playId): no hay que pedirle nada a ningún sitio, así que la lista
        // está disponible al instante.
        final PelisServidores.Servidores servidores =
                PelisServidores.servidoresDe(item, se, numeroEp);
        btnReproducir.setEnabled(true);
        if (!servidores.hayServidores()) {
            txtEstado.setText("Este título no tiene identificadores para los reproductores.");
            Log.w(TAG, "Sin servidores: playId=" + (item == null ? "?" : item.playId));
            return;
        }
        txtEstado.setText(servidores.opciones.size() + " servidor(es) disponible(s)");
        elegirServidor(servidores);
    }

    /** 0 = servidor con audio en español, 3 = solo copias en inglés. */
    private static int prioridad(String nombre, String url) {
        String k = (nombre == null ? "" : nombre).toLowerCase(Locale.US);
        String u = (url == null ? "" : url).toLowerCase(Locale.US);
        if (u.contains("vimeus") || k.equals("alt")) return 0;
        if (u.contains("embed69") || k.equals("alt1")) return 1;
        if (u.contains("vsembed") || k.equals("alt3")) return 3;
        return 2;
    }

    private void elegirServidor(final PelisServidores.Servidores servidores) {
        final List<String> nombres = new ArrayList<>(servidores.opciones.keySet());
        final List<String> urls = new ArrayList<>(servidores.opciones.values());

        if (urls.isEmpty()) {
            txtEstado.setText("Sin servidores disponibles");
            return;
        }
        if (urls.size() == 1) {
            resolverYReproducir(urls.get(0));
            return;
        }
        // Varios servidores: se prueban EN EL ORDEN EN QUE VIENEN (embed69
        // primero, que es el que da el .m3u8 en español) y gana el primero que
        // realmente resuelve. Si ninguno puede, se muestra el diálogo.
        final List<Integer> orden = new ArrayList<>();
        for (int i = 0; i < nombres.size(); i++) orden.add(i);
        final int miCadena = ++idCadena;
        inicioCadena = System.currentTimeMillis();
        probarServidor(nombres, urls, etiquetas(servidores, nombres), orden, 0, miCadena);
    }

    /** Cada servidor se llama "Idioma · Nombre": la etiqueta es el nombre tal cual. */
    private List<String> etiquetas(final PelisServidores.Servidores servidores,
                                   final List<String> nombres) {
        List<String> salida = new ArrayList<>();
        for (String n : nombres) salida.add(PelisServidores.etiqueta(n));
        return salida;
    }

    private void probarServidor(final List<String> nombres, final List<String> urls,
                                final List<String> etiquetas, final List<Integer> orden,
                                final int idx, final int miCadena) {
        if (miCadena != idCadena) {
            Log.d(TAG, "Cadena " + miCadena + " caducada (ya se eligió otro título); se descarta");
            return;
        }
        if (idx >= orden.size()) {
            btnReproducir.setEnabled(true);
            txtEstado.setText("Ningún servidor respondió. Elige uno:");
            mostrarDialogoServidores(nombres, urls, etiquetas);
            return;
        }
        final int i = orden.get(idx);
        final String url = urls.get(i);
        final String nombre = nombres.get(i);
        final String etiqueta = etiquetas.get(i);

        // Último recurso: el único servidor que queda es el que solo tiene
        // copias en inglés. Se avisa al usuario antes de reproducirlo.
        if (prioridad(nombre, url) == 3) {
            txtEstado.setText("⚠️ Sin copia en español; se reproducirá en inglés");
            Toast.makeText(this,
                    "⚠️ Este título solo está disponible en INGLÉS\n"
                            + "(sin audio ni subtítulos en español)",
                    Toast.LENGTH_LONG).show();
        }

        // Presupuesto global: si llevamos demasiado tiempo buscando español,
        // pasamos al último servidor de la lista para que el usuario no espere.
        if (idx > 0 && System.currentTimeMillis() - inicioCadena > 60_000) {
            Log.w(TAG, "Presupuesto de la cadena agotado; abriendo el embed directamente");
            txtEstado.setText("Abriendo player del servidor…");
            abrirReproductorConEmbed(urls.get(orden.get(orden.size() - 1)));
            return;
        }

        btnReproducir.setEnabled(false);
        txtEstado.setText("Probando " + etiqueta + "…");
        Log.d(TAG, "[servidor " + (idx + 1) + "/" + orden.size() + "] " + nombre + " -> " + url);

        executorService.execute(() -> {
            PelisStreamResolver.StreamResult sr = null;
            try {
                sr = resolverConLimite(url, 25_000);
            } catch (Throwable t) {
                Log.w(TAG, "Falló " + nombre + ": " + t.getMessage());
            }
            final PelisStreamResolver.StreamResult finalSr = sr;
            runOnUiThread(() -> {
                if (isFinishing()) return;
                if (finalSr != null && finalSr.m3u8Url != null && !finalSr.m3u8Url.isEmpty()) {
                    Log.d(TAG, "Servidor OK (" + nombre + "): " + finalSr.m3u8Url);
                    btnReproducir.setEnabled(true);
                    M3u8PelisResolver.M3u8Result listo = new M3u8PelisResolver.M3u8Result();
                    listo.m3u8Url = finalSr.m3u8Url;
                    listo.cookies = finalSr.cookies;
                    listo.referer = finalSr.referer;
                    listo.origin = finalSr.origin;
                    listo.headers = finalSr.headers;
                    if (miCadena != idCadena) {
                        Log.d(TAG, "Resultado descartado: llegó tarde (cadena " + miCadena + ")");
                        return;
                    }
                    txtEstado.setText("▶ " + etiqueta);
                    abrirReproductorConStream(listo);
                } else {
                    probarServidor(nombres, urls, etiquetas, orden, idx + 1, miCadena);
                }
            });
        });
    }

    private void mostrarDialogoServidores(final List<String> nombres, final List<String> urls,
                                          final List<String> etiquetas) {
        String[] items = new String[nombres.size()];
        for (int i = 0; i < nombres.size(); i++) items[i] = etiquetas.get(i);
        new AlertDialog.Builder(this)
                .setTitle("Elige un servidor")
                .setItems(items, (d, which) -> resolverYReproducir(urls.get(which)))
                .setNegativeButton("Cancelar", null)
                .show();
    }

    /** Cada URL va al resolver que le corresponde. */
    private PelisStreamResolver.StreamResult resolverServidorUrl(String url) throws Exception {
        if (VimeusResolver.isVimeus(url)) {
            Log.d(TAG, "Resolviendo con VimeusResolver (pista de audio Español)");
            return VimeusResolver.resolve(getApplicationContext(), url);
        }
        if (Embed69Resolver.isEmbed69(url)) {
            Log.d(TAG, "Resolviendo con Embed69Resolver (copia LAT/ESP)");
            return Embed69Resolver.resolve(getApplicationContext(), url);
        }
        return PelisStreamResolver.resolveSynchronously(getApplicationContext(), url);
    }

    /**
     * Ningún servidor puede colgarse más de <code>millis</code> ms.
     *
     * Se hacen DOS intentos: estos servidores devuelven errores puntuales
     * (rate-limit, TLS) y sin reintento se acabaría cayendo al servidor en
     * inglés por un fallo que no es real.
     */
    private PelisStreamResolver.StreamResult resolverConLimite(final String url, long millis)
            throws Exception {
        Exception ultima = null;
        for (int intento = 1; intento <= 2; intento++) {
            ExecutorService tmp = Executors.newSingleThreadExecutor();
            try {
                Future<PelisStreamResolver.StreamResult> f = tmp.submit(() -> resolverServidorUrl(url));
                return f.get(millis, TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                ultima = e;
                Log.w(TAG, "Intento " + intento + " falló (" + e.getMessage() + "); reintentando…");
                try { Thread.sleep(700); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
            } finally {
                tmp.shutdownNow();
            }
        }
        throw ultima != null ? ultima : new Exception("servidor no disponible");
    }

    /**
     * Toma la URL de un servidor (normalmente un iframe embed) y la resuelve
     * para obtener el m3u8 real antes de abrir el reproductor nativo.
     *
     * Si la resolución falla (players protegidos, cross-origin...), se abre el
     * reproductor nativo en modo WebView cargando el propio embed.
     */
    private void resolverYReproducir(final String url) {
        final int miCadena = ++idCadena;
        btnReproducir.setEnabled(false);
        txtEstado.setText("Resolviendo stream del servidor…");
        Log.d(TAG, "Resolviendo m3u8 desde servidor: " + url);

        executorService.execute(() -> {
            PelisStreamResolver.StreamResult sr = null;
            try {
                sr = resolverConLimite(url, 25_000);
            } catch (Throwable t) {
                Log.e(TAG, "El resolver lanzó excepción: " + t.getMessage(), t);
            }

            final PelisStreamResolver.StreamResult finalSr = sr;
            runOnUiThread(() -> {
                if (isFinishing()) return;
                if (miCadena != idCadena) {
                    Log.d(TAG, "Resolución manual descartada (cadena " + miCadena + ")");
                    return;
                }
                btnReproducir.setEnabled(true);
                if (finalSr != null && finalSr.m3u8Url != null && !finalSr.m3u8Url.isEmpty()) {
                    Log.d(TAG, "m3u8 resuelto: " + finalSr.m3u8Url);
                    M3u8PelisResolver.M3u8Result listo = new M3u8PelisResolver.M3u8Result();
                    listo.m3u8Url = finalSr.m3u8Url;
                    listo.cookies = finalSr.cookies;
                    listo.referer = finalSr.referer;
                    listo.origin = finalSr.origin;
                    listo.headers = finalSr.headers;
                    txtEstado.setText("✅ Stream listo");
                    abrirReproductorConStream(listo);
                } else {
                    Log.w(TAG, "No se pudo extraer el m3u8; abriendo embed en el reproductor: " + url);
                    txtEstado.setText("Abriendo player del servidor…");
                    abrirReproductorConEmbed(url);
                }
            });
        });
    }

    /** Abre el reproductor con un m3u8 ya resuelto, evitando el WebView. */
    @SuppressWarnings("UnstableApiUsage")
    private void abrirReproductorConStream(M3u8PelisResolver.M3u8Result resultado) {
        Intent intent = new Intent(this, PelisPlayerActivity.class);
        intent.putExtra("stream_url", resultado.m3u8Url);
        intent.putExtra("stream_cookies", resultado.cookies);
        intent.putExtra("stream_referer", resultado.referer);
        intent.putExtra("stream_origin", resultado.origin);
        startActivity(intent);
    }

    /**
     * Fallback cuando no se pudo extraer el m3u8: abre el reproductor nativo
     * en modo WebView cargando el embed del servidor. PlayerActivity ya
     * soporta esto vía el extra "url_iframe_inicial".
     */
    @SuppressWarnings("UnstableApiUsage")
    private void abrirReproductorConEmbed(String embedUrl) {
        Intent intent = new Intent(this, PelisPlayerActivity.class);
        intent.putExtra("url_iframe_inicial", embedUrl);
        // Referer del propio embed para que el servidor no lo rechace como
        // hotlink: la página del player comprueba el Referer.
        intent.putExtra("stream_referer", PelisServidores.refererDe(embedUrl));
        startActivity(intent);
    }

    @Override
    protected void onDestroy() {
        // Si quedaba algún WebView de resolución cargando un embed, se destruye:
        // su reproductor arranca solo y el audio se quedaba sonando.
        try { PelisStreamResolver.destruirWebViewsActivos(); } catch (Throwable ignored) { }
        try { StreamResolver.destruirWebViewsActivos(); } catch (Throwable ignored) { }
        super.onDestroy();
        if (!executorService.isShutdown()) {
            executorService.shutdownNow();
        }
    }
}