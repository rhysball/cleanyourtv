package io.github.sumyabazar1.weather;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;

/**
 * The weather now as one line for the Clean Your TV launcher's home_weather key ("10° · Partly cloudy"), on
 * {@code content://io.github.sumyabazar1.weather.now/now}. Read-only and exported without a permission, so it
 * holds only what Home shows anyway: no place, no coordinates. A query on a reading older than
 * {@link Forecast#REFRESH_MS} asks {@link RefreshJob} for a new one, so the add-on fetches only while a launcher is
 * asking, and the launcher stays offline. An empty cursor hides the line.
 */
public final class NowProvider extends ContentProvider {
    static final String AUTHORITY = "io.github.sumyabazar1.weather.now";
    static final Uri URI = Uri.parse("content://" + AUTHORITY + "/now");
    private static final String PREFS = "now";
    /** A reading older than this is not shown: better no line than a morning's weather in the evening. */
    private static final long SHOW_MS = 3 * 60 * 60_000L;

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) {
        Context ctx = getContext();
        MatrixCursor out = new MatrixCursor(new String[] {"line"});
        if (ctx == null || !ctx.getSharedPreferences("place", Context.MODE_PRIVATE).contains("name")) return out;
        SharedPreferences now = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        long at = now.getLong("at", 0);
        long age = System.currentTimeMillis() - at;
        if (at == 0 || age < 0 || age >= Forecast.REFRESH_MS) RefreshJob.request(ctx);
        if (at == 0 || age < 0 || age > SHOW_MS) return out;
        double tempC = Double.longBitsToDouble(now.getLong("temp", 0));
        int code = now.getInt("code", 3);
        boolean day = now.getBoolean("day", true);
        out.addRow(new Object[] {Forecast.degrees(tempC, fahrenheit(ctx)) + "° · " + ctx.getString(Forecast.sky(code, day))});
        out.setNotificationUri(ctx.getContentResolver(), URI);
        return out;
    }

    /** The unit the page shows (Weather settings, falling back to options.xml). */
    private static boolean fahrenheit(Context ctx) {
        return ctx.getSharedPreferences("options", Context.MODE_PRIVATE).getBoolean("fahrenheit",
                "f".equalsIgnoreCase(ctx.getString(R.string.temperature_unit).trim()));
    }

    /** Keep a fresh reading for Home and tell the launcher. */
    static void save(Context ctx, Forecast.Report r) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putLong("temp", Double.doubleToRawLongBits(r.tempC))
                .putInt("code", r.code)
                .putBoolean("day", r.day)
                .putLong("at", System.currentTimeMillis())
                .apply();
        changed(ctx);
    }

    /** A new place: the old reading is not its weather. */
    static void clear(Context ctx) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply();
        changed(ctx);
    }

    /** Something the line shows changed (a reading, the place or the unit). */
    static void changed(Context ctx) {
        ctx.getContentResolver().notifyChange(URI, null);
    }

    @Override
    public String getType(Uri uri) {
        return "vnd.android.cursor.item/vnd." + AUTHORITY;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("read-only");
    }

    @Override
    public int delete(Uri uri, String selection, String[] args) {
        throw new UnsupportedOperationException("read-only");
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] args) {
        throw new UnsupportedOperationException("read-only");
    }
}
