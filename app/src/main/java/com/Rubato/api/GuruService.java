package com.Rubato.api;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;

import com.Rubato.api.model.GuruDiscoverResponse;
import com.Rubato.api.model.GuruAlbumTracks;
import com.Rubato.api.model.GuruEnqueueAlbumRequest;
import com.Rubato.api.model.GuruEnqueueRequest;
import com.Rubato.api.model.GuruImportRequest;
import com.Rubato.api.model.GuruJobAccepted;
import com.Rubato.api.model.GuruJobStatus;
import com.Rubato.api.model.GuruRecommendResponse;

/** Retrofit interface per guru-api (Pi). Auth: Bearer via interceptor in GuruClient. */
public interface GuruService {

    @GET("health")
    Call<java.util.Map<String, Object>> health();

    @GET("discover")
    Call<GuruDiscoverResponse> discover(@Query("q") String query,
                                        @Query("artist") String artist);

    @GET("discover")
    Call<GuruDiscoverResponse> discover(@Query("q") String query,
                                        @Query("artist") String artist,
                                        @Query("type") String type);

    @GET("deezer/album/{id}")
    Call<GuruAlbumTracks> albumTracks(@Path("id") long albumId);

    @GET("recommend")
    Call<GuruRecommendResponse> recommend(@Query("artist") String artist,
                                          @Query("count") int count);

    @POST("slskd/enqueue")
    Call<GuruJobAccepted> enqueue(@Body GuruEnqueueRequest request);

    @POST("slskd/enqueue-album")
    Call<GuruJobAccepted> enqueueAlbum(@Body GuruEnqueueAlbumRequest request);

    @GET("slskd/status/{id}")
    Call<GuruJobStatus> status(@Path("id") String jobId);

    @GET("slskd/queue")
    Call<java.util.Map<String, Object>> queue();

    @POST("import")
    Call<GuruJobAccepted> importJob(@Body GuruImportRequest request);
}
