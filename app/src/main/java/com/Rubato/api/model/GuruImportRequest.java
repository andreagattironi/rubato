package com.Rubato.api.model;

import com.google.gson.annotations.SerializedName;

/** POST /import {artist?, full?, job_id?} */
public class GuruImportRequest {

    @SerializedName("artist")
    public final String artist;

    @SerializedName("full")
    public final boolean full;

    @SerializedName("job_id")
    public final String jobId;

    public GuruImportRequest(String artist, boolean full, String jobId) {
        this.artist = artist;
        this.full = full;
        this.jobId = jobId;
    }

    public static GuruImportRequest forFetchJob(String fetchJobId) {
        return new GuruImportRequest(null, false, fetchJobId);
    }
}
