package com.Rubato.api;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;

import com.Rubato.api.model.GuruDiscoverResponse;
import com.Rubato.api.model.GuruEnqueueRequest;
import com.Rubato.api.model.GuruImportRequest;
import com.Rubato.api.model.GuruJobAccepted;
import com.Rubato.api.model.GuruJobStatus;

/** Retrofit interface per guru-api (Pi). Auth: Bearer via interceptor in GuruClient. */
public interface GuruService {

    @GET("health")
    Call<java.util.Map<String, Object>> health();

    @GET("discover")
    Call<GuruDiscoverResponse> discover(@Query("q") String query,
                                        @Query("artist") String artist);

    @POST("slskd/enqueue")
    Call<GuruJobAccepted> enqueue(@Body GuruEnqueueRequest request);

    @GET("slskd/status/{id}")
    Call<GuruJobStatus> status(@Path("id") String jobId);

    @GET("slskd/queue")
    Call<java.util.Map<String, Object>> queue();

    @POST("import")
    Call<GuruJobAccepted> importJob(@Body GuruImportRequest request);
}
