package com.Rubato.ui.discovery;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.Rubato.api.GuruClient;
import com.Rubato.api.model.GuruDiscoverResponse;

import java.util.Collections;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** Ricerca catalogo esterno via guru-api /discover (Deezer). */
public class DiscoveryViewModel extends ViewModel {

    private final MutableLiveData<List<GuruDiscoverResponse.GuruTrack>> tracks =
            new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);
    private final MutableLiveData<String> error = new MutableLiveData<>(null);

    public LiveData<List<GuruDiscoverResponse.GuruTrack>> getTracks() {
        return tracks;
    }

    public LiveData<Boolean> getLoading() {
        return loading;
    }

    public LiveData<String> getError() {
        return error;
    }

    public void search(String query) {
        if (query == null || query.trim().length() < 2) return;
        if (!GuruClient.isConfigured()) {
            error.setValue("configure");
            return;
        }
        loading.setValue(true);
        error.setValue(null);
        GuruClient.getInstance().discover(query.trim(), null)
                .enqueue(new Callback<GuruDiscoverResponse>() {
                    @Override
                    public void onResponse(Call<GuruDiscoverResponse> call,
                                           Response<GuruDiscoverResponse> response) {
                        loading.setValue(false);
                        if (response.body() != null && response.body().tracks != null) {
                            tracks.setValue(response.body().tracks);
                            if (response.body().tracks.isEmpty()) {
                                error.setValue("empty");
                            }
                        } else {
                            error.setValue("http-" + response.code());
                        }
                    }

                    @Override
                    public void onFailure(Call<GuruDiscoverResponse> call, Throwable t) {
                        loading.setValue(false);
                        error.setValue("net");
                    }
                });
    }
}
