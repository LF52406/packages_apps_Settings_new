/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * Manual storage maintenance frontend. All filesystem work is performed by
 * vold through the permission-checked StorageManagerService binder API.
 */
package com.android.settings.deviceinfo.storage;

import android.app.ActionBar;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.IVoldTaskListener;
import android.os.Handler;
import android.os.Looper;
import android.os.PersistableBundle;
import android.os.ServiceManager;
import android.os.storage.IStorageManager;
import android.util.Log;
import android.view.MenuItem;

import com.android.settings.R;

import java.lang.ref.WeakReference;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * F2FS/flash storage maintenance, not file-by-file defragmentation.
 * The graph contains only confirmed MANUAL maintenance completions on this
 * device; it does not pretend to count filesystem fragments.
 */
public final class StorageRefreshActivity extends Activity {
    private static final String TAG = "MistStorageRefresh";
    private static final String PREFS = "mist_storage_refresh";
    private static final String HISTORY = "successful_manual_passes";
    private static final AtomicBoolean RUNNING = new AtomicBoolean(false);
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());
    private static final long COMPLETION_TIMEOUT_MS = 30L * 60L * 1000L;
    // Do not repeat successful maintenance unnecessarily. A completed request
    // is historical data, not a reason to turn the primary action into "Close".
    private static final long MIN_SUCCESS_INTERVAL_MS = 6L * 60L * 60L * 1000L;
    // Updated on each onResume so a rotated activity receives completion UI.
    private static volatile WeakReference<StorageRefreshActivity> sVisibleActivity =
            new WeakReference<>(null);

    private StorageRefreshDashboard mDashboard;
    private final Handler mUiHandler = new Handler(Looper.getMainLooper());
    private final Runnable mCooldownExpiryRefresh = this::refreshUi;
    private boolean mResumed;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setTitle(R.string.storage_refresh_title);
        final ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        mDashboard = new StorageRefreshDashboard(this, this::handleAction);
        setContentView(mDashboard);
        refreshUi();
    }

    private void handleAction() {
        if (!RUNNING.get()) startMaintenance();
    }

    /**
     * A successful manual job stays in history; it must never make the
     * dashboard act as a completion dialog on subsequent visits.
     * Returns zero if the next manual invocation is currently permitted.
     */
    private static long nextAllowedRunAt(SharedPreferences prefs, long now) {
        if (prefs.getInt("last_result", -1) != 0) return 0L;
        final long last = prefs.getLong("last_attempt", 0L);
        if (last <= 0L || now < last) return 0L;
        final long elapsed = now - last;
        if (elapsed >= MIN_SUCCESS_INTERVAL_MS) return 0L;
        return last + MIN_SUCCESS_INTERVAL_MS;
    }

    @Override
    protected void onResume() {
        super.onResume();
        mResumed = true;
        sVisibleActivity = new WeakReference<>(this);
        if (mDashboard != null) refreshUi();
    }

    @Override
    protected void onPause() {
        mResumed = false;
        mUiHandler.removeCallbacks(mCooldownExpiryRefresh);
        if (sVisibleActivity.get() == this) {
            sVisibleActivity = new WeakReference<>(null);
        }
        super.onPause();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // Data is kept independently from UI widgets so sessions can be restored
    // after screen rotation and day changes. Values are successful manual
    // maintenance callbacks, never invented filesystem fragment counts.
    static int[] successfulRunsPerDay(List<Long> history, LocalDate today, ZoneId zone) {
        final int[] counts = new int[7];
        for (long timestamp : history) {
            final LocalDate date = Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDate();
            final long daysOld = today.toEpochDay() - date.toEpochDay();
            if (daysOld >= 0 && daysOld < 7) counts[6 - (int) daysOld]++;
        }
        return counts;
    }

    private static List<Long> readHistory(SharedPreferences prefs) {
        final List<Long> history = new ArrayList<>();
        final String serialized = prefs.getString(HISTORY, "");
        if (serialized == null || serialized.isEmpty()) return history;
        for (String value : serialized.split(",")) {
            try {
                final long time = Long.parseLong(value);
                if (time > 0) history.add(time);
            } catch (NumberFormatException ignored) { }
        }
        return history;
    }

    private void refreshUi() {
        final SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        final long now = System.currentTimeMillis();
        final long eligibleAt = nextAllowedRunAt(prefs, now);
        mDashboard.render(
                readHistory(prefs),
                prefs.getLong("last_attempt", 0),
                prefs.getInt("last_result", -1),
                RUNNING.get(),
                eligibleAt);
        // Enable the action at the actual expiry even when the user keeps
        // this screen open. Never retain a paused Activity for hours.
        mUiHandler.removeCallbacks(mCooldownExpiryRefresh);
        if (mResumed && eligibleAt > now) {
            mUiHandler.postDelayed(mCooldownExpiryRefresh,
                    Math.max(1000L, eligibleAt - now + 100L));
        }
    }

    /**
     * Xiaomi's automatic policy uses >=75% battery or charger, temperature below
     * 40 C. Require these safeguards for a manually requested long flash job.
     */
    private boolean isSafeToRun() {
        final Intent battery = registerReceiver(null,
                new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (battery == null) return false;
        final int level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        final int scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
        final int temperature = battery.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 999);
        final int status = battery.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        final boolean charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL;
        return level >= 0 && scale > 0 && temperature < 400 &&
                (charging || 100L * level >= 75L * scale);
    }

    private void startMaintenance() {
        if (RUNNING.get()) return;
        // Re-running GC/TRIM repeatedly wastes flash write cycles.
        final SharedPreferences previous = getSharedPreferences(PREFS, MODE_PRIVATE);
        if (nextAllowedRunAt(previous, System.currentTimeMillis()) > 0L) {
            mDashboard.showMessage(R.string.storage_refresh_recent);
            return;
        }
        if (!isSafeToRun()) {
            mDashboard.showMessage(R.string.storage_refresh_unsafe);
            return;
        }
        if (!RUNNING.compareAndSet(false, true)) return;
        mDashboard.showMessage(R.string.storage_refresh_in_progress);
        refreshUi();

        final Context app = getApplicationContext();
        final AtomicBoolean finished = new AtomicBoolean(false);
        // Some vold/service failures lose the asynchronous completion callback.
        // Unblock the UI after the deadline, without recording false success.
        MAIN_HANDLER.postDelayed(
                () -> finishMaintenance(app, finished, -2), COMPLETION_TIMEOUT_MS);
        new Thread(() -> {
            try {
                final IStorageManager storage = IStorageManager.Stub.asInterface(
                        ServiceManager.getService("mount"));
                if (storage == null) throw new IllegalStateException("Storage service unavailable");
                storage.runIdleMaintenanceWithCallback(new IVoldTaskListener.Stub() {
                    @Override
                    public void onStatus(int status, PersistableBundle extras) {
                        // vold's status is not a percent-complete value.
                    }

                    @Override
                    public void onFinished(int status, PersistableBundle extras) {
                        finishMaintenance(app, finished, status);
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Unable to invoke privileged storage maintenance", e);
                finishMaintenance(app, finished, -1);
            }
        }, "MistStorageRefresh").start();
    }

    private static void finishMaintenance(
            Context app, AtomicBoolean finished, int status) {
        if (!finished.compareAndSet(false, true)) return;
        final long now = System.currentTimeMillis();
        final SharedPreferences prefs = app.getSharedPreferences(PREFS, MODE_PRIVATE);
        final SharedPreferences.Editor editor = prefs.edit();
        editor.putLong("last_attempt", now);
        editor.putInt("last_result", status);
        if (status == 0) {
            final List<Long> history = readHistory(prefs);
            history.add(now);
            // Keep the last 90 successful manual maintenance events.
            final StringBuilder builder = new StringBuilder();
            for (int i = Math.max(0, history.size() - 90); i < history.size(); i++) {
                if (builder.length() > 0) builder.append(',');
                builder.append(history.get(i));
            }
            editor.putString(HISTORY, builder.toString());
        }
        editor.apply();
        RUNNING.set(false);
        final StorageRefreshActivity current = sVisibleActivity.get();
        if (current != null) current.runOnUiThread(current::refreshUi);
    }
}
