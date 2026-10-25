/*
 * Copyright (C) 2026 MistOS
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.settings.system

import android.app.settings.SettingsEnums
import android.content.Context
import android.os.UserHandle
import android.provider.Settings
import androidx.preference.PreferenceScreen
import com.android.settings.R
import com.android.settings.utils.CandidateInfoExtra
import com.android.settings.widget.RadioButtonPickerFragment
import com.android.settingslib.widget.CandidateInfo
import com.android.settingslib.widget.SelectorWithWidgetPreference

/**
 * Chooses between the existing MistOS control center and standard Android 17 QS.
 * The default (including when the setting is absent) is the MistOS layout.
 */
class QuickSettingsStyleFragment : RadioButtonPickerFragment() {

    override fun getPreferenceScreenResId(): Int = R.xml.mist_qs_style_settings

    override fun getMetricsCategory(): Int = SettingsEnums.PAGE_VISIBLE

    override fun onAttach(context: Context) {
        super.onAttach(context)
        setCategory(R.string.mist_qs_style_title)
    }

    override fun getCandidates(): List<CandidateInfo> {
        val context = requireContext()
        return listOf(
            CandidateInfoExtra(
                context.getText(R.string.mist_qs_style_mist_title),
                context.getText(R.string.mist_qs_style_mist_summary),
                KEY_MIST,
                true,
            ),
            CandidateInfoExtra(
                context.getText(R.string.mist_qs_style_android_title),
                context.getText(R.string.mist_qs_style_android_summary),
                KEY_ANDROID,
                true,
            ),
        )
    }

    override fun bindPreferenceExtra(
        pref: SelectorWithWidgetPreference,
        key: String,
        info: CandidateInfo?,
        defaultKey: String,
        systemDefaultKey: String?,
    ) {
        if (info is CandidateInfoExtra) pref.setSummary(info.loadSummary())
    }

    override fun getDefaultKey(): String {
        val selected = Settings.System.getIntForUser(
            requireContext().contentResolver,
            SETTING,
            STYLE_MIST,
            UserHandle.USER_CURRENT,
        )
        return if (selected == STYLE_ANDROID) KEY_ANDROID else KEY_MIST
    }

    override fun setDefaultKey(key: String): Boolean {
        val style = when (key) {
            KEY_MIST -> STYLE_MIST
            KEY_ANDROID -> STYLE_ANDROID
            else -> return false
        }
        return Settings.System.putIntForUser(
            requireContext().contentResolver,
            SETTING,
            style,
            UserHandle.USER_CURRENT,
        )
    }

    override fun onSelectionPerformed(success: Boolean) {
        if (success) updateCandidates()
    }

    companion object {
        private const val SETTING = "mist_qs_style"
        private const val STYLE_MIST = 0
        private const val STYLE_ANDROID = 1
        private const val KEY_MIST = "mist"
        private const val KEY_ANDROID = "android"
    }
}
