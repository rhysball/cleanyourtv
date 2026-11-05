package io.github.sumyabazar1.weather;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * One reading for Home's weather line ({@link NowProvider}), run once when the launcher asks and the reading is
 * stale. Nothing repeats on its own: with no launcher asking, nothing is fetched. A failed fetch is not retried
 * for {@link Forecast#RETRY_MS}, however often Home asks.
 */
public final class RefreshJob extends JobService {
    private static final int JOB_ID = 1;
    private static final String PREFS = "refresh";

    private final ExecutorService io = Executors.newSingleThreadExecutor();

    /** Fetch soon, once the network is up, unless a fetch is already queued or one was tried just now. */
    static void request(Context ctx) {
        SharedPreferences tried = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        long since = System.currentTimeMillis() - tried.getLong("at", 0);
        if (since >= 0 && since < Forecast.RETRY_MS) return;
        JobScheduler jobs = ctx.getSystemService(JobScheduler.class);
        if (jobs == null || jobs.getPendingJob(JOB_ID) != null) return;
        tried.edit().putLong("at", System.currentTimeMillis()).apply();
        jobs.schedule(new JobInfo.Builder(JOB_ID, new ComponentName(ctx, RefreshJob.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .build());
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        SharedPreferences place = getSharedPreferences("place", MODE_PRIVATE);
        if (!place.contains("name")) return false;
        double lat = Double.longBitsToDouble(place.getLong("lat", 0));
        double lon = Double.longBitsToDouble(place.getLong("lon", 0));
        io.execute(() -> {
            try {
                NowProvider.save(this, Forecast.fetch(lat, lon, Forecast.MIN_DAYS));
            } catch (Exception e) {
                // Offline or Open-Meteo down: Home keeps its line until it is too old, and asks again later.
            }
            jobFinished(params, false);
        });
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return false;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        io.shutdownNow();
    }
}
