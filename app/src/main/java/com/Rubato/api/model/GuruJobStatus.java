package com.Rubato.api.model;

import com.google.gson.annotations.SerializedName;

/** GET /slskd/status/{id} — stato job persistito (fetch o import). */
public class GuruJobStatus {

    @SerializedName("job_id")
    public String jobId;

    @SerializedName("kind")
    public String kind;

    @SerializedName("status")
    public String status;

    @SerializedName("artist")
    public String artist;

    @SerializedName("title")
    public String title;

    @SerializedName("album")
    public String album;

    @SerializedName("dry_run")
    public boolean dryRun;

    @SerializedName("cli_tail")
    public String cliTail;

    public boolean isDone() {
        return "done".equals(status);
    }

    public boolean isFailed() {
        return "failed".equals(status);
    }

    public boolean isFetch() {
        return "fetch".equals(kind);
    }

    public boolean isImport() {
        return "import".equals(kind);
    }
}
