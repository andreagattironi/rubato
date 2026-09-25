package com.Rubato.api.model;

import com.google.gson.annotations.SerializedName;

/** POST /slskd/enqueue {artist, title, album?} */
public class GuruEnqueueRequest {

    @SerializedName("artist")
    public final String artist;

    @SerializedName("title")
    public final String title;

    @SerializedName("album")
    public final String album;

    public GuruEnqueueRequest(String artist, String title, String album) {
        this.artist = artist;
        this.title = title;
        this.album = album;
    }
}
