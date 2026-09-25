package com.Rubato.api.model;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/** POST /slskd/enqueue-album {artist, album, tracks[]} */
public class GuruEnqueueAlbumRequest {

    @SerializedName("artist")
    public final String artist;

    @SerializedName("album")
    public final String album;

    @SerializedName("tracks")
    public final List<String> tracks;

    public GuruEnqueueAlbumRequest(String artist, String album, List<String> tracks) {
        this.artist = artist;
        this.album = album;
        this.tracks = tracks;
    }
}
