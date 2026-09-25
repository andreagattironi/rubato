package com.Rubato.api.model;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/** GET /discover — type=track|album|artist (Deezer). */
public class GuruDiscoverResponse {

    @SerializedName("query")
    public String query;

    @SerializedName("type")
    public String type;

    @SerializedName("count")
    public int count;

    @SerializedName("tracks")
    public List<GuruTrack> tracks;

    @SerializedName("albums")
    public List<GuruAlbum> albums;

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

        /** Solo locale (non dal server): id album Deezer per la tracklist. */
        public long albumId;
    }

    public static class GuruAlbum {
        @SerializedName("id")
        public long id;

        @SerializedName("title")
        public String title;

        @SerializedName("artist")
        public String artist;

        @SerializedName("cover")
        public String cover;

        @SerializedName("deezer_link")
        public String deezerLink;
    }
}
