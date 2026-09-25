package com.Rubato.api.model;

import com.google.gson.annotations.SerializedName;

/** Risposta 202 di /slskd/enqueue e /import: {job_id, status, dry_run} */
public class GuruJobAccepted {

    @SerializedName("job_id")
    public String jobId;

    @SerializedName("status")
    public String status;

    @SerializedName("dry_run")
    public boolean dryRun;
}
