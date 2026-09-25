package com.Rubato.api.model;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/** GET /discover — catalogo esterno (Deezer) dal Pi. */
public class GuruDiscoverResponse {

    @SerializedName("query")
    public String query;

    @SerializedName("count")
    public int count;

    @SerializedName("tracks")
    public List<GuruTrack> tracks;

    public static class GuruTrack {
        @SerializedName("id")
        public long id;

        @SerializedName("title")
        public String title;

        @SerializedName("artist")
        public String artist;

        @SerializedName("album")
        public String album;

        @SerializedName("cover")
        public String cover;

        @SerializedName("duration")
        public int duration;

        @SerializedName("deezer_link")
        public String deezerLink;
    }
}
