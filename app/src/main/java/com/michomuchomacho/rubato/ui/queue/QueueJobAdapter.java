package com.michomuchomacho.rubato.ui.queue;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.michomuchomacho.rubato.R;
import com.michomuchomacho.rubato.api.model.GuruJobStatus;

import java.util.ArrayList;
import java.util.List;

/** Righe job fetch/import con stato live (niente azioni, sola lettura). */
public class QueueJobAdapter extends RecyclerView.Adapter<QueueJobAdapter.ViewHolder> {

    private final List<GuruJobStatus> items = new ArrayList<>();

    public void setItems(List<GuruJobStatus> jobs) {
        items.clear();
        if (jobs != null) items.addAll(jobs);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_queue_job, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        GuruJobStatus job = items.get(position);
        String what = job.artist != null ? job.artist : "";
        if (job.album != null && !job.album.isEmpty()) {
            what += what.isEmpty() ? job.album : " — " + job.album;
        } else if (job.title != null && !job.title.isEmpty()) {
            what += what.isEmpty() ? job.title : " — " + job.title;
        }
        holder.title.setText(what.isEmpty() ? job.jobId : what);
        StringBuilder sub = new StringBuilder(job.kind != null ? job.kind : "");
        if (job.moved != null && job.moved > 0) {
            sub.append(" · ").append(job.moved).append(" file");
        }
        if (job.scan != null && !job.scan.isEmpty()) {
            sub.append(" · scan ").append(job.scan);
        }
        if (job.retries != null && job.retries > 0) {
            sub.append(" · tent.").append(job.retries);
        }
        holder.subtitle.setText(sub.toString());
        holder.status.setText(statusLabel(job.status));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    private String statusLabel(String status) {
        if (status == null) return "?";
        switch (status) {
            case "queued": return "in coda…";
            case "running": return "in corso…";
            case "done": return "pronto ✓";
            case "failed": return "fallito ✗";
            default: return status;
        }
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView title;
        final TextView subtitle;
        final TextView status;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.queue_title);
            subtitle = itemView.findViewById(R.id.queue_subtitle);
            status = itemView.findViewById(R.id.queue_status);
        }
    }
}
