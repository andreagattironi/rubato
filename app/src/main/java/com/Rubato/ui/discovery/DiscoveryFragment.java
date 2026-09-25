package com.Rubato.ui.discovery;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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
import com.Rubato.api.model.GuruDiscoverResponse;
import com.Rubato.api.model.GuruJobAccepted;
import com.Rubato.work.GuruPollWorker;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** Tab Discovery: cerca nel catalogo esterno (Deezer via guru-api) e
 * scarica con ⬇ anche brani NON in libreria. */
public class DiscoveryFragment extends Fragment implements DiscoveryTrackAdapter.Listener {

    private DiscoveryViewModel viewModel;
    private DiscoveryTrackAdapter adapter;
    private ProgressBar progress;
    private TextView emptyView;

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

        if (!Preferences.isGuruConfigured()) {
            emptyView.setVisibility(View.VISIBLE);
            emptyView.setText(R.string.guru_not_configured);
        }
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
        GuruClient.getInstance().enqueue(track.artist, track.title, track.album)
                .enqueue(new Callback<GuruJobAccepted>() {
                    @Override
                    public void onResponse(Call<GuruJobAccepted> call,
                                           Response<GuruJobAccepted> response) {
                        if (response.body() == null || response.body().jobId == null) {
                            Toast.makeText(requireContext(),
                                    R.string.guru_enqueue_failed, Toast.LENGTH_SHORT).show();
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
}
