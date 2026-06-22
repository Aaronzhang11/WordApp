package gdufs.groupwork.WordApp.com;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.List;

/**
 * 学习任务相关的数据库查询与词书生成工具。
 *
 * 熟练度等级（master_level）：
 * 0 = 陌生；1/2/3 = 学习中（第 1/2/3 次复习）；4 = 已掌握。
 */
public class StudyTaskHelper {

    /** 陌生词，尚未完成首次学习 */
    public static final int LEVEL_UNFAMILIAR = 0;
    /** 学习中：待第 1 次复习（学习后第 2 天） */
    public static final int LEVEL_REVIEW_FIRST = 1;
    /** 学习中：待第 2 次复习（第 4 天） */
    public static final int LEVEL_REVIEW_SECOND = 2;
    /** 学习中：待第 3 次复习（第 8 天） */
    public static final int LEVEL_REVIEW_THIRD = 3;
    /** 三次复习全部完成，已掌握 */
    public static final int LEVEL_MASTERED = 4;

    /** SQL 片段：学习中（非陌生、非已掌握） */
    public static final String SQL_LEARNING_IN_PROGRESS =
            "master_level >= " + LEVEL_REVIEW_FIRST
                    + " AND master_level < " + LEVEL_MASTERED;

    private StudyTaskHelper() {
    }

    /**
     * 是否为学习中的单词（熟练度 1-3，待复习）。
     */
    public static boolean isLearningInProgress(int masterLevel) {
        return masterLevel >= LEVEL_REVIEW_FIRST && masterLevel < LEVEL_MASTERED;
    }

    /**
     * 是否已掌握（三次复习均完成）。
     */
    public static boolean isMastered(int masterLevel) {
        return masterLevel >= LEVEL_MASTERED;
    }

    /**
     * 统计熟练度不为 0 且未忽略的单词数量。
     */
    public static int countLearnedWords(SQLiteDatabase db, int userId) {
        Cursor cursor = db.rawQuery(
                "SELECT COUNT(*) FROM study_record "
                        + "WHERE user_id = ? AND is_ignored = 0 AND master_level > 0",
                new String[]{String.valueOf(userId)}
        );

        int count = 0;

        if (cursor.moveToFirst()) {
            count = cursor.getInt(0);
        }

        cursor.close();
        return count;
    }

    /**
     * 统计当前词书范围内尚未加入学习计划的单词数量。
     */
    public static int countAvailableNewWords(SQLiteDatabase db, int userId, List<String> bookTags) {
        VocabBookManager.TagFilter filter = VocabBookManager.buildTagFilter(bookTags);

        String sql = "SELECT COUNT(*) FROM ecdict WHERE " + filter.whereClause
                + " AND word NOT IN (SELECT word FROM study_record WHERE user_id = ?)";

        String[] args = appendUserIdArg(filter.args, userId);

        Cursor cursor = db.rawQuery(sql, args);

        int count = 0;

        if (cursor.moveToFirst()) {
            count = cursor.getInt(0);
        }

        cursor.close();
        return count;
    }

    /**
     * 统计词库中尚未加入学习计划的单词数量（全词库，兼容旧调用）。
     */
    public static int countAvailableNewWords(SQLiteDatabase db, int userId) {
        return countAvailableNewWords(db, userId, null);
    }

    /**
     * 统计计划中 master_level = 0 的陌生词数量。
     */
    public static int countUnfamiliarWords(SQLiteDatabase db, int userId) {
        Cursor cursor = db.rawQuery(
                "SELECT COUNT(*) FROM study_record "
                        + "WHERE user_id = ? AND is_ignored = 0 AND master_level = 0",
                new String[]{String.valueOf(userId)}
        );

        int count = 0;

        if (cursor.moveToFirst()) {
            count = cursor.getInt(0);
        }

        cursor.close();
        return count;
    }

    /**
     * 统计到期待复习的学习中单词数量（熟练度 1-3，不含陌生与已掌握）。
     */
    public static int countDueReviewWords(SQLiteDatabase db, int userId, long now) {
        Cursor cursor = db.rawQuery(
                "SELECT COUNT(*) FROM study_record "
                        + "WHERE user_id = ? AND is_ignored = 0 "
                        + "AND " + SQL_LEARNING_IN_PROGRESS + " "
                        + "AND next_review_time <= ?",
                new String[]{String.valueOf(userId), String.valueOf(now)}
        );

        int count = 0;

        if (cursor.moveToFirst()) {
            count = cursor.getInt(0);
        }

        cursor.close();
        return count;
    }

    /**
     * 统计当前词书范围内到期待复习的学习中单词数量。
     */
    public static int countDueReviewWords(
            SQLiteDatabase db,
            int userId,
            long now,
            List<String> bookTags
    ) {
        VocabBookManager.TagFilter filter = VocabBookManager.buildTagFilter(bookTags, "e");

        String sql = "SELECT COUNT(*) FROM study_record s "
                + "JOIN ecdict e ON s.word = e.word "
                + "WHERE s.user_id = ? AND s.is_ignored = 0 "
                + "AND s.master_level >= " + LEVEL_REVIEW_FIRST
                + " AND s.master_level < " + LEVEL_MASTERED + " "
                + "AND s.next_review_time <= ? AND "
                + filter.whereClause;

        String[] args = new String[filter.args.length + 2];
        args[0] = String.valueOf(userId);
        args[1] = String.valueOf(now);
        System.arraycopy(filter.args, 0, args, 2, filter.args.length);

        Cursor cursor = db.rawQuery(sql, args);
        int count = 0;

        if (cursor.moveToFirst()) {
            count = cursor.getInt(0);
        }

        cursor.close();
        return count;
    }

    /**
     * 统计学习中的单词数量（熟练度 1-3）。
     */
    public static int countLearningWords(SQLiteDatabase db, int userId) {
        Cursor cursor = db.rawQuery(
                "SELECT COUNT(*) FROM study_record "
                        + "WHERE user_id = ? AND is_ignored = 0 AND " + SQL_LEARNING_IN_PROGRESS,
                new String[]{String.valueOf(userId)}
        );

        int count = 0;

        if (cursor.moveToFirst()) {
            count = cursor.getInt(0);
        }

        cursor.close();
        return count;
    }

    /**
     * 从当前词书范围随机抽取新词写入 study_record。
     */
    public static int generateNewWords(
            SQLiteDatabase db,
            int userId,
            int targetCount,
            List<String> bookTags
    ) {
        if (targetCount <= 0) {
            return 0;
        }

        int available = countAvailableNewWords(db, userId, bookTags);

        if (available <= 0) {
            return 0;
        }

        int actualCount = Math.min(targetCount, available);
        VocabBookManager.TagFilter filter = VocabBookManager.buildTagFilter(bookTags);

        String sql = "SELECT word FROM ecdict WHERE " + filter.whereClause
                + " AND word NOT IN (SELECT word FROM study_record WHERE user_id = ?) "
                + "ORDER BY RANDOM() LIMIT ?";

        String[] args = appendUserIdAndLimitArgs(filter.args, userId, actualCount);

        Cursor cursor = db.rawQuery(sql, args);

        long now = System.currentTimeMillis();
        int insertCount = 0;

        db.beginTransaction();

        try {
            while (cursor.moveToNext()) {
                ContentValues values = new ContentValues();
                values.put("user_id", userId);
                values.put("word", cursor.getString(0));
                values.put("master_level", 0);
                values.put("next_review_time", now);
                values.put("error_count", 0);
                values.put("is_ignored", 0);

                long result = db.insertWithOnConflict(
                        "study_record",
                        null,
                        values,
                        SQLiteDatabase.CONFLICT_IGNORE
                );

                if (result != -1) {
                    insertCount++;
                }
            }

            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
            cursor.close();
        }

        return insertCount;
    }

    /**
     * 从全词库随机抽取新词（兼容旧调用）。
     */
    public static int generateNewWords(SQLiteDatabase db, int userId, int targetCount) {
        return generateNewWords(db, userId, targetCount, null);
    }

    private static String[] appendUserIdArg(String[] filterArgs, int userId) {
        String[] merged = new String[filterArgs.length + 1];
        System.arraycopy(filterArgs, 0, merged, 0, filterArgs.length);
        merged[filterArgs.length] = String.valueOf(userId);
        return merged;
    }

    private static String[] appendUserIdAndLimitArgs(String[] filterArgs, int userId, int limit) {
        String[] merged = new String[filterArgs.length + 2];
        System.arraycopy(filterArgs, 0, merged, 0, filterArgs.length);
        merged[filterArgs.length] = String.valueOf(userId);
        merged[filterArgs.length + 1] = String.valueOf(limit);
        return merged;
    }
}
