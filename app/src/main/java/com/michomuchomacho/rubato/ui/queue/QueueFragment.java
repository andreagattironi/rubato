package com.michomuchomacho.rubato.ui.queue;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.michomuchomacho.rubato.R;
import com.michomuchomacho.rubato.api.GuruClient;
import com.michomuchomacho.rubato.api.model.GuruJobsResponse;
import com.michomuchomacho.rubato.util.Preferences;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** Tab Home "Coda": job fetch/import guru-api con refresh ogni 15s
 * mentre visibile. Sola lettura: niente più cecità su Android. */
public class QueueFragment extends Fragment {

    private static final long POLL_MS = 15000;

    private QueueJobAdapter adapter;
    private ProgressBar progress;
    private TextView emptyView;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean visibleToUser;
    private boolean loading;

    private final Runnable poller = new Runnable() {
        @Override
        public void run() {
            if (!visibleToUser || !isAdded()) return;
            refresh(false);
            handler.postDelayed(this, POLL_MS);
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_queue, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        RecyclerView list = view.findViewById(R.id.queue_recycler_view);
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        list.setHasFixedSize(true);
        list.setNestedScrollingEnabled(false);
        adapter = new QueueJobAdapter();
        list.setAdapter(adapter);

        progress = view.findViewById(R.id.queue_progress);
        emptyView = view.findViewById(R.id.queue_empty_view);
    }

    @Override
    public void onResume() {
        super.onResume();
        visibleToUser = true;
        refresh(true);
        handler.postDelayed(poller, POLL_MS);
    }

    @Override
    public void onPause() {
        visibleToUser = false;
        handler.removeCallbacks(poller);
        super.onPause();
    }

    private void refresh(boolean showProgress) {
        if (!isAdded()) return;
        if (!Preferences.isGuruConfigured()) {
            progress.setVisibility(View.GONE);
            emptyView.setVisibility(View.VISIBLE);
            emptyView.setText(R.string.guru_not_configured);
            adapter.setItems(null);
            return;
        }
        if (showProgress && adapter.getItemCount() == 0) {
            progress.setVisibility(View.VISIBLE);
        }
        if (loading) return;
        loading = true;
        GuruClient.getInstance().jobs(20).enqueue(new Callback<GuruJobsResponse>() {
            @Override
            public void onResponse(Call<GuruJobsResponse> call,
                                   Response<GuruJobsResponse> response) {
                loading = false;
                if (!isAdded()) return;
                progress.setVisibility(View.GONE);
                if (response.body() != null && response.body().jobs != null
                        && !response.body().jobs.isEmpty()) {
                    emptyView.setVisibility(View.GONE);
                    adapter.setItems(response.body().jobs);
                } else {
                    adapter.setItems(null);
                    emptyView.setVisibility(View.VISIBLE);
                    if (response.isSuccessful()) {
                        emptyView.setText(R.string.queue_empty);
                    } else {
                        emptyView.setText(getString(R.string.discovery_error,
                                "http-" + response.code()));
                    }
                }
            }

            @Override
            public void onFailure(Call<GuruJobsResponse> call, Throwable t) {
                loading = false;
                if (!isAdded()) return;
                progress.setVisibility(View.GONE);
                if (adapter.getItemCount() == 0) {
                    emptyView.setVisibility(View.VISIBLE);
                    emptyView.setText(R.string.guru_enqueue_failed);
                }
            }
        });
    }
}
