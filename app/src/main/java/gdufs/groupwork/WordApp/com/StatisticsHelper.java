package gdufs.groupwork.WordApp.com;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 学习统计数据模块：按当前词书范围汇总看板数据。
 */
public class StatisticsHelper {

    private final SQLiteDatabase db;
    private final int currentUserId;
    private final VocabBookManager.TagFilter bookFilter;
    private final int bookWordCount;

    public StatisticsHelper(Context context) {
        DatabaseHelper helper = new DatabaseHelper(context);
        db = helper.getReadableDatabase();
        UserSessionManager sessionManager = new UserSessionManager(context);
        currentUserId = sessionManager.getCurrentUserId();

        VocabBookManager vocabBookManager = new VocabBookManager(context);
        VocabBookManager.VocabBook currentBook = vocabBookManager.getCurrentBook(currentUserId);
        bookFilter = VocabBookManager.buildTagFilter(currentBook.tags, "e");
        bookWordCount = currentBook.wordCount;
    }

    /**
     * 获取首页数据看板所需的汇总指标。
     */
    public DashboardData getDashboardData() {
        DashboardData data = new DashboardData();
        data.totalWords = bookWordCount;

        // 1. 当前词书内各掌握程度数量
        Cursor levelCursor = db.rawQuery(
                "SELECT s.master_level, COUNT(*) "
                        + "FROM study_record s "
                        + "JOIN ecdict e ON s.word = e.word "
                        + "WHERE s.user_id = ? AND s.is_ignored = 0 AND "
                        + bookFilter.whereClause
                        + " GROUP BY s.master_level",
                prependUserId(bookFilter.args)
        );

        while (levelCursor.moveToNext()) {
            int level = levelCursor.getInt(0);
            int count = levelCursor.getInt(1);

            if (level == 0) {
                data.unlearned = count;
            } else if (StudyTaskHelper.isLearningInProgress(level)) {
                data.learning += count;
            } else if (StudyTaskHelper.isMastered(level)) {
                data.mastered += count;
            }
        }

        levelCursor.close();

        // 2. 今日学习数量（当前词书范围内）
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        Cursor todayCursor = db.rawQuery(
                "SELECT COUNT(*) FROM study_record s "
                        + "JOIN ecdict e ON s.word = e.word "
                        + "WHERE s.user_id = ? AND s.is_ignored = 0 "
                        + "AND date(s.next_review_time/1000, 'unixepoch') = ? AND "
                        + bookFilter.whereClause,
                prependUserIdAndDate(bookFilter.args, today)
        );

        if (todayCursor.moveToFirst()) {
            data.todayNew = todayCursor.getInt(0);
        }

        todayCursor.close();

        // 3. 连续学习天数与近 7 天趋势
        data.consecutiveDays = calculateConsecutiveDays();
        data.last7DaysCount = getLast7DaysCount();

        return data;
    }

    /**
     * 从今天往前统计连续有学习记录的天数。
     */
    private int calculateConsecutiveDays() {
        int days = 0;
        Calendar cal = Calendar.getInstance();

        while (true) {
            String dateStr = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    .format(cal.getTime());

            Cursor cursor = db.rawQuery(
                    "SELECT COUNT(*) FROM study_record s "
                            + "JOIN ecdict e ON s.word = e.word "
                            + "WHERE s.user_id = ? AND s.is_ignored = 0 "
                            + "AND date(s.next_review_time/1000, 'unixepoch') = ? AND "
                            + bookFilter.whereClause,
                    prependUserIdAndDate(bookFilter.args, dateStr)
            );

            if (cursor.moveToFirst() && cursor.getInt(0) > 0) {
                days++;
                cal.add(Calendar.DAY_OF_YEAR, -1);
            } else {
                cursor.close();
                break;
            }

            cursor.close();
        }

        return days;
    }

    /**
     * 获取近 7 天每日学习数量（从 6 天前到今天）。
     */
    private List<Integer> getLast7DaysCount() {
        List<Integer> list = new ArrayList<>();
        Calendar cal = Calendar.getInstance();

        for (int i = 0; i < 7; i++) {
            String dateStr = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    .format(cal.getTime());

            Cursor cursor = db.rawQuery(
                    "SELECT COUNT(*) FROM study_record s "
                            + "JOIN ecdict e ON s.word = e.word "
                            + "WHERE s.user_id = ? AND s.is_ignored = 0 "
                            + "AND date(s.next_review_time/1000, 'unixepoch') = ? AND "
                            + bookFilter.whereClause,
                    prependUserIdAndDate(bookFilter.args, dateStr)
            );

            if (cursor.moveToFirst()) {
                list.add(cursor.getInt(0));
            } else {
                list.add(0);
            }

            cursor.close();
            cal.add(Calendar.DAY_OF_YEAR, -1);
        }

        Collections.reverse(list);
        return list;
    }

    private String[] prependUserId(String[] filterArgs) {
        String[] merged = new String[filterArgs.length + 1];
        merged[0] = String.valueOf(currentUserId);
        System.arraycopy(filterArgs, 0, merged, 1, filterArgs.length);
        return merged;
    }

    private String[] prependUserIdAndDate(String[] filterArgs, String date) {
        String[] merged = new String[filterArgs.length + 2];
        merged[0] = String.valueOf(currentUserId);
        merged[1] = date;
        System.arraycopy(filterArgs, 0, merged, 2, filterArgs.length);
        return merged;
    }
}
