package com.Rubato.ui.discovery;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.eddyizm.tempus.R;
import com.Rubato.api.model.GuruDiscoverResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Risultati discovery: copertina + titoli + bottone download ⬇ per brano. */
public class DiscoveryTrackAdapter
        extends RecyclerView.Adapter<DiscoveryTrackAdapter.ViewHolder> {

    public interface Listener {
        void onDownloadClicked(GuruDiscoverResponse.GuruTrack track);
    }

    private final Listener listener;
    private final List<GuruDiscoverResponse.GuruTrack> items = new ArrayList<>();

    public DiscoveryTrackAdapter(Listener listener) {
        this.listener = listener;
    }

    public void setItems(List<GuruDiscoverResponse.GuruTrack> tracks) {
        items.clear();
        if (tracks != null) items.addAll(tracks);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_discovery_track, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        GuruDiscoverResponse.GuruTrack track = items.get(position);
        holder.title.setText(track.title != null ? track.title : "");
        String sub = (track.artist != null ? track.artist : "")
                + (track.album != null ? " • " + track.album : "");
        holder.subtitle.setText(sub);
        if (track.duration > 0) {
            holder.duration.setVisibility(View.VISIBLE);
            holder.duration.setText(String.format(Locale.US, "%d:%02d",
                    track.duration / 60, track.duration % 60));
        } else {
            holder.duration.setVisibility(View.GONE);
        }
        if (track.cover != null && !track.cover.isEmpty()) {
            Glide.with(holder.cover.getContext())
                    .load(track.cover)
                    .placeholder(R.drawable.ic_placeholder_song)
                    .into(holder.cover);
        } else {
            holder.cover.setImageResource(R.drawable.ic_placeholder_song);
        }
        holder.download.setOnClickListener(v -> {
            if (listener != null) listener.onDownloadClicked(track);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final ImageView cover;
        final TextView title;
        final TextView subtitle;
        final TextView duration;
        final ImageButton download;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            cover = itemView.findViewById(R.id.discovery_cover);
            title = itemView.findViewById(R.id.discovery_title);
            subtitle = itemView.findViewById(R.id.discovery_subtitle);
            duration = itemView.findViewById(R.id.discovery_duration);
            download = itemView.findViewById(R.id.discovery_download_button);
        }
    }
}
