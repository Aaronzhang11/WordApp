package gdufs.groupwork.WordApp.com;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * 学习趋势柱状图渲染工具。
 */
public final class TrendChartHelper {

    private static final int BAR_AREA_HEIGHT_DP = 140;
    private static final int MIN_BAR_HEIGHT_DP = 6;

    private static final int COLOR_BAR_NORMAL = Color.parseColor("#5B8DEF");
    private static final int COLOR_BAR_TODAY = Color.parseColor("#2C5282");
    private static final int COLOR_BAR_PEAK = Color.parseColor("#319795");

    private TrendChartHelper() {
    }

    /**
     * 趋势汇总数据。
     */
    public static class TrendSummary {
        public int total;
        public int average;
        public int peak;
    }

    /**
     * 根据每日数量计算汇总指标。
     */
    public static TrendSummary buildSummary(List<Integer> counts) {
        TrendSummary summary = new TrendSummary();

        if (counts == null || counts.isEmpty()) {
            return summary;
        }

        int total = 0;
        int peak = 0;

        for (int count : counts) {
            total += count;
            peak = Math.max(peak, count);
        }

        summary.total = total;
        summary.peak = peak;
        summary.average = Math.round(total / (float) counts.size());
        return summary;
    }

    /**
     * 生成近 7 天横轴标签（从 6 天前到今天）。
     */
    public static String[] buildLast7DayLabels() {
        String[] labels = new String[7];
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, -6);

        SimpleDateFormat dateFormat = new SimpleDateFormat("M/d", Locale.getDefault());

        for (int i = 0; i < 7; i++) {
            if (i == 5) {
                labels[i] = "昨天";
            } else if (i == 6) {
                labels[i] = "今天";
            } else {
                labels[i] = dateFormat.format(cal.getTime());
            }
            cal.add(Calendar.DAY_OF_YEAR, 1);
        }

        return labels;
    }

    /**
     * 在容器中绘制近 7 天学习趋势柱状图。
     */
    public static void render(
            Context context,
            LinearLayout container,
            List<Integer> counts
    ) {
        container.removeAllViews();

        if (counts == null || counts.isEmpty()) {
            return;
        }

        int max = Collections.max(counts);
        if (max == 0) {
            max = 1;
        }

        int peakIndex = counts.indexOf(Collections.max(counts));
        String[] dayLabels = buildLast7DayLabels();
        float density = context.getResources().getDisplayMetrics().density;
        int barAreaHeightPx = Math.round(BAR_AREA_HEIGHT_DP * density);
        int minBarHeightPx = Math.round(MIN_BAR_HEIGHT_DP * density);

        for (int i = 0; i < counts.size(); i++) {
            int count = counts.get(i);
            boolean isToday = i == counts.size() - 1;
            boolean isPeak = i == peakIndex && count > 0;

            LinearLayout item = new LinearLayout(context);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER_HORIZONTAL);
            item.setLayoutParams(new LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f
            ));

            TextView tvCount = new TextView(context);
            tvCount.setText(String.valueOf(count));
            tvCount.setTextSize(11);
            tvCount.setTextColor(isPeak ? COLOR_BAR_PEAK : Color.parseColor("#4A5568"));
            tvCount.setTypeface(null, isPeak ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
            tvCount.setGravity(Gravity.CENTER);
            tvCount.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            ));

            FrameLayout barArea = new FrameLayout(context);
            LinearLayout.LayoutParams barAreaParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    barAreaHeightPx
            );
            barAreaParams.topMargin = Math.round(4 * density);
            barAreaParams.bottomMargin = Math.round(4 * density);
            barAreaParams.leftMargin = Math.round(3 * density);
            barAreaParams.rightMargin = Math.round(3 * density);
            barArea.setLayoutParams(barAreaParams);

            int barHeight = Math.max(
                    minBarHeightPx,
                    Math.round(count * barAreaHeightPx / (float) max)
            );

            View bar = new View(context);
            FrameLayout.LayoutParams barParams = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    barHeight,
                    Gravity.BOTTOM
            );
            bar.setLayoutParams(barParams);
            bar.setBackground(buildBarBackground(context, isToday, isPeak));

            TextView tvDay = new TextView(context);
            tvDay.setText(i < dayLabels.length ? dayLabels[i] : "");
            tvDay.setTextSize(10);
            tvDay.setTextColor(isToday ? Color.parseColor("#2C5282") : Color.parseColor("#718096"));
            tvDay.setTypeface(null, isToday ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
            tvDay.setGravity(Gravity.CENTER);
            tvDay.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            ));

            barArea.addView(bar);
            item.addView(tvCount);
            item.addView(barArea);
            item.addView(tvDay);
            container.addView(item);
        }
    }

    private static GradientDrawable buildBarBackground(
            Context context,
            boolean isToday,
            boolean isPeak
    ) {
        int color;

        if (isPeak) {
            color = COLOR_BAR_PEAK;
        } else if (isToday) {
            color = COLOR_BAR_TODAY;
        } else {
            color = COLOR_BAR_NORMAL;
        }

        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        float radius = 6 * context.getResources().getDisplayMetrics().density;
        drawable.setCornerRadii(new float[]{
                radius, radius, radius, radius, 0f, 0f, 0f, 0f
        });
        return drawable;
    }
}
