package com.michomuchomacho.rubato.api.model;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/** GET /slskd/jobs — ultimi job fetch+import. */
public class GuruJobsResponse {

    @SerializedName("jobs")
    public List<GuruJobStatus> jobs;
}
