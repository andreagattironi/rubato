package com.Rubato.api;

import android.util.Log;

import com.eddyizm.tempus.BuildConfig;
import com.eddyizm.tempus.util.Preferences;
import com.Rubato.api.model.GuruDiscoverResponse;
import com.Rubato.api.model.GuruEnqueueRequest;
import com.Rubato.api.model.GuruImportRequest;
import com.Rubato.api.model.GuruJobAccepted;
import com.Rubato.api.model.GuruJobStatus;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Call;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Client HTTP per guru-api (Pi). Seconda base URL + Bearer token,
 * configurabili in Impostazioni (chiavi guru_url / guru_token).
 * Ricostruito quando le prefs cambiano (reset()).
 */
public class GuruClient {

    private static final String TAG = "GuruClient";
    private static GuruClient instance;

    private final GuruService service;

    private GuruClient(String baseUrl, String token) {
        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        logging.setLevel(BuildConfig.DEBUG
                ? HttpLoggingInterceptor.Level.BASIC
                : HttpLoggingInterceptor.Level.NONE);

        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .callTimeout(5, TimeUnit.MINUTES)
                .addInterceptor(logging)
                .addInterceptor(chain -> chain.proceed(chain.request().newBuilder()
                        .header("Authorization", "Bearer " + token)
                        .header("User-Agent", "Rubato/" + BuildConfig.VERSION_NAME)
                        .build()))
                .build();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(ensureTrailingSlash(baseUrl))
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        service = retrofit.create(GuruService.class);
    }

    public static synchronized GuruClient getInstance() {
        if (instance == null) {
            String url = Preferences.getGuruUrl();
            String token = Preferences.getGuruToken();
            if (url == null || url.trim().isEmpty() || token == null || token.trim().isEmpty()) {
                throw new IllegalStateException("guru-api non configurato (URL/token in Impostazioni)");
            }
            instance = new GuruClient(url.trim(), token.trim());
        }
        return instance;
    }

    /** Da chiamare quando URL/token cambiano nelle impostazioni. */
    public static synchronized void reset() {
        instance = null;
    }

    public static boolean isConfigured() {
        String url = Preferences.getGuruUrl();
        String token = Preferences.getGuruToken();
        return url != null && !url.trim().isEmpty() && token != null && !token.trim().isEmpty();
    }

    private static String ensureTrailingSlash(String url) {
        String u = url.trim();
        return u.endsWith("/") ? u : u + "/";
    }

    // --- API -----------------------------------------------------------------

    public Call<GuruDiscoverResponse> discover(String query, String artist) {
        Log.d(TAG, "discover: " + query);
        return service.discover(query, artist);
    }

    public Call<GuruJobAccepted> enqueue(String artist, String title, String album) {
        Log.d(TAG, "enqueue: " + artist + " - " + title);
        return service.enqueue(new GuruEnqueueRequest(artist, title, album));
    }

    public Call<GuruJobStatus> status(String jobId) {
        return service.status(jobId);
    }

    public Call<GuruJobAccepted> importFetchJob(String fetchJobId) {
        Log.d(TAG, "importFetchJob: " + fetchJobId);
        return service.importJob(GuruImportRequest.forFetchJob(fetchJobId));
    }

    public Call<Map<String, Object>> queue() {
        return service.queue();
    }
}
