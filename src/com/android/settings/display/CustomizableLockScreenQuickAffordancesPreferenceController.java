/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.settings.display;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.widget.Toast;

import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;

import com.android.settings.R;
import com.android.settings.core.BasePreferenceController;
import com.android.settings.core.PreferenceControllerMixin;
import com.android.settingslib.utils.ThreadUtils;

/**
 * Preference for accessing an experience to customize lock screen quick affordances.
 */
public class CustomizableLockScreenQuickAffordancesPreferenceController extends
        BasePreferenceController implements PreferenceControllerMixin {

    private static final String TAG = "LockscreenQuickActions";
    private static final String ROM_WALLPAPER_PICKER = "com.android.wallpaper";

    public CustomizableLockScreenQuickAffordancesPreferenceController(Context context, String key) {
        super(context, key);
    }

    @Override
    public int getAvailabilityStatus() {
        return CustomizableLockScreenUtils.isFeatureEnabled(mContext)
                ? AVAILABLE
                : UNSUPPORTED_ON_DEVICE;
    }

    @Override
    public void displayPreference(PreferenceScreen screen) {
        super.displayPreference(screen);
        final Preference preference = screen.findPreference(getPreferenceKey());
        if (preference != null) {
            preference.setOnPreferenceClickListener(preference1 -> {
                final Intent intent = CustomizableLockScreenUtils.newIntent();
                intent.putExtra("destination", "quick_affordances");

                // Prefer the installed MistOS WallpaperPicker2. The default
                // config_wallpaper_picker_package points to com.android.settings,
                // which does not handle ACTION_SET_WALLPAPER here.
                intent.setPackage(ROM_WALLPAPER_PICKER);
                if (intent.resolveActivity(mContext.getPackageManager()) == null) {
                    // Keep the standard wallpaper handler for other builds.
                    intent.setPackage(null);
                }

                try {
                    mContext.startActivity(intent);
                } catch (ActivityNotFoundException | SecurityException e) {
                    Log.w(TAG, "Cannot open lock screen quick actions editor", e);
                    Toast.makeText(mContext,
                            R.string.lockscreen_shortcuts_editor_unavailable,
                            Toast.LENGTH_SHORT).show();
                }
                return true;
            });
            refreshSummary(preference);
        }
    }

    @Override
    protected void refreshSummary(Preference preference) {
        ThreadUtils.postOnBackgroundThread(() -> {
            final CharSequence summary =
                    CustomizableLockScreenUtils.getQuickAffordanceSummary(mContext);
            ThreadUtils.postOnMainThread(() -> preference.setSummary(summary));
        });
    }
}
