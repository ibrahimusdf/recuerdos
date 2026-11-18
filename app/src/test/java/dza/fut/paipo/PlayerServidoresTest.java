package dza.fut.paipo;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.Test;

/**
 * v3.2.66 — el player estilo dzasports (chips de servidores dentro del
 * reproductor) con la corrección pedida por el usuario: el cambio de servidor
 * es SOLO manual. En dzasports, onPlayerError llamaba a un salto silencioso al
 * siguiente servidor; esta guardia estática (misma técnica que
 * TinteBlancoTest) verifica que aquí ningún camino de error cambia de opción:
 * la que falla se marca y elige el usuario.
 *
 * v7.9 — el paisaje pasa a ser la especificación completa del estilo del player
 * (§1 landscape): RelativeLayout negro, PlayerView a pantalla completa con
 * use_controller=false y resize_mode=fit, ProgressBar de carga centrado y overlay
 * vertical transparente con la cabecera de servidores ARRIBA (pegada al borde),
 * los controles en el CENTRO y la tira de canales ABAJO (pegada al borde).
 */
public class PlayerServidoresTest {

    /** Directorio de las fuentes, para comprobar que un fichero YA NO existe. */
    private static final java.io.File DIR = new java.io.File("app/src/main/java/dza/fut/paipo");

    private static String fuente(String archivo) {
        String r = "dza/fut/paipo/" + archivo;
        try (InputStream in = PlayerServidoresTest.class.getClassLoader()
                .getResourceAsStream(r)) {
            if (in != null) return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (java.io.IOException ignore) { }
        java.io.File f = new java.io.File("src/main/java/dza/fut/paipo/" + archivo);
        if (!f.isFile()) f = new java.io.File("app/src/main/java/dza/fut/paipo/" + archivo);
        try {
            return new String(java.nio.file.Files.readAllBytes(f.toPath()),
                    StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            throw new AssertionError("no encuentro la fuente de " + archivo + ": " + e);
        }
    }

    @Test
    public void hayChipsDeServidoresYElTapLlevaTodasLasOpciones() {
        String p = fuente("PlayerActivity.java");
        assertTrue("falta cargarOpcionServidor", p.contains("void cargarOpcionServidor("));
        assertTrue("falta pintarChipsServidores", p.contains("void pintarChipsServidores()"));
        assertTrue("falta leerOpcionesDeIntent", p.contains("void leerOpcionesDeIntent("));
        assertTrue("falta el marcado de opciones fallidas", p.contains("opcionesFallidas"));

        // v7.27: el tap en la rejilla ya no pasa por la hoja intermedia; quien
        // empaqueta las opciones es el adaptador, y debe llevarlas TODAS mas el
        // indice de arranque para que el reproductor pinte sus chips.
        String ad = fuente("CanalAdapter.java");
        assertTrue("el adaptador debe pasar las etiquetas de todas las opciones",
                ad.contains("opciones_labels"));
        assertTrue("el adaptador debe pasar el indice de arranque",
                ad.contains("opcion_indice"));
        assertTrue("el adaptador debe resolver el stream por adelantado",
                ad.contains("preResolver"));

        // La hoja de opciones tenia su PROPIA copia de la normalizacion, que es
        // justo la clase de bug que nos costo la v7.26 (tres copias de srv.get(0)).
        // Ya no se abre desde ningun sitio: se fue.
        assertFalse("la hoja de opciones no debe existir",
                new java.io.File(DIR, "BottomSheetOpcionesFragment.java").exists());
        assertFalse("el adaptador no debe mostrar ninguna hoja intermedia",
                ad.contains("bottomSheet.show"));
        assertFalse("ni el reproductor debe abrir la hoja",
                p.contains("BottomSheetOpcionesFragment"));
    }

    @Test
    public void ningunErrorSaltaSoloDeOpcion() {
        String p = fuente("PlayerActivity.java");
        int i = p.indexOf("public void onPlayerError(PlaybackException");
        assertTrue("debe existir onPlayerError", i >= 0);
        int fin = p.indexOf("@Override", i + 10);
        String cuerpo = p.substring(i, fin > i ? fin : Math.min(p.length(), i + 6000));
        assertFalse("onPlayerError NO debe cambiar de servidor por su cuenta",
                cuerpo.contains("cargarOpcionServidor("));
        assertFalse("onPlayerError NO debe zapear de canal por su cuenta",
                cuerpo.contains("zapearCanal("));
        assertFalse("no debe existir el salto silencioso estilo dzasports",
                p.contains("switchToNextServerSilently"));
        // Lo que sí debe hacer al agotar reintentos: marcar y avisar.
        assertTrue("al agotar reintentos se marca la opcion fallida",
                cuerpo.contains("opcionesFallidas.add(opcionActual)"));
    }

    private static String fuenteRes(String ruta) {
        java.io.File f = new java.io.File("src/main/res/" + ruta);
        if (!f.isFile()) f = new java.io.File("app/src/main/res/" + ruta);
        try {
            return new String(java.nio.file.Files.readAllBytes(f.toPath()),
                    StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            throw new AssertionError("no encuentro el recurso " + ruta + ": " + e);
        }
    }

    @Test
    public void retratoYPaisajeConZapeoEstiloDzasports() {
        // Existen los dos layouts, con los mismos ids clave.
        String land = fuenteRes("layout/activity_player.xml");
        String port = fuenteRes("layout/activity_player_portrait.xml");
        // v7.9: en paisaje NO hay cabecera con nombre de canal (la spec no la
        // pone), pero SÍ hay tira de canales para zapear sin salir.
        assertFalse("paisaje: fuera la cabecera con el nombre", land.contains("@+id/cabeceraPlayer"));
        assertTrue("paisaje: tira de canales para zapear", land.contains("@+id/tiraCanalesPlayer"));
        assertTrue("retrato: zona de video", port.contains("@+id/zonaVideo"));
        assertTrue("retrato: tira de canales", port.contains("@+id/tiraCanalesPlayer"));
        assertTrue("retrato: servidores", port.contains("@+id/contenedorServidores"));
        assertTrue("retrato: canales", port.contains("@+id/contenedorCanalesPlayer"));

        String p = fuente("PlayerActivity.java");
        assertTrue("rotar re-infla sin recrear la actividad",
                p.contains("onConfigurationChanged") && p.contains("atarInterfazPlayer()"));
        assertTrue("el reproductor inicia en retrato y conserva el cambio manual a paisaje",
                p.contains("setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)")
                        && p.contains("ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE")
                        && !p.contains("SCREEN_ORIENTATION_SENSOR"));
        java.io.File manifestFile = new java.io.File("src/main/AndroidManifest.xml");
        if (!manifestFile.isFile()) {
            manifestFile = new java.io.File("app/src/main/AndroidManifest.xml");
        }
        String manifest;
        try {
            manifest = new String(java.nio.file.Files.readAllBytes(manifestFile.toPath()),
                    java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            throw new AssertionError("no se pudo leer AndroidManifest.xml", e);
        }
        int playerActivity = manifest.indexOf("android:name=\".PlayerActivity\"");
        int endActivity = manifest.indexOf("/>", playerActivity);
        assertTrue("el manifiesto fija el inicio en retrato",
                playerActivity >= 0 && endActivity > playerActivity
                        && manifest.substring(playerActivity, endActivity)
                        .contains("android:screenOrientation=\"portrait\""));
        assertTrue("existe el zapeo manual de canales",
                p.contains("void zapearCanal(") && p.contains("void pintarTiraCanales()"));
    }

    /**
     * v7.9 — el paisaje es la especificación completa del estilo del player
     * (§1 landscape), con el ajuste pedido por el usuario: los servidores
     * PEGADOS al borde de arriba y la tira de canales PEGADA al borde de abajo.
     */
    @Test
    public void paisajeEsLaEspecificacionDelPlayer() {
        String land = fuenteRes("layout/activity_player.xml");

        // Root: RelativeLayout de fondo negro y video a pantalla completa.
        assertTrue("paisaje: root RelativeLayout", land.contains("<RelativeLayout"));
        assertTrue("paisaje: fondo negro", land.contains("@android:color/black"));
        assertTrue("paisaje: el PlayerView", land.contains("@+id/playerView"));
        assertTrue("paisaje: video a pantalla completa",
                land.contains("android:layout_width=\"match_parent\""));

        // §1: controles nativos apagados y video llenando el marco en paisaje.
        assertTrue("paisaje: use_controller=false", land.contains("app:use_controller=\"false\""));
        assertTrue("paisaje: resize_mode=fill", land.contains("app:resize_mode=\"fill\""));

        // §1: ProgressBar centrado (el cargando).
        assertTrue("paisaje: cargando centrado", land.contains("@+id/cargandoPlayer"));
        assertTrue("paisaje: el cargando esta centrado en pantalla",
                land.contains("android:layout_centerInParent=\"true\""));

        // §1: ARRIBA, pegada al borde — cabecera de servidores + refrescar.
        assertTrue("paisaje: cabecera de servidores", land.contains("@+id/filaServidores"));
        assertTrue("paisaje: etiqueta de servidores", land.contains("@string/player_servidores"));
        assertTrue("paisaje: lista de servidores", land.contains("@+id/contenedorServidores"));
        assertTrue("paisaje: boton de refrescar en la cabecera", land.contains("@+id/btnRefresh"));
        assertTrue("paisaje: refrescar de 28dp", land.contains("android:layout_width=\"28dp\""));
        assertTrue("paisaje: cabecera con padding 8dp", land.contains("android:padding=\"8dp\""));

        // §1: CENTRO — los cinco controles, inicialmente gone.
        assertTrue("paisaje: controles del centro", land.contains("@+id/centerControls"));
        for (String b : new String[]{"@+id/btnRewind", "@+id/btnPlayPause", "@+id/btnForward",
                "@+id/btnVolume", "@+id/btnPantalla"}) {
            assertTrue("paisaje: falta el control " + b, land.contains(b));
        }
        assertTrue("paisaje: botones de 55dp", land.contains("android:layout_width=\"55dp\""));
        assertTrue("paisaje: botones borderless",
                land.contains("?attr/selectableItemBackgroundBorderless"));
        int iCentro = land.indexOf("@+id/centerControls");
        assertTrue("paisaje: los controles empiezan en gone",
                land.substring(iCentro, iCentro + 400).contains("android:visibility=\"gone\""));

        // §1: ABAJO, pegada al borde — tira de canales de 135dp con #80000000.
        assertTrue("paisaje: tira de canales", land.contains("@+id/tiraCanalesPlayer"));
        assertTrue("paisaje: tira de 135dp", land.contains("android:layout_height=\"135dp\""));
        assertTrue("paisaje: fondo de la tira #80000000", land.contains("#80000000"));
        assertTrue("paisaje: padding 9dp de la tira", land.contains("android:padding=\"9dp\""));
        assertTrue("paisaje: scroll de canales", land.contains("@+id/scrollCanalesPlayer"));
        assertTrue("paisaje: contenedor de canales", land.contains("@+id/contenedorCanalesPlayer"));

        // Controles opcionales del reproductor en paisaje.
        assertTrue("paisaje: botón PiP disponible dentro de los controles",
                land.contains("@+id/btnPip"));
        // v7.33: la spec decia "sin barra", pero el usuario la pidio explicita
        // ("como en Netflix"). Estos dos assertFalse eran el candado de aquella
        // decision; se invierten a proposito, no es una regresion.
        assertTrue("paisaje: la barra de progreso volvio (v7.33)", land.contains("@+id/seekBar"));
        assertFalse("paisaje: fuera el chip de calidad", land.contains("@+id/chipCalidad"));
        assertFalse("paisaje: fuera el nombre del canal", land.contains("@+id/tvTituloPlayer"));
        assertFalse("paisaje: fuera la flecha de atras", land.contains("@+id/btnAtrasPlayer"));
        assertFalse("paisaje: fuera el include de controles del retrato",
                land.contains("custom_player_controls"));

        // El WebView de reserva existe pero esta en gone: no se ve.
        assertTrue("paisaje: el WebView de reserva", land.contains("@+id/webPlayable"));
        assertTrue("paisaje: el WebView esta oculto", land.contains("android:visibility=\"gone\""));
    }

    /** v7.9 — lo que la spec pide y se cumple en el código, no en el XML. */
    @Test
    public void paisajeCumpleLaSpecEnElCodigo() {
        String p = fuente("PlayerActivity.java");
        String land = fuenteRes("layout/activity_player.xml");
        assertTrue("auto-ocultar a los 5 s", p.contains("CONTROLS_TIMEOUT = 5000"));
        assertTrue("el escalado cambia a fill en paisaje y fit en retrato",
                p.contains("esHorizontal\n                ? AspectRatioFrameLayout.RESIZE_MODE_FILL\n"
                        + "                : AspectRatioFrameLayout.RESIZE_MODE_FIT"));
        assertTrue("paisaje: fill en el layout", land.contains("app:resize_mode=\"fill\""));
        assertTrue("el cargando se oculta a los 8 s",
                p.contains("postDelayed(ocultarCargandoRun, 8000)"));
        assertTrue("el cargando se ensena al bufferear", p.contains("mostrarCargando();"));
        assertTrue("el PiP se conecta cuando el layout incluye el boton",
                p.contains("if (btnPip != null) btnPip.setOnClickListener"));
        assertTrue("la barra de progreso se ata solo si el layout la trae (v7.33: ya la trae)",
                p.contains("if (seekBar != null) seekBar.setOnSeekBarChangeListener"));
        assertTrue("los controles del centro van y vienen con el overlay",
                p.contains("if (centerControls != null) centerControls.setVisibility(View.VISIBLE)")
                        && p.contains("if (centerControls != null) centerControls.setVisibility(View.GONE)"));
    }

    @Test
    public void agendaPrecargaCanalesParaMostrarLaListaAlRegresar() {
        String main = fuente("Main.java");
        String precarga = fuente("AutoCanales.java");
        String canales = fuente("CanalesEnVivoFragment.java");
        assertTrue("la app inicia la precarga al abrir",
                main.contains("AutoCanales.precargarAsync(this);"));
        assertTrue("la precarga se hace fuera del hilo principal",
                precarga.contains("cargarAsync(app, false, new Oyente()")
                        && precarga.contains("FuenteCanales.desdePreferencias(app).cargarAsync"));
        assertTrue("la rejilla consume los canales precargados",
                canales.contains("AutoCanales.precargaDisponible(contexto)")
                        && canales.contains("aplicarCanales(precarga.canales, precarga.origen)"));
    }


    /**
     * v7.11 - el RETRATO pasa a ser la especificacion completa del estilo del
     * player (S1 portrait), que es la del repo de referencia (v7.8.5):
     * video de 300dp arriba, cabecera transparente superpuesta (atras + titulo
     * + servidores), controles centrados de 55dp con PiP, tira de canales de
     * 115dp superpuesta abajo y marco del banner debajo del video.
     */
    @Test public void retratoEsLaEspecificacionDelPlayer() {
        String port = fuenteRes("layout/activity_player_portrait.xml");

        // Root: RelativeLayout de fondo negro.
        assertTrue("retrato: root RelativeLayout", port.contains("<RelativeLayout"));
        assertTrue("retrato: fondo negro", port.contains("@android:color/black"));

        // S1 portrait: CABECERA superpuesta al video - atras 40dp + titulo
        // 16sp bold, y debajo los servidores con su refrescar de 28dp.
        assertTrue("retrato: cabecera", port.contains("@+id/cabeceraPlayer"));
        assertTrue("retrato: cabecera sobre el video",
                port.contains("android:layout_alignTop=\"@id/zonaVideo\""));
        assertTrue("retrato: flecha de atras", port.contains("@+id/btnAtrasPlayer"));
        assertTrue("retrato: atras de 40dp", port.contains("android:layout_width=\"40dp\""));
        assertTrue("retrato: titulo del canal", port.contains("@+id/tvTituloPlayer"));
        assertTrue("retrato: titulo 16sp", port.contains("android:textSize=\"16sp\""));
        assertTrue("retrato: servidores en la cabecera", port.contains("@+id/filaServidores"));
        assertTrue("retrato: etiqueta de servidores", port.contains("@string/player_servidores"));
        assertTrue("retrato: lista de servidores", port.contains("@+id/contenedorServidores"));
        assertTrue("retrato: boton de refrescar", port.contains("@+id/btnRefresh"));
        assertTrue("retrato: refrescar de 28dp", port.contains("android:layout_width=\"28dp\""));

        // S1 portrait: VIDEO de 300dp pegado al borde superior.
        assertTrue("retrato: zona de video", port.contains("@+id/zonaVideo"));
        assertTrue("retrato: video de 300dp", port.contains("android:layout_height=\"300dp\""));
        assertTrue("retrato: video arriba", port.contains("android:layout_alignParentTop=\"true\""));
        assertTrue("retrato: el PlayerView", port.contains("@+id/playerView"));
        assertTrue("retrato: use_controller=false", port.contains("app:use_controller=\"false\""));

        // S1 portrait: cargando centrado.
        assertTrue("retrato: cargando centrado", port.contains("@+id/cargandoPlayer"));
        assertTrue("retrato: el cargando esta centrado",
                port.contains("android:layout_gravity=\"center\""));

        // S1 portrait: CONTROLES CENTRADOS de 55dp, con PiP.
        assertTrue("retrato: overlay de controles", port.contains("@+id/controls"));
        for (String b : new String[]{"@+id/btnRewind", "@+id/btnPlayPause", "@+id/btnForward",
                "@+id/btnVolume", "@+id/btnPip", "@+id/btnPantalla"}) {
            assertTrue("retrato: falta el control " + b, port.contains(b));
        }
        assertTrue("retrato: botones de 55dp", port.contains("android:layout_width=\"55dp\""));
        assertTrue("retrato: botones borderless",
                port.contains("?attr/selectableItemBackgroundBorderless"));

        // S1 portrait: CANALES de 115dp con #80000000 y padding 8dp,
        // SUPERPUESTOS al borde de abajo del video.
        assertTrue("retrato: tira de canales", port.contains("@+id/tiraCanalesPlayer"));
        assertTrue("retrato: tira de 115dp", port.contains("android:layout_height=\"115dp\""));
        assertTrue("retrato: fondo de la tira #80000000", port.contains("#80000000"));
        assertTrue("retrato: padding 8dp de la tira", port.contains("android:padding=\"8dp\""));
        assertTrue("retrato: la tira va al fondo del video",
                port.contains("android:layout_gravity=\"bottom\""));
        assertTrue("retrato: scroll de canales", port.contains("@+id/scrollCanalesPlayer"));
        assertTrue("retrato: contenedor de canales", port.contains("@+id/contenedorCanalesPlayer"));

        // S1 portrait: BANNER de 60dp (#C9F0C5) debajo del video.
        assertTrue("retrato: marco del banner", port.contains("@+id/adBannerContainer"));
        assertTrue("retrato: banner de 60dp",
                port.contains("android:layout_height=\"60dp\""));
        assertTrue("retrato: banner debajo del video",
                port.contains("android:layout_below=\"@id/zonaVideo\""));
        assertTrue("retrato: color del banner #C9F0C5", port.contains("#C9F0C5"));

        // Lo que la spec NO pone en retrato.
        // v7.33: igual que en paisaje, la barra vuelve a proposito.
        assertTrue("retrato: la barra de progreso volvio (v7.33)", port.contains("@+id/seekBar"));
        assertFalse("retrato: la spec no pone el include de controles compartido",
                port.contains("custom_player_controls"));

        // El WebView de reserva existe pero esta en gone: no se ve.
        assertTrue("retrato: el WebView de reserva", port.contains("@+id/webPlayable"));
    }

    // ---------- v7.14: paginas web fuera de ExoPlayer ----------

    /** El log del usuario: la «cura» dejo en la fila
     *  https://www.youtube.com/watch?v=IGEHE6fw-3U y el player se la mando a
     *  ExoPlayer como si fuera un m3u8. Acabo pidiendo un .js a una red de anuncios
     *  (spe6.speedaidata.cfd) desde el WebView oculto. */
    @Test public void unEnlaceDeYoutubeNoEsUnStream() {
        assertTrue(PlayerActivity.esPaginaWebNoStream("https://www.youtube.com/watch?v=IGEHE6fw-3U"));
        assertTrue(PlayerActivity.esPaginaWebNoStream("https://youtu.be/IGEHE6fw-3U"));
        assertTrue(PlayerActivity.esPaginaWebNoStream("http://youtube.com/embed/abc"));
        assertTrue(PlayerActivity.esPaginaWebNoStream("https://vimeo.com/123456"));
        assertTrue(PlayerActivity.esPaginaWebNoStream("https://ejemplo.tv/pelicula.html"));
        assertTrue(PlayerActivity.esPaginaWebNoStream("https://ejemplo.tv/pelicula.html?ref=x"));
    }

    /** Los streams de verdad SIGUEN yendo a ExoPlayer. Ojo aqui: muchos canales
     *  sirven el playlist sin extension (/live/user/pass/1234, playlist.php?id=..). */
    @Test public void losStreamsDeVerdadNoSeTocan() {
        assertFalse(PlayerActivity.esPaginaWebNoStream(
                "http://45.228.234.30:16000/play/a05t/index.m3u8"));
        assertFalse(PlayerActivity.esPaginaWebNoStream(
                "https://cdn.ejemplo/live/8571/9271/45123"));
        assertFalse(PlayerActivity.esPaginaWebNoStream(
                "https://ejemplo.tv/playlist.php?id=44&sig=ab12"));
        assertFalse(PlayerActivity.esPaginaWebNoStream(
                "https://ejemplo.tv/pages/bein/index.html"));
        assertFalse(PlayerActivity.esPaginaWebNoStream(
                "https://ejemplo.tv/video.mp4"));
        assertFalse(PlayerActivity.esPaginaWebNoStream(null));
        assertFalse(PlayerActivity.esPaginaWebNoStream(""));
    }
}
