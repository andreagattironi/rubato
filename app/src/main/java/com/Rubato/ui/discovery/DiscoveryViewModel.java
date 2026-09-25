package com.Rubato.ui.discovery;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.Rubato.api.GuruClient;
import com.Rubato.api.model.GuruDiscoverResponse;
import com.Rubato.api.model.GuruRecommendResponse;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** Ricerca catalogo esterno (track/album) + consigliati (owned/missing). */
public class DiscoveryViewModel extends ViewModel {

    public enum Mode { TRACKS, ALBUMS }

    private final MutableLiveData<List<GuruDiscoverResponse.GuruTrack>> tracks =
            new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);
    private final MutableLiveData<String> error = new MutableLiveData<>(null);
    private final MutableLiveData<GuruRecommendResponse> recommend =
            new MutableLiveData<>(null);
    private final MutableLiveData<Mode> mode = new MutableLiveData<>(Mode.TRACKS);
    private String lastQuery = "";

    public LiveData<List<GuruDiscoverResponse.GuruTrack>> getTracks() {
        return tracks;
    }

    public LiveData<Boolean> getLoading() {
        return loading;
    }

    public LiveData<String> getError() {
        return error;
    }

    public LiveData<GuruRecommendResponse> getRecommend() {
        return recommend;
    }

    public LiveData<Mode> getMode() {
        return mode;
    }

    public void setMode(Mode m) {
        mode.setValue(m);
        if (!lastQuery.isEmpty()) search(lastQuery);
        else loadRecommend();
    }

    public void search(String query) {
        lastQuery = query != null ? query.trim() : "";
        recommend.setValue(null);
        if (lastQuery.length() < 2) {
            tracks.setValue(Collections.emptyList());
            error.setValue(null);
            if (mode.getValue() == Mode.TRACKS) loadRecommend();
            return;
        }
        if (!GuruClient.isConfigured()) {
            error.setValue("configure");
            return;
        }
        loading.setValue(true);
        error.setValue(null);
        String type = mode.getValue() == Mode.ALBUMS ? "album" : "track";
        GuruClient.getInstance().discoverByType(lastQuery, type)
                .enqueue(new Callback<GuruDiscoverResponse>() {
                    @Override
                    public void onResponse(Call<GuruDiscoverResponse> call,
                                           Response<GuruDiscoverResponse> response) {
                        loading.setValue(false);
                        List<GuruDiscoverResponse.GuruTrack> rows = new ArrayList<>();
                        if (response.body() != null) {
                            if (response.body().tracks != null) {
                                rows.addAll(response.body().tracks);
                            }
                            if (response.body().albums != null) {
                                for (GuruDiscoverResponse.GuruAlbum a : response.body().albums) {
                                    GuruDiscoverResponse.GuruTrack r =
                                            new GuruDiscoverResponse.GuruTrack();
                                    r.id = a.id;
                                    r.albumId = a.id;
                                    r.title = a.title;
                                    r.artist = a.artist;
                                    r.album = a.title;
                                    r.cover = a.cover;
                                    r.deezerLink = a.deezerLink;
                                    rows.add(r);
                                }
                            }
                        }
                        tracks.setValue(rows);
                        if (rows.isEmpty()) error.setValue("empty");
                    }

                    @Override
                    public void onFailure(Call<GuruDiscoverResponse> call, Throwable t) {
                        loading.setValue(false);
                        error.setValue("net");
                    }
                });
    }

    public void loadRecommend() {
        if (!GuruClient.isConfigured()) {
            error.setValue("configure");
            return;
        }
        loading.setValue(true);
        GuruClient.getInstance().recommend(null, 8)
                .enqueue(new Callback<GuruRecommendResponse>() {
                    @Override
                    public void onResponse(Call<GuruRecommendResponse> call,
                                           Response<GuruRecommendResponse> response) {
                        loading.setValue(false);
                        if (response.body() != null) {
                            recommend.setValue(response.body());
                            showMissing(response.body());
                        } else {
                            error.setValue("http-" + response.code());
                        }
                    }

                    @Override
                    public void onFailure(Call<GuruRecommendResponse> call, Throwable t) {
                        loading.setValue(false);
                        error.setValue("net");
                    }
                });
    }

    /** Missing -> righe scaricabili: in ALBUMS i top album, altrimenti top track. */
    private void showMissing(GuruRecommendResponse rec) {
        List<GuruDiscoverResponse.GuruTrack> rows = new ArrayList<>();
        boolean albums = mode.getValue() == Mode.ALBUMS;
        if (rec.missing != null) {
            for (GuruRecommendResponse.RecommendArtist a : rec.missing) {
                if (albums) {
                    if (a.topAlbum == null || a.topAlbum.id == 0) continue;
                    GuruDiscoverResponse.GuruTrack r = new GuruDiscoverResponse.GuruTrack();
                    r.albumId = a.topAlbum.id;
                    r.id = a.topAlbum.id;
                    r.title = a.topAlbum.title;
                    r.artist = a.topAlbum.artist != null && !a.topAlbum.artist.isEmpty()
                            ? a.topAlbum.artist : a.name;
                    r.album = r.title;
                    r.cover = a.topAlbum.cover != null ? a.topAlbum.cover : "";
                    rows.add(r);
                    continue;
                }
                String title = a.bestTrackTitle();
                if (title == null) continue;
                GuruDiscoverResponse.GuruTrack r = new GuruDiscoverResponse.GuruTrack();
                r.title = title;
                r.artist = a.name;
                r.album = "";
                r.cover = a.cover != null ? a.cover : "";
                rows.add(r);
            }
        }
        tracks.setValue(rows);
        error.setValue(rows.isEmpty() ? "empty" : null);
    }
}
