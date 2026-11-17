package dza.fut.paipo;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.text.TextUtils;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AlertDialog;
import androidx.media3.common.util.UnstableApi;
import androidx.recyclerview.widget.RecyclerView;

import android.graphics.drawable.Drawable;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;

import java.util.List;

public class CanalAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_CANAL = 0;

    private List<Object> items;   // solo Canal desde v3.2.41
    private final Context context;

    /**
     * Índice del tile seleccionado. DramaLive no pinta el tile activo con un relleno
     * fuerte: le dibuja un anillo de acento, así que aquí basta con propagar el estado
     * android:stateActivated al MaterialCardView (ver color/pelis_tile_stroke.xml).
     */
    private int posicionActiva = RecyclerView.NO_POSITION;

    /**
     * Con catálogos enormes, pedir el logo crudo (PNG de 200 KB para un tile de 80 dp)
     * es lo que hace que la rejilla tarde una eternidad. {@link Logos} hace lo mismo que
     * el cliente web de DramaLive: lo pide recortado y en WebP por images.weserv.nl.
     * Apagado salvo que el usuario lo encienda en la hoja "Fuente".
     */
    private boolean reducirLogos;

    public void setReducirLogos(boolean reducir) {
        if (reducirLogos == reducir) return;
        reducirLogos = reducir;
        notifyDataSetChanged();
    }

    /**
     * v3.2.43 — el corazon de la losa (favoritos de la tira) y el aviso al fragmento.
     * El set vive en el fragmento (UsoCanales); aqui solo se pinta y se notifica.
     */
    private java.util.Set<String> favoritos = java.util.Collections.emptySet();
    private OnFavorito onFavorito;

    public interface OnFavorito { void alTocar(Canal canal); }

    public void setFavoritos(java.util.Set<String> favoritos) {
        this.favoritos = favoritos == null ? java.util.Collections.<String>emptySet() : favoritos;
        notifyDataSetChanged();
    }

    public void setOnFavorito(OnFavorito l) { onFavorito = l; }

    public CanalAdapter(List<Object> items, Context context) {
        this.items = items;
        this.context = context;
    }

    /**
     * El fragmento la usa al filtrar por tópico o al escribir en la búsqueda. Un
     * re-filtro cambia la lista entera (y la posición de los anuncios), así que
     * aquí notifyItemRangeChanged sería exactamente lo mismo pero más verboso.
     */
    @SuppressLint("NotifyDataSetChanged")
    public void actualizarLista(List<Object> nuevosItems) {
        this.items = nuevosItems;
        this.posicionActiva = RecyclerView.NO_POSITION;
        notifyDataSetChanged();
    }

    // v3.2.41: un solo tipo de fila (los tiles), como en PartidoAdapter.
    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_canal, parent, false);
        return new CanalViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof CanalViewHolder) {
            Canal canal = (Canal) items.get(position);
            CanalViewHolder cHolder = (CanalViewHolder) holder;
            cHolder.txtNombre.setText(canal.title);
            cHolder.itemView.setActivated(position == posicionActiva);
            if (TextUtils.isEmpty(canal.image)) {
                // v3.2.32: adios al hueco gris — losa de iniciales con gradiente (DramaLive
                // nunca muestra un tile vacio; el icono pálido vivia muriendo de vergüenza).
                cHolder.imgCanal.setImageDrawable(Logos.avatar(context, canal.title));
            } else {
                String logo = reducirLogos ? Logos.reducido(canal.image, Logos.ANCHO_TILE) : canal.image;
                // v3.2.33: SIN centerCropTransform — la imagen entra ENTERA (fitCenter en la
                // vista, downsampling de Glide al tamano real del tile). El avatar hace de
                // placeholder/error: mientras carga o si el logo muere, la losa sigue llena.
                Drawable av = Logos.avatar(context, canal.title);
                Glide.with(context).load(logo).apply(new RequestOptions()
                        .placeholder(av).error(av)).into(cHolder.imgCanal);
            }
            // v3.2.43: el corazon alterna sin abrir la hoja, y abrir desde la rejilla
            // ya cuenta para el chip ★ Los más vistos (como el historial de Drama).
            final Canal paraFav = canal;
            if (cHolder.btnFav != null) {
                cHolder.btnFav.setText(favoritos.contains(UsoCanales.claveDe(canal))
                        ? "♥" : "♡");
                cHolder.btnFav.setOnClickListener(v -> {
                    if (onFavorito != null) onFavorito.alTocar(paraFav);
                });
            }
            cHolder.itemView.setOnClickListener(v -> {
                UsoCanales.registrarApertura(context, canal);
                marcarComoActivo(holder.getBindingAdapterPosition());
                abrirReproductor(canal);
            });
        }
    }

    private void marcarComoActivo(int posicion) {
        if (posicion == posicionActiva || posicion == RecyclerView.NO_POSITION) return;
        int anterior = posicionActiva;
        posicionActiva = posicion;
        if (anterior != RecyclerView.NO_POSITION) notifyItemChanged(anterior);
        notifyItemChanged(posicionActiva);
    }

    /**
     * Carga útil del tap sobre un canal de la rejilla: lo que necesita el
     * reproductor para arrancar y para pintar sus chips de servidores.
     *
     * <p>Clase plana y sin Android, para poder probarla en JVM puro
     * ({@code CanalAdapterTest}): si la normalización se rompe, se rompe el
     * test y no el usuario.</p>
     */
    static final class CargaCanal {
        final String titulo;
        final String[] etiquetas;
        final String[] urls;
        /** Índice de la opción con la que se arranca. */
        final int indice;
        final String urlInicial;

        CargaCanal(String titulo, String[] etiquetas, String[] urls, int indice, String urlInicial) {
            this.titulo = titulo;
            this.etiquetas = etiquetas;
            this.urls = urls;
            this.indice = indice;
            this.urlInicial = urlInicial;
        }
    }

    /**
     * v7.27: tocar un canal de la rejilla abre DIRECTAMENTE el reproductor, con
     * todos sus servidores ya cargados como opciones.
     *
     * <p>Antes salía una hoja intermedia ({@code BottomSheetOpcionesFragment})
     * que volvía a listar las mismas opciones que el reproductor ya iba a
     * pintar como chips: un tap de más para llegar al mismo sitio. Como en
     * dzasports, el tap lleva directo al vídeo y el cambio de servidor se hace
     * allí, sin salir.</p>
     */
    private void abrirReproductor(Canal canal) {
        ZapeoCanales.fijar(items, canal);   // v3.2.67: zapeo dentro del reproductor

        CargaCanal c = cargaDe(canal, FuenteCanales.dramaKey(context));
        if (c == null) {
            Toast.makeText(context, "No hay opciones disponibles", Toast.LENGTH_SHORT).show();
            return;
        }

        // v7.27: se resuelve el stream MIENTRAS se abre el reproductor, para
        // que cuando la pantalla esté arriba ya esté en caché (preResolve).
        // v7.30: TODAS las opciones, no solo la primera — si no, cambiar de
        // servidor era siempre a resolver de cero.
        java.util.List<String> todas = new java.util.ArrayList<>(c.urls.length);
        for (String u : c.urls) if (u != null && !u.isEmpty()) todas.add(u);
        PlayerActivity.preResolverVarios(context, todas);

        // v7.28: para que la rejilla se refresque al volver. Sin esto,
        // onResume() no tenía forma de saber de dónde veníamos.
        CanalesEnVivoFragment.marcarSalidaAlPlayer();

        Intent intent = new Intent(context, PlayerActivity.class);
        intent.putExtra("canal_titulo", c.titulo);
        intent.putExtra("opciones_labels", c.etiquetas);
        intent.putExtra("opciones_urls", c.urls);
        intent.putExtra("opcion_indice", c.indice);
        intent.putExtra("url_iframe_inicial", c.urlInicial);
        if (!(context instanceof android.app.Activity)) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    /**
     * Normaliza TODAS las opciones del canal y elige con cuál se arranca.
     *
     * <p>Devuelve null si el canal no trae ninguna opción usable. El índice se
     * queda en la primera que tenga URL real: así, si la opción 0 es un hueco,
     * se arranca con la siguiente en vez de con un error.</p>
     *
     * <p>Una opción con la URL en blanco se trata como ausente: si se dejara
     * pasar, el normalizador la completaría con la base del sitio
     * ({@code https://belkaperu.github.io} a secas, que es su último recurso
     * para rutas relativas) y el reproductor se quedaría intentando resolver
     * la portada en vez de un stream.</p>
     */
    @Nullable
    static CargaCanal cargaDe(@Nullable Canal canal, @Nullable String claveDrama) {
        if (canal == null || canal.options == null || canal.options.isEmpty()) return null;
        int n = canal.options.size();
        String[] etiquetas = new String[n];
        String[] urls = new String[n];
        int indice = -1;
        for (int i = 0; i < n; i++) {
            Canal.OpcionCanal o = canal.options.get(i);
            etiquetas[i] = (o == null || o.label == null) ? "" : o.label;
            String crudo = (o == null || o.url == null) ? "" : o.url.trim();
            urls[i] = crudo.isEmpty() ? "" : normalizarUrlCanal(o.url, claveDrama);
            if (indice < 0 && urls[i] != null && !urls[i].isEmpty()) indice = i;
        }
        if (indice < 0) return null;
        return new CargaCanal(canal.title, etiquetas, urls, indice, urls[indice]);
    }

    /**
     * El JSON de canales no entrega URLs completas, sino rutas como esta:
     *
     *   /p/foxrepro1.html?r=https://belkaperu.github.io/belkafut/repron.html?r=<embed>
     *
     * Dos problemas:
     *   1) es una ruta RELATIVA: ni el WebView ni OkHttp saben abrirla.
     *   2) el envoltorio "/p/*.html" YA NO EXISTE en el servidor del proveedor
     *      (responde 404 aunque se le ponga la base delante), asi que tampoco
     *      vale con completarla.
     *
     * Lo que si responde 200 es saltarse el envoltorio y abrir directamente la
     * URL interna que va despues de "?r=", que siempre es absoluta.
     */
    String normalizarUrlCanal(String url) {
        return normalizarUrlCanal(url, FuenteCanales.dramaKey(context));
    }

    /** Version pura (sin Android) para el test JVM. */
    static String normalizarUrlCanal(String url, @Nullable String claveDrama) {
        if (url == null) return null;
        String u = url.trim();
        // v3.2.5: el esquema propio del app NUNCA lleva prefijos: es la instruccion de
        // resolver glbr al reproducir (PlayerActivity lo entiende). Antes caia en el
        // "ultimo recurso" y salia corrupto con https://belkaperu.github.io delante.
        if (u.startsWith("dramalive://")) return u;
        if (u.startsWith("http")) {
            // enlace cifrado tipo Drama -> la URL de adentro, si se puede abrir
            String drama = EnlaceDrama.desenrollar(u, claveDrama);
            return drama != null ? drama : u;
        }
        int i = u.indexOf("?r=");
        if (i >= 0) {
            String interna = u.substring(i + 3).trim();
            if (interna.startsWith("http")) return interna;
        }
        // Ultimo recurso: se le pone la base del sitio del listado.
        return "https://belkaperu.github.io" + u;
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    // ─── ViewHolders ───
    public static class CanalViewHolder extends RecyclerView.ViewHolder {
        ImageView imgCanal;
        TextView txtNombre;
        TextView btnFav;   // v3.2.43
        public CanalViewHolder(View itemView) {
            super(itemView);
            imgCanal = itemView.findViewById(R.id.imgCanal);
            txtNombre = itemView.findViewById(R.id.txtNombreCanal);
            btnFav = itemView.findViewById(R.id.btnFavCanal);
        }
    }
}