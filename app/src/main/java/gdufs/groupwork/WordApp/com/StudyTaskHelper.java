package gdufs.groupwork.WordApp.com;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.List;

/**
 * 学习任务相关的数据库查询与词书生成工具。
 */
public class StudyTaskHelper {

    private StudyTaskHelper() {
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
     * 统计到期且熟练度不为 0 的复习词数量。
     */
    public static int countDueReviewWords(SQLiteDatabase db, int userId, long now) {
        Cursor cursor = db.rawQuery(
                "SELECT COUNT(*) FROM study_record "
                        + "WHERE user_id = ? AND is_ignored = 0 "
                        + "AND master_level > 0 AND next_review_time <= ?",
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
