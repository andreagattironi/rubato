package com.michomuchomacho.rubato.ui.adapter;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.michomuchomacho.rubato.databinding.ItemAlbumCarouselBinding;
import com.michomuchomacho.rubato.glide.CustomGlideRequest;
import com.michomuchomacho.rubato.interfaces.ClickCallback;
import com.michomuchomacho.rubato.subsonic.models.AlbumID3;
import com.michomuchomacho.rubato.util.Constants;
import com.michomuchomacho.rubato.util.Preferences;
import com.michomuchomacho.rubato.util.TileSizeManager;

import java.util.Collections;
import java.util.List;

public class AlbumCarouselAdapter extends RecyclerView.Adapter<AlbumCarouselAdapter.ViewHolder> {
    private final ClickCallback click;
    private List<AlbumID3> albums;
    private boolean showArtist;
    private int sizePx = 400;

    public AlbumCarouselAdapter(ClickCallback click, boolean showArtist) {
        this.click = click;
        this.albums = Collections.emptyList();
        this.showArtist = showArtist;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemAlbumCarouselBinding view = ItemAlbumCarouselBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        TileSizeManager.getInstance().calculateTileSize(parent.getContext());
        sizePx = TileSizeManager.getInstance().getTileSizePx(parent.getContext());
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {

        ViewGroup.LayoutParams lp = holder.item.albumCoverImageView.getLayoutParams();
        lp.width = sizePx;
        lp.height = sizePx;
        holder.item.albumCoverImageView.setLayoutParams(lp);

        AlbumID3 album = albums.get(position);

        if (Preferences.getAlbumYearVisible()) {
            String albumName = album.getName() + " (" + album.getYear() + ")";
            holder.item.albumNameLabel.setText(albumName);
        } else {
            holder.item.albumNameLabel.setText(album.getName());
        }

        holder.item.artistNameLabel.setText(album.getArtist());
        holder.item.artistNameLabel.setVisibility(showArtist ? View.VISIBLE : View.GONE);

        CustomGlideRequest.Builder
                .from(holder.itemView.getContext(), album.getCoverArtId(), CustomGlideRequest.ResourceType.Album)
                .build()
                .into(holder.item.albumCoverImageView);
    }

    @Override
    public int getItemCount() {
        return albums.size();
    }

    public void setItems(List<AlbumID3> albums) {
        this.albums = albums;
        notifyDataSetChanged();
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        ItemAlbumCarouselBinding item;

        ViewHolder(ItemAlbumCarouselBinding item) {
            super(item.getRoot());
            this.item = item;

            itemView.setOnClickListener(v -> {
                Bundle bundle = new Bundle();
                bundle.putParcelable(Constants.ALBUM_OBJECT, albums.get(getBindingAdapterPosition()).strippedForNav());
                click.onAlbumClick(bundle);
            });

            itemView.setOnLongClickListener(v -> {
                Bundle bundle = new Bundle();
                bundle.putParcelable(Constants.ALBUM_OBJECT, albums.get(getBindingAdapterPosition()).strippedForNav());
                click.onAlbumLongClick(bundle);
                return true;
            });

            item.albumNameLabel.setSelected(true);
            item.artistNameLabel.setSelected(true);
        }
    }
}
