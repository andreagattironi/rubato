package com.Rubato.work;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.eddyizm.tempus.R;
import com.eddyizm.tempus.ui.activity.MainActivity;
import com.eddyizm.tempus.util.DownloadUtil;
import com.Rubato.api.GuruClient;
import com.Rubato.api.model.GuruJobAccepted;
import com.Rubato.api.model.GuruJobStatus;

import java.util.concurrent.TimeUnit;

import retrofit2.Response;

/**
 * Polls guru-api for a slskd job until it completes, then notifies.
 *
 * Flow: fetch (download on Pi) -> on done, POST /import (retag+library) ->
 * poll import -> terminal notification ("Ready to play" / failed).
 * One status check per run; not done -> Result.retry() with exponential
 * backoff (server-side work takes minutes; slskd is never instant).
 */
public class GuruPollWorker extends Worker {

    private static final String TAG = "GuruPollWorker";
    private static final int MAX_ATTEMPTS = 120;

    public static final String KEY_JOB_ID = "job_id";
    public static final String KEY_IMPORT_JOB_ID = "import_job_id";
    public static final String KEY_ARTIST = "artist";
    public static final String KEY_TITLE = "title";

    public GuruPollWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    /** Starts (or keeps) polling for a fetch job. Unique per job id. */
    public static void watchFetchJob(Context context, String jobId, String artist, String title) {
        Data input = new Data.Builder()
                .putString(KEY_JOB_ID, jobId)
                .putString(KEY_ARTIST, artist)
                .putString(KEY_TITLE, title)
                .build();
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(GuruPollWorker.class)
                .setInputData(input)
                .setConstraints(new Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build();
        WorkManager.getInstance(context).enqueueUniqueWork(
                "guru-" + jobId, ExistingWorkPolicy.KEEP, request);
    }

    @NonNull
    @Override
    public Result doWork() {
        if (!GuruClient.isConfigured()) {
            Log.w(TAG, "guru-api not configured, dropping work");
            return Result.failure();
        }
        if (getRunAttemptCount() > MAX_ATTEMPTS) {
            notifyTerminal(false, "Timed out waiting for the Pi — check manually");
            return Result.failure();
        }

        String jobId = getInputData().getString(KEY_JOB_ID);
        String importJobId = getInputData().getString(KEY_IMPORT_JOB_ID);
        String artist = getInputData().getString(KEY_ARTIST);
        String title = getInputData().getString(KEY_TITLE);
        if (jobId == null) return Result.failure();

        try {
            GuruClient client = GuruClient.getInstance();

            if (importJobId == null) {
                // Phase 1: wait for the download.
                Response<GuruJobStatus> resp = client.status(jobId).execute();
                GuruJobStatus st = resp.body();
                if (st == null) return Result.retry();
                Log.d(TAG, "fetch " + jobId + " -> " + st.status);
                if (st.isFailed()) {
                    notifyTerminal(false, label(artist, title));
                    return Result.failure();
                }
                if (!st.isDone()) return Result.retry();

                // Download done: trigger server-side import, then poll that.
                Response<GuruJobAccepted> imp = client.importFetchJob(jobId).execute();
                if (imp.body() == null || imp.body().jobId == null) {
                    // Import not accepted (e.g. dry-run server): still notify download done.
                    notifyTerminal(true, label(artist, title));
                    return Result.success();
                }
                // Re-enqueue self for the import phase (fresh attempt budget).
                Data next = new Data.Builder()
                        .putString(KEY_JOB_ID, jobId)
                        .putString(KEY_IMPORT_JOB_ID, imp.body().jobId)
                        .putString(KEY_ARTIST, artist)
                        .putString(KEY_TITLE, title)
                        .build();
                OneTimeWorkRequest followUp = new OneTimeWorkRequest.Builder(GuruPollWorker.class)
                        .setInputData(next)
                        .setConstraints(new Constraints.Builder()
                                .setRequiredNetworkType(NetworkType.CONNECTED)
                                .build())
                        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                        .build();
                WorkManager.getInstance(getApplicationContext()).enqueueUniqueWork(
                        "guru-import-" + jobId, ExistingWorkPolicy.KEEP, followUp);
                return Result.success();
            }

            // Phase 2: wait for the import.
            Response<GuruJobStatus> resp = client.status(importJobId).execute();
            GuruJobStatus st = resp.body();
            if (st == null) return Result.retry();
            Log.d(TAG, "import " + importJobId + " -> " + st.status);
            if (st.isFailed()) {
                notifyTerminal(false, label(artist, title));
                return Result.failure();
            }
            if (!st.isDone()) return Result.retry();

            notifyTerminal(true, label(artist, title));
            return Result.success();

        } catch (IllegalStateException e) {
            Log.w(TAG, "guru-api misconfigured", e);
            return Result.failure();
        } catch (Exception e) {
            Log.w(TAG, "poll failed, retrying", e);
            return Result.retry();
        }
    }

    private String label(String artist, String title) {
        if (artist != null && title != null) return artist + " — " + title;
        if (title != null) return title;
        return "";
    }

    private void notifyTerminal(boolean success, String detail) {
        Context context = getApplicationContext();
        String jobId = getInputData().getString(KEY_JOB_ID);
        int notifId = jobId != null ? jobId.hashCode() : 9001;

        Intent intent = new Intent(context, MainActivity.class)
                .setAction(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER);
        PendingIntent tap = PendingIntent.getActivity(
                context, notifId, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(
                context, DownloadUtil.DOWNLOAD_NOTIFICATION_CHANNEL_ID)
                .setContentTitle(context.getString(R.string.guru_job_title))
                .setContentText(success
                        ? context.getString(R.string.guru_job_done)
                          + (detail.isEmpty() ? "" : ": " + detail)
                        : context.getString(R.string.guru_job_failed)
                          + (detail.isEmpty() ? "" : ": " + detail))
                .setSmallIcon(success ? R.drawable.ic_check_circle : R.drawable.ic_error)
                .setContentIntent(tap)
                .setOngoing(false)
                .setAutoCancel(true)
                .setOnlyAlertOnce(false)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);

        NotificationManager nm =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.notify(notifId, builder.build());
    }
}
