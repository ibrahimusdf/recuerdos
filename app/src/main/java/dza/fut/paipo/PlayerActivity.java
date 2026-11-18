package dza.fut.paipo;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.app.PictureInPictureParams;
import android.app.RemoteAction;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.graphics.drawable.Icon;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.Rational;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.net.http.SslError;
import android.webkit.SslErrorHandler;
import android.webkit.CookieManager;
import android.widget.ImageButton;
import android.widget.ScrollView;
import android.widget.Toast;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.Tracks;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.datasource.HttpDataSource;
import androidx.media3.datasource.TransferListener;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

@UnstableApi
public class PlayerActivity extends AppCompatActivity {

    /** Logcat. Faltaba y el módulo no compilaba (Log.w/e la usaban en 3 sitios). */
    private static final String TAG = "PlayerActivity";

    /** Pares (de,a) del "swap" del enlace Drama descifrado; null si no hay enlace Drama. */
    private java.util.List<String[]> dramaSwapPares;
    private ExoPlayer player;
    private PlayerView playerView;
    private WebView webViewFallback;
    private String urlIframeInicial;
    private View fallbackCustomView;
    private WebChromeClient.CustomViewCallback fallbackCustomCallback;

    // Stream directo recibido del detalle (sin pasar por el resolver/WebView)
    private String streamDirecto;
    private String streamCookies;
    private String streamReferer;
    private String streamOrigin;
    /** Cabeceras extra que pidió el canal (o la fuente): se mezclan en resolverStreamDirecto. */
    private String streamCabeceras;

    private DefaultHttpDataSource.Factory dataSourceFactory;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private int curaCiclos;   // v3.2.21: ciclos «aun no llega» seguidos (escape a los 3)
    @androidx.annotation.Nullable private static java.lang.ref.WeakReference<PlayerActivity> vivoCura;
    private ExecutorService executorService;

    private TextView tvDebugLog;
    private ScrollView scrollDebugLog;
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault());

    private boolean streamReady = false;
    /** v7.36.1: true cuando ya se avisó de que esta opción no tiene vídeo
     *  decodificable, para no saltar en bucle. Se limpia en cada carga. */
    private boolean sinVideoYaAvisado = false;
    private android.view.View filaServidores;
    private android.widget.LinearLayout contenedorServidores;
    private final java.util.List<String[]> opcionesServidor = new java.util.ArrayList<>();
    private final java.util.Set<Integer> opcionesFallidas = new java.util.HashSet<>();
    private int opcionActual = -1;
    // v3.2.67: retrato+paisaje estilo dzasports (cabecera, tira de zapeo, girar).
    private boolean esHorizontal = true;
    private boolean controlesYaAtados;
    private android.view.View cabeceraPlayer;
    private android.widget.TextView tvTituloPlayer;
    private String tituloCanalActual;
    private android.view.View tiraCanalesPlayer;
    private android.widget.HorizontalScrollView scrollCanalesPlayer;
    private android.widget.LinearLayout contenedorCanalesPlayer;

    // ── v7.5: la tira de zapeo se construye por VENTANAS, no entera ──
    /**
     * Tarjetas por tanda. La tira de zapeo recibe {@link ZapeoCanales#canales}, que
     * es la lista VISIBLE de la rejilla: con el catálogo completo eso son decenas de
     * miles de canales. Antes se construía una tarjeta (LinearLayout + ImageView +
     * TextView) y se lanzaba un {@code Glide.with(this)} por CADA canal, en el hilo
     * principal, dentro de onCreate. El logcat de un teléfono real lo enseñó:
     * el proceso se quedaba mudo ~10 s justo después de montar el player y el
     * sistema declaraba ANR. Ahora se construye un ventana alrededor del canal
     * actual, en segundo plano, y se alarga sola si el usuario llega al borde.
     */
    static final int ZAPEO_TANDA = 40;
    /** Índice real (en ZapeoCanales.canales) de cada tarjeta ya construida. */
    private final java.util.List<Integer> tiraIndices = new java.util.ArrayList<>();
    private int tiraDesde = 0;
    private int tiraHasta = 0;
    private boolean tiraConstruyendo = false;
    private boolean tiraScrollAtado = false;
    private android.widget.ImageButton btnPantalla;
    private android.widget.TextView tvTiempoActual;
    private android.widget.TextView tvTiempoTotal;
    private boolean webViewOcultoYaProbado = false;   // el WebView oculto se prueba una sola vez por título
    private boolean isResolving = false;
    /** Momento en que arrancó la resolución en curso (para el "perro guardián"). */
    private long resolucionInicio = 0L;
    private int errorRefreshCount = 0;
    private static final int MAX_REFRESH_RETRIES = 5;
    private long lastRefreshTime = 0;
    private static final long MIN_REFRESH_INTERVAL_MS = 2000;

    // --- Controles personalizados ---
    private View controlsOverlay;
    private android.view.View centerControls;            // spec: controles del centro (paisaje)
    private android.widget.ProgressBar cargandoPlayer;   // spec: cargando centrado
    private int mensajeErrorPlayerResId;
    private boolean cargaEnCurso;
    private final Runnable ocultarCargandoRun = () -> {
        if (!cargaEnCurso) return;
        if (cargandoPlayer != null) cargandoPlayer.setVisibility(View.GONE);
        cargaEnCurso = false;
        showControls();
    };
    private ImageButton btnPlayPause, btnRewind, btnForward, btnVolume, btnRefresh, btnPip, btnCerrar;
    private float volumenAntesDeSilencio = 1f;
    private boolean silenciado;
    // v3.2.31: chip de calidad + estado del play nativo (glasbi->glbr->advanced)
    private android.widget.TextView chipCalidad;
    private volatile String streamOpcionesJson;

    /** v7.38.0 — la url y el backup CRUDOS del canal, para no perder ningún
     *  servidor (antes solo se guardaba la lista ya cocinada). */
    private volatile String streamUrl = null;
    private volatile String streamBackup = null;

    /** Servidores ya probados en este canal (para no repetir y no rendirse antes). */
    /**
     * v7.38.4 — al abrir un canal NUEVO, la lista de «ya probados» se vacía: si no,
     * un canal que se había agotado antes llegaba a este con todo marcado y no se
     * probaba ni un servidor.
     */
    private String canalCuyosServidoresSeEstanProbando = null;

    private final java.util.List<String> servidoresProbados =
            java.util.Collections.synchronizedList(new java.util.ArrayList<>());
    private volatile String calidadElegida;
    private volatile String marcadorForzado;
    private volatile String agenteForzado;
    private volatile String canalActual;
    private SeekBar seekBar;
    private android.view.View filaProgreso;              // barra + tiempos (solo si se puede buscar)
    private boolean isSeeking = false;
    private boolean controlesVisibles;
    private final Handler seekHandler = new Handler(Looper.getMainLooper());
    private Runnable seekRunnable;

    // --- PIP ---
    private boolean isInPipMode = false;
    private boolean entrandoEnPip = false;   // true entre enterPictureInPictureMode() y onPictureInPictureModeChanged()
    private boolean cerrandoDesdePip = false;
    private boolean enPrimerPlano = true;   // la app se ve (onResume) o esta detras (onStop)
    // v7.14: true mientras el player lanza EL MISMO el captador headless. Lanzar
    // nuestra propia actividad contaba como «el usuario se fue» y onUserLeaveHint
    // metia el reproductor en PiP: con el clip de relleno sonando, el usuario veia
    // la ventanita en vez del player («cuando sale relleno… sale del player»).
    private boolean lanzandoCaptador = false;
    // v3.2.15: la cura headless es POR CANAL y con cooldown de 100 s (el boolean de
    // v3.2.13 bloqueaba reintentos legitimos y sus ramas eran mudas en el log), y el
    // player SE AUTO-REINTENTA a los 70 s: si la ENTREGA ya curo la fila, reproduce
    // sin que nadie toque nada.
    private String canalCurado;              // pid cuya cura esta en vuelo (null: ninguna)
    private static final java.util.concurrent.ConcurrentHashMap<String, Long> curaTsMap =
            new java.util.concurrent.ConcurrentHashMap<>(); // v3.2.16: cooldown POR PROCESO
    private boolean curaReintento;             // re-resolucion silenciosa tras la cura
    private String dramaLiveEnJuego;          // fuente dramalive:// actual del player
    private final Runnable curaReintentoRun = new Runnable() {
        @Override public void run() { reintentaTrasCura(); }
    };
    private int intentosTraerAlFrente = 0;
    private BroadcastReceiver pipReceiver;

    // --- Un solo reproductor vivo a la vez + foco de audio ---
    /** Instancia del reproductor abierta ahora mismo (null = ninguna). */
    private static PlayerActivity instanciaActiva = null;
    private AudioManager audioManager = null;
    private AudioFocusRequest peticionFoco = null;
    private final AudioManager.OnAudioFocusChangeListener focoListener = cambio -> {
        if (cambio == AudioManager.AUDIOFOCUS_LOSS
                || cambio == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
            // Otra app (u otro reproductor) quiere el audio: nosotros callamos.
            if (player != null && !isInPipMode) {
                try { player.pause(); } catch (Exception ignored) { }
            }
        }
    };
    /** Vistas ocultadas al entrar en PiP -> se restauran al salir. */
    private final Map<View, Integer> visibilidadPrePip = new LinkedHashMap<>();

    private static final String ACTION_PIP_CONTROL = "dza.fut.paipo.PIP_CONTROL";
    private static final String EXTRA_PIP_ACTION = "pip_action";
    private static final int PIP_ACTION_PLAY_PAUSE = 1;
    private static final int PIP_ACTION_FULLSCREEN = 2;   // salir del PiP y volver a pantalla completa
    private static final String ACTION_SALIR_PIP = "dza.fut.paipo.SALIR_PIP";

    // Auto-ocultar controles
    private static final long CONTROLS_TIMEOUT = 5000; // 5 segundos (spec: auto-ocultar a los 5 s)
    private static final long CONTROLS_FADE_DURATION_MS = 180;
    private final Runnable hideControlsRunnable = () -> {
        // OJO: esto corre en el campo, antes de que existan los campos declarados
        // mas abajo (embeddedEnVentana). Se consulta por metodo para no adelantar
        // la referencia (lo cazo el compilador: «illegal forward reference»).
        boolean reproduciendo = hayImagenAhora();
        if (!isInPipMode && controlsOverlay != null
                && reproduciendo && mensajeErrorPlayerResId == 0) {
            hideControlsImmediately();
        }
    };

    /** ¿Es una peticion de VIDEO de verdad (no de pagina)? Puro. */
    public static boolean esSegmentoDeVideo(String url) {
        if (url == null) return false;
        String u = url.toLowerCase(java.util.Locale.ROOT);
        int corte = u.indexOf('?');
        if (corte > 0) u = u.substring(0, corte);
        return u.endsWith(".ts") || u.endsWith(".m4s") || u.endsWith(".mp4")
                || u.endsWith(".m4v") || u.endsWith(".aac") || u.endsWith(".m3u8")
                || u.contains(".ts/") || u.contains("tracks-v1");
    }

    /** v7.37.19: el embed ya esta pidiendo video: hay canal, no cartel. */
    private void marcarVideoDelEmbedVisto() {
        peticionesDeVideo++;
        if (videoDeEmbedAvanza) return;
        videoDeEmbedAvanza = true;
        log("✅ plan B: el embed está pidiendo vídeo (segmentos .ts)");
        ocultarCargando();
        // v7.38.0: si además se cazó el playlist de su reproductor, se pasa a
        // ExoPlayer (imagen limpia, sin los anuncios del embed).
        if (playlistCazado != null) mainHandler.postDelayed(this::pasarANativoSiSePuede, 2500);
    }

    /** ¿Hay imagen ahora mismo? El embed (plan B) o ExoPlayer reproduciendo. */
    private boolean hayImagenAhora() {
        if (embeddedEnVentana) return true;
        return player != null && player.isPlaying();
    }

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // v7.19: precalfentar los dos hosts del sitio AQUI, antes de inflar nada, para
        // que el DNS+TCP+TLS se pague mientras Android crea la Activity. Medido en el
        // logcat del usuario: los dos POST en serie costaban 3,17 s de los 5,31 s que
        // tardaba un canal en echar a andar. Es best-effort; si falla no pasa nada.
        try { DramaSesion.precalentar(FuenteCanales.sesionDrama(this)); }
        catch (Throwable ignore) { }
        esHorizontal = false;
        // v7.38.2 — banderas de PiP limpias al nacer. Si la Activity anterior murió
        // dentro del PiP, la nueva podía heredar el «estoy en PiP» y cerrarse con
        // finishAndRemoveTask() nada más abrir (eso se ve como «vuelve a canales»).
        isInPipMode = false;
        entrandoEnPip = false;
        cerrandoDesdePip = false;
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        setContentView(R.layout.activity_player_portrait);

        // ATRÁS (gesto, botón y "predictive back" de Android moderno):
        // se registra el callback moderno para que nunca se nos escape.
        try {
            getOnBackPressedDispatcher().addCallback(this,
                    new androidx.activity.OnBackPressedCallback(true) {
                        @Override
                        public void handleOnBackPressed() {
                            cerrarReproductor();
                        }
                    });
        } catch (Throwable t) {
            Log.w(TAG, "No se pudo registrar el callback de atrás: " + t.getMessage());
        }

        // SOLO UN REPRODUCTOR: si quedaba otro abierto (por ejemplo un canal y
        // después una película), se cierra YA para que no se pisen los audios.
        // v7.38.1 — FALLO REAL («click en National Geographic Abu Dhabi y sale y
        // regresa a canales»): aquí se llamaba a cerrarReproductor(), que acaba en
        // finish()/finishAndRemoveTask(). Según Android, finish() se aplica a la
        // Activity que se está creando, o sea a la NUEVA: el reproductor se abría y
        // se cerraba solo. Ahora se sueltan los recursos del viejo y ya.
        if (instanciaActiva != null && instanciaActiva != this) {
            log("⏹ Soltando los recursos del reproductor anterior");
            try {
                instanciaActiva.liberarPlayer();
                instanciaActiva.instanciaActiva = null;
            } catch (Throwable t) {
                Log.w(TAG, "no se pudo soltar el player anterior: " + t.getMessage());
            }
        }
        instanciaActiva = this;

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (getSupportActionBar() != null) getSupportActionBar().hide();

        playerView = findViewById(R.id.playerView);
        configurarEscaladoVideo();
        playerView.setUseController(false);



        urlIframeInicial = getIntent().getStringExtra("url_iframe_inicial");
        streamDirecto = getIntent().getStringExtra("stream_url");
        streamCookies = getIntent().getStringExtra("stream_cookies");
        streamReferer = getIntent().getStringExtra("stream_referer");
        streamOrigin = getIntent().getStringExtra("stream_origin");
        streamCabeceras = getIntent().getStringExtra("stream_headers");
        // "Canal desde Drama": si lo que llegó es un enlace cifrado `player.html#<hash>`,
        // la URL real va adentro. Se intenta aqui ademas de en la importacion para que
        // valga tambien un intent directo (PiP, "abrir con…") sin re-importar la lista.
        String fuenteDrama = !vacioTexto(streamDirecto) ? streamDirecto : urlIframeInicial;
        // Canales del captador in-app (v3.2.3): url esquema dramalive://<parametros> — la
        // url real del stream NO viene en el catalogo (el item del sitio trae url:"1");
        // se resuelve contra el redirector glbr del sitio en el momento de reproducir,
        // con la sesion que dejo el captador. Fresca siempre, nunca podrida.
        final String dramaLive = fuenteDrama != null && fuenteDrama.startsWith("dramalive://") ? fuenteDrama : null;
        dramaLiveEnJuego = dramaLive;   // v3.2.15: el auto-reintento necesita la fuente actual
        if (dramaLive != null) {
            streamDirecto = null;
            urlIframeInicial = null;
        }
        EnlaceDrama.Contenido drama = dramaLive == null
                ? EnlaceDrama.abrir(fuenteDrama, FuenteCanales.dramaKey(this)) : null;
        if (drama != null) {
            streamDirecto = drama.url;
            dramaSwapPares = EnlaceDrama.paresSwap(drama.texto);
            String swapInfo = dramaSwapPares.isEmpty() ? "sin swap"
                    : dramaSwapPares.get(0)[0] + "'" + dramaSwapPares.get(0)[1] + "'";
            log("Enlace Drama descifrado (clave '" + drama.clave + "', " + swapInfo + ") -> " + drama.url);
        }
        log("URL Inicial: " + urlIframeInicial);
        if (streamDirecto != null && !streamDirecto.isEmpty()) {
            log("Stream directo recibido: " + streamDirecto);
        }

        executorService = Executors.newSingleThreadExecutor();

        configurarSSLInseguroUniversal();
        hideSystemUI();
        setupMedia3Player();
        setupCustomControls();
        atarOverlaysPlayer();
        leerOpcionesDeIntent(getIntent());
        tituloCanalActual = getIntent().getStringExtra("canal_titulo");
        prepararZapeoAgenda();
        pintarTituloPlayer();
        pintarChipsServidores();
        pintarTiraCanales();
        setupWebViewFallback();
        registerPipReceiver();
        aplicarParamsPip();          // Android 12+: deja listo el auto-entrar en PiP
        if (dramaLive != null) {
            resolverDramaLive(dramaLive);
        } else if (streamDirecto != null && !streamDirecto.isEmpty()) {
            resolverStreamDirecto(streamDirecto, streamCookies, streamReferer, streamOrigin);
        } else {
            resolverStreamEnSegundoPlano();
        }
    }

    /** dramalive:// → glbr (red, hilo secundario) → stream real con el User-Agent que exige. */
    /** v3.2.15: a los 70 s de lanzada la cura el player se reintenta SOLO: si la
     * ENTREGA del captador ya dejo la fila con https real (cosecha + fila sintetica),
     * reproduce sin que el usuario toque nada; si aun no llego, re-resuelve el glbr
     * UNA vez en silencio — el cooldown de 100 s de arriba evita bucles de relanzamiento. */
    /** v3.2.16: le pide al captador vivo (su propio task) un poll cada 20 s: si este
     * player se cierra antes de la ENTREGA, el captador sigue y la fila queda curada
     * para el proximo tap. Best-effort: sin captador vivo, no hace nada. */
    private void relevarCuraAlCaptador() {
        try {
            DramaAutoActivity pv = DramaAutoActivity.instanciaViva();
            if (pv != null) pv.pedirRelevo(20_000L);
        } catch (Throwable ignore) { }
    }

    /** v3.2.21: cuando el captador ENTREGA, el player que espera la cura se despierta YA —
     *  antes esperaba ciego su poll de 70 s y parecia que la cura «no llegaba». */
    static void notificarEntrega() {
        PlayerActivity p = vivoCura == null ? null : vivoCura.get();
        if (p != null && p.canalCurado != null && !p.isFinishing()) {
            android.util.Log.d("BLABONGO_DEBUG", "entrega del captador: despierto al player ya");
            p.mainHandler.removeCallbacks(p.curaReintentoRun);
            p.mainHandler.post(p.curaReintentoRun);
        }
    }

    /** v3.2.23: pids a los que el headless ya agoto sin curar (server terco con el sueno).
     *  Un segundo headless de 2 min es tiempo perdido: se escapa directo al captador VISIBLE. */
    private static final java.util.concurrent.ConcurrentHashMap<String, Boolean> quemadosHeadless =
            new java.util.concurrent.ConcurrentHashMap<>();

    /** v3.2.23: el captador aviso que la cosecha termino/plomazo sin curar este pid — quemarlo y,
     *  si hay un player esperando por ese canal, escapar YA al captador visible (no esperar mas
     *  ciclos ni relevar al plomazo). */
    static void notificarCuraFracasada(final String pid) {
        if (pid == null || pid.isEmpty()) return;
        quemadosHeadless.put(pid, Boolean.TRUE);
        PlayerActivity p = vivoCura == null ? null : vivoCura.get();
        if (p != null && !p.isFinishing() && pid.equals(p.canalCurado)) {
            p.mainHandler.post(() -> {
                if (p.isFinishing()) return;
                // v3.2.47 (log del usuario 01:06:36): el escape VISIBLE ya no existe — la
                // cosecha a pantalla completa cerro con 0 curados y revolvi6 la pantalla.
                // La quemadura queda apuntada y a los 25 s el player re-mira la fila; si no
                // hay cosecha en vuelo, el flujo normal lanza OTRA ronda headless (invisible),
                // que es la que reproduce el anuncio muted y sube runCount.
                android.util.Log.d("BLABONGO_DEBUG",
                        "captador agoto la cosecha sin curar " + pid + " — ronda headless extra en 25 s (todo en segundo plano)");
                p.curaCiclos = 0;
                p.mainHandler.removeCallbacks(p.curaReintentoRun);
                p.mainHandler.postDelayed(p.curaReintentoRun, 25_000L);
            });
        }
    }

    /**
     * v7.38.5 — MARCA EL CANAL PARA LA CURA Y LA LANZA. Antes no existía: el campo
     * {@code canalCurado} se declaraba y <b>nunca</b> recibía un valor distinto de
     * {@code null} (4 asignaciones en todo el fichero, las 4 a {@code null}), así
     * que toda la máquina de auto-curación quedaba inerte:
     *
     * <ul>
     *   <li>{@link #reintentaTrasCura()} retornaba en su PRIMERA línea
     *       ({@code canalCurado == null});</li>
     *   <li>{@code notificarEntrega()} (v3.2.21, «despierto al player ya») exigía
     *       {@code canalCurado != null} → nunca despertaba a nadie;</li>
     *   <li>{@code notificarCuraFracasada()} comparaba contra {@code null} → nunca
     *       coincidía.</li>
     * </ul>
     *
     * <p>Y {@link DramaFondo#abrirCaptadorHeadless} — la ventana 2x2 invisible que
     * ejecuta la cosecha — se quedó sin un solo llamador. La documentación
     * (docs/COMO_SESION_DRAMA.md v3.2.13→v3.2.47) describía un flujo que el código
     * ya no ejecutaba.</p>
     *
     * <p><b>Lo que NO se repone:</b> el salto al captador VISIBLE a pantalla
     * completa (los «tres caminos» que se retiraron en v3.2.46/v3.2.47 tras la
     * queja del usuario). Aquí solo se lanza el headless, con el tope de ciclos
     * intacto y el cooldown de 100 s por canal.</p>
     */
    private void lanzarCuraDelCanal(@androidx.annotation.Nullable String canal) {
        String pid = DramaSesion.idDeCanal(canal);
        if (pid == null || pid.isEmpty()) {
            log("cura: sin id_live (canal no viene de Drama) — no hay que curar nada");
            return;
        }
        if (quemadosHeadless.containsKey(pid)) {
            log("cura: " + pid + " ya se quemo en este proceso (server terco con el sueno)");
            return;
        }
        long ahora = System.currentTimeMillis();
        if (DramaSesion.curaFresca(curaTsMap, pid, ahora, 100_000L)) {
            log("cura: " + pid + " tiene una cura fresca (<100 s) — no se relanza");
            return;
        }
        canalCurado = pid;
        curaCiclos = 0;
        curaTsMap.put(pid, ahora);
        log("cura LANZADA para " + pid + " — cosecha headless (invisible) + reintento a 70 s");
        try {
            DramaFondo.abrirCaptadorHeadless(this);
        } catch (Throwable t) {
            log("cura: no se pudo abrir el captador — " + t.getMessage());
            canalCurado = null;
            return;
        }
        mainHandler.removeCallbacks(curaReintentoRun);
        mainHandler.postDelayed(curaReintentoRun, 70_000L);
    }

    private void reintentaTrasCura() {
        if (isFinishing() || canalCurado == null) return;
        String filaUrl = null;
        try {
            String[] fr = DramaSesion.filaDeRejilla(FuenteCanales.dramaExtraIdo(this), canalCurado);
            if (fr != null && fr.length > 3) filaUrl = fr[3];
        } catch (Exception ignore) { }
        if (filaUrl != null && (filaUrl.startsWith("http://") || filaUrl.startsWith("https://"))
                && !esPaginaWebNoStream(filaUrl)) {
            log("cura LLEGO a la rejilla — reproduciendo sin salir: " + filaUrl);
            canalCurado = null;
            curaCiclos = 0;   // v3.2.21
            relevarCuraAlCaptador();
            streamDirecto = filaUrl; streamCookies = null; streamCabeceras = null;
            streamReferer = "https://web.dramalive.cc/";
            streamOrigin = "https://web.dramalive.cc";
            try { FuenteCanales.fijarUrlResuelta(this,
                    dramaLiveEnJuego != null ? dramaLiveEnJuego : filaUrl, filaUrl); }
            catch (Exception ignore) { }
            resolverStreamDirecto(filaUrl, null, streamReferer, streamOrigin);
        } else if (canalCurado != null && DramaSesion.curaFresca(curaTsMap, canalCurado,
                System.currentTimeMillis(), 100_000L)) {
            // v3.2.22 (log 13:01:30.69): el despertar post-ENTREGA re-resolvia el glbr, el
            // server repetia el sueno y el player volvia a dormirse 20 s — POSTs gratis para
            // el site mientras la cosecha sigue viva en el captador. Con cura EN VUELO solo
            // re-miro la rejilla, barato, cada 15 s; la cura entra por fusion en cuanto exista.
            log("aun no llega y la cura sigue en vuelo — miro la fila de nuevo en 15 s (sin repetir el glbr)");
            relevarCuraAlCaptador();
            mainHandler.removeCallbacks(curaReintentoRun);
            mainHandler.postDelayed(curaReintentoRun, 15_000L);
        } else {
            log("cura aun no llega a la fila — re-resuelvo el glbr en silencio");
            relevarCuraAlCaptador();
            if (++curaCiclos >= 6) {
                // v3.2.47 (log del usuario 01:06:28): antes, al TERCER ciclo en seco, esto
                // abria el captador VISIBLE «donde el reproductor del sitio si levanta». Con
                // la cuenta inmadura tampoco ahi levanta: la cosecha cerro con 0 curados y
                // el usuario vio giros de pantalla y anuncios a full. Ahora: ~90 s de polling
                // discreto (cada ciclo relanza headless si no hay cosecha en vuelo), y ahi se
                // para con aviso honesto. La maduracion sigue sola en segundo plano.
                canalCurado = null;
                curaCiclos = 0;
                try {
                    android.widget.Toast.makeText(this,
                            "Este canal todavia no madura en la cuenta — se cura solo en segundo plano; probalo en unos minutos",
                            android.widget.Toast.LENGTH_LONG).show();
                } catch (Exception ignore) { }
            }
            curaReintento = true;
            try { if (dramaLiveEnJuego != null) resolverDramaLive(dramaLiveEnJuego); }
            finally { curaReintento = false; }
        }
    }

    private void resolverDramaLive(String canal) {
        canalActual = canal;
        // v7.19: por si el player se abrio por otra ruta (servidor elegido a mano,
        // vuelta atras) y no paso por onCreate con la sesion ya en disco.
        try { DramaSesion.precalentar(FuenteCanales.sesionDrama(this)); }
        catch (Throwable ignore) { }
        // v7.37.15: la version se imprime en CADA intento.
        log("Drama v" + dza.fut.paipo.BuildConfig.VERSION_NAME
                + " (code " + dza.fut.paipo.BuildConfig.VERSION_CODE + ") · canal=" + canal);
        log("Canal DramaLive: resolviendo glbr…");
        Toast.makeText(this, "Resolviendo glbr…", Toast.LENGTH_SHORT).show();
        mostrarCargando();
        relayForzado = false;      // v7.22: canal nuevo, se prueba directo otra vez
        paginaSinRelay = null;
        RegistroCarga.abrir("arranque de canal");
        executorService.execute(new Runnable() {
            @Override public void run() {
                String[] ra = null;
                String err = null;
                final long t0N = System.currentTimeMillis();
                try {
                    String ses = FuenteCanales.sesionDrama(PlayerActivity.this);
                    log("post glbr: " + DramaSesion.diagnosticoGlbr(ses)); // v3.2.12
                    if (ses == null || ses.trim().isEmpty()) {
                        // v3.2.34: sin sesion NO se manda al usuario al Captador: nos
                        // auto-conectamos aqui mismo (glt empotrado) y seguimos. Fresh
                        // install reproduce un canal en el primer toque.
                        log("sin sesion drama — auto-conectando (glt nativo)…");
                        ses = DramaSesion.conectarYSembrar(getApplicationContext());
                        if (ses != null) {
                            FuenteCanales.guardarSesionDrama(getApplicationContext(), ses);
                            log("auto-conexion OK — semilla propia en disco");
                        } else {
                            throw new IllegalStateException("no hay sesion y la auto-conexion fallo (sin red?)");
                        }
                    }
                    if (marcadorForzado != null) {
                        // cambio de calidad: el marcador de la opcion elegida va directo al glbr
                        ra = DramaSesion.resolverMarcado(canal, ses, marcadorForzado,
                                agenteForzado == null ? "redirect" : agenteForzado);
                        log("glbr con calidad " + calidadElegida + " -> " + (ra == null ? "null" : ra[0]));
                    } else {
                        DramaSesion.Vivo vv = DramaSesion.resolverVivo(canal, ses);
                        if (vv != null) {
                            // v7.37.15: las opciones se guardan SIEMPRE, incluso cuando
                            // glasbi no da un stream directo. Medido 2026-11-16 en el
                            // movil del usuario con live_tv_beinsportnews: sin esto
                            // streamOpcionesJson llegaba null y el plan B no tenia nada
                            // que abrir (el log no mostraba ni el «plan B»).
                            streamOpcionesJson = vv.opciones;
                            // v7.38.0: los servidores del canal, TODOS, con su UA y su
                            // Referer. Antes solo se guardaba la lista «cocinada», que
                            // tiraba los que no eran «la primera opción no marcador».
                            cargarServidoresDelCanal(canal, ses);
                            if (DramaSesion.siYaEsStream(vv.url)) {
                                ra = new String[]{ vv.url, vv.ua };
                                long tPost = System.currentTimeMillis() - t0N;
                                log("play nativo (glasbi) en " + tPost
                                        + " ms · " + vv.url + " · opciones=" + vv.opciones);
                            } else {
                                log("glasbi dio 201 sin stream directo (" + vv.url
                                        + "); se prueban las opciones del backup");
                                ra = null;
                            }
                        } else {
                            ra = DramaSesion.resolverDramaLive(canal, ses);
                        }
                        // v7.23: el paso se apunta en los dos caminos. Antes sólo se
                        // apuntaba cuando contestaba el glasbi, y un canal resuelto por
                        // el glbr dejaba el bloque vacío: no se podía distinguir «no se
                        // resolvió» de «se resolvió y el player no arrancó».
                        RegistroCarga.paso("resolver canal (2 POST)");
                    }
                    if (ra == null) throw new IllegalStateException("el glbr no devolvio un stream utilizable");
                } catch (Exception e) {
                    err = e.getClass().getSimpleName() + ": " + e.getMessage();
                }
                final String[] rr = ra;
                final String er = err;
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        if (rr == null) {
                            // v7.23: sin estas dos lineas el bloque se queda ABIERTO y
                            // VACIO, que es lo que le paso al usuario: cuatro toques y
                            // cuatro bloques sin una linea dentro.
                            RegistroCarga.paso("FALLO v" + dza.fut.paipo.BuildConfig.VERSION_NAME
                                    + ": el glbr no dio stream");
                            RegistroCarga.fin();
                            // v3.2.6: el fallo del glbr NUNCA es silencioso: toast con el
                            // motivo exacto (el «no se resuelven» anterior moria en logcat)
                            // y plan B: el sitio en el WebView — ahi el usuario juega el
                            // canal una vez, los ganchos del captador ven el glbr propio del
                            // sitio y hacen el BACKFILL de la url real en la rejilla.
                            log("glbr fallo: " + er);
                            Toast.makeText(PlayerActivity.this,
                                    "No se resolvió el canal (v"
                                            + dza.fut.paipo.BuildConfig.VERSION_NAME + "): " + er
                                            + " — abriendo el sitio; jugalo ahí una vez y reintenta",
                                    Toast.LENGTH_LONG).show();
                            streamDirecto = null;
                            // v7.37.15: si glasbi dejo opciones, se lleva al
                            // StreamResolver la PRIMERA que no sea un marcador .LS.
                            DramaResolucion.Opcion embebida = siguienteOpcionUtil();
                            if (embebida != null) {
                                log("plan B: probando el servidor del backup -> " + embebida.etiqueta());
                                urlIframeInicial = embebida.url;
                            } else if (urlIframeInicial == null || urlIframeInicial.isEmpty()) {
                                urlIframeInicial = "https://web.dramalive.cc/";
                            }
                            resolverStreamEnSegundoPlano();
                            return;
                        }
                        if (rr[1] != null && !rr[1].isEmpty()) streamCabeceras = "User-Agent: " + rr[1];
                        streamReferer = "https://web.dramalive.cc/";
                        streamOrigin = "https://web.dramalive.cc";
                        log("glbr resuelto -> " + rr[0]);
                        mostrarChipCalidad();
                        if (rr[0].contains("update.mp4")) {
                            RegistroCarga.paso("glbr devolvio relleno: se prueban los servidores del canal");
                            RegistroCarga.fin();
                            streamReady = false;
                            if (player != null) {
                                player.pause();
                            }
                            ocultarCargando();
                            if (opcionActual >= 0) opcionesFallidas.add(opcionActual);
                            if (opcionesServidor.size() > 1) pintarChipsServidores();
                            mainHandler.removeCallbacks(curaReintentoRun);
                            canalCurado = null;
                            mostrarErrorPlayer(R.string.player_error_relleno);
                            // v7.38.5 — ESTA RAMA ERA UN CALLEJÓN SIN SALIDA, y es la
                            // que el usuario vive a diario. Todas las demás (glbr falló,
                            // error de ExoPlayer, avisarSinStream) prueban el siguiente
                            // servidor o el embed; esta solo mostraba «Servidor no
                            // disponible por ahora» y hacía return. Pero el clip de
                            // relleno es una respuesta LEGÍTIMA del edge al POST desde
                            // Java (docs/COMO_SESION_DRAMA.md, v3.2.12) y el embed del
                            // MISMO canal sí emite: era justo el caso donde más falta
                            // hacía el plan B.
                            DramaResolucion.Opcion planB = siguienteOpcionUtil();
                            if (planB != null) {
                                log("relleno -> plan B: " + planB.etiqueta());
                                urlIframeInicial = planB.url;
                                // directo al marco: la carrera de 4 estrategias no puede
                                // con un embed y son 20 s de negro (ver resolverStream…)
                                mostrarEmbedEnLaVentana(planB);
                                return;
                            }
                            // Sin más servidores que probar no se miente: se dice tal cual
                            // y se deja la cura en segundo plano (v7.38.5 la reactiva).
                            log("relleno y sin mas servidores utiles: " + DramaResolucion.utiles(
                                    streamUrl, streamBackup).size() + " en la lista");
                            showControls();
                            mostrarAvisoSinMasServidores();
                            lanzarCuraDelCanal(canal);
                            return;
                        }
                        if (canalCurado != null) {  // v3.2.15: este canal ya curo — cancela el auto-reintento
                            canalCurado = null;
                            mainHandler.removeCallbacks(curaReintentoRun);
                        }
                        try { FuenteCanales.fijarUrlResuelta(PlayerActivity.this, canal, rr[0]); }
                        catch (Exception ignore) { }
                        // plan B disponible si el stream real fallara despues (el log mostraba
                        // "No hay URL valida que resolver" porque la habiamos vaciado):
                        urlIframeInicial = "https://web.dramalive.cc/";
                        resolverStreamDirecto(rr[0], null, streamReferer, streamOrigin);
                    }
                });
            }
        });
    }

    // --------------- CONTROLES PERSONALIZADOS ---------------
    /** isEmpty sin depender de TextUtils (que en los tests de JVM esta stubbeado). */
    private static boolean vacioTexto(String s) {
        return s == null || s.trim().isEmpty();
    }

    private void setupCustomControls() {
        controlsOverlay = findViewById(R.id.controls);
        btnPlayPause = findViewById(R.id.btnPlayPause);
        btnRewind = findViewById(R.id.btnRewind);
        btnForward = findViewById(R.id.btnForward);
        btnVolume = findViewById(R.id.btnVolume);
        btnRefresh = findViewById(R.id.btnRefresh);
        btnPip = findViewById(R.id.btnPip);
        btnCerrar = findViewById(R.id.btnCerrar);
        btnPantalla = findViewById(R.id.btnPantalla);
        tvTiempoActual = findViewById(R.id.tvTiempoActual);
        tvTiempoTotal = findViewById(R.id.tvTiempoTotal);
        actualizarIconoPantalla();
        chipCalidad = findViewById(R.id.chipCalidad);
        if (chipCalidad != null) {
            chipCalidad.setVisibility(android.view.View.GONE);
            chipCalidad.setOnClickListener(v -> mostrarMenuCalidad());
        }
        if (!controlesYaAtados && marcadorForzado == null) { calidadElegida = null; streamOpcionesJson = null; }   // v3.2.67: al girar no se olvida la calidad elegida
        seekBar = findViewById(R.id.seekBar);
        filaProgreso = findViewById(R.id.filaProgreso);
        centerControls = findViewById(R.id.centerControls);
        cargandoPlayer = findViewById(R.id.cargandoPlayer);
        if (!hayControlesPropios()) return;   // v7.9: sin overlay no hay nada que atar
        actualizarIconoVolumen();

        // Play/Pause
        btnPlayPause.setOnClickListener(v -> {
            // v7.37.16: si lo que suena es el embed (plan B), el play/pausa manda
            // al <video> de adentro; ExoPlayer ahi no tiene nada que hacer.
            if (embeddedEnVentana) {
                embedPausado = !embedPausado;
                pausarEmbed(embedPausado);
                updatePlayPauseButton();
                resetControlsTimeout();
                return;
            }
            if (player != null) {
                if (player.isPlaying()) player.pause();
                else player.play();
            }
            updatePlayPauseButton();
            actualizarAccionesPip();   // refresca el boton play/pausa del PiP
            resetControlsTimeout();
        });

        // Retroceder 10 segundos
        btnRewind.setOnClickListener(v -> {
            if (player != null) {
                long pos = player.getCurrentPosition() - 10000;
                player.seekTo(Math.max(pos, 0));
            }
            resetControlsTimeout();
        });

        // Adelantar 10 segundos
        btnForward.setOnClickListener(v -> {
            if (player != null) {
                long pos = player.getCurrentPosition() + 10000;
                long dur = player.getDuration();
                if (dur > 0) pos = Math.min(pos, dur);
                player.seekTo(pos);
            }
            resetControlsTimeout();
        });

        // Volumen (toggle silencio)
        btnVolume.setOnClickListener(v -> {
            if (embeddedEnVentana) {
                embedSilenciado = !embedSilenciado;
                silenciarEmbed(embedSilenciado);
                resetControlsTimeout();
                return;
            }
            if (player != null) {
                if (!silenciado) {
                    volumenAntesDeSilencio = player.getVolume();
                    silenciado = true;
                    player.setVolume(0f);
                } else {
                    silenciado = false;
                    player.setVolume(volumenAntesDeSilencio);
                }
            }
            actualizarIconoVolumen();
            resetControlsTimeout();
        });

        // Refrescar stream
        btnRefresh.setOnClickListener(v -> {
            if (streamDirecto != null && !streamDirecto.isEmpty()) {
                resolverStreamDirecto(streamDirecto, streamCookies, streamReferer, streamOrigin);
            } else {
                resolverStreamEnSegundoPlano();
            }
            resetControlsTimeout();
        });

        // PiP (en paisaje no hay boton: se entra con el boton de inicio)
        if (btnPip != null) btnPip.setOnClickListener(v -> {
            if (!soportaPip()) {
                log("⚠️ Este dispositivo no soporta Picture-in-Picture");
                Toast.makeText(this, "PiP no disponible en este dispositivo", Toast.LENGTH_SHORT).show();
            } else if (!hayVideoNativo()) {
                log("⚠️ Todavía no hay video nativo: no se puede entrar en PiP");
                Toast.makeText(this, "Espera a que cargue el video", Toast.LENGTH_SHORT).show();
            } else {
                entrarEnPip();
            }
            resetControlsTimeout();
        });

        // Girar a mano (v3.2.67): alterna la orientación pedida
        if (btnPantalla != null) {
            btnPantalla.setOnClickListener(v -> {
                setRequestedOrientation(esHorizontal
                        ? ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                        : ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                resetControlsTimeout();
            });
        }

        // Cerrar: corta el audio y sale del reproductor
        if (btnCerrar != null) {
            btnCerrar.setOnClickListener(v -> {
                log("⏹ Botón cerrar pulsado");
                cerrarReproductor();
            });
        }

        // SeekBar (en paisaje no hay barra: la spec no la pone)
        if (seekBar != null) seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) { }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                isSeeking = true;
                stopSeekUpdate();
                // Mantener controles visibles mientras arrastra
                mainHandler.removeCallbacks(hideControlsRunnable);
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if (player != null) {
                    long duration = player.getDuration();
                    if (duration > 0) {
                        player.seekTo(posicionBuscada(duration, seekBar.getProgress()));
                    }
                }
                isSeeking = false;
                startSeekUpdate();
                resetControlsTimeout();
            }
        });

        // Mostrar/ocultar controles al tocar la superficie del video
        final float touchSlop = android.view.ViewConfiguration.get(this).getScaledTouchSlop();
        final float[] touchStart = new float[2];
        playerView.setContentDescription(getString(R.string.player_toggle_controls));
        playerView.setOnClickListener(v -> alternarControles());
        // v7.37.16: los controles de la app manda sobre el embed: cada vez que
        // aparece o desaparece la capa, se pausa o se reanuda el <video> de adentro
        // (asi sus botones no asoman ni se desincroniza el audio).
        if (controlsOverlay != null) {
            controlsOverlay.addOnLayoutChangeListener((v2, l, t2, r2, b2_, ol, ot, or, ob) -> {
                if (!embeddedEnVentana) return;
                boolean visibles = v2.getVisibility() == View.VISIBLE && v2.getAlpha() > 0.05f;
                boolean debePausar = visibles || embedPausado;
                pausarEmbed(debePausar);
            });
        }
        playerView.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    touchStart[0] = event.getX();
                    touchStart[1] = event.getY();
                    return true;
                case MotionEvent.ACTION_UP:
                    float dx = event.getX() - touchStart[0];
                    float dy = event.getY() - touchStart[1];
                    if (dx * dx + dy * dy <= touchSlop * touchSlop) {
                        v.performClick();
                    }
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    return true;
                default:
                    return true;
            }
        });

        controlesYaAtados = true;
        if (cargaEnCurso) {
            ocultarControlesPorCarga();
        } else if (player != null && (player.isPlaying() || mensajeErrorPlayerResId != 0)) {
            showControls();
        }
    }

    /**
     * v7.9 — el overlay de controles (id "controls") vive en los dos layouts.
     * Si alguno no lo trajera, los findViewById de arriba devuelven null y sin
     * este corte el primer setOnClickListener reventaria con NPE.
     */
    private boolean hayControlesPropios() {
        return controlsOverlay != null;
    }

    private void showControls() {
        if (controlsOverlay != null && !isInPipMode) {
            if (cargaEnCurso) {
                ocultarControlesPorCarga();
                return;
            }
            if (player == null || (!player.isPlaying() && mensajeErrorPlayerResId == 0)) return;
            if (cargandoPlayer != null) cargandoPlayer.setVisibility(View.GONE);
            mainHandler.removeCallbacks(ocultarCargandoRun);
            controlesVisibles = true;
            View controlesPrincipales = centerControls != null
                    ? centerControls : controlsOverlay;
            animarVisibilidadControles(controlesPrincipales, true);
            if (centerControls != null) centerControls.setVisibility(View.VISIBLE);
            aplicarVisibilidadExtra(true);
            updatePlayPauseButton();
            resetControlsTimeout();
            startSeekUpdate();
        }
    }

    private void alternarControles() {
        // v7.37.16: con el embed (plan B) el toque SIEMPRE funciona. Antes
        // hideControlsImmediately() se negaba a esconder si ExoPlayer no estaba
        // reproduciendo (y con el embed esta parado), asi que los controles se
        // quedaban encima del canal para siempre.
        if (embeddedEnVentana) {
            if (controlesVisibles) {
                controlesVisibles = false;
                View c = centerControls != null ? centerControls : controlsOverlay;
                if (c != null) animarVisibilidadControles(c, false);
                aplicarVisibilidadExtra(false);
                resetControlsTimeout();
            } else {
                showControls();
            }
            return;
        }
        if (controlesVisibles) hideControlsImmediately();
        else showControls();
    }

    private void hideControlsImmediately() {
        if (!controlesVisibles) return;
        // v7.37.16: con el embed el player nativo esta parado, pero los controles
        // SI tienen que esconderse (si no, se quedan encima del canal).
        if (!embeddedEnVentana
                && (player == null || !player.isPlaying() || mensajeErrorPlayerResId != 0)) return;
        mainHandler.removeCallbacks(hideControlsRunnable);
        controlesVisibles = false;
        View controlesPrincipales = centerControls != null
                ? centerControls : controlsOverlay;
        if (controlesPrincipales != null) {
            animarVisibilidadControles(controlesPrincipales, false);
        }
        aplicarVisibilidadExtra(false);
        stopSeekUpdate();
    }

    private void animarVisibilidadControles(View view, boolean visible) {
        view.animate().cancel();
        if (visible) {
            if (view.getVisibility() != View.VISIBLE) {
                view.setAlpha(0f);
                view.setVisibility(View.VISIBLE);
            }
            view.animate().alpha(1f).setDuration(CONTROLS_FADE_DURATION_MS).start();
            return;
        }
        if (view.getVisibility() != View.VISIBLE) return;
        view.animate().alpha(0f).setDuration(CONTROLS_FADE_DURATION_MS).withEndAction(() -> {
            if (view.getAlpha() == 0f) {
                view.setVisibility(View.GONE);
                if (view == controlsOverlay) {
                    if (centerControls != null) centerControls.setVisibility(View.GONE);
                }
            }
        }).start();
    }

    private void resetControlsTimeout() {
        mainHandler.removeCallbacks(hideControlsRunnable);
        mainHandler.postDelayed(hideControlsRunnable, CONTROLS_TIMEOUT);
    }

    private void startSeekUpdate() {
        stopSeekUpdate();
        seekRunnable = new Runnable() {
            @Override
            public void run() {
                if (player != null && !isSeeking && seekBar != null
                        && controlsOverlay != null
                        && controlsOverlay.getVisibility() == View.VISIBLE) {
                    long duration = player.getDuration();
                    long position = player.getCurrentPosition();
                    if (tvTiempoActual != null) tvTiempoActual.setText(formatoTiempo(position));
                    if (duration > 0) {
                        int progress = (int) (position * 1000L / duration);
                        seekBar.setProgress(progress);
                        if (tvTiempoTotal != null) tvTiempoTotal.setText(formatoTiempo(duration));
                    } else if (tvTiempoTotal != null) {
                        tvTiempoTotal.setText("--:--");
                    }
                }
                seekHandler.postDelayed(this, 200);
            }
        };
        seekHandler.post(seekRunnable);
    }

    private void stopSeekUpdate() {
        if (seekRunnable != null) {
            seekHandler.removeCallbacks(seekRunnable);
        }
    }

    private void updatePlayPauseButton() {
        if (btnPlayPause == null) return;
        if (embeddedEnVentana) {
            // plan B: el icono sigue al embed, no a ExoPlayer (que esta parado)
            btnPlayPause.setImageResource(embedPausado ? R.drawable.ic_play : R.drawable.ic_pause);
            return;
        }
        if (player != null) {
            btnPlayPause.setImageResource(player.isPlaying() ? R.drawable.ic_pause : R.drawable.ic_play);
        }
    }

    private void actualizarIconoVolumen() {
        if (btnVolume == null) return;
        btnVolume.setImageResource(silenciado
                ? R.drawable.ic_volume_mute : R.drawable.ic_volume);
        btnVolume.setContentDescription(getString(silenciado
                ? R.string.player_unmute : R.string.player_mute));
    }

    // --------------- PICTURE IN PICTURE ---------------

    /** El dispositivo soporta PiP (Android 8+ y con la feature del sistema). */
    private boolean soportaPip() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && getPackageManager().hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE);
    }

    /** Solo tiene sentido entrar en PiP si hay video nativo (no el WebView). */
    private boolean hayVideoNativo() {
        return player != null && streamReady
                && (webViewFallback == null || webViewFallback.getVisibility() != View.VISIBLE);
    }

    private boolean estaEnPipSistema() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInPictureInPictureMode();
    }

    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        if (isInPipMode || entrandoEnPip || lanzandoCaptador) return;
        if (soportaPip() && hayVideoNativo() && player != null && player.isPlaying()) {
            entrarEnPip();
        }
    }

    @Override
    public void onBackPressed() {
        // ATRÁS SIEMPRE CIERRA el reproductor y corta el audio.
        // Antes, si estaba reproduciendo, volvía a entrar en PiP y no había
        // forma de salir. Para la ventanita flotante está el botón PiP o el
        // botón de inicio (que sí puede entrar en PiP).
        cerrarReproductor();
    }

    /**
     * Cierra el reproductor de verdad: para el audio, libera ExoPlayer, suelta
     * el foco de audio y termina la Activity.
     */
    protected void cerrarReproductor() {
        log("⏹ Cerrando reproductor");
        cerrandoDesdePip = true;
        liberarPlayer();
        if (instanciaActiva == this) instanciaActiva = null;
        // v7.38.1 — finishAndRemoveTask() quita la TAREA y se llevaba por delante lo
        // que hubiera detrás (de ahí el «regresa a canales»). Solo si se sale de PiP.
        try {
            if (isInPipMode && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                finishAndRemoveTask();
            } else {
                finish();
            }
        } catch (Exception e) {
            try { finish(); } catch (Exception ignored) { }
        }
    }

    /**
     * Corta el audio de raíz: para la reproducción, libera el reproductor y
     * suelta el foco de audio. Se llama al cerrar y al abrir otro reproductor,
     * para que nunca queden dos sonando a la vez.
     */
    protected void liberarPlayer() {
        try {
            if (player != null) {
                try { player.setPlayWhenReady(false); } catch (Exception ignored) { }
                try { player.stop(); } catch (Exception ignored) { }
                try { player.release(); } catch (Exception ignored) { }
                player = null;
            }
        } catch (Exception ignored) { }
        abandonarFocoAudio();
        // Los WebViews ocultos del resolver cargan el embed y su reproductor
        // arranca solo: si no se destruyen, el audio sigue sonando detrás.
        try { PelisStreamResolver.destruirWebViewsActivos(); } catch (Throwable ignored) { }
        try { StreamResolver.destruirWebViewsActivos(); } catch (Throwable ignored) { }
        if (webViewFallback != null) {
            try {
                webViewFallback.stopLoading();
                webViewFallback.loadUrl("about:blank");
            } catch (Exception ignored) { }
        }
    }

    /**
     * Si el reproductor se liberó al cerrar (player == null) lo vuelve a crear.
     * Imprescindible porque esta Activity es singleTask: al abrir otra película
     * Android puede reutilizar la instancia (onNewIntent) en lugar de crear una
     * nueva, y sin esto la segunda película no sonaba nunca.
     */
    private boolean asegurarPlayer() {
        if (player != null) return true;
        if (isFinishing()) return false;
        try {
            cerrandoDesdePip = false;
            setupMedia3Player();
            log("♻️ Reproductor recreado para el nuevo título");
        } catch (Throwable t) {
            Log.e(TAG, "No se pudo recrear el reproductor: " + t.getMessage(), t);
        }
        return player != null;
    }

    /** Si el executor se apagó al salir, se crea uno nuevo. */
    private void asegurarExecutor() {
        if (executorService == null || executorService.isShutdown() || executorService.isTerminated()) {
            executorService = Executors.newSingleThreadExecutor();
        }
    }

    /** Pide el foco de audio: si otra app está sonando, la calla. */
    @SuppressWarnings("deprecation")
    private void pedirFocoAudio() {
        try {
            if (audioManager == null) {
                audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            }
            if (audioManager == null) return;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (peticionFoco == null) {
                    android.media.AudioAttributes attrs =
                            new android.media.AudioAttributes.Builder()
                                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MOVIE)
                                    .build();
                    peticionFoco = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                            .setAudioAttributes(attrs)
                            .setOnAudioFocusChangeListener(focoListener)
                            .build();
                }
                audioManager.requestAudioFocus(peticionFoco);
            } else {
                audioManager.requestAudioFocus(focoListener, AudioManager.STREAM_MUSIC,
                        AudioManager.AUDIOFOCUS_GAIN);
            }
        } catch (Exception ignored) { }
    }

    @SuppressWarnings("deprecation")
    private void abandonarFocoAudio() {
        if (audioManager == null) return;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (peticionFoco != null) audioManager.abandonAudioFocusRequest(peticionFoco);
            } else {
                audioManager.abandonAudioFocus(focoListener);
            }
        } catch (Exception ignored) { }
    }

    private void entrarEnPip() {
        if (!soportaPip() || isInPipMode) return;
        try {
            entrandoEnPip = true;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                enterPictureInPictureMode(construirParamsPip());
            }
        } catch (Exception e) {
            entrandoEnPip = false;
            log("⚠️ No se pudo entrar en PiP: " + e.getMessage());
        }
    }

    @SuppressLint("NewApi")
    private PictureInPictureParams construirParamsPip() {
        PictureInPictureParams.Builder b = new PictureInPictureParams.Builder()
                .setAspectRatio(relacionAspectoVideo())
                .setActions(accionesPip());
        Rect r = rectOrigenPip();
        if (r != null) b.setSourceRectHint(r);      // animacion desde el video
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            b.setAutoEnterEnabled(hayVideoNativo());  // Android 12+: gesto de inicio -> PiP
            b.setSeamlessResizeEnabled(false);
        }
        return b.build();
    }

    /** Prepara el auto-entrar ANTES de salir de la app (Android 12+). */
    private void aplicarParamsPip() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || !soportaPip()) return;
        try {
            setPictureInPictureParams(construirParamsPip());
        } catch (Exception ignored) { }
    }

    /** Refresca los botones del PiP sin volver a entrar en el modo. */
    private void actualizarAccionesPip() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        if (!isInPipMode && !entrandoEnPip) return;
        try {
            setPictureInPictureParams(new PictureInPictureParams.Builder()
                    .setAspectRatio(relacionAspectoVideo())
                    .setActions(accionesPip())
                    .build());
        } catch (Exception ignored) { }
    }

    /** Piso de la relacion del PiP: 1.0 = SIEMPRE landscape (nunca una ventanita
     *  flotante vertical). Pedido del usuario: «el pip siempre en landscap debe ser». */
    private static final float PIP_MIN_LANDSCAPE = 1.0f;
    /** Techo que impone Android para la ventanita. */
    private static final float PIP_MAX = 2.390000f;

    /**
     * Relacion de aspecto del PiP a partir del tamaño del video. Puro (sin Android)
     * para poder fijarlo con un test: «el pip siempre en landscap debe ser».
     *
     * <p>Se usa la real del video, pero JAMAS por debajo de 1.0: si el video es
     * vertical (o todavia no se conocio su tamaño) la ventanita sale landscape y el
     * video se ve letterboxeado dentro. Antes el piso era el de Android (0.418) y un
     * canal vertical daba un PiP alto y estrecho.
     */
    static float relacionPip(int ancho, int alto) {
        if (ancho <= 0 || alto <= 0) return 16f / 9f;   // sin datos: 16:9
        float r = (float) ancho / (float) alto;
        if (r < PIP_MIN_LANDSCAPE) r = PIP_MIN_LANDSCAPE;   // landscape obligatorio
        if (r > PIP_MAX) r = PIP_MAX;                       // limite de Android
        return r;
    }

    /** Relacion de aspecto del video real, con el piso landscape del PiP. */
    private Rational relacionAspectoVideo() {
        int w = 16, h = 9;
        if (player != null) {
            androidx.media3.common.VideoSize vs = player.getVideoSize();
            if (vs != null && vs.width > 0 && vs.height > 0) {
                w = vs.width;
                h = vs.height;
            }
        }
        float r = relacionPip(w, h);
        if (r <= 1.0f && w < h) {
            android.util.Log.d("BLABONGO_DEBUG", "PiP: video " + w + "x" + h
                    + " es vertical — ventanita forzada a landscape");
        }
        return new Rational(Math.round(r * 1000), 1000);
    }

    private Rect rectOrigenPip() {
        if (playerView == null) return null;
        int[] loc = new int[2];
        playerView.getLocationOnScreen(loc);
        return new Rect(loc[0], loc[1],
                loc[0] + playerView.getWidth(), loc[1] + playerView.getHeight());
    }

    /** Botones visibles dentro de la ventana flotante. */
    private java.util.List<RemoteAction> accionesPip() {
        java.util.List<RemoteAction> acciones = new java.util.ArrayList<>();
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return acciones;
        boolean reproduciendo = player != null && player.isPlaying();
        acciones.add(new RemoteAction(
                Icon.createWithResource(this, reproduciendo ? R.drawable.ic_pause : R.drawable.ic_play),
                reproduciendo ? "Pausar" : "Reproducir",
                reproduciendo ? "Pausar" : "Reproducir",
                pendingIntentPip(PIP_ACTION_PLAY_PAUSE)));
        // Ojo: aqui ANTES habia un boton "Cerrar" (icono de X) que cerraba el
        // reproductor y borraba la tarea. La gente lo pulsaba para volver a
        // pantalla completa y se le cerraba todo. Ahora este boton SACA del
        // PiP y devuelve la app a pantalla completa; para cerrar del todo
        // esta el boton ATRAS una vez fuera del PiP.
        acciones.add(new RemoteAction(
                Icon.createWithResource(this, android.R.drawable.ic_menu_view),
                "Pantalla completa",
                "Sale del PiP y vuelve a pantalla completa",
                pendingIntentPip(PIP_ACTION_FULLSCREEN)));
        return acciones;
    }

    private PendingIntent pendingIntentPip(int accion) {
        Intent i = new Intent(ACTION_PIP_CONTROL).setPackage(getPackageName());
        i.putExtra(EXTRA_PIP_ACTION, accion);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getBroadcast(this, accion, i, flags);
    }

    private void registerPipReceiver() {
        pipReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent intent) {
                if (intent == null || !ACTION_PIP_CONTROL.equals(intent.getAction())) return;
                int accion = intent.getIntExtra(EXTRA_PIP_ACTION, 0);
                if (accion == PIP_ACTION_PLAY_PAUSE) {
                    alternarPlayPause();
                    actualizarAccionesPip();
                } else if (accion == PIP_ACTION_FULLSCREEN) {
                    salirDePip();
                }
            }
        };
        IntentFilter f = new IntentFilter(ACTION_PIP_CONTROL);
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                registerReceiver(pipReceiver, f, Context.RECEIVER_NOT_EXPORTED);
            } else {
                registerReceiver(pipReceiver, f);
            }
        } catch (Exception e) {
            log("⚠️ No se pudo registrar el receptor de PiP: " + e.getMessage());
        }
    }

    private void alternarPlayPause() {
        if (player == null) return;
        if (player.isPlaying()) player.pause();
        else player.play();
        updatePlayPauseButton();
    }

    /**
     * Saca la app del PiP y la devuelve a pantalla completa (botón de la
     * ventanita flotante).
     *
     * No existe una llamada directa para salir del modo PiP: lo que lo saca es
     * traer la tarea al frente con REORDER_TO_FRONT. El intent lleva una marca
     * (ACTION_SALIR_PIP) para que onNewIntent sepa que no es un título nuevo y
     * no toque ni el intent original ni el stream que está sonando.
     */
    private void salirDePip() {
        log("⤢ Saliendo del PiP: vuelta a pantalla completa");
        Intent volver = new Intent(this, PlayerActivity.class);
        volver.setAction(ACTION_SALIR_PIP);
        volver.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        // CLAVE: esta Activity es launchMode="singleTask", y en ese modo
        // lanzar el intent desde la propia Activity NO trae la app al primer
        // plano (se queda detras del escritorio y parece que se ha cerrado
        // todo). Hay que lanzarlo desde el contexto de la APLICACION.
        try {
            android.content.Context ctx = getApplicationContext();
            if (ctx == null) ctx = this;
            ctx.startActivity(volver);
            return;
        } catch (Throwable t) {
            log("⚠️ Reintento para volver al primer plano: " + t.getMessage());
        }
        // Plan B (Android 14+): desde segundo plano el sistema puede bloquear
        // el arranque; con un PendingIntent con permiso explícito se permite.
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                android.app.ActivityOptions op = android.app.ActivityOptions.makeBasic();
                op.setPendingIntentBackgroundActivityStartMode(
                        android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED);
                PendingIntent pi = PendingIntent.getActivity(this, 77, volver,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE,
                        op.toBundle());
                pi.send();
                return;
            }
        } catch (Throwable t) {
            log("⚠️ Reintento 2 para volver al primer plano: " + t.getMessage());
        }
        // Plan C: por si todo lo anterior se bloquea.
        try {
            startActivity(volver);
        } catch (Throwable t) {
            log("⚠️ No se pudo salir del PiP: " + t.getMessage());
        }
    }

    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode,
                                              @NonNull Configuration newConfig) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig);
        isInPipMode = isInPictureInPictureMode;
        entrandoEnPip = false;
        if (isInPictureInPictureMode) {
            // En PiP SOLO se ve el vídeo: se oculta absolutamente todo lo demás
            // (controles, panel de depuración, WebView, anuncios...).
            ocultarTodoMenosElVideo();
            if (filaServidores != null) filaServidores.setVisibility(View.GONE);   // v3.2.66
            stopSeekUpdate();
            hideSystemUI();
            actualizarAccionesPip();

            // Comportamiento ESTÁNDAR de Android: al entrar en PiP la
            // aplicación SE ESCONDE y solo queda la ventanita flotante; al
            // salir del PiP la app vuelve a pantalla completa.
            //
            // Para esconderla NO vale moveTaskToBack(): en muchos móviles
            // (Samsung, Xiaomi, Android 12+) destruye la ventanita del PiP,
            // y entonces se veía la app a pantalla completa y el reproductor
            // flotante desaparecía, justo al revés de lo que tiene que pasar.
            //
            // Lo que hace el sistema es ir al escritorio: es lo mismo que
            // pulsar el botón de inicio, el PiP sigue vivo encima de todo y
            // la tarea de la app queda intacta para restaurarla después.
            mainHandler.postDelayed(this::esconderAppCuandoEstaEnPip, 500);
            // Vigía: si la ventanita se pierde sola, se apunta en el log de
            // depuración para poder saberlo.
            mainHandler.postDelayed(() -> {
                if (cerrandoDesdePip || isFinishing()) return;
                if (!estaEnPipSistema() && entrandoEnPip) {
                    log("⚠️ El sistema no llegó a abrir el PiP");
                }
            }, 2000);
            try {
                String info = "PiP · nativo=" + hayVideoNativo()
                        + (player != null ? ", playing=" + player.isPlaying() : ", player=null");
                if (player != null) {
                    androidx.media3.common.VideoSize vs = player.getVideoSize();
                    info += ", video=" + vs.width + "x" + vs.height;
                }
                log(info);
            } catch (Throwable ignored) { }
        } else if (!cerrandoDesdePip) {
            // Volvemos a pantalla completa
            restaurarVistasTrasPip();
            hideSystemUI();
            showControls();
            aplicarParamsPip();
            // Al salir del PiP el sistema devuelve la app a pantalla completa.
            // Si se cerró la ventanita desde el escritorio, la tarea puede
            // quedarse detrás: se trae al frente para que la app aparezca tal
            // como estaba. (Si ya está delante no hace nada.)
            // Si la ventanita se cierra estando la app detras del escritorio,
            // hay que traerla al frente; si el sistema ya lo ha hecho, no hace
            // nada mas.
            intentosTraerAlFrente = 0;
            mainHandler.postDelayed(this::asegurarAppEnPrimerPlano, 600);
        }
    }

    /**
     * Esconde la aplicación para que al entrar en PiP solo se vea la
     * ventanita flotante. Se llama un poco después de entrar en PiP, para dar
     * tiempo a que la ventanita esté montada.
     *
     * NO se usa moveTaskToBack(): en muchos móviles (Samsung, Xiaomi,
     * Android 12+) destruye la ventanita del PiP y entonces se quedaba la app
     * a pantalla completa y el reproductor flotante desaparecía, al revés de
     * lo que tiene que pasar. Ir al escritorio es lo mismo que pulsar el botón
     * de inicio: el PiP sigue vivo encima de todo y la tarea queda intacta.
     */
    private void esconderAppCuandoEstaEnPip() {
        if (!isInPipMode || isFinishing() || cerrandoDesdePip) return;
        try {
            Intent inicio = new Intent(Intent.ACTION_MAIN);
            inicio.addCategory(Intent.CATEGORY_HOME);
            inicio.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(inicio);
            log("PiP: app escondida, solo la ventanita flotante");
        } catch (Throwable t) {
            log("⚠️ No se pudo esconder la app: " + t.getMessage());
        }
    }

    /**
     * Devuelve la app a pantalla completa al salir del PiP. Si ya estaba
     * delante no hace nada (es lo que pasa al tocar la ventanita para
     * expandirla); si se cerró desde el escritorio, la trae al frente.
     */
    /**
     * Comprueba que la app ha vuelto realmente al primer plano y, si no, la
     * trae. Se reintenta un par de veces porque el sistema tarda en terminar
     * la animacion de salida del PiP.
     */
    private void asegurarAppEnPrimerPlano() {
        if (isInPipMode || isFinishing() || cerrandoDesdePip) return;
        if (enPrimerPlano || intentosTraerAlFrente >= 3) return;
        intentosTraerAlFrente++;
        salirDePip();
        mainHandler.postDelayed(this::asegurarAppEnPrimerPlano, 1200);
    }

    /**
     * Oculta la interfaz para que en el PiP SOLO se vea el vídeo
     * (ni controles, ni panel de depuración, ni WebView).
     *
     * Se recorre el camino desde el vídeo hasta la raíz ocultando los HERMANOS
     * de cada nivel. Nunca se oculta el vídeo ni ninguno de sus contenedores:
     * ocultarlos deja el PiP en NEGRO (se oye pero no se ve).
     */
    private void ocultarTodoMenosElVideo() {
        visibilidadPrePip.clear();
        // Si el vídeo lo está dando el WebView de respaldo, hay que dejar ESE
        // visible y ocultar el PlayerView; si no, el PiP sale negro (se oye el
        // audio del WebView pero no se ve nada).
        final boolean videoEnWebView = webViewFallback != null
                && webViewFallback.getVisibility() == View.VISIBLE;
        if (videoEnWebView) {
            visibilidadPrePip.put(playerView, playerView.getVisibility());
            playerView.setVisibility(View.GONE);
        }
        if (playerView != null) {
            View hijo = playerView;
            android.view.ViewParent padre = hijo.getParent();
            while (padre instanceof ViewGroup) {
                ViewGroup grupo = (ViewGroup) padre;
                for (int i = 0; i < grupo.getChildCount(); i++) {
                    View v = grupo.getChildAt(i);
                    if (v == hijo) continue;               // por aquí sube el vídeo
                    if (videoEnWebView && v == webViewFallback) continue;  // es el que se ve
                    visibilidadPrePip.put(v, v.getVisibility());
                    if (v.getVisibility() != View.GONE) v.setVisibility(View.GONE);
                }
                hijo = grupo;
                padre = grupo.getParent();
            }
        }
        if (playerView != null) {
            playerView.hideController();
            playerView.setUseController(false);
        }
        // El panel de depuración se recuerda para poder restaurarlo al salir.
        if (tvDebugLog != null) {
            visibilidadPrePip.put(tvDebugLog, tvDebugLog.getVisibility());
            tvDebugLog.setVisibility(View.GONE);
        }
        if (scrollDebugLog != null) {
            visibilidadPrePip.put(scrollDebugLog, scrollDebugLog.getVisibility());
            scrollDebugLog.setVisibility(View.GONE);
        }
    }

    /** Devuelve cada vista al estado que tenía antes de entrar en PiP. */
    private void restaurarVistasTrasPip() {
        for (Map.Entry<View, Integer> e : visibilidadPrePip.entrySet()) {
            if (e.getKey() != null) e.getKey().setVisibility(e.getValue() == null ? View.VISIBLE : e.getValue());
        }
        visibilidadPrePip.clear();
    }

    // --------------- CICLO DE VIDA ---------------

    /** Con launchMode="singleTask" sirve para abrir otra peli/canal sin duplicar Activity. */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent == null) return;
        // Marca interna para salir del PiP: NO es un título nuevo, así que no
        // se toca el intent original (si lo reemplazáramos se perdería la URL
        // del stream que está sonando).
        if (ACTION_SALIR_PIP.equals(intent.getAction())) {
            cerrandoDesdePip = false;
            asegurarPlayer();
            return;
        }
        setIntent(intent);
        leerOpcionesDeIntent(intent);   // v3.2.66: chips del canal recien llegado
        String tituloNuevo = intent.getStringExtra("canal_titulo");
        if (tituloNuevo != null) { tituloCanalActual = tituloNuevo; pintarTituloPlayer(); }
        pintarChipsServidores();
        pintarTiraCanales();
        // Venimos de cerrar o de otra película: se limpia cualquier resolución
        // anterior y se garantiza que el reproductor existe.
        isResolving = false;
        resolucionInicio = 0L;
        webViewOcultoYaProbado = false;
        cerrandoDesdePip = false;
        asegurarPlayer();
        String nuevaUrl = intent.getStringExtra("stream_url");
        String nuevoIframe = intent.getStringExtra("url_iframe_inicial");
        if (nuevaUrl != null && !nuevaUrl.isEmpty()) {
            streamDirecto = nuevaUrl;
            streamCookies = intent.getStringExtra("stream_cookies");
            streamReferer = intent.getStringExtra("stream_referer");
            streamOrigin = intent.getStringExtra("stream_origin");
            String cab = intent.getStringExtra("stream_headers");
            if (cab != null) streamCabeceras = cab;
            urlIframeInicial = null;
            errorRefreshCount = 0;
            resolverStreamDirecto(streamDirecto, streamCookies, streamReferer, streamOrigin);
        } else if (nuevoIframe != null && !nuevoIframe.isEmpty()) {
            // Si vuelve a llegar la MISMA url (doble clic en el canal) mientras
            // ya se esta resolviendo o reproduciendo, no se reinicia todo: antes
            // eso hacia resolver dos veces y volver a preparar el player.
            if (nuevoIframe.equals(urlIframeInicial) && (streamReady || isResolving)) {
                log("⏳ Vuelve a llegar la misma URL; se ignora (ya estaba en marcha).");
                return;
            }
            urlIframeInicial = nuevoIframe;
            streamDirecto = null;
            errorRefreshCount = 0;
            resolverStreamEnSegundoPlano();
        }
    }

    /** v3.2.67: al girar NO se recrea la actividad (configChanges del
     *  manifiesto): se re-infla el layout de la nueva orientación y se re-ata
     *  todo al MISMO ExoPlayer — el vídeo no se corta. */
    @Override
    public void onConfigurationChanged(
            @NonNull android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        boolean ahoraHorizontal = newConfig.orientation
                == android.content.res.Configuration.ORIENTATION_LANDSCAPE;
        if (ahoraHorizontal == esHorizontal || isInPipMode || entrandoEnPip) return;
        esHorizontal = ahoraHorizontal;
        atarInterfazPlayer();
        hideSystemUI();
    }

    private void atarInterfazPlayer() {
        setContentView(esHorizontal ? R.layout.activity_player
                : R.layout.activity_player_portrait);
        playerView = findViewById(R.id.playerView);
        configurarEscaladoVideo();
        playerView.setUseController(false);
        if (player != null) playerView.setPlayer(player);
        setupCustomControls();
        setupWebViewFallback();
        atarOverlaysPlayer();
        pintarTituloPlayer();
        pintarChipsServidores();
        pintarTiraCanales();
        actualizarIconoPantalla();
        if (!streamReady && webViewOcultoYaProbado) {
            // El WebView oculto murió con la vista vieja a mitad de resolución:
            // se reintenta la misma opción con las vistas nuevas.
            webViewOcultoYaProbado = false;
            isResolving = false;
            resolverStreamEnSegundoPlano();
        }
    }

    private void configurarEscaladoVideo() {
        if (playerView == null) return;
        playerView.setResizeMode(esHorizontal
                ? AspectRatioFrameLayout.RESIZE_MODE_FILL
                : AspectRatioFrameLayout.RESIZE_MODE_FIT);
    }

    @Override
    protected void onPause() {
        // Si estamos entrando/ya en PiP el video sigue sonando: NO pausar.
        if (!isInPipMode && !entrandoEnPip && !estaEnPipSistema() && player != null) {
            try { player.pause(); } catch (Exception ignored) { }
        }
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        enPrimerPlano = true;
        lanzandoCaptador = false;   // v7.14: el captador ya cerró: el PiP vuelve a mandar
        intentosTraerAlFrente = 0;
        hideSystemUI();
        // Si la Activity se reutilizó tras cerrarla, el reproductor puede estar
        // liberado: se recrea para que el título actual pueda sonar.
        if (!isFinishing() && player == null
                && ((streamDirecto != null && !streamDirecto.isEmpty())
                    || (urlIframeInicial != null && !urlIframeInicial.isEmpty()))) {
            asegurarPlayer();
        }
        if (!isInPipMode) aplicarParamsPip();
    }

    @Override
    protected void onStop() {
        // ANTES: aqui se liberaba el player al ir a segundo plano, asi que al
        // volver la pantalla quedaba en negro. Solo liberamos si la Activity se
        // esta destruyendo de verdad (y nunca mientras estamos en PiP).
        if (!isInPipMode && !entrandoEnPip && !estaEnPipSistema()) {
            if (player != null) {
                try { player.pause(); } catch (Exception ignored) { }
                // Si la Activity se está cerrando de verdad, se libera YA para
                // que no quede ningún audio sonando.
                if (isFinishing()) liberarPlayer();
            }
        }
        enPrimerPlano = false;
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        if (pipReceiver != null) {
            try { unregisterReceiver(pipReceiver); } catch (Exception ignored) { }
            pipReceiver = null;
        }
        if (executorService != null) {
            executorService.shutdownNow();
            executorService = null;   // asegurarExecutor() lo crea de nuevo si hace falta
        }
        liberarPlayer();
        if (instanciaActiva == this) instanciaActiva = null;
        if (webViewFallback != null) {
            webViewFallback.stopLoading();
            webViewFallback.loadUrl("about:blank");
            webViewFallback.destroy();
            webViewFallback = null;
        }
        mainHandler.removeCallbacks(hideControlsRunnable);
        stopSeekUpdate();
        super.onDestroy();
    }

    // --------------- MÉTODOS ORIGINALES (SIN CAMBIOS) ---------------
    private void log(String msg) {
        String ts = timeFormat.format(new Date());
        String line = "[" + ts + "] " + msg + "\n";
        Log.d("BLABONGO_DEBUG", msg);
        runOnUiThread(() -> {
            if (tvDebugLog != null) {
                tvDebugLog.append(line);
                scrollDebugLog.post(() -> scrollDebugLog.fullScroll(ScrollView.FOCUS_DOWN));
            }
        });
    }

    private void setupMedia3Player() {
        dataSourceFactory = new DefaultHttpDataSource.Factory()
                .setConnectTimeoutMs(15000)
                .setReadTimeoutMs(15000)
                // v7.37.17 — MEDIDO en el movil del usuario (beIN Sports Fr1, 14:05):
                // la "directa" del backup es http://ua.online24.pm/play/... y contesta
                // 302 a http://ua102.online24.pm:8222/... y de ahi a https://…:8443.
                // ExoPlayer, con el DefaultHttpDataSource a secas, NO sigue el salto
                // entre esquemas y devolvia «Source error | Response code: 302»
                // (cinco refrescos y el canal muerto). Con esto sigue el 302.
                .setAllowCrossProtocolRedirects(true);

        HttpDataSource.Factory loggingDataSourceFactory = new HttpDataSource.Factory() {
            @Override
            public HttpDataSource createDataSource() {
                return new LoggingHttpDataSource(dataSourceFactory.createDataSource());
            }

            @Override
            public HttpDataSource.Factory setDefaultRequestProperties(Map<String, String> defaultRequestProperties) {
                dataSourceFactory.setDefaultRequestProperties(defaultRequestProperties);
                return this;
            }
        };

        // Swap de los paneles Drama (p.ej. k9x_ -> "") aplicado a CADA lectura de
        // playlist, incluidas las recargas en vivo de ExoPlayer; con pares explicitos
        // del JSON descifrado, o auto-deteccion cuando la URI es .../index.html.
        // v7.8 — EL RELAY APUNTA A UN file:// Y LA CADENA ERA SOLO HTTP.
        // relayHlsSiPagina() baja la playlist del panel, le aplica el swap, la
        // escribe en cache y devuelve file:///.../drama_relay.m3u8. Pero la
        // fabrica de arriba envuelve SIEMPRE un DefaultHttpDataSource, que hace
        // el cast a HttpURLConnection a mano: al abrir un file:// Java devuelve
        // un FileURLConnection y revienta con
        //   ClassCastException: FileURLConnection cannot be cast to HttpURLConnection
        // Medido en el logcat real: el canal resolvia bien (glasbi 811 ms,
        // playlist de 972 B con el swap aplicado) y moria solo en el transporte.
        // DefaultDataSource enruta por esquema: file/content/asset van al
        // datasource local; http/https siguen yendo al de siempre, con sus
        // cabeceras de usuario y sus logs.
        DataSource.Factory enrutado = new DefaultDataSource.Factory(this, loggingDataSourceFactory);

        DefaultMediaSourceFactory mediaSourceFactory = new DefaultMediaSourceFactory(
                DramaSwapDataSource.nuevaFabrica(enrutado, dramaSwapPares));
        // v7.38.2 — AQUÍ ESTABA EL FALLO DE «cualquier canal vuelve a la lista».
        // En la 7.38.1 se instalaba un gestor de DRM (ClearKey) para TODOS los
        // MediaItem del reproductor. Media3 lo consulta al preparar CADA canal, y
        // en un móvil real eso tiraba abajo la apertura del reproductor (que se
        // ve como «vuelve a canales»). Se retira: el camino de reproducción queda
        // EXACTAMENTE como en la 7.38.0, que abría todos los canales.
        // Ningún canal del catálogo necesita DRM local: los de starzplay son
        // Widevine (imposible con clave suelta, comprobado en el propio .mpd).
        player = new ExoPlayer.Builder(this)
                .setMediaSourceFactory(mediaSourceFactory)
                // v3.2.63: arrancar con 0.8 s de buffer (default 2.5 s) = el canal
                // se ve antes; tras un rebuffer pide 2 s para estabilizarse.
                .setLoadControl(new androidx.media3.exoplayer.DefaultLoadControl.Builder()
                        .setBufferDurationsMs(15000, 30000, 800, 2000)
                        .build())
                .build();
        playerView.setPlayer(player);
        player.setVolume(silenciado ? 0f : volumenAntesDeSilencio);
        actualizarIconoVolumen();

        // AUDIO EN ESPAÑOL: si el stream trae varias pistas de audio (vimeus
        // publica p.ej. "Español"/"es" + "한국어"/"ko"), se fuerza español.
        try {
            player.setTrackSelectionParameters(
                    player.getTrackSelectionParameters()
                            .buildUpon()
                            .setPreferredAudioLanguage("spa")
                            .setPreferredAudioRoleFlags(0)
                            .build());
            log("Pista de audio preferida: español (spa)");
        } catch (Throwable t) {
            Log.w(TAG, "No se pudo fijar el idioma de audio preferido: " + t.getMessage());
        }

        player.addListener(new Player.Listener() {
            @Override
            public void onPlayerError(PlaybackException error) {
                String msg = "❌ ERROR ExoPlayer: " + error.getMessage();
                if (error.getCause() != null) msg += " | " + error.getCause().getMessage();
                log(msg);

                // v7.37.16 — lo medido con beIN: el proveedor da un m3u8 "directo"
                // que contesta 403 (Cloudflare) o un CDN que exige un UA imposible.
                // Antes de reintentar 5 veces el mismo m3u8 muerto se prueba el
                // plan B que SI funciona: el embed del canal dentro de la ventana.
                // v7.37.17: el plan B debe ser un EMBEBIDO (ordernap/quotarevival).
                // Antes se cogia la primera opcion no-marcador, que en beIN es el
                // mismo m3u8 directo que acaba de fallar: se "enmarcaba" un m3u8 y
                // el usuario veia un reproductor vacio (log del movil, 14:05:32).
                DramaResolucion.Opcion planB = siguienteOpcionUtil();
                boolean planBEsElMismoStream = planB != null && urlEnReproduccion != null
                        && planB.url.equals(urlEnReproduccion);
                if (planB != null && !planBEsElMismoStream && !embeddedEnVentana) {
                    log("↩️ ExoPlayer fallo; plan B -> " + planB.etiqueta());
                    errorRefreshCount = MAX_REFRESH_RETRIES;   // no insistir con el m3u8 muerto
                    try { player.stop(); } catch (Throwable ignored) { }
                    if (planB.tipo == DramaResolucion.Tipo.EMBEBIDA) {
                        mostrarEmbedEnLaVentana(planB);
                    } else {
                        // v7.38.4 — una DIRECTA con su UA y su Referer, no el UA de
                        // escritorio de siempre: el CDN del proveedor contesta 403 si
                        // no le llega el mismo UA con el que se pidió el enlace
                        // (medido con primetv: 403 con los dos UA habituales).
                        probarDirectaConSusCabeceras(planB);
                    }
                    return;
                }

                // v7.38.4 — «considerar más servidores del mismo canal»: si a esta
                // directa no le quedan reintentos, se prueban los demás servidores
                // (otra directa, o el embed) en vez de quedarse en el mismo bucle.
                if (errorRefreshCount >= MAX_REFRESH_RETRIES && !embeddedEnVentana) {
                    DramaResolucion.Opcion otra = siguienteOpcionUtil();
                    if (otra != null) {
                        log("↪️ esta directa no da vídeo; se prueba otro servidor del canal -> "
                                + otra.etiqueta());
                        errorRefreshCount = 0;
                        lastRefreshTime = System.currentTimeMillis();
                        try { player.stop(); } catch (Throwable ignored) { }
                        if (otra.tipo == DramaResolucion.Tipo.EMBEBIDA) {
                            mostrarEmbedEnLaVentana(otra);
                        } else {
                            probarDirectaConSusCabeceras(otra);
                        }
                        return;
                    }
                }

                if (!isNetworkAvailable()) {
                    log("🚫 Sin conexión. No se reintentará hasta que vuelva la red.");
                    return;
                }

                long now = System.currentTimeMillis();
                if (now - lastRefreshTime < MIN_REFRESH_INTERVAL_MS) {
                    log("⏱️ Refresco demasiado rápido, esperando...");
                    mainHandler.postDelayed(() -> {
                        if (!isResolving && errorRefreshCount < MAX_REFRESH_RETRIES) {
                            if (streamDirecto != null && !streamDirecto.isEmpty()) {
                                resolverStreamDirecto(streamDirecto, streamCookies, streamReferer, streamOrigin);
                            } else {
                                resolverStreamEnSegundoPlano();
                            }
                        }
                    }, MIN_REFRESH_INTERVAL_MS - (now - lastRefreshTime));
                    return;
                }

                // v7.22: si estabamos en el camino sin relay, el reintento va por el
                // relay de siempre (que es lo que funcionaba antes de quitarlo).
                if (paginaSinRelay != null) {
                    relayForzado = true;
                    paginaSinRelay = null;
                    log("↩️ el camino sin relay falló — el reintento va por el relay");
                }

                streamReady = false;
                if (!isResolving && errorRefreshCount < MAX_REFRESH_RETRIES) {
                    errorRefreshCount++;
                    lastRefreshTime = System.currentTimeMillis();
                    log("⚠️ Refrescando stream (intento " + errorRefreshCount + "/" + MAX_REFRESH_RETRIES + ")...");
                    if (streamDirecto != null && !streamDirecto.isEmpty()) {
                        resolverStreamDirecto(streamDirecto, streamCookies, streamReferer, streamOrigin);
                    } else {
                        resolverStreamEnSegundoPlano();
                    }
                } else if (errorRefreshCount >= MAX_REFRESH_RETRIES) {
                    log("❌ Máximo de refrescos alcanzado. Abortando.");
                    // v3.2.66: esta opcion agoto sus reintentos — queda marcada
                    // con una ✕ y elige el usuario. Sin salto automatico a otro
                    // servidor (al reves que dzasports, a pedido del usuario).
                    if (opcionActual >= 0 && opcionesServidor.size() > 1) {
                        opcionesFallidas.add(opcionActual);
                        pintarChipsServidores();
                        showControls();
                        android.widget.Toast.makeText(PlayerActivity.this,
                                R.string.player_opcion_fallo,
                                android.widget.Toast.LENGTH_LONG).show();
                    }
                    mainHandler.postDelayed(() -> errorRefreshCount = 0, 30000);
                    if (webViewFallback != null && webViewFallback.getVisibility() != View.VISIBLE) {
                        log("🌐 Reproducción ExoPlayer fallida, abriendo reproductor web.");
                        isResolving = false;
                        usarWebViewOcultoParaResolver();
                    }
                }
            }

            @Override
            public void onTracksChanged(Tracks tracks) {
                revisarSiHayVideo(tracks);
            }

            @Override
            public void onPlaybackStateChanged(int state) {
                switch (state) {
                    case Player.STATE_BUFFERING:
                        log("Buffering...");
                        mostrarCargando();
                        break;
                    case Player.STATE_READY:
                        log("✅ Ready");
                        // v7.38.3 — si hay imagen, esta opción ya no está «fallida».
                        if (opcionActual >= 0) opcionesFallidas.remove(opcionActual);
                        // v7.37.17: el 200 no garantiza que sea el canal. El CDN de
                        // Drama sirve la tarjeta rusa «ОТСУТСТВУЕТ ПОДПИСКА» como si
                        // fuera video (visto en beIN Fr1). Se comprueba la cabeza del
                        // playlist y, si es aviso, se salta al embed.
                        if (urlEnReproduccion != null && !embeddedEnVentana) {
                            revisarSiElStreamEsUnAviso(urlEnReproduccion);
                        }
                        RegistroCarga.paso("primer fotograma (ExoPlayer)");
                        RegistroCarga.fin();
                        errorRefreshCount = 0;
                        updatePlayPauseButton();
                        ocultarCargando();
                        ocultarErrorPlayer();
                        showControls();
                        break;
                    case Player.STATE_ENDED:
                        log("Ended");
                        updatePlayPauseButton();
                        ocultarCargando();
                        showControls();
                        break;
                    case Player.STATE_IDLE: log("Idle"); break;
                }
            }

            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                updatePlayPauseButton();
                actualizarAccionesPip();
            }

            @Override
            public void onVideoSizeChanged(androidx.media3.common.VideoSize videoSize) {
                // Si el video tiene otra relacion de aspecto (4:3, 21:9...) se
                // reajusta la ventana flotante.
                if (isInPipMode) actualizarAccionesPip();
            }
        });
    }

    /** spec: el cargando aparece al cargar y se oculta a los 8 s o en cuanto
     *  el video esta listo, lo que antes pase. */
    private void mostrarCargando() {
        Runnable iniciarCarga = () -> {
            cargaEnCurso = true;
            ocultarControlesPorCarga();
            ocultarErrorPlayer();
            if (cargandoPlayer != null) cargandoPlayer.setVisibility(View.VISIBLE);
            mainHandler.removeCallbacks(ocultarCargandoRun);
            mainHandler.postDelayed(ocultarCargandoRun, 8000);
        };
        if (Looper.myLooper() == Looper.getMainLooper()) iniciarCarga.run();
        else mainHandler.post(iniciarCarga);
    }

    private void ocultarControlesPorCarga() {
        mainHandler.removeCallbacks(hideControlsRunnable);
        controlesVisibles = false;
        View controlesPrincipales = centerControls != null
                ? centerControls : controlsOverlay;
        if (controlesPrincipales != null) {
            controlesPrincipales.animate().cancel();
            controlesPrincipales.setAlpha(1f);
            controlesPrincipales.setVisibility(View.GONE);
        }
        aplicarVisibilidadExtra(false);
        stopSeekUpdate();
    }

    private void mostrarErrorPlayer(int mensajeResId) {
        mensajeErrorPlayerResId = mensajeResId;
        Toast.makeText(getApplicationContext(), mensajeResId, Toast.LENGTH_SHORT).show();
    }

    private void ocultarErrorPlayer() {
        mensajeErrorPlayerResId = 0;
    }

    /**
     * v7.38.5 — el canal se quedó sin servidores que probar. Antes esto se contaba con
     * el mismo texto que el clip de relleno («Servidor no disponible por ahora»), que
     * se lee como «la app está rota» y no dice lo que de verdad pasa ni que hay una
     * cura trabajando en segundo plano.
     */
    private void mostrarAvisoSinMasServidores() {
        Toast.makeText(getApplicationContext(),
                R.string.player_sin_mas_servidores, Toast.LENGTH_LONG).show();
    }

    private void ocultarCargando() {
        Runnable terminarCarga = () -> {
            cargaEnCurso = false;
            if (cargandoPlayer != null) cargandoPlayer.setVisibility(View.GONE);
            mainHandler.removeCallbacks(ocultarCargandoRun);
        };
        if (Looper.myLooper() == Looper.getMainLooper()) terminarCarga.run();
        else mainHandler.post(terminarCarga);
    }

    private void setupWebViewFallback() {
        webViewFallback = findViewById(R.id.webPlayable);
        if (webViewFallback == null) return;

        WebSettings settings = webViewFallback.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        // v7.37.16: el UA de escritorio de siempre pero con el Chrome al dia: los
        // embeds (ordernap/quotarevival) mandan a la landing si ven Chrome/124.
        settings.setUserAgentString(uaDelMarco(StreamResolver.DESKTOP_USER_AGENT));
        settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }
        try { webViewFallback.addJavascriptInterface(new PuenteEmbed(), "AppPuente"); }
        catch (Throwable ignored) { }
        CookieManager.getInstance().setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            CookieManager.getInstance().setAcceptThirdPartyCookies(webViewFallback, true);
        }

        webViewFallback.setWebViewClient(new WebViewClient() {
            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                handler.proceed();
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                // v7.37.16: con el embed en la ventana, un salto de pagina NO se navega:
                // se queda reproduciendo el canal (si saltara, se perderia).
                if (embeddedEnVentana) return true;
                view.loadUrl(url);
                return true;
            }

            /**
             * Si por el WebView OCULTO pasa un playlist HLS, se reproduce con
             * ExoPlayer. El WebView solo sirve para que la página ejecute su
             * JavaScript; NUNCA se muestra al usuario.
             */
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                String u = (request.getUrl() == null) ? null : request.getUrl().toString();
                // v7.37.19 — LA SEÑAL BUENA. Medido 2026-11-17 15:47 en el movil del
                // usuario: el embed (ordernap -> iframe -> su reproductor) pidio
                // segmentos .ts y el log no lo vio, porque aqui solo se miraban los
                // .m3u8. El video se estaba viendo y la app se creia que no.
                if (u != null && esSegmentoDeVideo(u)) { marcarVideoDelEmbedVisto(); }
                // v7.38.0 — el caso «el canal manda JS en vez de un m3u8»: el embed
                // fabrica el playlist con su reproductor. Se guarda el CANDIDATO con
                // las cabeceras EXACTAS con las que lo pidió y, si el vídeo se ve, se
                // pasa a ExoPlayer (sin anuncios y con los controles de la app).
                if (u != null && esPlaylistCazable(u) && embeddedEnVentana) {
                    recordarPlaylistCazado(u, request.getRequestHeaders());
                }
                if (u != null && u.contains(".m3u8") && !streamReady) {
                    String ref = (request.getRequestHeaders() == null) ? null
                            : request.getRequestHeaders().get("Referer");
                    final String m3u8 = u;
                    final String referer = (ref != null && !ref.isEmpty()) ? ref : urlIframeInicial;
                    mainHandler.post(() -> {
                        if (streamReady || isFinishing()) return;
                        log("🎯 Stream visto desde el WebView oculto");
                        try {
                            webViewFallback.stopLoading();
                            webViewFallback.setVisibility(View.GONE);
                        } catch (Throwable ignored) { }
                        resolverStreamDirecto(m3u8, "", referer, urlIframeInicial);
                    });
                }
                return super.shouldInterceptRequest(view, request);
            }
        });

        webViewFallback.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                super.onProgressChanged(view, newProgress);
                if (embeddedEnVentana && newProgress > 70) {
                    // el embed ya esta pintando: se esconde la carga
                    ocultarCargando();
                    log("plan B: embed cargado en la ventana (" + newProgress + "%)");
                }
            }
        });
    }

    /**
     * Último recurso: el WebView se usa OCULTO, solo para que la página ejecute
     * su JavaScript (hay reproductores que arman el stream con JS y no queda
     * otra). En cuanto aparece un playlist HLS se reproduce con ExoPlayer.
     *
     * El WebView NUNCA se muestra: antes salía la página entera en pantalla y
     * eso es justo lo que no se quiere. Si de aquí tampoco sale nada, se avisa
     * al usuario en vez de dejarle viendo una web.
     */
    // ═══════════════════════════════════════════════════════════════════════
    // v7.37.16 — PLAN B DE VERDAD (medido 2026-11-17, beIN Sports de Drama)
    //
    // El plan B que había cargaba el embed DIRECTO (ordernap.net/e/xxx) en el
    // WebView. Medido en Chromium con el UA de la app: esas páginas hacen
    // `if (window == window.top) location = "/"` y como el WebView es el tope,
    // acaban en la landing del proveedor (bstream.st) y NO dan playlist. Por eso
    // beIN siempre se quedaba sin stream. Dentro de un iframe SÍ funcionan:
    //   - beIN Sports 1  -> embed z7iiiknxnuqjk -> HLS 200 -> vídeo 1280x720 (medido)
    //   - beIN Sports 2,5,6 y News -> ídem.
    // Y los segmentos .ts del CDN exigen el MISMO UA que pidió el m3u8 (verificado:
    // con otro UA el playlist da 403), así que no se puede pescar el m3u8 desde
    // fuera y dárselo a ExoPlayer: hay que dejar que el embed reproduzca solo,
    // dentro del WebView.
    //
    // Regla del usuario (v7.37.5 y siguientes): el WebView NUNCA a pantalla
    // completa tapando el reproductor. Se ve DENTRO de la ventana del vídeo, con
    // los controles de la app encima; al tocar para que salgan los controles, el
    // embed se congela (poner pausa) para que no asomen sus botones ni se
    // desincronice el audio.
    // ═══════════════════════════════════════════════════════════════════════
    private static final String EMBED_QUE_EXIGE_IFRAME = "ordernap.net";
    private static final String EMBED_QUE_EXIGE_IFRAME_2 = "quotarevival.net";

    /** ¿Este enlace es de los que se cierran si no van dentro de un iframe? Puro. */
    public static boolean esEmbedQueExigeIframe(String url) {
        if (url == null) return false;
        String u = url.toLowerCase();
        return u.contains(EMBED_QUE_EXIGE_IFRAME) || u.contains(EMBED_QUE_EXIGE_IFRAME_2);
    }

    /**
     * HTML del marco: mete `embed` en un iframe del tamaño de la ventana. El
     * reproductor del embed ve un `window.top` distinto y se queda quieto en su
     * sitio en vez de saltar a la landing. El body es negro para que no se vea
     * ni un borde blanco entre el vídeo y el marco. Puro (se prueba en JVM).
     */
    public static String htmlMarcoParaEmbed(String embed) {
        String u = embed == null ? "" : embed.trim();
        if (u.isEmpty()) return null;
        String uSegura = u.replace("&", "&amp;").replace("\"", "&quot;")
                .replace("<", "&lt;").replace(">", "&gt;");
        return "<!doctype html><html><head><meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
                + "<style>html,body{margin:0;padding:0;background:#000;height:100%;overflow:hidden}"
                + "iframe{border:0;width:100%;height:100%;display:block}</style></head>"
                + "<body><iframe src=\"" + uSegura + "\" allow=\"autoplay; fullscreen; encrypted-media\""
                + " allowfullscreen referrerpolicy=\"no-referrer-when-downgrade\"></iframe></body></html>";
    }

    /**
     * v7.37.18 — el UA del marco. <b>Tiene que ser de MÓVIL Android.</b>
     *
     * <p><b>Medido 2026-11-17 20:2x</b> (Chromium dentro de iframe, 1280x720):
     * <pre>
     *   ordernap.net/e/z7iiiknxnuqjk
     *     UA escritorio Windows Chrome/131 ....... 403 Forbidden (nginx, página en blanco)
     *     UA Android Chrome/131 (móvil) ......... 200 y beIN Sports 1 en vivo (NIG-MAR)
     *   quotarevival.net/e/yxtmusy4xj8tj
     *     UA móvil ............................. «NOT ALLOWED · domain protected»
     * </pre>
     * La v7.37.16 puso aquí el UA de ESCRITORIO «con el Chrome al día» y eso es
     * exactamente lo que hacía que beIN no abriera nunca: el embed ni siquiera
     * cargaba. Ahora se usa el UA de móvil de siempre, sin «wv» (el WebView
     * añade «; wv» si le da la gana y algunos embeds lo detectan y cierran). */
    public static String uaDelMarco(String ua) {
        return UA_DEL_EMBED;
    }

    /** UA de móvil Android, Chrome al día. Fijo a propósito: es lo único que el
     *  embed acepta (ver {@link #uaDelMarco(String)}). */
    public static final String UA_DEL_EMBED =
            "Mozilla/5.0 (Linux; Android 13; SM-A536B) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36";

    /** ¿Ese embed es de los que sabemos que están muertos? Puro. */
    public static boolean esEmbedMuerto(String url) {
        return DramaSesion.esEmbedMuerto(url);
    }

    /**
     * v7.37.18 — HTML del marco SIN anuncios, medido contra el embed real.
     *
     * <p>El embed abre capas de publicidad encima del vídeo (medido: peticiones a
     * {@code adsco.re} y compañía) y un «×» que nunca se pulsa solo. Aquí se le
     * tapa con una hoja propia (dentro del propio iframe; al DOM de dentro no se
     * llega por CORS) y se deja el botón de sonido de la app. Puro: se prueba en
     * JVM.
     */
    public static String htmlMarcoSinEstorbos(String embed) {
        return htmlMarcoSinEstorbos(embed, null);
    }

    /** Igual, pero avisando a la app ({@code AppPuente}) cuando el vídeo avanza de
     *  verdad. Puro cuando {@code puente} es null (se prueba en JVM). */
    public static String htmlMarcoSinEstorbos(String embed, String puente) {
        String base = htmlMarcoParaEmbed(embed);
        if (base == null) return null;
        String avisar = (puente == null || puente.isEmpty()) ? ""
                : ("if(t>1&&!avisado){avisado=true;try{window." + puente + ".videoVivo(t);}catch(e){}}");
        String extra = "<script>var avisado=false;"
                // si el vídeo de dentro se queda parado, se le da al play cada 4 s
                + "setInterval(function(){try{var f=document.querySelector('iframe');"
                + "var d=f&&f.contentDocument;if(!d)return;var v=d.querySelector('video');"
                + "if(v&&v.paused&&v.readyState>2){v.play().catch(function(){});}"
                + "var t=v?v.currentTime:0;" + avisar + "}catch(e){}},4000);"
                + "</script>";
        return base.replace("</body>", extra + "</body>");
    }

    /**
     * Plan B real: pinta el embed DENTRO de la ventana del vídeo (WebView con el
     * marco de iframe) y deja el sonido al WebView. No tapa los controles: el
     * WebView queda por debajo de la capa de controles y, cuando el usuario los
     * saca, se pausa el embed (`window.top.pausarEmbed()`), que lo único que hace
     * es pulsar el <video> de adentro. Se puede volver al player nativo con el
     * chip de calidad o con el botón de reintentar.
     */
    /**
     * v7.38.0 — trae el bloque {@code live} EN CRUDO del canal y deja la lista
     * completa de servidores lista para el plan B. Es un POST extra (≈1,2 s) y va
     * en el mismo hilo de fondo que el resto de la resolución, nunca en el de UI.
     */
    private void cargarServidoresDelCanal(final String canal, final String sesion) {
        try {
            com.google.gson.JsonObject live = DramaSesion.glasbiCrudo(canal, sesion);
            if (live == null) {
                log("servidores: glasbi no dio el bloque live en crudo; se usa la lista cocinada");
                return;
            }
            streamUrl = DramaSesion.textoDeLive(live, "url");
            streamBackup = DramaSesion.textoDeLive(live, "backup");
            java.util.List<DramaResolucion.Opcion> todas =
                    DramaResolucion.opciones(streamUrl, streamBackup);
            java.util.List<DramaResolucion.Opcion> utiles =
                    DramaResolucion.utiles(streamUrl, streamBackup);
            log("servidores del canal: " + todas.size() + " · útiles " + utiles.size());
            for (DramaResolucion.Opcion o : todas) log("   · " + o.etiqueta());
        } catch (Throwable t) {
            log("servidores: fallo al leer el crudo (" + t.getClass().getSimpleName() + ")");
        }
    }

    /**
     * v7.38.4 — reproduce una directa del canal con EL UA Y EL REFERER de esa opción.
     *
     * <p>El CDN del proveedor exige el UA con el que se pidió el enlace; con otro
     * contesta 403 (medido con {@code primetv.cc.cd}: 403 con «Mozilla» y con el UA
     * de móvil habitual). Por eso cada directa se prueba con lo suyo, no con el UA
     * de escritorio global.</p>
     */
    private void probarDirectaConSusCabeceras(DramaResolucion.Opcion opcion) {
        if (opcion == null || opcion.url == null) { avisarSinStream(); return; }
        String ua = DramaResolucion.pareceUnUa(opcion.ua) ? opcion.ua : UA_DEL_EMBED;
        streamCabeceras = "User-Agent: " + ua;
        if (opcion.referer != null && !opcion.referer.isEmpty()) {
            streamReferer = opcion.referer;
            streamOrigin = origenDe(opcion.referer);
            streamCabeceras = streamCabeceras + "\nReferer: " + opcion.referer;
        } else {
            streamReferer = null;
            streamOrigin = null;
        }
        log("🎬 directa con su UA/Referer -> " + opcion.etiqueta());
        resolverStreamDirecto(opcion.url, null, streamReferer, streamOrigin);
    }

    /** La opción de Drama que se está mostrando como embed (su UA manda). */
    private DramaResolucion.Opcion embedEnCurso = null;

    /**
     * Vuelve a la lista de servidores del canal y devuelve el siguiente que aún no
     * se haya probado: primero embebidas, luego directas. Sin repetir y sin
     * marcadores. Así no queda ningún servidor sin intentar.
     */
    private DramaResolucion.Opcion siguienteOpcionUtil() {
        return siguienteOpcionUtil(streamUrl, streamBackup, servidoresProbados);
    }

    /**
     * v7.38.4 — elige el siguiente servidor del canal que aún no se probó: primero
     * las embebidas (en orden) y después las directas. Puro y testeable.
     *
     * @param probados URLs ya intentadas en este canal (se saltan)
     * @return la siguiente opción, o {@code null} si ya no queda ninguna
     */
    public static DramaResolucion.Opcion siguienteOpcionUtil(
            String url, String backup,
            java.util.Collection<String> probados) {
        // v7.38.5 — la decisión vive en UN solo sitio (DramaResolucion.siguiente), que
        // es pura y tiene sus propias pruebas (PlanBTest). Aquí solo se le pasa la
        // lista del canal. Sin esto había dos selectores con reglas parecidas pero no
        // iguales, y el bug del `return null` vivía en el tercero.
        return DramaResolucion.siguiente(
                DramaResolucion.utiles(url, backup), probados, null, null);
    }

    private void mostrarEmbedEnLaVentana(DramaResolucion.Opcion opcion) {
        if (opcion == null) { avisarSinStream(); return; }
        if (!servidoresProbados.contains(opcion.url)) servidoresProbados.add(opcion.url);
        embedEnCurso = opcion;
        mostrarEmbedEnLaVentana(opcion.url, opcion.ua, opcion.referer);
    }

    private void mostrarEmbedEnLaVentana(final String embed) {
        DramaResolucion.Opcion conUa = buscarOpcion(embed);
        mostrarEmbedEnLaVentana(embed,
                conUa == null ? null : conUa.ua,
                conUa == null ? null : conUa.referer);
    }

    /** Busca en la lista de servidores del canal la opción con esa URL. */
    private DramaResolucion.Opcion buscarOpcion(String url) {
        for (DramaResolucion.Opcion o : DramaResolucion.opciones(streamUrl, streamBackup)) {
            if (o.url.equals(url)) return o;
        }
        return null;
    }

    private void mostrarEmbedEnLaVentana(final String embed, final String uaDelEmbed,
                                         final String refererDelEmbed) {
        runOnUiThread(() -> {
            if (webViewFallback == null || embed == null || embed.isEmpty()) {
                avisarSinStream();
                return;
            }
            // v7.38.0 — el UA que manda el PROVEEDOR para este servidor, si lo hay.
            // Medido: el CDN del embed solo acepta el UA con el que se pidió el
            // token; con otro contesta 403. Si el proveedor no manda ninguno, se
            // usa el de móvil que sí funciona.
            try {
                String ua = (uaDelEmbed != null && DramaResolucion.pareceUnUa(uaDelEmbed))
                        ? uaDelEmbed : UA_DEL_EMBED;
                webViewFallback.getSettings().setUserAgentString(ua);
                log("plan B: UA del embed = " + ua);
            } catch (Throwable ignored) { }
            String html = htmlMarcoSinEstorbos(embed, "AppPuente");
            if (html == null) {
                avisarSinStream();
                return;
            }
            try {
                webViewFallback.setVisibility(View.VISIBLE);
                // Por debajo de los controles y de los avisos: la ventana manda.
                try { webViewFallback.setZ(0f); } catch (Throwable ignored) { }
                webViewFallback.loadDataWithBaseURL(embed, html, "text/html", "utf-8", null);
            } catch (Throwable t) {
                log("❌ no se pudo abrir el embed en la ventana: " + t.getMessage());
                avisarSinStream();
                return;
            }
            embeddedEnVentana = true;
            videoDeEmbedAvanza = false;
            peticionesDeVideo = 0;
            // v7.38.4 — FALLO REAL del bucle: solo se marcaba como «probado» al pasar
            // por el camino de arriba, así que el temporizador de 30 s volvía a poner
            // EL MISMO embed una y otra vez (tu log: ordernap 19:02:04, 19:02:19,
            // otra vez ordernap). Aquí se marca siempre, antes de nada.
            if (!servidoresProbados.contains(embed)) servidoresProbados.add(embed);
            segundaOportunidadDada = false;
            streamReady = true;          // hay imagen: no se dispara el aviso de «sin stream»
            isResolving = false;
            ocultarCargando();
            log("✅ plan B: embed dentro de la ventana -> " + embed);
            Toast.makeText(PlayerActivity.this,
                    "Reproduciendo por el servidor del canal (plan B)", Toast.LENGTH_SHORT).show();
            // Si a los 15 s no hay imagen, se avisa igual (el embed a veces tarda).
            // v7.37.18: si a los 15 s sigue sin verse nada, se prueba el SIGUIENTE
            // embed del canal en vez de dejar el cuadro negro para siempre.
            final String elQueSePuso = embed;
            mainHandler.postDelayed(() -> {
                if (!embeddedEnVentana || isFinishing()) return;
                if (videoDeEmbedAvanza) {
                    log("plan B: hay imagen del embed (vídeo confirmado)");
                    return;
                }
                // v7.38.1 — FALLO REAL: beIN 1 trae DOS embeds y el segundo
                // (quotarevival) está muerto. Aquí se cambiaba al segundo aunque el
                // primero estuviera emitiendo (una primera petición .ts tardía bastaba
                // para tirarlo) y el usuario se quedaba con el cuadro negro.
                if (peticionesDeVideo > 0) {
                    log("plan B: el embed ya pide vídeo (" + peticionesDeVideo
                            + " peticiones); se queda");
                    return;
                }
                if (!segundaOportunidadDada) {
                    segundaOportunidadDada = true;
                    log("plan B: 15 s sin vídeo; se le dan 15 s más antes de cambiar");
                    mainHandler.postDelayed(this::revisarSiElEmbedArranco, 15000);
                    return;
                }
                log("plan B: el embed no arranca -> siguiente servidor");
                if (!servidoresProbados.contains(elQueSePuso)) servidoresProbados.add(elQueSePuso);
                DramaResolucion.Opcion siguiente = siguienteOpcionUtil();
                if (siguiente != null) {
                    log("plan B: probando -> " + siguiente.etiqueta());
                    segundaOportunidadDada = false;
                    mostrarEmbedEnLaVentana(siguiente);
                } else {
                    log("plan B: se han probado TODOS los servidores del canal");
                }
            }, 15000);
        });
    }

    /** true si lo que se está viendo es el embed (plan B), no ExoPlayer. */
    private boolean embeddedEnVentana = false;

    /** v7.38.1 — peticiones de vídeo del embed y su segunda oportunidad. */
    private int peticionesDeVideo = 0;
    private boolean segundaOportunidadDada = false;

    /** A los 30 s: ¿arrancó el embed? Si no, se pasa al siguiente servidor. */
    private void revisarSiElEmbedArranco() {
        if (!embeddedEnVentana || isFinishing()) return;
        if (peticionesDeVideo > 0 || videoDeEmbedAvanza) {
            log("plan B: el embed arrancó (" + peticionesDeVideo + " peticiones de vídeo)");
            return;
        }
        // v7.38.4 — antes de cambiar, una última mirada al vídeo (en tu log el embed
        // confirmó imagen a los 15 s de este punto y la app seguía cambiando).
        if (videoDeEmbedAvanza) {
            log("plan B: hay imagen del embed (vídeo confirmado)");
            return;
        }
        log("plan B: el embed no arranca ni con 30 s -> siguiente servidor");
        DramaResolucion.Opcion siguiente = siguienteOpcionUtil();
        if (siguiente != null) {
            log("plan B: probando -> " + siguiente.etiqueta());
            segundaOportunidadDada = false;
            mostrarEmbedEnLaVentana(siguiente);
        } else {
            log("plan B: se han probado TODOS los servidores del canal");
            avisarSinStream();
        }
    }

    /** v7.38.0 — playlist cazado dentro del embed, con sus cabeceras exactas. */
    private String playlistCazado = null;
    private String playlistCazadoUa = null;
    private String playlistCazadoReferer = null;
    private boolean yaSeHizoNativo = false;

    /** ¿Merece la pena quedarse con esta petición para dársela a ExoPlayer? Puro. */
    public static boolean esPlaylistCazable(String url) {
        if (url == null) return false;
        String u = url.toLowerCase(java.util.Locale.ROOT);
        return u.contains(".m3u8") && !u.contains(".m3u8.bak");
    }

    /** Guarda el playlist del embed con las cabeceras con las que lo pidió su JS. */
    private void recordarPlaylistCazado(String url,
                                        java.util.Map<String, String> cabeceras) {
        if (url == null) return;
        if (playlistCazado != null && playlistCazado.equals(url)) return;
        String ua = null, ref = null;
        if (cabeceras != null) {
            for (java.util.Map.Entry<String, String> e : cabeceras.entrySet()) {
                if (e.getKey() == null) continue;
                String k = e.getKey().toLowerCase(java.util.Locale.ROOT);
                if (k.equals("user-agent")) ua = e.getValue();
                if (k.equals("referer")) ref = e.getValue();
            }
        }
        playlistCazado = url;
        playlistCazadoUa = (ua != null && !ua.isEmpty()) ? ua : UA_DEL_EMBED;
        playlistCazadoReferer = (ref != null && !ref.isEmpty()) ? ref : "https://ordernap.net/";
        log("🎯 playlist cazado en el embed -> " + url);
    }

    /**
     * v7.38.0 — del WebView a ExoPlayer: se cierra el embed (y con él sus anuncios)
     * y se reproduce el playlist que su propio reproductor estaba usando, con el
     * MISMO UA y el mismo Referer (medido: con otro UA el CDN contesta 403).
     */
    private void pasarANativoSiSePuede() {
        if (yaSeHizoNativo || playlistCazado == null || isFinishing()) return;
        yaSeHizoNativo = true;
        final String url = playlistCazado;
        log("↪️ el embed manda JS; se pasa a ExoPlayer con el playlist cazado");
        try {
            webViewFallback.stopLoading();
            webViewFallback.setVisibility(View.GONE);
        } catch (Throwable ignored) { }
        embeddedEnVentana = false;
        streamReferer = playlistCazadoReferer;
        streamOrigin = origenDe(playlistCazadoReferer);
        streamCookies = null;
        resolverStreamDirecto(url, null, playlistCazadoReferer, streamOrigin);
        // si el nativo no arranca en 12 s, se vuelve al embed (que sí se estaba viendo)
        mainHandler.postDelayed(() -> {
            if (!isFinishing() && !streamReady) {
                log("↪️ el playlist cazado no arrancó; se vuelve al embed");
                yaSeHizoNativo = false;
                if (embedEnCurso != null) mostrarEmbedEnLaVentana(embedEnCurso);
            }
        }, 12000);
    }

    /** «https://a.b/c» -> «https://a.b». Para el Origin. Puro. */
    public static String origenDe(String url) {
        if (url == null) return null;
        int i = url.indexOf("://");
        if (i < 0) return null;
        int fin = url.indexOf('/', i + 3);
        return fin < 0 ? url : url.substring(0, fin);
    }

    /** v7.37.18: el <video> de dentro del embed ya avanzo (hay canal, no cartel). */
    private boolean videoDeEmbedAvanza = false;

    /** Puente para que el marco avise cuando el vídeo del embed arranca de verdad. */
    public class PuenteEmbed {
        @android.webkit.JavascriptInterface
        public void videoVivo(float t) {
            runOnUiThread(() -> {
                if (videoDeEmbedAvanza) return;
                videoDeEmbedAvanza = true;
                log("✅ plan B: el vídeo del embed avanza (t=" + t + " s)");
                ocultarCargando();
            });
        }
    }

    /** true si el embed (plan B) esta en pausa porque el usuario lo pidio. */
    private boolean embedPausado = false;

    /** v7.37.17: ya se detecto una tarjeta de aviso en este canal (no repetir). */
    private boolean avisoDeStreamYaVisto = false;

    /** v7.37.17: url que ExoPlayer tiene cargada (para revisarla al estar READY). */
    private String urlEnReproduccion = null;

    /** true si el embed (plan B) esta silenciado desde los controles de la app. */
    private boolean embedSilenciado = false;

    /**
     * v7.37.17 — ¿este stream es un canal o una TARJETA de "no hay suscripcion"?
     *
     * Medido 2026-11-17 en el movil del usuario con beIN Sports Fr1: el m3u8 del
     * backup responde 200, ExoPlayer lo reproduce sin quejarse y lo que sale en
     * pantalla es la tarjeta rusa «ОТСУТСТВУЕТ ПОДПИСКА» (no hay suscripcion) que
     * el proveedor sirve en lugar del canal. El usuario lo vio como «solo imagen
     * en ruso». Se baja la cabeza del playlist en un hilo aparte (nunca en el
     * principal) y, si es un aviso, se salta al embed del canal. Si el playlist es
     * normal, no se toca nada (el player ya esta reproduciendo).
     */
    private void revisarSiElStreamEsUnAviso(final String url) {
        if (url == null || url.isEmpty() || avisoDeStreamYaVisto) return;
        new Thread(() -> {
            String cabeza = "";
            try {
                androidx.media3.datasource.DataSource ds = dataSourceFactory.createDataSource();
                try {
                    androidx.media3.datasource.DataSpec spec =
                            new androidx.media3.datasource.DataSpec(android.net.Uri.parse(url));
                    ds.open(spec);
                    byte[] buf = new byte[4096];
                    int n = ds.read(buf, 0, buf.length);
                    if (n > 0) cabeza = new String(buf, 0, n, java.nio.charset.StandardCharsets.UTF_8);
                } finally {
                    try { ds.close(); } catch (Throwable ignored) { }
                }
            } catch (Throwable t) {
                return;   // sin red o sin permiso de lectura: no se decide nada
            }
            if (!DramaSesion.esAvisoSinCanal(cabeza)) return;
            avisoDeStreamYaVisto = true;
            final String embed = DramaSesion.primeraOpcionNoMarcador(streamOpcionesJson);
            runOnUiThread(() -> {
                log("🛑 el stream es una TARJETA de aviso (no hay canal): " + url);
                try { if (player != null) { player.pause(); player.stop(); } } catch (Throwable ignored) { }
                if (embed != null) {
                    log("↪️ se salta al embed del canal -> " + embed);
                    mostrarEmbedEnLaVentana(embed);
                } else {
                    avisarSinStream();
                }
            });
        }, "aviso-stream").start();
    }

    /** Silencia/desilencia el embed de dentro del iframe. */
    private void silenciarEmbed(boolean silenciar) {
        if (webViewFallback == null || !embeddedEnVentana) return;
        String js = "(function(){try{var f=document.querySelector('iframe');if(!f||!f.contentWindow)return;"
                + "var d=f.contentWindow.document;if(!d)return;var v=d.querySelector('video');if(!v)return;"
                + "v.muted=" + silenciar + ";}catch(e){}})();";
        try { webViewFallback.evaluateJavascript(js, null); } catch (Throwable ignored) { }
    }

    /** Pausa/reanuda el embed de dentro del iframe (lo llama la capa de controles). */
    private void pausarEmbed(boolean pausar) {
        if (webViewFallback == null || !embeddedEnVentana) return;
        String js = "(function(){try{var f=document.querySelector('iframe');if(!f||!f.contentWindow)return;"
                + "var d=f.contentWindow.document;if(!d)return;var v=d.querySelector('video');"
                + "if(!v)return;" + "if(" + pausar + "){v.pause();}else{v.play();}"
                + "}catch(e){}})();";
        try { webViewFallback.evaluateJavascript(js, null); } catch (Throwable ignored) { }
    }

    private void usarWebViewOcultoParaResolver() {
        runOnUiThread(() -> {
            if (webViewFallback == null) return;
            String url = urlIframeInicial;
            if (url == null || url.isEmpty() || !url.startsWith("http")) {
                log("❌ No hay URL válida que resolver.");
                avisarSinStream();
                return;
            }
            if (webViewOcultoYaProbado) {
                avisarSinStream();
                return;
            }
            webViewOcultoYaProbado = true;
            log("🕵️ Último recurso: WebView oculto para ejecutar el JS de la página");
            try {
                // v7.37.16: si lo que hay es un embed de los que exigen iframe, se
                // prueba PRIMERO el marco en la ventana (medido: así sí reproduce).
                if (esEmbedQueExigeIframe(url)) {
                    log("↪️ el enlace exige iframe (ordernap/quotarevival): marco en la ventana");
                    mostrarEmbedEnLaVentana(url);
                    return;
                }
                webViewFallback.setVisibility(View.GONE);   // NUNCA visible
                webViewFallback.stopLoading();
                webViewFallback.loadUrl(url);
            } catch (Throwable t) {
                avisarSinStream();
                return;
            }
            mainHandler.postDelayed(() -> {
                if (!streamReady && !isFinishing() && !cerrandoDesdePip) {
                    log("⚠️ Ni la cadena ni el WebView oculto encontraron el stream.");
                    avisarSinStream();
                }
            }, 20000);
        });
    }

    /**
     * v7.38.3 — ¿es un marcador {@code http://.LS.V2…}? Puro.
     *
     * <p>Ese marcador no es un stream: es la marca que deja el proveedor para que el
     * redirector del sitio ({@code glbr}, con id/url/agent) devuelva la URL real.
     * Sin ese redirector —hoy 404 en el host nuevo y 522 en el viejo— ningún WebView
     * puede fabricar el enlace, así que no se le dan 40 s de «cargando».</p>
     */
    public static boolean esMarcadorLs(String url) {
        if (url == null) return false;
        return url.contains(".LS.") || url.startsWith("http://.LS") || url.startsWith("https://.LS");
    }

    /** Avisa al usuario de que este enlace no da stream, en vez de mostrarle una web. */
    private void avisarSinStream() {
        // v7.38.3 — marcador .LS.: el que resuelve es el proveedor, no el WebView.
        if (esMarcadorLs(streamDirecto) || esMarcadorLs(urlIframeInicial)) {
            log("🔒 canal con marcador .LS.: lo resuelve el redirector del proveedor "
                    + "(glbr), que ahora mismo no responde");
            runOnUiThread(() -> {
                ocultarCargando();
                showControls();
                try {
                    Toast.makeText(this, "Este canal lo sirve el proveedor y su redirector "
                            + "no responde ahora mismo. Prueba otro canal.",
                            Toast.LENGTH_LONG).show();
                } catch (Throwable ignored) { }
            });
            return;
        }
        // v7.37.16: antes de rendirse, si el canal trajo un embed (ordernap /
        // quotarevival), se abre DENTRO de la ventana: está medido que así sí
        // reproduce beIN 1, 2, 5, 6 y News. Solo si no hay embed se avisa.
        String elegida = DramaSesion.primeraEmbebida(streamOpcionesJson);
        if (elegida == null) elegida = DramaSesion.primeraOpcionNoMarcador(streamOpcionesJson);
        final String embebida = elegida;
        if (embebida != null && !embeddedEnVentana && !isFinishing()) {
            log("↩️ sin stream nativo; se prueba el embed en la ventana -> " + embebida);
            mostrarEmbedEnLaVentana(embebida);
            return;
        }
        runOnUiThread(() -> {
            ocultarCargando();
            showControls();
            try {
                Toast.makeText(this, "No se pudo cargar este enlace. Prueba con otra opción del canal.",
                        Toast.LENGTH_LONG).show();
            } catch (Throwable ignored) { }
        });
    }
    /**
     * MEJORA 2026-09-28: elige el resolver según la URL.
     *
     * vsembed (y equivalentes con ds_lang=es o cloudorchestranova) no exponen el
     * m3u8 en el HTML: hay que pasar por vs_src -> cloudorchestranova ->
     * data.vidsrc.sh -> WASM -> token. Eso lo hace PelisStreamResolver.
     * StreamResolver solo usa WebView y con esos dominios se queda en timeout,
     * así que antes el reproductor caía al modo WebView aunque sí había stream.
     */
    private StreamResolver.StreamResult resolverInteligente(String url) throws Exception {
        // vimeus = el servidor LATINO (audio en español): resolver nativo, sin WebView.
        if (url != null && VimeusResolver.isVimeus(url)) {
            log("Usando VimeusResolver (Latino)...");
            try {
                PelisStreamResolver.StreamResult pr =
                        VimeusResolver.resolve(PlayerActivity.this, url);
                if (pr != null && pr.m3u8Url != null && !pr.m3u8Url.isEmpty()) {
                    log("✅ VimeusResolver devolvió master HLS en español");
                    return new StreamResolver.StreamResult(pr.m3u8Url, pr.cookies,
                            pr.referer, pr.origin, pr.headers);
                }
            } catch (Throwable t) {
                log("⚠️ VimeusResolver falló: " + t.getMessage());
            }
        }
        // embed69 = copias LAT/ESP con PoW+AES: resolver nativo, sin WebView.
        if (url != null && Embed69Resolver.isEmbed69(url)) {
            log("Usando Embed69Resolver (LAT/ESP)...");
            try {
                PelisStreamResolver.StreamResult pr =
                        Embed69Resolver.resolve(PlayerActivity.this, url);
                if (pr != null && pr.m3u8Url != null && !pr.m3u8Url.isEmpty()) {
                    log("✅ Embed69Resolver devolvió master HLS");
                    return new StreamResolver.StreamResult(pr.m3u8Url, pr.cookies,
                            pr.referer, pr.origin, pr.headers);
                }
            } catch (Throwable t) {
                log("⚠️ Embed69Resolver falló: " + t.getMessage());
            }
        }
        if (url != null && (url.contains("vsembed") || url.contains("ds_lang=es")
                || url.contains("cloudorchestranova"))) {
            log("Usando PelisStreamResolver (vsembed WASM+Token)...");
            try {
                PelisStreamResolver.StreamResult pr =
                        PelisStreamResolver.resolveSynchronously(PlayerActivity.this, url);
                if (pr != null && pr.m3u8Url != null && !pr.m3u8Url.isEmpty()) {
                    log("✅ PelisStreamResolver devolvió master HLS");
                    return new StreamResolver.StreamResult(pr.m3u8Url, pr.cookies,
                            pr.referer, pr.origin, pr.headers);
                }
            } catch (Throwable t) {
                log("⚠️ PelisStreamResolver falló: " + t.getMessage());
            }
            log("⚠️ Sin stream por el camino rápido, probando resolver genérico...");
        }
        log("Resolviendo stream con StreamResolver (WebView)...");
        return StreamResolver.resolveSynchronously(PlayerActivity.this, url);
    }

    // ---------- v3.2.67: overlays estilo dzasports (cabecera + zapeo) ----------

    /** Ata las vistas que viven fuera de la barra de controles en AMBOS layouts. */
    private void atarOverlaysPlayer() {
        filaServidores = findViewById(R.id.filaServidores);
        contenedorServidores = findViewById(R.id.contenedorServidores);
        cabeceraPlayer = findViewById(R.id.cabeceraPlayer);
        tvTituloPlayer = findViewById(R.id.tvTituloPlayer);
        tiraCanalesPlayer = findViewById(R.id.tiraCanalesPlayer);
        scrollCanalesPlayer = findViewById(R.id.scrollCanalesPlayer);
        contenedorCanalesPlayer = findViewById(R.id.contenedorCanalesPlayer);
        android.view.View atras = findViewById(R.id.btnAtrasPlayer);
        if (atras != null) atras.setOnClickListener(v -> cerrarReproductor());
        // La altura de la zona de vídeo la manda el layout (300dp en retrato,
        // pantalla completa en paisaje): aquí ya no se recalcula.
    }

    private void pintarTituloPlayer() {
        if (tvTituloPlayer != null)
            tvTituloPlayer.setText(tituloCanalActual == null ? "" : tituloCanalActual);
    }

    private void prepararZapeoAgenda() {
        if (!getIntent().getBooleanExtra("agenda_evento", false)) return;

        Canal evento = new Canal();
        evento.title = tituloCanalActual == null ? "" : tituloCanalActual;
        evento.category = "Agenda";
        evento.options = new java.util.ArrayList<>();
        for (String[] servidor : opcionesServidor) {
            if (servidor[1] == null || servidor[1].trim().isEmpty()) continue;
            Canal.OpcionCanal opcion = new Canal.OpcionCanal();
            opcion.label = servidor[0];
            opcion.url = servidor[1];
            evento.options.add(opcion);
        }
        ZapeoCanales.fijar(java.util.Collections.singletonList(evento), evento);

        dza.fut.paipo.core.AppExecutors.enSerie(() -> {
            final java.util.List<Canal> seleccionados;
            try {
                seleccionados = ZapeoCanales.seleccionarCanalesAgenda(
                        evento, CatalogoJson.aCanales(getApplicationContext()), 10);
            } catch (RuntimeException e) {
                Log.e(TAG, "No se pudieron cargar canales aleatorios para la agenda", e);
                return;
            }
            mainHandler.post(() -> {
                if (isFinishing() || isDestroyed()) return;
                ZapeoCanales.fijar(seleccionados, evento);
                pintarTiraCanales();
            });
        });
    }

    private void actualizarIconoPantalla() {
        if (btnPantalla != null) btnPantalla.setImageResource(esHorizontal
                ? R.drawable.ic_salir_pantalla : R.drawable.ic_pantalla_completa);
    }

    /** Mantiene la visibilidad de canales al cargar; en PiP no se muestra overlay. */
    private void aplicarVisibilidadExtra(boolean controlesVisibles) {
        boolean fueraDePip = !isInPipMode;
        boolean mostrarCabecera = fueraDePip && !esHorizontal;
        boolean mostrarServidores = fueraDePip && opcionesServidor.size() > 1
                && (!esHorizontal || controlesVisibles);
        if (cabeceraPlayer != null) {
            cabeceraPlayer.setVisibility(mostrarCabecera ? View.VISIBLE : View.GONE);
        }
        if (filaServidores != null && (!cargaEnCurso || !fueraDePip)) {
            filaServidores.setVisibility(mostrarServidores ? View.VISIBLE : View.GONE);
        }
        // Barra de progreso: entra y sale con los controles, pero solo cuando el
        // contenido admite busqueda. En un canal en directo la duracion es
        // desconocida y seekTo no hace nada, asi que la barra se queda oculta en
        // vez de ensenar una barra que no obedece. Sin la guarda de carga: aqui
        // si hay que esconderla, porque durante la carga los controles estan
        // ocultos (controlesVisibles=false).
        if (filaProgreso != null) {
            boolean mostrarProgreso = fueraDePip && controlesVisibles && sePuedeBuscar();
            filaProgreso.setVisibility(mostrarProgreso ? View.VISIBLE : View.GONE);
        }
        if (tiraCanalesPlayer != null && (!cargaEnCurso || !fueraDePip)) {
            boolean mostrarCanales = fueraDePip && (!esHorizontal || controlesVisibles);
            tiraCanalesPlayer.setVisibility(mostrarCanales && ZapeoCanales.canales.size() > 1
                    ? View.VISIBLE : View.GONE);
        }
        // v7.8.3: en paisaje el chip de calidad es lo unico que queda flotando
        // sobre el video (la fila de servidores se fue), asi que en PiP tambien
        // se oculta. En los demas casos manda mostrarChipCalidad(), que es quien
        // sabe si el canal trajo opciones.
        if (chipCalidad != null && isInPipMode) chipCalidad.setVisibility(View.GONE);
    }

    /**
     * v7.34.1 — ¿se puede arrastrar la barra de progreso hasta donde uno quiera?
     *
     * <p>Sí en películas y episodios. <b>No en los canales en directo, y esta vez
     * es una comprobación explícita, no una consecuencia.</b>
     *
     * <p>El fallo de la v7.34.0: bastaba con {@code getDuration() > 0}. Pero un
     * canal en directo con ventana de DVR <b>publica una duración</b> —la de su
     * ventana— y admite buscar dentro de ella. ExoPlayer lleva DVR desde la 2.1.1
     * y hay issues abiertos por exactly esto («exoplayer now returns 60s as
     * duration for a stream source»). Resultado: la barra le salía al
     * reproductor de canales en directo y le rompía el estilo.
     *
     * <p>Ahora hacen falta las tres señales a la vez. Si alguna falta, no hay
     * barra. Es el fallo seguro: que la barra no aparezca es un informe; que
     * aparezca donde no debe es un reproductor roto.
     */
    private boolean sePuedeBuscar() {
        if (player == null) return false;
        if (player.isCurrentMediaItemLive()) return false;       // directo: nunca
        if (!player.isCurrentMediaItemSeekable()) return false;  // sin busqueda, no
        return player.getDuration() > 0;                         // y duracion ya conocida
    }

    /** La tira inferior de canales: tocar una tarjeta zapea sin salir. */
    private void pintarTiraCanales() {
        if (contenedorCanalesPlayer == null || tiraCanalesPlayer == null) return;
        contenedorCanalesPlayer.removeAllViews();
        tiraIndices.clear();
        tiraConstruyendo = false;
        final java.util.List<Canal> canales = ZapeoCanales.canales;
        if (canales.size() < 2) {
            tiraCanalesPlayer.setVisibility(View.GONE);
            return;
        }
        int centro = ZapeoCanales.indiceActual;
        if (centro < 0 || centro >= canales.size()) centro = 0;
        // v7.5: se empieza por una ventana alrededor del canal que se está viendo.
        tiraDesde = zapeoVentana(centro, canales.size(), ZAPEO_TANDA);
        tiraHasta = tiraDesde;
        atarScrollTira();
        extenderTira(canales, ZAPEO_TANDA);
        aplicarVisibilidadExtra(controlsOverlay != null
                && controlsOverlay.getVisibility() == View.VISIBLE);
        centrarTiraEn(centro);
    }

    /**
     * v7.5: por qué canal empieza la ventana de la tira de zapeo.
     *
     * <p>Puro y estático para poder probarlo sin Android. El contrato que arregla el
     * ANR es el de siempre: <b>como máximo una tanda de tarjetas por vez</b>, nunca la
     * lista entera. Con catálogos pequeños la ventana cubre todo y no se nota.
     */
    static int zapeoVentana(int centro, int total, int tanda) {
        if (total <= tanda) return 0;
        int ini = centro - tanda / 2;
        if (ini < 0) ini = 0;
        int maxIni = total - tanda;
        return Math.min(ini, maxIni);
    }

    /** Deja la tarjeta del canal actual a la vista (si ya está construida). */
    private void centrarTiraEn(final int real) {
        if (scrollCanalesPlayer == null) return;
        scrollCanalesPlayer.post(() -> {
            if (contenedorCanalesPlayer == null) return;
            int pos = tiraIndices.indexOf(Integer.valueOf(real));
            if (pos < 0 || pos >= contenedorCanalesPlayer.getChildCount()) return;
            android.view.View hijo = contenedorCanalesPlayer.getChildAt(pos);
            scrollCanalesPlayer.smoothScrollTo(
                    Math.max(0, hijo.getLeft() - (int) (16 * getResources().getDisplayMetrics().density)), 0);
        });
    }

    /** Al llegar al borde derecho se alarga la tira con la tanda siguiente. */
    private void atarScrollTira() {
        if (tiraScrollAtado || scrollCanalesPlayer == null) return;
        tiraScrollAtado = true;
        final float d = getResources().getDisplayMetrics().density;
        scrollCanalesPlayer.setOnScrollChangeListener((v, sx, sy, osx, ooy) -> {
            if (contenedorCanalesPlayer == null || tiraConstruyendo) return;
            int falta = contenedorCanalesPlayer.getWidth() - (sx + v.getWidth());
            if (falta < (int) (320 * d)) extenderTira(ZapeoCanales.canales, ZAPEO_TANDA);
        });
    }

    /**
     * Construye {@code cuantas} tarjetas más (de {@link #tiraHasta} en adelante) FUERA
     * del hilo principal y las pega todas de una. Los logos se piden a Glide ya en el
     * hilo principal, con el fragmento/actividad vivos.
     */
    private void extenderTira(final java.util.List<Canal> lista, final int cuantas) {
        if (tiraConstruyendo) return;
        final int desde = tiraHasta;
        final int hasta = Math.min(lista.size(), desde + cuantas);
        if (hasta <= desde) return;
        tiraConstruyendo = true;
        final float d = getResources().getDisplayMetrics().density;
        final boolean horizontal = esHorizontal;
        dza.fut.paipo.core.AppExecutors.enSerie(() -> {
            final java.util.List<android.view.View> lote = new java.util.ArrayList<>(hasta - desde);
            final java.util.List<android.widget.ImageView> logos = new java.util.ArrayList<>(hasta - desde);
            final java.util.List<Canal> conLogo = new java.util.ArrayList<>(hasta - desde);
            try {
                for (int i = desde; i < hasta; i++) {
                    final Canal c = lista.get(i);
                    final int real = i;
                    android.widget.LinearLayout tarjeta = new android.widget.LinearLayout(PlayerActivity.this);
                    tarjeta.setOrientation(android.widget.LinearLayout.VERTICAL);
                    tarjeta.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
                    tarjeta.setBackgroundResource(R.drawable.bg_tarjeta_canal);
                    tarjeta.setSelected(real == ZapeoCanales.indiceActual);
                    tarjeta.setPadding(horizontal ? 16 : 12, horizontal ? 12 : 8,
                            horizontal ? 16 : 12, horizontal ? 12 : 8);
                    android.widget.LinearLayout.LayoutParams lpT =
                            new android.widget.LinearLayout.LayoutParams((int) ((horizontal ? 120 : 90) * d),
                                    horizontal ? (int) (140 * d)
                                            : android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
                    if (horizontal) {
                        int margin = (int) (6 * d);
                        lpT.setMargins(margin, 0, margin, 0);
                    }
                    tarjeta.setLayoutParams(lpT);
                    android.widget.ImageView img = new android.widget.ImageView(PlayerActivity.this);
                    android.widget.LinearLayout.LayoutParams imageParams =
                            new android.widget.LinearLayout.LayoutParams(
                                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                    (int) ((horizontal ? 60 : 48) * d));
                    int imageMargin = (int) ((horizontal ? 6 : 4) * d);
                    imageParams.setMargins(imageMargin, imageMargin, imageMargin, imageMargin);
                    img.setLayoutParams(imageParams);
                    img.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
                    if (c.image == null || c.image.isEmpty()) {
                        img.setImageDrawable(Logos.avatar(PlayerActivity.this, c.title));
                    } else {
                        logos.add(img);
                        conLogo.add(c);
                    }
                    android.widget.TextView nombre = new android.widget.TextView(PlayerActivity.this);
                    nombre.setText(c.title);
                    nombre.setTextColor(0xFFFFFFFF);
                    nombre.setTextSize(horizontal ? 14 : 12);
                    nombre.setTypeface(nombre.getTypeface(), android.graphics.Typeface.BOLD);
                    nombre.setSingleLine(!horizontal);
                    nombre.setMaxLines(horizontal ? 2 : 1);
                    nombre.setEllipsize(android.text.TextUtils.TruncateAt.END);
                    nombre.setGravity(android.view.Gravity.CENTER);
                    if (horizontal) {
                        nombre.setPadding(0, (int) (8 * d), 0, 0);
                    } else {
                        android.widget.LinearLayout.LayoutParams nameParams =
                                new android.widget.LinearLayout.LayoutParams(
                                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                        android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
                        nameParams.topMargin = (int) (4 * d);
                        nombre.setLayoutParams(nameParams);
                    }
                    tarjeta.addView(img);
                    tarjeta.addView(nombre);
                    // el índice real va en el tag: la tarjeta no sabe en qué tanda está
                    tarjeta.setTag(Integer.valueOf(real));
                    tarjeta.setOnClickListener(v -> zapearCanal((Integer) v.getTag()));
                    lote.add(tarjeta);
                }
            } catch (Throwable t) {
                android.util.Log.w("Player", "armando la tira de canales: " + t);
            }
            final int fin = hasta;
            runOnUiThread(() -> {
                tiraConstruyendo = false;
                if (contenedorCanalesPlayer == null || isDestroyed()) return;
                for (int k = 0; k < lote.size(); k++) {
                    contenedorCanalesPlayer.addView(lote.get(k));
                    tiraIndices.add(Integer.valueOf(desde + k));
                }
                tiraHasta = fin;
                // Los logos, ya en el hilo principal y con la actividad viva.
                for (int k = 0; k < logos.size(); k++) {
                    Canal c = conLogo.get(k);
                    com.bumptech.glide.Glide.with(PlayerActivity.this)
                            .load(c.image)
                            .placeholder(Logos.avatar(PlayerActivity.this, c.title))
                            .error(Logos.avatar(PlayerActivity.this, c.title))
                            .into(logos.get(k));
                }
            });
        });
    }

    /** Zapeo manual: pone el canal elegido con su primera opción; sus
     *  servidores quedan en los chips (también a mano, nunca automáticos). */
    private void zapearCanal(int idx) {
        java.util.List<Canal> canales = ZapeoCanales.canales;
        if (idx < 0 || idx >= canales.size()) return;
        if (idx == ZapeoCanales.indiceActual && (streamReady || isResolving)) {
            showControls();
            return;
        }
        Canal canal = canales.get(idx);
        if (canal.options == null || canal.options.isEmpty()) {
            android.widget.Toast.makeText(this, R.string.player_sin_opciones,
                    android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        // v7.30: iba antes el feedback. En dzasports el cambio de canal se
        // siente al toque; aquí el título se repintaba y ya.
        mostrarCargando();
        ZapeoCanales.indiceActual = idx;
        tituloCanalActual = canal.title;
        pintarTituloPlayer();
        opcionesServidor.clear();
        opcionesFallidas.clear();
        for (int i = 0; i < canal.options.size(); i++) {
            Canal.OpcionCanal o = canal.options.get(i);
            String et = o.label == null || o.label.trim().isEmpty()
                    ? getString(R.string.player_opcion_n, i + 1) : o.label.trim();
            String u = CanalAdapter.normalizarUrlCanal(o.url, FuenteCanales.dramaKey(this));
            opcionesServidor.add(new String[]{et, u == null ? "" : u});
        }
        opcionActual = -1;
        UsoCanales.registrarApertura(this, canal);
        pintarTiraCanales();
        cargarOpcionServidor(0);
    }

    /**
     * v7.33 — pura: a qué milisegundo de la película corresponde un punto de la
     * barra. La barra va de 0 a 1000, así que arrastrar hasta la mitad de una
     * película de dos horas cae exactamente en la hora uno. Se probó sin Android
     * porque es justo el cálculo que decide si el usuario aterriza donde soltó.
     */
    static long posicionBuscada(long duracionMs, int progreso) {
        if (duracionMs <= 0 || progreso <= 0) return 0;
        if (progreso >= 1000) return duracionMs;
        return duracionMs * progreso / 1000L;
    }

    private static String formatoTiempo(long ms) {
        if (ms < 0) ms = 0;
        long s = ms / 1000;
        long h = s / 3600, m = (s % 3600) / 60, ss = s % 60;
        return h > 0
                ? String.format(java.util.Locale.ROOT, "%d:%02d:%02d", h, m, ss)
                : String.format(java.util.Locale.ROOT, "%d:%02d", m, ss);
    }

    // v3.2.66 (estilo dzasports, pedido del usuario): fila de servidores dentro
    // del reproductor para cambiar de opcion SIN salir — y SOLO a mano. En
    // dzasports un error saltaba solo al siguiente servidor en silencio; aqui
    // NO: la opcion que agota sus reintentos queda marcada con una ✕ y el
    // usuario elige otra tocando su chip.

    private void leerOpcionesDeIntent(Intent intent) {
        opcionesServidor.clear();
        opcionesFallidas.clear();
        opcionActual = -1;
        if (intent == null) return;
        String[] etiquetas = intent.getStringArrayExtra("opciones_labels");
        String[] urls = intent.getStringArrayExtra("opciones_urls");
        if (etiquetas == null || urls == null) return;
        int n = Math.min(etiquetas.length, urls.length);
        for (int i = 0; i < n; i++) {
            String et = etiquetas[i] == null || etiquetas[i].trim().isEmpty()
                    ? getString(R.string.player_opcion_n, i + 1) : etiquetas[i].trim();
            opcionesServidor.add(new String[]{et, urls[i] == null ? "" : urls[i]});
        }
        int idx = intent.getIntExtra("opcion_indice", -1);
        opcionActual = (idx >= 0 && idx < opcionesServidor.size()) ? idx
                : (opcionesServidor.isEmpty() ? -1 : 0);
    }

    private void pintarChipsServidores() {
        if (contenedorServidores == null || filaServidores == null) return;
        contenedorServidores.removeAllViews();
        if (opcionesServidor.size() < 2) {
            filaServidores.setVisibility(View.GONE);
            return;
        }
        for (int i = 0; i < opcionesServidor.size(); i++) {
            final int idx = i;
            boolean fallida = opcionesFallidas.contains(i)
                    && !(i == opcionActual && streamReady);
            android.widget.TextView chip = new android.widget.TextView(this);
            chip.setText(opcionesServidor.get(i)[0] + (fallida ? " \u2715" : ""));
            chip.setTextColor(0xFFFFFFFF);
            chip.setTextSize(14);
            chip.setTypeface(chip.getTypeface(), android.graphics.Typeface.BOLD);
            chip.setBackgroundResource(R.drawable.bg_chip_servidor);
            chip.setSelected(i == opcionActual);
            chip.setAlpha(fallida ? 0.5f : 1f);
            chip.setPadding(32, 16, 32, 16);
            android.widget.LinearLayout.LayoutParams lp =
                    new android.widget.LinearLayout.LayoutParams(
                            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                            android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, 12, 0);
            chip.setLayoutParams(lp);
            chip.setOnClickListener(v -> cargarOpcionServidor(idx));
            contenedorServidores.addView(chip);
        }
        aplicarVisibilidadExtra(controlsOverlay != null
                && controlsOverlay.getVisibility() == View.VISIBLE);
    }

    /** v3.2.66: cambio de servidor tocado por el usuario — mismo reinicio limpio
     *  e intercepciones que la entrada inicial, sin salir de la pantalla. */
    private void cargarOpcionServidor(int idx) {
        if (idx < 0 || idx >= opcionesServidor.size()) return;
        if (idx == opcionActual && (streamReady || isResolving)) {
            showControls();
            return;
        }
        String url = opcionesServidor.get(idx)[1];
        if (url == null || url.isEmpty()) {
            android.widget.Toast.makeText(this, R.string.player_sin_url,
                    android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        // v7.30: el cargando aparece AL PULSAR, no cuando ExoPlayer entre en
        // buffering. Sin esto el toque no se veía: el video viejo seguía
        // sonando y no había forma de saber si había llegado. Va DESPUES de
        // validar la URL: si no, un servidor sin URL dejaba el cargando 8 s.
        mostrarCargando();
        opcionActual = idx;
        // v7.38.3 — rastro claro para el log: así se ve que al pulsar un canal SÍ se
        // entra al reproductor (y no «vuelve a la lista») y con qué fuente arranca.
        log("\u25B6\uFE0F canal abierto · servidor " + (idx + 1) + "/" + opcionesServidor.size()
                + " · " + opcionesServidor.get(idx)[0] + " · " + url);
        // v7.38.4 — servidores probados del canal anterior no valen para este.
        if (!url.equals(canalCuyosServidoresSeEstanProbando)) {
            servidoresProbados.clear();
            canalCuyosServidoresSeEstanProbando = url;
        }
        pintarChipsServidores();
        log("\uD83D\uDD01 Servidor elegido a mano: " + opcionesServidor.get(idx)[0]);
        isResolving = false;
        resolucionInicio = 0L;
        webViewOcultoYaProbado = false;
        cerrandoDesdePip = false;
        asegurarPlayer();
        errorRefreshCount = 0;
        lastRefreshTime = 0L;
        streamReady = false;
        streamCookies = null;
        streamReferer = null;
        streamOrigin = null;
        streamCabeceras = null;
        final String dramaLive2 = url.startsWith("dramalive://") ? url : null;
        dramaLiveEnJuego = dramaLive2;
        if (dramaLive2 != null) {
            streamDirecto = null;
            urlIframeInicial = null;
            resolverDramaLive(dramaLive2);
        } else {
            EnlaceDrama.Contenido drama2 = EnlaceDrama.abrir(url, FuenteCanales.dramaKey(this));
            if (drama2 != null) {
                streamDirecto = drama2.url;
                dramaSwapPares = EnlaceDrama.paresSwap(drama2.texto);
                urlIframeInicial = null;
                resolverStreamDirecto(streamDirecto, null, null, null);
            } else {
                streamDirecto = null;
                urlIframeInicial = url;
                resolverStreamEnSegundoPlano();
            }
        }
    }

    /**
     * Hilos para calentar la caché desde fuera del reproductor (v7.27).
     *
     * v7.30: eran de uno en uno y por eso solo se llegaba a calentar el primer
     * servidor. Con tres caben todas las opciones de un canal a la vez.
     */
    private static final java.util.concurrent.ExecutorService PRE =
            java.util.concurrent.Executors.newFixedThreadPool(3);

    /**
     * v7.27: resuelve el stream en segundo plano para tenerlo listo.
     *
     * <p>Es el {@code preResolve} de dzasports: al tocar un canal en la rejilla
     * se lanza la resolución <em>antes</em> de abrir el reproductor, así que
     * cuando la pantalla ya está arriba el stream suele estar en caché y
     * arranca sin espera. Si la resolución va más lenta que la apertura, el
     * reproductor la encuentra a medias y no se pierde nada.</p>
     *
     * <p>Si ya está en caché no hace nada.</p>
     */
    public static void preResolver(android.content.Context cx, String url) {
        if (cx == null || url == null || url.isEmpty()) return;
        if (ResolucionCache.tiene(url)) return;
        PRE.execute(() -> resolverYGuardar(cx, url));
    }

    /**
     * v7.30: calienta TODAS las opciones, no solo la primera.
     *
     * <p>Es el `preResolve` de dzasports aplicado a todos los servidores del
     * canal. Sin esto, cambiar de servidor era siempre «a resolver de cero»
     * salvo el que abría el reproductor: justo lo que el usuario notaba como
     * «el click en servidor no se cambia de inmediato».</p>
     */
    public static void preResolverVarios(android.content.Context cx, java.util.List<String> urls) {
        if (cx == null || urls == null || urls.isEmpty()) return;
        for (String u : urls) preResolver(cx, u);
    }

    private static void resolverYGuardar(android.content.Context cx, String url) {
        try {
            StreamResolver.StreamResult r = StreamResolver.resolveSynchronously(cx, url);
            if (r != null && r.m3u8Url != null && !r.m3u8Url.isEmpty()) {
                ResolucionCache.put(url, new ResolucionCache.Entrada(
                        r.m3u8Url, r.cookies, r.referer, r.origin, r.headers));
            }
        } catch (Throwable ignored) {
            // Calentar es una cortesía, no una promesa: si falla, el
            // reproductor resuelve igual cuando se abra.
        }
    }

    private void resolverStreamEnSegundoPlano() {
        // PERRO GUARDIÁN: si una resolución anterior se quedó colgada más de
        // 60 s (hilo muerto, executor apagado...), se libera el bloqueo; si no,
        // la Activity dejaba de resolver CUALQUIER stream para siempre y solo
        // funcionaba la primera película.
        if (isResolving && System.currentTimeMillis() - resolucionInicio > 60_000) {
            log("⚠️ La resolución anterior se quedó colgada; se libera el bloqueo");
            isResolving = false;
        }
        if (isResolving) {
            log("⏳ Ya se está resolviendo, ignoramos llamada duplicada.");
            return;
        }
        isResolving = true;
        resolucionInicio = System.currentTimeMillis();
        mostrarCargando();

        // v7.37.16 — ATAJO MEDIDO: si la opcion que hay que resolver es un embed de
        // los que exigen iframe (ordernap/quotarevival, los que Drama da para beIN),
        // NO se lanza la carrera de 4 estrategias: son 20 s de espera y ninguna
        // puede resolverlo (el player del embed exige el UA exacto que pide su
        // m3u8, verificado: con otro UA el playlist da 403). Se va directo al
        // marco en la ventana, que esta medido y reproduce en ~5 s.
        String atajo = urlIframeInicial;
        if (atajo != null && esEmbedQueExigeIframe(atajo)) {
            log("⚡ atajo: embed que exige iframe -> directo al marco en la ventana");
            isResolving = false;
            mostrarEmbedEnLaVentana(atajo);
            return;
        }

        try {
            asegurarExecutor();
        } catch (Throwable t) {
            isResolving = false;
            log("❌ No se pudo preparar el hilo de resolución: " + t.getMessage());
            usarWebViewOcultoParaResolver();
            return;
        }

        executorService.execute(() -> {
            try {
                // v7.27: si este stream ya se resolvió hace poco (se acaba de abrir
                // el mismo canal, o lo calentó el tap en la rejilla), se usa tal
                // cual. Sin esto, cada apertura volvía a lanzar la carrera de 4
                // estrategias con WebViews para llegar al mismo sitio.
                String claveCache = urlIframeInicial;
                ResolucionCache.Entrada enCache = ResolucionCache.get(claveCache);
                if (enCache != null) {
                    log("♻️ Stream en caché (resuelto hace poco): " + enCache.url);
                }
                log("Resolviendo stream con el resolver adecuado...");
                StreamResolver.StreamResult result = enCache != null
                        ? new StreamResolver.StreamResult(enCache.url, enCache.cookies,
                                enCache.referer, enCache.origin, enCache.headers)
                        : resolverInteligente(urlIframeInicial);
                if (result == null || result.m3u8Url == null || result.m3u8Url.isEmpty()) {
                    log("❌ StreamResolver no devolvió stream. Cambiando a reproductor web.");
                    mainHandler.post(() -> {
                        isResolving = false;
                        usarWebViewOcultoParaResolver();
                    });
                    return;
                }
                String url = result.m3u8Url;
                String cookies = result.cookies;
                String referer = result.referer;
                String origin = result.origin;
                Map<String, String> capturedHeaders = result.headers;

                // v7.27: se guarda para la próxima vez que se abra este mismo canal.
                if (enCache == null && claveCache != null) {
                    ResolucionCache.put(claveCache, new ResolucionCache.Entrada(
                            url, cookies, referer, origin, capturedHeaders));
                }

                log("Stream: " + url);
                log("Cookies: " + (cookies.isEmpty() ? "(vacías)" : cookies));
                log("Referer (wrapper): " + referer);
                log("Origin (iframe real): " + origin);

                if (capturedHeaders != null && !capturedHeaders.isEmpty()) {
                    log("Headers capturados del WebView:");
                    for (Map.Entry<String, String> entry : capturedHeaders.entrySet()) {
                        log("   " + entry.getKey() + ": " + entry.getValue());
                    }
                } else {
                    log("(Sin headers capturados)");
                }

                mainHandler.post(() -> {
                    if (player == null) {
                        isResolving = false;
                        usarWebViewOcultoParaResolver();
                        return;
                    }

                    Map<String, String> finalHeaders = new HashMap<>();
                    if (capturedHeaders != null) {
                        finalHeaders.putAll(capturedHeaders);
                    }

                    finalHeaders.put("User-Agent", StreamResolver.DESKTOP_USER_AGENT);

                    boolean hasReferer = false;
                    for (String key : finalHeaders.keySet()) {
                        if (key.equalsIgnoreCase("Referer")) {
                            hasReferer = true;
                            break;
                        }
                    }
                    if (!hasReferer) {
                        finalHeaders.put("Referer", referer != null ? referer : "https://belkaperu.github.io/");
                    }
                    // Sin Accept-Language, CDNs como vimeos devuelven 403 (verificado).
                    boolean hasLang = false;
                    for (String key : finalHeaders.keySet()) {
                        if (key.equalsIgnoreCase("Accept-Language")) { hasLang = true; break; }
                    }
                    if (!hasLang) finalHeaders.put("Accept-Language", "es-ES,es;q=0.9");

                    boolean hasOrigin = false;
                    for (String key : finalHeaders.keySet()) {
                        if (key.equalsIgnoreCase("Origin")) {
                            hasOrigin = true;
                            break;
                        }
                    }
                    if (!hasOrigin && origin != null && !origin.isEmpty()) {
                        finalHeaders.put("Origin", origin);
                    } else if (!hasOrigin) {
                        try {
                            java.net.URL urlObj = new java.net.URL(referer);
                            finalHeaders.put("Origin", urlObj.getProtocol() + "://" + urlObj.getHost());
                        } catch (Exception ignored) {}
                    }

                    if (cookies != null && !cookies.isEmpty()) {
                        finalHeaders.put("Cookie", cookies);
                    }

                    log("Headers finales para ExoPlayer:");
                    for (Map.Entry<String, String> entry : finalHeaders.entrySet()) {
                        log("   " + entry.getKey() + ": " + entry.getValue());
                    }

                    player.stop();
                    player.clearMediaItems();

                    dataSourceFactory.setDefaultRequestProperties(finalHeaders);
                    conRelaySiToca(url, () -> {
                        MediaItem mediaItem = mediaItemPara(urlConRelay != null ? urlConRelay : url);
                        sinVideoYaAvisado = false;   // fuente nueva: se vuelve a mirar
                        player.setMediaItem(mediaItem);
                        mostrarCargando();
                        player.prepare();
                        pedirFocoAudio();
                        try { PelisStreamResolver.destruirWebViewsActivos(); } catch (Throwable ignored) { }
                        try { StreamResolver.destruirWebViewsActivos(); } catch (Throwable ignored) { }
                        player.setPlayWhenReady(true);
                        streamReady = true;
                        isResolving = false;
                        if (webViewFallback != null && webViewFallback.getVisibility() == View.VISIBLE) {
                            webViewFallback.stopLoading();
                            webViewFallback.setVisibility(View.GONE);
                        }
                        log("Reproducción iniciada con headers reales.");
                    });
                });
            } catch (Exception e) {
                log("❌ Error en callback: " + e.getMessage());
                mainHandler.post(() -> {
                    isResolving = false;
                    usarWebViewOcultoParaResolver();
                });
            }
        });
    }

    /**
     * v7.14 — URLs que son PÁGINAS web, no streams. ExoPlayer no puede con ellas
     * (las fuerza a HLS y se come un HTML), y el WebView oculto de reserva acaba
     * pidiendo scripts a redes de anuncios: justo lo que se veia en el log del
     * usuario («🌍 Petición HTTP a: …speedaidata.cfd…js»).
     *
     * <p>Estrecho a propósito: un stream SIN extensión (
     * {@code /live/user/pass/1234}, {@code playlist.php?id=..}) es legítimo y sigue
     * yendo a ExoPlayer, que es donde {@link #mediaItemPara} le pone el MIME.
     */
    static boolean esPaginaWebNoStream(String url) {
        if (url == null) return false;
        String u = url.trim().toLowerCase(Locale.ROOT);
        if (u.isEmpty()) return false;
        if (u.contains("youtube.com/") || u.contains("youtube-nocookie.com/")
                || u.startsWith("https://youtu.be/") || u.startsWith("http://youtu.be/"))
            return true;
        if (u.contains("vimeo.com/") || u.contains("dailymotion.com/")
                || u.contains("facebook.com/") || u.startsWith("https://fb.watch/"))
            return true;
        // …/algo.html es una pagina. La EXCEPCION es /index.html: el relay ya la
        // trata como la playlist del player (ver esPaginaPlayerHls).
        int q = u.indexOf('?');
        String path = q >= 0 ? u.substring(0, q) : u;
        if (path.endsWith("/index.html")) return false;
        return path.endsWith(".html") || path.endsWith(".htm");
    }

    private void resolverStreamDirecto(String url, String cookies, String referer, String origin) {
        if (url == null || url.isEmpty()) {
            log("Stream directo vacio");
            return;
        }
        if (esPaginaWebNoStream(url)) {
            // v7.14 (log del usuario): la «cura» dejo en la fila un enlace de
            // youtube.com/watch y el player se lo mando a ExoPlayer como si fuera
            // un m3u8. Se avisa y se para, en vez de refrescar 5 veces y acabar
            // cargando la pagina (con sus anuncios) en el WebView oculto.
            log("⛔ " + url + " es una pagina web, no un stream — no se reproduce");
            errorRefreshCount = MAX_REFRESH_RETRIES;
            webViewOcultoYaProbado = true;   // no se carga la pagina: trae anuncios
            streamReady = false;
            avisarSinStream();
            return;
        }
        mostrarCargando();
        log("Reproduciendo stream m3u8 directo (sin WebView)");
        isResolving = false;
        // v7.38.4 — esta directa también cuenta como servidor probado del canal.
        if (!servidoresProbados.contains(url)) servidoresProbados.add(url);
        mainHandler.post(() -> {
            if (!asegurarPlayer()) return;
            Map<String, String> headers = new HashMap<>();
            headers.put("User-Agent", StreamResolver.DESKTOP_USER_AGENT);
            if (referer != null && !referer.isEmpty()) headers.put("Referer", referer);
            if (origin != null && !origin.isEmpty()) headers.put("Origin", origin);
            if (cookies != null && !cookies.isEmpty()) headers.put("Cookie", cookies);
            // Sin Accept-Language, CDNs como vimeos devuelven 403 (verificado).
            if (!headers.containsKey("Accept-Language")) {
                headers.put("Accept-Language", "es-ES,es;q=0.9");
            }
            // Cabeceras del usuario: las de la fuente, y por encima las que pidió ESTE
            // canal. Van al final a propósito — si alguien fija su User-Agent, manda él;
            // el UA forzado de abajo es sólo un respaldo, no una imposición.
            Map<String, String> delUsuario = new java.util.LinkedHashMap<>(
                    Encabezados.fromTexto(FuenteCanales.cabecerasEnBruto(getApplicationContext())).para(url));
            if (!Encabezados.fromTexto(streamCabeceras).estaVacio()) {
                delUsuario.putAll(Encabezados.fromTexto(streamCabeceras).para(url));
            }
            if (!delUsuario.isEmpty()) {
                headers.putAll(delUsuario);
                log("Cabeceras del usuario aplicadas: " + delUsuario.keySet());
            }
            player.stop();
            player.clearMediaItems();
            dataSourceFactory.setDefaultRequestProperties(headers);
            conRelaySiToca(url, () -> {
                MediaItem mediaItem = mediaItemPara(urlConRelay != null ? urlConRelay : url);
                urlEnReproduccion = url;
                sinVideoYaAvisado = false;   // fuente nueva: se vuelve a mirar
                player.setMediaItem(mediaItem);
                mostrarCargando();
                player.prepare();
                pedirFocoAudio();
                try { PelisStreamResolver.destruirWebViewsActivos(); } catch (Throwable ignored) { }
                try { StreamResolver.destruirWebViewsActivos(); } catch (Throwable ignored) { }
                player.setPlayWhenReady(true);
                streamReady = true;
                isResolving = false;
                log("Stream directo cargado.");
            });
        });
    }

    /** v3.2.31: chip «HD ▾» con las opciones que extrajimos del glasbi. Vaciarlo si el
     *  canal no trajo nada (que no aparezca un boton que no hace nada). */
    private void mostrarChipCalidad() {
        java.util.List<String[]> ops = nuevasOpcionesCalidad(streamOpcionesJson);
        if (chipCalidad == null) return;
        if (ops.isEmpty()) { chipCalidad.setVisibility(android.view.View.GONE); return; }
        if (calidadElegida == null || calidadElegida.isEmpty()) calidadElegida = ops.get(0)[0];
        chipCalidad.setText(calidadElegida + " \u25BE");
        chipCalidad.setVisibility(android.view.View.VISIBLE);
    }

    /** Menu del chip: elegir opcion FIJA el marcador y re-resuelve el canal en el acto
     *  (mismo camino del play nativo, sin volver por el sueno). */
    private void mostrarMenuCalidad() {
        final java.util.List<String[]> ops = nuevasOpcionesCalidad(streamOpcionesJson);
        if (ops.isEmpty()) {
            Toast.makeText(this, "Este canal no trajo opciones de calidad", Toast.LENGTH_SHORT).show();
            return;
        }
        android.widget.PopupMenu pm = new android.widget.PopupMenu(this, chipCalidad);
        for (int i = 0; i < ops.size(); i++) {
            String marca = ops.get(i)[0];
            pm.getMenu().add(0, i, i, marca.equals(calidadElegida) ? marca + "  \u2713" : marca);
        }
        pm.setOnMenuItemClickListener(mi -> {
            String[] op = ops.get(mi.getItemId());
            calidadElegida = op[0];
            if (chipCalidad != null) chipCalidad.setText(op[0] + " \u25BE");
            Toast.makeText(this, "Cambiando a " + op[0] + "…", Toast.LENGTH_SHORT).show();
            // v7.24: las opciones ya no son solo marcadores .LS. — el menu muestra
            // tambien urls directas. Un marcador hay que pasarlo por el glbr para que
            // lo cambie por una url fresca; una url directa se juega tal cual, y
            // mandarla otra vez por el glbr la rompe.
            if (op[1].contains(".LS.")) {
                marcadorForzado = op[1];
                agenteForzado = "redirect";
                log("calidad " + op[0] + " elegida — re-resolviendo con su marcador");
                if (canalActual != null) resolverDramaLive(canalActual);
            } else {
                marcadorForzado = null;
                agenteForzado = null;
                log("calidad " + op[0] + " elegida — url directa, se juega tal cual");
                resolverStreamDirecto(op[1], null, streamReferer, streamOrigin);
            }
            return true;
        });
        pm.show();
    }

    /** Puro y testeable: [{n,u}] del glasbi -> lista {nombre, marcador}; vacio ante basura. */
    static java.util.List<String[]> nuevasOpcionesCalidad(String json) {
        java.util.List<String[]> out = new java.util.ArrayList<>();
        try {
            com.google.gson.JsonElement e = com.google.gson.JsonParser.parseString(json);
            if (e != null && e.isJsonArray()) {
                for (com.google.gson.JsonElement x : e.getAsJsonArray()) {
                    if (x.isJsonObject() && x.getAsJsonObject().has("n") && x.getAsJsonObject().has("u")
                            && x.getAsJsonObject().get("n").isJsonPrimitive()
                            && x.getAsJsonObject().get("u").isJsonPrimitive()) {
                        out.add(new String[]{ x.getAsJsonObject().get("n").getAsString(),
                                x.getAsJsonObject().get("u").getAsString() });
                    }
                }
            }
        } catch (Exception ignored) { }
        return out;
    }

    /** v3.2.29 (play probado en PYTHON con frame decodificado): la ruta glasbi->advanced
     *  devuelve como "stream" la pagina del player (index.html), que ES una playlist live
     *  cuyo segmento viene con prefijo ofuscado (swap {"k9x_":""}: con prefijo el CDN da
     *  404, sin el 200). ExoPlayer no puede reescribir segmento a segmento, asi que la app
     *  hace de relai: baja la playlist, aplica el swap, absolutiza, la escribe en cache con
     *  extension .m3u8 y la refresca cada 4 s (sin ENDLIST ExoPlayer la recarga solo). */
    private static final java.util.regex.Pattern SEG_SWAPPEADO =
            java.util.regex.Pattern.compile("(?m)^([0-9A-Za-z]{2,8}_)(?=\\d)");

    private static final String RELAY_UA = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) "
            + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36";

    static boolean esPaginaPlayerHls(String url) {
        // v3.2.30: los players del advanced rotan de host Y de ruta — medidos en el logcat
        // real del usuario: /pages/.. (beIN, con swap k9x_), /mirsp/.. (thmanyah) y
        // /malasp2/.. (beIN sport open) en la MISMA sesion. La firma estable es que el
        // "stream" es una pagina index.html; el guardadillo de que sea playlist real lo
        // pone el propio relay (si la primera linea no es #EXTM3U, devolvemos la url cruda).
        return url != null && url.endsWith("/index.html");
    }

    /** Pura y testeable: swap (quita el prefijo antes del primer digito) + absolutizado. */
    static String reescribirPlaylist(String txt, String baseUrl) {
        if (txt == null || txt.isEmpty() || baseUrl == null) return null;
        String limpio = SEG_SWAPPEADO.matcher(txt).replaceAll("");
        int cut = baseUrl.lastIndexOf('/');
        String base = cut > 0 ? baseUrl.substring(0, cut + 1) : baseUrl;
        StringBuilder sb = new StringBuilder();
        for (String raw : limpio.split("\n")) {
            String l = raw.trim();
            if (sb.length() > 0) sb.append('\n');
            if (l.isEmpty() || l.charAt(0) == '#') sb.append(l);
            else if (l.startsWith("http://") || l.startsWith("https://")) sb.append(l);
            else sb.append(base).append(l);
        }
        return sb.toString();
    }

    private volatile String relayBase;

    private java.io.File relayArchivo() { return new java.io.File(getCacheDir(), "drama_relay.m3u8"); }

    private static byte[] drain(java.io.InputStream in) throws java.io.IOException {
        java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8192]; int r;
        while ((r = in.read(buf)) > 0) bo.write(buf, 0, r);
        return bo.toByteArray();
    }

    /**
     * v7.21 — el host del player ROTA por canal (medido: /pages/, /mirsp/ y
     * /malasp2/ en la misma sesion), asi que no se puede precalentar de antemano.
     * Pero se conoce EN CUANTO el glbr contesta, antes de que el relay lo toque.
     *
     * Medido en el diagnostico real del usuario (SM-A075M, Android 16):
     *   relay: baja la playlist ....... 1068 ms
     *   primer fotograma (ExoPlayer) ... 1120 ms
     * Los DOS iban al MISMO host con dos conexiones en frio distintas.
     */
    private Thread precalentarHost(@androidx.annotation.NonNull String urlPagina) {
        final String origen;
        try {
            java.net.URL u = new java.net.URL(urlPagina);
            origen = u.getProtocol() + "://" + u.getHost()
                    + (u.getPort() >= 0 ? ":" + u.getPort() : "") + "/";
        } catch (Exception e) {
            return null;
        }
        Thread t = new Thread(new Runnable() {
            @Override public void run() {
                try {
                    java.net.HttpURLConnection c = (java.net.HttpURLConnection)
                            new java.net.URL(origen).openConnection();
                    c.setRequestMethod("HEAD");
                    c.setConnectTimeout(5000);
                    c.setReadTimeout(5000);
                    c.setRequestProperty("User-Agent", RELAY_UA);
                    c.getResponseCode();
                    if (c.getInputStream() != null) c.getInputStream().close();
                } catch (Exception ignore) { }
            }
        }, "drama-precalentar-host");
        t.setDaemon(true);
        t.start();
        return t;
    }

    private byte[] bajarPlaylist(String url) throws Exception {
        java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
        c.setConnectTimeout(6000); c.setReadTimeout(8000);
        c.setRequestProperty("User-Agent", RELAY_UA);
        try (java.io.InputStream in = c.getInputStream()) { return drain(in); }
    }

    /** v7.3: el relay baja la playlist por red, asi que se prepara en un hilo aparte y
     *  el resto (setMediaItem/prepare) sigue en el principal. Antes se llamaba dentro
     *  de un mainHandler.post y petaba con NetworkOnMainThreadException — que se veia
     *  en el logcat como «relay HLS: primer fetch fallo» y dejaba el relay sin
     *  aplicar (sin el swap de segmentos k9x_). */
    private void conRelaySiToca(@NonNull final String url, @NonNull final Runnable resto) {
        if (url == null || url.isEmpty() || !esPaginaPlayerHls(url)) {
            // v7.23: se apunta cual fue la url, que es la unica forma de saber si el
            // canal trajo una pagina del player o otra cosa.
            RegistroCarga.paso("sin relay: la url no es pagina del player",
                    url == null ? "null" : url);
            resto.run();   // no es pagina del player: se sigue tal cual
            return;
        }
        if (relayForzado) {
            log("↩️ vuelta por el relay (el camino directo falló en este canal)");
            new Thread(() -> {
                final String lista = relayHlsSiPagina(url);
                mainHandler.post(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    urlConRelay = lista;
                    RegistroCarga.paso("relay (red de seguridad)");
                    resto.run();
                });
            }, "drama-relay-prep").start();
            return;
        }
        // v7.22: camino directo. El host del player rota por canal pero se conoce
        // ya, asi que se le precalienta la conexión para que el PRIMER SEGMENTO no
        // pague el handshake (medido: 1.120 ms).
        Thread pre = precalentarHost(url);
        if (pre != null) {
            try { pre.join(300); } catch (InterruptedException ignore) { }
        }
        paginaSinRelay = url;
        RegistroCarga.paso("sin relay: ExoPlayer directo");
        mainHandler.post(() -> {
            if (isFinishing() || isDestroyed()) return;
            urlConRelay = null;
            resto.run();
        });
    }

    /** Ultima url ya pasada por el relay (la usa conRelaySiToca). */
    private volatile String urlConRelay;

    /** v7.22: pagina del player que se esta intentando SIN relay. */
    private volatile String paginaSinRelay;

    /** v7.22: el camino directo fallo en este canal -> el reintento va por el relay. */
    private volatile boolean relayForzado;

    /** Primer fetch de la playlist del player; si algo falla devolvemos la url
     *  original y ExoPlayer intenta lo suyo. Llamar desde un hilo en segundo plano. */
    private String relayHlsSiPagina(String url) {
        if (!esPaginaPlayerHls(url)) return url;
        final long tRelay = System.currentTimeMillis();
        try {
            String crudo = new String(bajarPlaylist(url), java.nio.charset.StandardCharsets.UTF_8);
            if (crudo.trim().split("\n", 2)[0].indexOf("#EXTM3U") < 0) {
                log("relay HLS: la pagina no es playlist (no arranca con #EXTM3U) — la dejo cruda");
                return url;
            }
            String nuevo = reescribirPlaylist(crudo, url);
            if (nuevo == null) return url;
            try (java.io.FileWriter w = new java.io.FileWriter(relayArchivo())) { w.write(nuevo); }
            RegistroCarga.paso("relay: baja la playlist");
            android.util.Log.d("BLABONGO_DEBUG", "relay HLS: playlist en cache (" + nuevo.length()
                    + " B) con swap aplicado — play desde archivo .m3u8 local");
            iniciarBucleRelay(url);
            return android.net.Uri.fromFile(relayArchivo()).toString();
        } catch (Exception e) {
            log("relay HLS: primer fetch fallo (" + e + ") — dejo la url cruda");
            return url;
        }
    }

    private void iniciarBucleRelay(final String url) {
        relayBase = url;
        Thread t = new Thread(() -> {
            while (!isFinishing() && url.equals(relayBase)) {
                try { Thread.sleep(4000); } catch (InterruptedException e) { return; }
                if (!url.equals(relayBase) || isFinishing()) return;
                try {
                    String nuevo = reescribirPlaylist(new String(bajarPlaylist(url), java.nio.charset.StandardCharsets.UTF_8), url);
                    if (nuevo != null) { try (java.io.FileWriter w = new java.io.FileWriter(relayArchivo())) { w.write(nuevo); } }
                } catch (Exception ignored) { }
            }
        }, "drama-relay");
        t.setDaemon(true);
        t.start();
    }

    /**
     * v7.36.1: un canal cuyo vídeo es MPEG-2 (o HEVC sin decodificador) NO da
     * error en ExoPlayer. El audio AAC se decodifica sin problemas, la pista de
     * vídeo simplemente no se selecciona y el reproductor llega a READY: el
     * usuario oye el canal y no ve nada. Y como no hay error, la app se quedaba
     * quieta en esa opción en vez de pasar a la siguiente.
     *
     * Medido: el servidor Astra (181.205.130.194:4000) servía exactamente eso,
     * MPEG-2 + AAC, y es la causa del «en la app solo audio».
     *
     * Solo se salta cuando el móvil no puede decodificar el vídeo. Si el canal
     * tiene una sola opción no se hace nada: no hay a donde ir.
     */
    private void revisarSiHayVideo(Tracks tracks) {
        if (sinVideoYaAvisado) return;
        if (player == null) return;
        if (player.getPlaybackState() != Player.STATE_READY) return;
        if (opcionActual < 0 || opcionesServidor.size() < 2) return;
        if (hayVideoDecodificable(tracks)) return;

        sinVideoYaAvisado = true;
        opcionesFallidas.add(opcionActual);
        String muerta = opcionesServidor.get(opcionActual)[0];
        log("\uD83D\uDEAB «" + muerta + "» no tiene pista de vídeo que este móvil sepa"
                + " decodificar (solo se oye) — se prueba el servidor siguiente");
        pintarChipsServidores();

        int siguiente = -1;
        for (int i = 0; i < opcionesServidor.size(); i++) {
            if (i != opcionActual && !opcionesFallidas.contains(i)) { siguiente = i; break; }
        }
        if (siguiente < 0) {
            log("\u274C Ningún servidor de este canal tiene vídeo decodificable.");
            Toast.makeText(this, R.string.player_sin_video, Toast.LENGTH_LONG).show();
            showControls();
            return;
        }
        Toast.makeText(this, R.string.player_sin_video_cambiando, Toast.LENGTH_LONG).show();
        cargarOpcionServidor(siguiente);
    }

    /**
     * ¿Hay al menos una pista de vídeo que el móvil sepa decodificar?
     *
     * Devuelve false en los dos casos que dejan la pantalla en negro: que el
     * origen no traiga vídeo (solo audio) y que lo traiga en un códec sin
     * decodificador (MPEG-2). Con las pistas aún sin conocer devuelve true,
     * que es no hacer nada, para no saltar por una carrera.
     */
    private static boolean hayVideoDecodificable(Tracks tracks) {
        if (tracks == null) return true;
        java.util.List<Tracks.Group> grupos = tracks.getGroups();
        boolean[] esVideo = new boolean[grupos.size()];
        boolean[] soportada = new boolean[grupos.size()];
        for (int i = 0; i < grupos.size(); i++) {
            Tracks.Group g = grupos.get(i);
            esVideo[i] = (g.getType() == C.TRACK_TYPE_VIDEO);
            for (int j = 0; j < g.length; j++) {
                if (g.isTrackSupported(j)) { soportada[i] = true; break; }
            }
        }
        return hayVideoUtil(esVideo, soportada);
    }

    /**
     * Pura y testeable, sin clases de media3 (en los tests JVM vienen en stubs y
     * todas las constantes valdrían 0): de las pistas recibidas, ¿hay alguna de
     * vídeo que el móvil sepa decodificar?
     */
    static boolean hayVideoUtil(boolean[] esVideo, boolean[] soportada) {
        if (esVideo.length == 0) return true;   // aún no se conocen las pistas: no inventamos
        for (int i = 0; i < esVideo.length; i++) {
            if (esVideo[i] && soportada[i]) return true;
        }
        return false;
    }

    /**
     * ExoPlayer decide si algo es HLS mirando la RUTA de la URL (que acabe en
     * .m3u8). Los canales de deportes sirven el playlist como
     * playlist.php?id=..&sig=.., sin .m3u8, asi que ExoPlayer lo toma por un
     * fichero normal e intenta leer un contenedor de media dentro de un texto:
     * falla aunque el stream sea perfecto.
     *
     * Cuando la URL no trae .m3u8 se lo decimos nosotros con el MIME. Las
     * peliculas siempre resuelven a un master.m3u8, asi que esto no las toca.
     *
     * v7.36.2: PERO solo cuando la URL tiene pinta de pagina de player
     * (extension .php/.html/... o query string). Una URL como
     * /play/a07j, sin extension y sin query, es TS binario en crudo: decirle
     * a ExoPlayer que eso es HLS lo manda al parser de playlists, que se
     * atasca con bytes que no son #EXTM3U. En ese caso NO se le pone MIME y
     * se deja que DefaultExtractorsFactory lo detecte por contenido, que es
     * lo que da el TsExtractor correcto. Medido: esas URLs sirven
     * MPEG-TS (Content-Type: application/octet-stream).
     */
    private MediaItem mediaItemPara(String url) {
        MediaItem.Builder b = new MediaItem.Builder().setUri(Uri.parse(url));
        if (url != null && url.toLowerCase().contains(".mpd")) {
            // v7.38.1 — Abu Dhabi Al Emarat (y el resto de starzplay/intigral) no
            // manda HLS sino DASH: sin decírselo a ExoPlayer, el .mpd entraba por
            // el parser de HLS y el canal se quedaba en negro.
            b.setMimeType(MimeTypes.APPLICATION_MPD);
            log("\uD83C\uDFAC Es DASH (.mpd): se reproduce con el parser DASH");
        } else if (esContenedorDeMedia(url)) {
            log("\uD83C\uDFAC Contenedor progresivo (.mp4/.mkv/...): ExoPlayer lo autodetecta, NO lo metemos al parser HLS");
        } else if (url != null && !url.toLowerCase().contains(".m3u8")
                && !url.toLowerCase().endsWith(".mpd")) {
            if (parecePaginaPlayer(url)) {
                // Si esta linea sale por el log, el arreglo SI esta en el build.
                // Si no sale, es que hay un APK viejo instalado.
                log("\uD83C\uDFAC Reproduciendo como HLS (la URL no trae .m3u8): " + url);
                b.setMimeType(MimeTypes.APPLICATION_M3U8);
            } else {
                log("\uD83D\uDCE6 Sin extension de playlist: se deja que ExoPlayer detecte el contenedor: " + url);
            }
        }
        return b.build();
    }

    /**
     * v7.36.2, pura y testeable: ¿la URL tiene pinta de pagina de player que
     * sirve un playlist HLS, en vez de un stream binario?
     *
     * Sirve para las .php y .html del catalogo (embed.saohgdasregions.fun,
     * tvf90.com, alilus.github.io...) que devuelven el m3u8 sin llevarlo en
     * el nombre. Una URL sin extension y sin query — /play/a07j — es al
     * contrario: TS binario en crudo, y ponerle MIME de HLS lo rompe.
     */
    static boolean parecePaginaPlayer(String url) {
        if (url == null) return false;
        String u = url.toLowerCase();
        int q = u.indexOf('?');
        if (q >= 0) return true;                 // playlist.php?id=..&sig=..
        String path = q >= 0 ? u.substring(0, q) : u;
        int slash = path.lastIndexOf('/');
        String ultimo = slash >= 0 ? path.substring(slash + 1) : path;
        int punto = ultimo.lastIndexOf('.');
        if (punto <= 0) return false;            // sin extension: /play/a07j
        String ext = ultimo.substring(punto + 1);
        return ext.equals("php") || ext.equals("html") || ext.equals("htm")
                || ext.equals("asp") || ext.equals("aspx") || ext.equals("jsp")
                || ext.equals("cgi") || ext.equals("pls") || ext.equals("m3u");
    }

    /** Pura y testeable: extensiones de fichero-contenedor (no playlist). */
    static boolean esContenedorDeMedia(String url) {
        if (url == null) return false;
        String u = url.toLowerCase();
        int q = u.indexOf('?');
        String path = q >= 0 ? u.substring(0, q) : u;
        return path.endsWith(".mp4") || path.endsWith(".mkv") || path.endsWith(".webm")
                || path.endsWith(".mp3") || path.endsWith(".m4a") || path.endsWith(".aac")
                || path.endsWith(".mov") || path.endsWith(".avi") || path.endsWith(".flv");
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo netInfo = cm.getActiveNetworkInfo();
        return netInfo != null && netInfo.isConnected();
    }

    private void configurarSSLInseguroUniversal() {
        try {
            TrustManager[] trustAll = new TrustManager[]{
                    new X509TrustManager() {
                        public java.security.cert.X509Certificate[] getAcceptedIssuers() { return null; }
                        public void checkClientTrusted(java.security.cert.X509Certificate[] c, String a) {}
                        public void checkServerTrusted(java.security.cert.X509Certificate[] c, String a) {}
                    }
            };
            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, trustAll, new java.security.SecureRandom());
            javax.net.ssl.HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
            javax.net.ssl.HttpsURLConnection.setDefaultHostnameVerifier((h, s) -> true);
        } catch (Exception ignored) {}
    }

    private void hideSystemUI() {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        WindowInsetsControllerCompat ctrl = new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView());
        ctrl.hide(WindowInsetsCompat.Type.systemBars());
        ctrl.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
    }

    // ===================== INTERCEPTOR DE RED (LOGS) =====================
    private class LoggingHttpDataSource implements HttpDataSource {
        private final HttpDataSource delegate;

        LoggingHttpDataSource(HttpDataSource delegate) {
            this.delegate = delegate;
        }

        @Override
        public void addTransferListener(TransferListener transferListener) {
            delegate.addTransferListener(transferListener);
        }

        @Override
        public long open(DataSpec dataSpec) throws HttpDataSourceException {
            log("🌍 Petición HTTP a: " + dataSpec.uri);
            try {
                long bytes = delegate.open(dataSpec);
                log("✅ Conexión exitosa (HTTP " + delegate.getResponseCode() + ")");
                return bytes;
            } catch (IOException e) {
                log("❌ Error de red HTTP: " + e.getMessage());
                throw e;
            }
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws HttpDataSourceException {
            return delegate.read(buffer, offset, length);
        }

        @Override
        public Uri getUri() {
            return delegate.getUri();
        }

        @Override
        public void close() throws HttpDataSourceException {
            delegate.close();
        }

        @Override
        public Map<String, List<String>> getResponseHeaders() {
            return delegate.getResponseHeaders();
        }

        @Override
        public void setRequestProperty(String name, String value) {
            delegate.setRequestProperty(name, value);
        }

        @Override
        public void clearRequestProperty(String name) {
            delegate.clearRequestProperty(name);
        }

        @Override
        public void clearAllRequestProperties() {
            delegate.clearAllRequestProperties();
        }

        @Override
        public int getResponseCode() {
            return delegate.getResponseCode();
        }
    }
}