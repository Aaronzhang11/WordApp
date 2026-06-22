package gdufs.groupwork.WordApp.com;

import android.content.Context;
import android.content.SharedPreferences;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 学习计划偏好与每日进度管理。
 * 按用户保存：每日新词数、每日复习上限、当日已完成数量。
 */
public class StudyPlanManager {

    private static final String PREF_NAME = "study_plan";

    private static final String KEY_DAILY_NEW_PREFIX = "daily_new_";
    private static final String KEY_DAILY_REVIEW_PREFIX = "daily_review_";
    private static final String KEY_TODAY_DATE_PREFIX = "today_date_";
    private static final String KEY_TODAY_NEW_DONE_PREFIX = "today_new_done_";
    private static final String KEY_TODAY_REVIEW_DONE_PREFIX = "today_review_done_";

    public static final int MIN_DAILY_NEW = 10;
    public static final int MAX_DAILY_NEW = 100;
    public static final int DEFAULT_DAILY_NEW = 20;

    public static final int MIN_DAILY_REVIEW = 20;
    public static final int MAX_DAILY_REVIEW = 200;
    public static final int DEFAULT_DAILY_REVIEW = 50;

    private final SharedPreferences preferences;

    public StudyPlanManager(Context context) {
        preferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    /**
     * 获取用户设置的每日新词数量（10-100）。
     */
    public int getDailyNewWordCount(int userId) {
        return clamp(
                preferences.getInt(KEY_DAILY_NEW_PREFIX + userId, DEFAULT_DAILY_NEW),
                MIN_DAILY_NEW,
                MAX_DAILY_NEW
        );
    }

    /**
     * 保存每日新词数量。
     */
    public void setDailyNewWordCount(int userId, int count) {
        preferences.edit()
                .putInt(KEY_DAILY_NEW_PREFIX + userId, clamp(count, MIN_DAILY_NEW, MAX_DAILY_NEW))
                .apply();
    }

    /**
     * 获取用户设置的每日复习上限（20-200）。
     */
    public int getDailyReviewLimit(int userId) {
        return clamp(
                preferences.getInt(KEY_DAILY_REVIEW_PREFIX + userId, DEFAULT_DAILY_REVIEW),
                MIN_DAILY_REVIEW,
                MAX_DAILY_REVIEW
        );
    }

    /**
     * 保存每日复习上限。
     */
    public void setDailyReviewLimit(int userId, int count) {
        preferences.edit()
                .putInt(KEY_DAILY_REVIEW_PREFIX + userId, clamp(count, MIN_DAILY_REVIEW, MAX_DAILY_REVIEW))
                .apply();
    }

    /**
     * 若日期变更则重置当日进度计数。
     */
    public void ensureToday(int userId) {
        String today = getTodayString();
        String savedDate = preferences.getString(KEY_TODAY_DATE_PREFIX + userId, "");

        if (!today.equals(savedDate)) {
            preferences.edit()
                    .putString(KEY_TODAY_DATE_PREFIX + userId, today)
                    .putInt(KEY_TODAY_NEW_DONE_PREFIX + userId, 0)
                    .putInt(KEY_TODAY_REVIEW_DONE_PREFIX + userId, 0)
                    .apply();
        }
    }

    /**
     * 今日已完成的新词学习数（仅统计「开始学习」任务）。
     */
    public int getTodayNewCompleted(int userId) {
        ensureToday(userId);
        return preferences.getInt(KEY_TODAY_NEW_DONE_PREFIX + userId, 0);
    }

    /**
     * 今日已完成的复习数（仅统计「开始学习」任务）。
     */
    public int getTodayReviewCompleted(int userId) {
        ensureToday(userId);
        return preferences.getInt(KEY_TODAY_REVIEW_DONE_PREFIX + userId, 0);
    }

    /**
     * 记录今日新词学习完成一次。
     */
    public void incrementTodayNewCompleted(int userId) {
        ensureToday(userId);
        int current = getTodayNewCompleted(userId);
        preferences.edit()
                .putInt(KEY_TODAY_NEW_DONE_PREFIX + userId, current + 1)
                .apply();
    }

    /**
     * 记录今日复习完成一次。
     */
    public void incrementTodayReviewCompleted(int userId) {
        ensureToday(userId);
        int current = getTodayReviewCompleted(userId);
        preferences.edit()
                .putInt(KEY_TODAY_REVIEW_DONE_PREFIX + userId, current + 1)
                .apply();
    }

    /**
     * 今日剩余可学新词配额。
     */
    public int getRemainingNewQuota(int userId) {
        return Math.max(0, getDailyNewWordCount(userId) - getTodayNewCompleted(userId));
    }

    /**
     * 今日剩余可复习配额。
     */
    public int getRemainingReviewQuota(int userId) {
        return Math.max(0, getDailyReviewLimit(userId) - getTodayReviewCompleted(userId));
    }

    /**
     * 今日「开始学习」任务是否已全部完成。
     *
     * @param dueLearningReviewCount 当前到期待复习的学习中单词数；为 0 时视为复习配额已满足
     */
    public boolean isTodayTaskComplete(int userId, int dueLearningReviewCount) {
        boolean reviewSatisfied = getRemainingReviewQuota(userId) <= 0
                || dueLearningReviewCount <= 0;
        return getRemainingNewQuota(userId) <= 0 && reviewSatisfied;
    }

    /**
     * 今日「开始学习」任务是否已全部完成（不考虑到期复习词，兼容旧调用）。
     */
    public boolean isTodayTaskComplete(int userId) {
        return getRemainingNewQuota(userId) <= 0 && getRemainingReviewQuota(userId) <= 0;
    }

    private String getTodayString() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
