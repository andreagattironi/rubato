package com.Rubato.api.model;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/** GET /recommend — owned[] in libreria, missing[] con top track per ⬇. */
public class GuruRecommendResponse {

    @SerializedName("seeds")
    public List<String> seeds;

    @SerializedName("owned")
    public List<RecommendArtist> owned;

    @SerializedName("missing")
    public List<RecommendArtist> missing;

    public static class RecommendArtist {
        @SerializedName("name")
        public String name;

        @SerializedName("match")
        public String match;

        @SerializedName("seed")
        public String seed;

        @SerializedName("cover")
        public String cover;

        @SerializedName("top_tracks")
        public List<TopTrack> topTracks;

        public String bestTrackTitle() {
            if (topTracks != null) {
                for (TopTrack t : topTracks) {
                    if (t.title != null && !t.title.isEmpty()) return t.title;
                }
            }
            return null;
        }
    }

    public static class TopTrack {
        @SerializedName("title")
        public String title;

        @SerializedName("artist")
        public String artist;
    }
}
