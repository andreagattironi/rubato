package com.Rubato.api.model;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/** GET /deezer/album/{id} — tracklist per enqueue album. */
public class GuruAlbumTracks {

    @SerializedName("id")
    public long id;

    @SerializedName("title")
    public String title;

    @SerializedName("artist")
    public String artist;

    @SerializedName("cover")
    public String cover;

    @SerializedName("tracks")
    public List<AlbumTrack> tracks;

    public static class AlbumTrack {
        @SerializedName("id")
        public long id;

        @SerializedName("title")
        public String title;

        @SerializedName("artist")
        public String artist;

        @SerializedName("duration")
        public int duration;
    }
}
