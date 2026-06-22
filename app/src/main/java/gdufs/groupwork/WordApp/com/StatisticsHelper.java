package gdufs.groupwork.WordApp.com;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * 学习统计数据模块。
 *
 * 当前词书范围统计：
 * - 已掌握
 * - 学习中
 * - 计划中
 * - 当前词书总量
 *
 * 当前用户真实学习行为统计：
 * - 今日学习
 * - 连续学习天数
 * - 近 7 天趋势
 */
public class StatisticsHelper {

    private final SQLiteDatabase db;

    private final int currentUserId;

    private final VocabBookManager.TagFilter bookFilter;

    private final int bookWordCount;

    public StatisticsHelper(Context context) {
        DatabaseHelper helper = new DatabaseHelper(context);

        db = helper.getReadableDatabase();

        UserSessionManager sessionManager =
                new UserSessionManager(context);

        currentUserId = sessionManager.getCurrentUserId();

        VocabBookManager vocabBookManager =
                new VocabBookManager(context);

        VocabBookManager.VocabBook currentBook =
                vocabBookManager.getCurrentBook(currentUserId);

        bookFilter = VocabBookManager.buildTagFilter(
                currentBook.tags,
                "e"
        );

        bookWordCount = currentBook.wordCount;
    }

    /**
     * 获取首页和数据看板需要的全部统计信息。
     */
    public DashboardData getDashboardData() {
        DashboardData data = new DashboardData();

        // 当前词书总词数
        data.totalWords = bookWordCount;

        /*
         * 第一部分：
         * 当前词书内的掌握等级统计。
         */
        loadCurrentBookLevelStats(data);

        /*
         * 第二部分：
         * 真实学习行为统计。
         *
         * 这里不再使用 next_review_time。
         * 只统计 study_history 中真正发生的学习操作。
         */
        data.todayNew = getTodayStudyActionCount();

        data.consecutiveDays = calculateConsecutiveDays();

        data.last7DaysCount = getLast7DaysCount();

        return data;
    }

    /**
     * 统计当前词书中：
     * - 计划中：level = 0
     * - 学习中：level = 1、2、3
     * - 已掌握：level >= 4
     */
    private void loadCurrentBookLevelStats(DashboardData data) {
        Cursor levelCursor = db.rawQuery(
                "SELECT s.master_level, COUNT(*) "
                        + "FROM study_record s "
                        + "JOIN ecdict e ON s.word = e.word "
                        + "WHERE s.user_id = ? "
                        + "AND s.is_ignored = 0 "
                        + "AND "
                        + bookFilter.whereClause
                        + " GROUP BY s.master_level",
                prependUserId(bookFilter.args)
        );

        while (levelCursor.moveToNext()) {
            int level = levelCursor.getInt(0);
            int count = levelCursor.getInt(1);

            if (level == StudyTaskHelper.LEVEL_UNFAMILIAR) {
                data.unlearned += count;
            } else if (StudyTaskHelper.isLearningInProgress(level)) {
                data.learning += count;
            } else if (StudyTaskHelper.isMastered(level)) {
                data.mastered += count;
            }
        }

        levelCursor.close();
    }

    /**
     * 统计今天真实学习过多少次。
     *
     * 每点击一次“认识”或“不认识”，都会记为一次学习行为。
     *
     * 例如：
     * 今天完成 20 张单词卡，
     * 无论是认识还是不认识，
     * 今日学习都会是 20。
     */
    private int getTodayStudyActionCount() {
        long startToday = getStartOfDayMillis(0);
        long startTomorrow = getStartOfDayMillis(1);

        return getStudyActionCountBetween(
                startToday,
                startTomorrow
        );
    }

    /**
     * 计算连续学习天数。
     *
     * 从今天开始向前查：
     * 今天有学习记录 → 1 天
     * 昨天也有学习记录 → 2 天
     * 前天也有学习记录 → 3 天
     *
     * 直到某一天没有学习记录为止。
     */
    private int calculateConsecutiveDays() {
        int consecutiveDays = 0;

        int dayOffset = 0;

        while (true) {
            long start = getStartOfDayMillis(dayOffset);
            long end = getStartOfDayMillis(dayOffset + 1);

            int count = getStudyActionCountBetween(start, end);

            if (count <= 0) {
                break;
            }

            consecutiveDays++;
            dayOffset--;
        }

        return consecutiveDays;
    }

    /**
     * 获取最近 7 天每天的真实学习次数。
     *
     * 返回顺序：
     * 6 天前 → 5 天前 → ... → 今天
     */
    private List<Integer> getLast7DaysCount() {
        List<Integer> result = new ArrayList<>();

        for (int dayOffset = -6; dayOffset <= 0; dayOffset++) {
            long start = getStartOfDayMillis(dayOffset);
            long end = getStartOfDayMillis(dayOffset + 1);

            result.add(
                    getStudyActionCountBetween(start, end)
            );
        }

        return result;
    }

    /**
     * 查询某一段时间内的真实学习行为数量。
     *
     * 这里按当前用户统计，
     * 不因为用户切换词书而让学习天数突然变成 0。
     */
    private int getStudyActionCountBetween(
            long startTime,
            long endTime
    ) {
        Cursor cursor = db.rawQuery(
                "SELECT COUNT(*) FROM study_history "
                        + "WHERE user_id = ? "
                        + "AND study_time >= ? "
                        + "AND study_time < ?",
                new String[]{
                        String.valueOf(currentUserId),
                        String.valueOf(startTime),
                        String.valueOf(endTime)
                }
        );

        int count = 0;

        if (cursor.moveToFirst()) {
            count = cursor.getInt(0);
        }

        cursor.close();

        return count;
    }

    /**
     * 获取某一天开始时的毫秒时间戳。
     *
     * dayOffset:
     * 0  = 今天零点
     * 1  = 明天零点
     * -1 = 昨天零点
     */
    private long getStartOfDayMillis(int dayOffset) {
        Calendar calendar = Calendar.getInstance();

        calendar.set(
                Calendar.HOUR_OF_DAY,
                0
        );

        calendar.set(
                Calendar.MINUTE,
                0
        );

        calendar.set(
                Calendar.SECOND,
                0
        );

        calendar.set(
                Calendar.MILLISECOND,
                0
        );

        calendar.add(
                Calendar.DAY_OF_YEAR,
                dayOffset
        );

        return calendar.getTimeInMillis();
    }

    /**
     * 给当前词书统计 SQL 补上 user_id 参数。
     */
    private String[] prependUserId(String[] filterArgs) {
        String[] merged = new String[filterArgs.length + 1];

        merged[0] = String.valueOf(currentUserId);

        System.arraycopy(
                filterArgs,
                0,
                merged,
                1,
                filterArgs.length
        );

        return merged;
    }
}