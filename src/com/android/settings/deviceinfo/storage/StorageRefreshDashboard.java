/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * Minimal system-theme storage maintenance dashboard for MistOS.
 * Only real successful manual operations are shown in the history chart.
 */
package com.android.settings.deviceinfo.storage;

import android.app.Activity;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.StringRes;
import androidx.core.graphics.ColorUtils;

import com.android.settings.R;

import java.text.DateFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Pure presentation layer. No access to vold, shell commands or privileged
 * storage APIs: these remain in StorageRefreshActivity.
 *
 * The reference layout intentionally follows the simple HyperOS hierarchy:
 * history, description, last operation, and one anchored primary action.
 * Colors are from the active MistOS/Settings theme, not hardcoded Xiaomi blue.
 */
final class StorageRefreshDashboard extends LinearLayout {
    private static final int MATCH = LayoutParams.MATCH_PARENT;
    private static final int WRAP = LayoutParams.WRAP_CONTENT;

    private final Activity mActivity;
    private final int mPrimary;
    private final int mSecondary;
    private final int mAccent;
    private final int mBackground;
    private final boolean mDark;

    private final TextView mCount;
    private final TextView mLastAttempt;
    private final TextView mStatus;
    private final Button mAction;
    private final ProgressBar mProgress;
    private final WeeklyChart mChart;

    StorageRefreshDashboard(Activity activity, Runnable onAction) {
        super(activity);
        mActivity = activity;
        mPrimary = colorAttr(android.R.attr.textColorPrimary, Color.WHITE);
        mSecondary = colorAttr(android.R.attr.textColorSecondary, 0xffaaaaaa);
        mBackground = colorAttr(android.R.attr.colorBackground, Color.BLACK);
        mAccent = colorAttr(android.R.attr.colorAccent, 0xff367bff);
        mDark = (activity.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;

        setOrientation(VERTICAL);
        setBackgroundColor(mBackground);
        setFitsSystemWindows(false);

        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(false);
        scroll.setVerticalScrollBarEnabled(false);
        addView(scroll, new LayoutParams(MATCH, 0, 1f));

        LinearLayout page = new LinearLayout(activity);
        page.setOrientation(VERTICAL);
        page.setPadding(dp(18), dp(16), dp(18), dp(20));
        scroll.addView(page, new FrameLayout.LayoutParams(MATCH, WRAP));

        final LinearLayout historyCard = card(page, 0);
        label(historyCard, R.string.storage_refresh_chart_title, 17, true, mPrimary);
        final TextView chartSubtitle =
                label(historyCard, R.string.storage_refresh_chart_subtitle, 13, false, mSecondary);
        marginTop(chartSubtitle, 2);
        mCount = label(historyCard, "", 28, true, mPrimary);
        marginTop(mCount, 12);

        mChart = new WeeklyChart(activity, mAccent, mSecondary, mPrimary, mDark);
        LinearLayout.LayoutParams chartLp = new LayoutParams(MATCH, dp(176));
        chartLp.topMargin = dp(12);
        historyCard.addView(mChart, chartLp);

        final LinearLayout explanation = card(page, 14);
        label(explanation, R.string.storage_refresh_background_title, 16, true, mPrimary);
        final TextView details =
                label(explanation, R.string.storage_refresh_description, 14, false, mSecondary);
        marginTop(details, 8);

        final LinearLayout attemptCard = card(page, 14);
        label(attemptCard, R.string.storage_refresh_last_title, 16, true, mPrimary);
        mLastAttempt = label(attemptCard, "", 14, false, mSecondary);
        marginTop(mLastAttempt, 6);

        final TextView note =
                label(page, R.string.storage_refresh_history_note, 12, false, mSecondary);
        LayoutParams noteLp = (LayoutParams) note.getLayoutParams();
        noteLp.setMargins(dp(4), dp(14), dp(4), 0);
        note.setLayoutParams(noteLp);

        // Keep the action visible without covering the chart or description.
        LinearLayout bottom = new LinearLayout(activity);
        bottom.setOrientation(VERTICAL);
        bottom.setPadding(dp(18), dp(12), dp(18), dp(16));
        bottom.setBackgroundColor(mBackground);
        addView(bottom, new LayoutParams(MATCH, WRAP));

        mStatus = label(bottom, "", 13, false, mSecondary);
        mStatus.setGravity(Gravity.CENTER);
        // Let localized safety/error messages wrap without truncation.

        mProgress = new ProgressBar(
                activity, null, android.R.attr.progressBarStyleHorizontal);
        mProgress.setIndeterminate(true);
        mProgress.setIndeterminateTintList(
                android.content.res.ColorStateList.valueOf(mAccent));
        LayoutParams barLp = new LayoutParams(MATCH, dp(3));
        barLp.setMargins(dp(4), dp(10), dp(4), dp(4));
        bottom.addView(mProgress, barLp);

        mAction = new Button(activity);
        mAction.setAllCaps(false);
        mAction.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        mAction.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        mAction.setTextColor(contrastColor(mAccent));
        mAction.setBackground(new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(
                        ColorUtils.setAlphaComponent(contrastColor(mAccent), 40)),
                rounded(mAccent, dp(16)), null));
        mAction.setMinHeight(dp(54));
        mAction.setElevation(0);
        LayoutParams actionLp = new LayoutParams(MATCH, dp(54));
        actionLp.topMargin = dp(10);
        bottom.addView(mAction, actionLp);
        mAction.setOnClickListener(v -> onAction.run());
    }

    void render(List<Long> history, long lastAttempt, int lastResult, boolean running,
            long nextAllowedRunAt) {
        final int[] daily = StorageRefreshActivity.successfulRunsPerDay(history,
                LocalDate.now(), ZoneId.systemDefault());
        int weekTotal = 0;
        for (int count : daily) weekTotal += count;
        // Prominent truthful count, in the same hierarchy as the HyperOS reference.
        mCount.setText(java.lang.Integer.toString(weekTotal));
        mChart.setDays(daily);

        if (lastAttempt <= 0) {
            mLastAttempt.setText(R.string.storage_refresh_never);
        } else {
            final String date = DateFormat.getDateTimeInstance(DateFormat.MEDIUM,
                    DateFormat.SHORT).format(new Date(lastAttempt));
            mLastAttempt.setText(date);
        }

        final boolean coolingDown =
                nextAllowedRunAt > 0L && nextAllowedRunAt > System.currentTimeMillis();
        mProgress.setVisibility(running ? View.VISIBLE : View.GONE);
        mAction.setEnabled(!running && !coolingDown);

        if (running) {
            mStatus.setText(R.string.storage_refresh_in_progress);
            mAction.setText(R.string.storage_refresh_button_running);
        } else if (coolingDown) {
            // Prior success is a HISTORY entry, not an active completion dialog.
            // Show the next possible manual run instead of a misleading
            // "Done" button that silently navigates away.
            final String availableAt = DateFormat.getDateTimeInstance(
                    DateFormat.MEDIUM, DateFormat.SHORT).format(new Date(nextAllowedRunAt));
            mStatus.setText(mActivity.getString(
                    R.string.storage_refresh_next_available, availableAt));
            mAction.setText(R.string.storage_refresh_button);
        } else if (lastAttempt > 0 && lastResult == 0) {
            mStatus.setText(R.string.storage_refresh_success);
            mAction.setText(R.string.storage_refresh_button);
        } else if (lastAttempt > 0 && lastResult == -2) {
            mStatus.setText(R.string.storage_refresh_timeout);
            mAction.setText(R.string.storage_refresh_button_retry);
        } else if (lastAttempt > 0) {
            mStatus.setText(R.string.storage_refresh_failed);
            mAction.setText(R.string.storage_refresh_button_retry);
        } else {
            mStatus.setText(R.string.storage_refresh_ready);
            mAction.setText(R.string.storage_refresh_button);
        }
        mAction.setAlpha(mAction.isEnabled() ? 1f : 0.52f);
    }

    void showMessage(@StringRes int message) {
        mStatus.setText(message);
    }

    private LinearLayout card(LinearLayout parent, int topDp) {
        LinearLayout card = new LinearLayout(mActivity);
        card.setOrientation(VERTICAL);
        card.setPadding(dp(18), dp(18), dp(18), dp(18));
        // Gently raised neutral surface in dark and light MistOS themes.
        final int surface = ColorUtils.blendARGB(mBackground, mPrimary, mDark ? 0.10f : 0.045f);
        card.setBackground(rounded(surface, dp(18)));
        LayoutParams lp = new LayoutParams(MATCH, WRAP);
        lp.topMargin = dp(topDp);
        parent.addView(card, lp);
        return card;
    }

    private TextView label(LinearLayout parent, @StringRes int text, int sp,
            boolean bold, int color) {
        return label(parent, mActivity.getString(text), sp, bold, color);
    }

    private TextView label(LinearLayout parent, String text, int sp,
            boolean bold, int color) {
        TextView view = new TextView(mActivity);
        view.setText(text);
        view.setTextColor(color);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        view.setIncludeFontPadding(true);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        parent.addView(view, new LayoutParams(MATCH, WRAP));
        return view;
    }

    private void marginTop(View view, int dp) {
        final LayoutParams params = (LayoutParams) view.getLayoutParams();
        params.topMargin = this.dp(dp);
        view.setLayoutParams(params);
    }

    private int colorAttr(int attr, int fallback) {
        TypedValue value = new TypedValue();
        if (mActivity.getTheme().resolveAttribute(attr, value, true)) {
            if (value.resourceId != 0) return mActivity.getColor(value.resourceId);
            return value.data;
        }
        return fallback;
    }

    private int dp(float value) {
        return Math.round(mActivity.getResources().getDisplayMetrics().density * value);
    }

    private int contrastColor(int background) {
        return ColorUtils.calculateLuminance(background) > 0.45 ? Color.BLACK : Color.WHITE;
    }

    private static GradientDrawable rounded(int color, float radius) {
        final GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private static final class WeeklyChart extends View {
        private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int mAccent;
        private final int mSecondary;
        private final int mPrimary;
        private final boolean mDark;
        private int[] mDaily = new int[7];

        WeeklyChart(Activity context, int accent, int secondary, int primary, boolean dark) {
            super(context);
            mAccent = accent;
            mSecondary = secondary;
            mPrimary = primary;
            mDark = dark;
            setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        }

        void setDays(int[] data) {
            mDaily = data.clone();
            int sum = 0;
            for (int value : mDaily) sum += value;
            setContentDescription(getResources().getString(
                    R.string.storage_refresh_completed_count, sum));
            invalidate();
        }

        private float dp(float v) {
            return getResources().getDisplayMetrics().density * v;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            final float width = getWidth();
            final float height = getHeight();
            final float startX = dp(8);
            final float endX = width - dp(8);
            final float topY = dp(10);
            final float axisY = height - dp(29);
            final float graphHeight = Math.max(0f, axisY - topY);
            final float slotWidth = (endX - startX) / 7f;
            int maxValue = 1;
            for (int value : mDaily) maxValue = Math.max(maxValue, value);

            mPaint.setStrokeWidth(dp(1));
            mPaint.setStyle(Paint.Style.STROKE);
            mPaint.setPathEffect(new DashPathEffect(new float[]{dp(3), dp(5)}, 0));
            mPaint.setColor(ColorUtils.setAlphaComponent(mSecondary, mDark ? 60 : 75));
            for (int j = 0; j < 4; j++) {
                float y = axisY - (j * graphHeight / 3f);
                canvas.drawLine(startX, y, endX, y, mPaint);
            }
            mPaint.setPathEffect(null);
            mPaint.setStyle(Paint.Style.FILL);
            mPaint.setTextSize(getResources().getDisplayMetrics().scaledDensity * 10f);
            mPaint.setTextAlign(Paint.Align.CENTER);

            DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("dd.MM",
                    Locale.getDefault());
            LocalDate today = LocalDate.now();
            for (int i = 0; i < 7; i++) {
                final float centerX = startX + slotWidth * (i + 0.5f);
                final float barWidth = Math.min(dp(24), slotWidth * 0.44f);
                final int count = mDaily[i];
                if (count > 0) {
                    final float barHeight = graphHeight * count / maxValue;
                    mPaint.setColor(mAccent);
                    canvas.drawRoundRect(new RectF(centerX - barWidth / 2f,
                            axisY - barHeight, centerX + barWidth / 2f, axisY),
                            dp(4), dp(4), mPaint);
                } else {
                    mPaint.setColor(ColorUtils.setAlphaComponent(
                            mSecondary, mDark ? 75 : 95));
                    canvas.drawRoundRect(new RectF(centerX - barWidth / 2f,
                            axisY - dp(2), centerX + barWidth / 2f, axisY),
                            dp(2), dp(2), mPaint);
                }
                mPaint.setColor(i == 6 ? mPrimary : mSecondary);
                final String day = today.minusDays(6L - i).format(dateFormat);
                final float fullTextSize = getResources().getDisplayMetrics().scaledDensity * 10f;
                mPaint.setTextSize(fullTextSize);
                final float measuredWidth = mPaint.measureText(day);
                if (measuredWidth > slotWidth - dp(2)) {
                    mPaint.setTextSize(fullTextSize * (slotWidth - dp(2)) / measuredWidth);
                }
                canvas.drawText(day, centerX, height - dp(7), mPaint);
            }
        }
    }
}
