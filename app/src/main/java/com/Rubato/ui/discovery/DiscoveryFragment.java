package com.Rubato.ui.discovery;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SearchView;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.eddyizm.tempus.R;
import com.eddyizm.tempus.util.Preferences;
import com.Rubato.api.GuruClient;
import com.Rubato.api.model.GuruAlbumTracks;
import com.Rubato.api.model.GuruDiscoverResponse;
import com.Rubato.api.model.GuruJobAccepted;
import com.Rubato.api.model.GuruRecommendResponse;
import com.Rubato.work.GuruPollWorker;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** Tab Discovery: brani/album fuori libreria (Deezer) + consigliati
 * (owned in libreria vs missing da scaricare). */
public class DiscoveryFragment extends Fragment implements DiscoveryTrackAdapter.Listener {

    private DiscoveryViewModel viewModel;
    private DiscoveryTrackAdapter adapter;
    private ProgressBar progress;
    private TextView emptyView;
    private TextView sectionHeader;
    private Button songsButton;
    private Button albumsButton;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_discovery, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(this).get(DiscoveryViewModel.class);

        SearchView searchView = view.findViewById(R.id.discovery_search_view);
        searchView.setQueryHint(getString(R.string.discovery_hint));
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                viewModel.search(query);
                searchView.clearFocus();
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                return false;
            }
        });

        songsButton = view.findViewById(R.id.discovery_mode_songs);
        albumsButton = view.findViewById(R.id.discovery_mode_albums);
        songsButton.setOnClickListener(v -> viewModel.setMode(DiscoveryViewModel.Mode.TRACKS));
        albumsButton.setOnClickListener(v -> viewModel.setMode(DiscoveryViewModel.Mode.ALBUMS));

        sectionHeader = view.findViewById(R.id.discovery_section_header);

        RecyclerView list = view.findViewById(R.id.discovery_recycler_view);
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new DiscoveryTrackAdapter(this);
        list.setAdapter(adapter);

        progress = view.findViewById(R.id.discovery_progress);
        emptyView = view.findViewById(R.id.discovery_empty_view);

        viewModel.getTracks().observe(getViewLifecycleOwner(), tracks ->
                adapter.setItems(tracks));
        viewModel.getLoading().observe(getViewLifecycleOwner(), loading ->
                progress.setVisibility(Boolean.TRUE.equals(loading) ? View.VISIBLE : View.GONE));
        viewModel.getError().observe(getViewLifecycleOwner(), error -> {
            if (error == null) {
                emptyView.setVisibility(View.GONE);
                return;
            }
            emptyView.setVisibility(View.VISIBLE);
            switch (error) {
                case "configure":
                    emptyView.setText(R.string.guru_not_configured);
                    break;
                case "empty":
                    emptyView.setText(R.string.discovery_empty);
                    break;
                case "net":
                    emptyView.setText(R.string.guru_enqueue_failed);
                    break;
                default:
                    emptyView.setText(getString(R.string.discovery_error, error));
                    break;
            }
        });
        viewModel.getMode().observe(getViewLifecycleOwner(), mode -> {
            // Entrambi sempre cliccabili: l'attivo si distingue per opacità,
            // MAI con setEnabled(false) che uccide il tap (bug visto su Pixel).
            boolean albums = mode == DiscoveryViewModel.Mode.ALBUMS;
            songsButton.setEnabled(true);
            albumsButton.setEnabled(true);
            songsButton.setAlpha(albums ? 0.5f : 1.0f);
            albumsButton.setAlpha(albums ? 1.0f : 0.5f);
            sectionHeader.setVisibility(View.GONE);
        });
        viewModel.getRecommend().observe(getViewLifecycleOwner(), rec -> {
            if (rec == null) {
                sectionHeader.setVisibility(View.GONE);
                return;
            }
            StringBuilder sb = new StringBuilder();
            if (rec.seeds != null && !rec.seeds.isEmpty()) {
                sb.append(getString(R.string.discovery_because, join(rec.seeds)));
            }
            if (rec.owned != null && !rec.owned.isEmpty()) {
                if (sb.length() > 0) sb.append("\n");
                sb.append(getString(R.string.discovery_owned, joinNames(rec.owned)));
            }
            if (sb.length() > 0) {
                sectionHeader.setText(sb.toString());
                sectionHeader.setVisibility(View.VISIBLE);
            } else {
                sectionHeader.setVisibility(View.GONE);
            }
        });

        if (!Preferences.isGuruConfigured()) {
            emptyView.setVisibility(View.VISIBLE);
            emptyView.setText(R.string.guru_not_configured);
        }
    }

    private String join(List<String> items) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(items.get(i));
        }
        return sb.toString();
    }

    private String joinNames(List<GuruRecommendResponse.RecommendArtist> items) {
        List<String> names = new ArrayList<>();
        for (GuruRecommendResponse.RecommendArtist a : items) names.add(a.name);
        return join(names);
    }

    @Override
    public void onDownloadClicked(GuruDiscoverResponse.GuruTrack track) {
        if (!Preferences.isGuruConfigured()) {
            Toast.makeText(requireContext(), R.string.guru_not_configured, Toast.LENGTH_LONG).show();
            return;
        }
        if (track.artist == null || track.title == null) {
            Toast.makeText(requireContext(), R.string.guru_enqueue_failed, Toast.LENGTH_SHORT).show();
            return;
        }
        if (track.albumId > 0) {
            downloadAlbum(track);
            return;
        }
        GuruClient.getInstance().enqueue(track.artist, track.title, track.album)
                .enqueue(new Callback<GuruJobAccepted>() {
                    @Override
                    public void onResponse(Call<GuruJobAccepted> call,
                                           Response<GuruJobAccepted> response) {
                        if (!isAdded() || response.body() == null || response.body().jobId == null) {
                            if (isAdded()) {
                                Toast.makeText(requireContext(),
                                        R.string.guru_enqueue_failed, Toast.LENGTH_SHORT).show();
                            }
                            return;
                        }
                        GuruPollWorker.watchFetchJob(
                                requireContext().getApplicationContext(),
                                response.body().jobId, track.artist, track.title);
                        Toast.makeText(requireContext(),
                                R.string.guru_enqueue_queued, Toast.LENGTH_LONG).show();
                    }

                    @Override
                    public void onFailure(Call<GuruJobAccepted> call, Throwable t) {
                        if (isAdded()) {
                            Toast.makeText(requireContext(),
                                    R.string.guru_enqueue_failed, Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    /** ⬇ su un album: tracklist Deezer -> un job per tutto l'album. */
    private void downloadAlbum(GuruDiscoverResponse.GuruTrack track) {
        Toast.makeText(requireContext(), R.string.guru_album_loading, Toast.LENGTH_SHORT).show();
        GuruClient.getInstance().albumTracks(track.albumId)
                .enqueue(new Callback<GuruAlbumTracks>() {
                    @Override
                    public void onResponse(Call<GuruAlbumTracks> call,
                                           Response<GuruAlbumTracks> response) {
                        if (!isAdded() || response.body() == null
                                || response.body().tracks == null
                                || response.body().tracks.isEmpty()) {
                            if (isAdded()) {
                                Toast.makeText(requireContext(),
                                        R.string.guru_enqueue_failed, Toast.LENGTH_SHORT).show();
                            }
                            return;
                        }
                        List<String> titles = new ArrayList<>();
                        for (GuruAlbumTracks.AlbumTrack t : response.body().tracks) {
                            if (t.title != null && !t.title.isEmpty()) titles.add(t.title);
                        }
                        String artist = response.body().artist != null
                                && !response.body().artist.isEmpty()
                                ? response.body().artist : track.artist;
                        GuruClient.getInstance()
                                .enqueueAlbum(artist, response.body().title, titles)
                                .enqueue(new Callback<GuruJobAccepted>() {
                                    @Override
                                    public void onResponse(Call<GuruJobAccepted> call2,
                                                           Response<GuruJobAccepted> response2) {
                                        if (!isAdded() || response2.body() == null
                                                || response2.body().jobId == null) {
                                            if (isAdded()) {
                                                Toast.makeText(requireContext(),
                                                        R.string.guru_enqueue_failed,
                                                        Toast.LENGTH_SHORT).show();
                                            }
                                            return;
                                        }
                                        GuruPollWorker.watchFetchJob(
                                                requireContext().getApplicationContext(),
                                                response2.body().jobId, artist,
                                                response.body().title);
                                        Toast.makeText(requireContext(),
                                                R.string.guru_enqueue_queued, Toast.LENGTH_LONG).show();
                                    }

                                    @Override
                                    public void onFailure(Call<GuruJobAccepted> call2, Throwable t) {
                                        if (isAdded()) {
                                            Toast.makeText(requireContext(),
                                                    R.string.guru_enqueue_failed,
                                                    Toast.LENGTH_SHORT).show();
                                        }
                                    }
                                });
                    }

                    @Override
                    public void onFailure(Call<GuruAlbumTracks> call, Throwable t) {
                        if (isAdded()) {
                            Toast.makeText(requireContext(),
                                    R.string.guru_enqueue_failed, Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }
}
