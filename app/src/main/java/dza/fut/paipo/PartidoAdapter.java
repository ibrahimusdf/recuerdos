package dza.fut.paipo;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.media3.common.util.UnstableApi;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PartidoAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_PARTIDO = 0;

    private final List<Object> items;   // solo Partido desde v3.2.41
    private final Context context;
    private int expandedPosition = -1;

    public PartidoAdapter(List<Object> items, Context context) {
        this.items = items;
        this.context = context;
    }

    // v3.2.41: queda un único tipo de fila; sin filas publicitarias no hay que
    // discriminar, así que el getItemViewType se retira.
    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_partido, parent, false);
        return new PartidoViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof PartidoViewHolder) {
            Partido p = (Partido) items.get(position);
            PartidoViewHolder pHolder = (PartidoViewHolder) holder;
            pHolder.tvHora.setText(p.attributes.getHoraLocal());
            String desc = p.attributes.diary_description.replaceAll("^\\d{2}:\\d{2}\\s*", "");
            pHolder.tvDesc.setText(desc);

            boolean expanded = (position == expandedPosition);
            pHolder.channelsContainer.setVisibility(expanded ? View.VISIBLE : View.GONE);
            if (expanded) {
                pHolder.channelsContainer.removeAllViews();
                if (p.attributes.embeds != null && p.attributes.embeds.data != null) {
                    for (Partido.EmbedData embed : p.attributes.embeds.data) {
                        if (embed == null || embed.attributes == null
                                || embed.attributes.embed_name == null
                                || embed.attributes.embed_name.trim().isEmpty()) {
                            continue;
                        }
                        View fila = LayoutInflater.from(context)
                                .inflate(R.layout.item_canal_partido, pHolder.channelsContainer, false);
                        TextView canalVista = fila.findViewById(R.id.txtCanalPartidoNombre);
                        canalVista.setText(embed.attributes.embed_name);
                        String urlCanal = normalizarUrlAgenda(embed.attributes.embed_iframe);
                        if (urlCanal.isEmpty()) {
                            fila.setEnabled(false);
                            fila.setAlpha(0.5f);
                        } else {
                            fila.setOnClickListener(v -> lanzarReproductor(p, embed));
                        }
                        pHolder.channelsContainer.addView(fila);
                    }
                }
            }

            pHolder.itemView.setOnClickListener(v -> {
                int pos = holder.getAdapterPosition();
                if (expandedPosition == pos) {
                    int old = expandedPosition;
                    expandedPosition = -1;
                    notifyItemChanged(old);
                } else {
                    int old = expandedPosition;
                    expandedPosition = pos;
                    if (old >= 0) notifyItemChanged(old);
                    notifyItemChanged(expandedPosition);
                }
            });

        }
    }

    @OptIn(markerClass = UnstableApi.class)
    private void lanzarReproductor(Partido partido, Partido.EmbedData canalSeleccionado) {
        if (partido == null || partido.attributes == null || partido.attributes.embeds == null
                || partido.attributes.embeds.data == null || canalSeleccionado == null
                || canalSeleccionado.attributes == null) {
            return;
        }

        List<String> nombresServidores = new ArrayList<>();
        List<String> urlsServidores = new ArrayList<>();
        int indiceSeleccionado = -1;
        for (Partido.EmbedData embed : partido.attributes.embeds.data) {
            if (embed == null || embed.attributes == null
                    || embed.attributes.embed_name == null
                    || embed.attributes.embed_name.trim().isEmpty()) {
                continue;
            }
            nombresServidores.add(embed.attributes.embed_name.trim());
            urlsServidores.add(normalizarUrlAgenda(embed.attributes.embed_iframe));
            if (embed == canalSeleccionado) {
                indiceSeleccionado = nombresServidores.size() - 1;
            }
        }

        if (indiceSeleccionado < 0 || urlsServidores.get(indiceSeleccionado).isEmpty()) return;
        String tituloEvento = partido.attributes.diary_description == null
                ? "" : partido.attributes.diary_description.replaceAll("^\\d{2}:\\d{2}\\s*", "").trim();

        if (context instanceof android.app.Activity) {
            Intent intent = new Intent(context, PlayerActivity.class);
            intent.putExtra("url_iframe_inicial", urlsServidores.get(indiceSeleccionado));
            intent.putExtra("canal_titulo", tituloEvento);
            intent.putExtra("opciones_labels", nombresServidores.toArray(new String[0]));
            intent.putExtra("opciones_urls", urlsServidores.toArray(new String[0]));
            intent.putExtra("opcion_indice", indiceSeleccionado);
            intent.putExtra("agenda_evento", true);
            context.startActivity(intent);
        }
    }

    private static String normalizarUrlAgenda(String iframeUrl) {
        if (iframeUrl == null || iframeUrl.trim().isEmpty()) return "";
        String valor = iframeUrl.trim();
        String token = null;
        try {
            token = Uri.parse(valor).getQueryParameter("r");
        } catch (IllegalArgumentException ignored) {
            Matcher matcher = Pattern.compile("[?&]r=([^&]+)").matcher(valor);
            if (matcher.find()) token = matcher.group(1);
        }
        if (token == null) {
            Matcher matcher = Pattern.compile("[?&]r=([^&]+)").matcher(valor);
            if (matcher.find()) token = matcher.group(1);
        }
        if (token != null && !token.isEmpty()) {
            return "https://belkaperu.github.io/belkafut/repro.html?r=" + token;
        }
        if (valor.startsWith("http://") || valor.startsWith("https://")) return valor;
        return "https://belkaperu.github.io/belkafut/repro.html?r=" + valor;
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    // ─── ViewHolders ───
    public static class PartidoViewHolder extends RecyclerView.ViewHolder {
        TextView tvDesc, tvHora;
        LinearLayout channelsContainer;
        public PartidoViewHolder(View itemView) {
            super(itemView);
            tvDesc = itemView.findViewById(R.id.textDescripcion);
            tvHora = itemView.findViewById(R.id.textHora);
            channelsContainer = itemView.findViewById(R.id.channelsContainer);
        }
    }
}