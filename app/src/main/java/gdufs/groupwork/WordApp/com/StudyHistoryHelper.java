package gdufs.groupwork.WordApp.com;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;

/**
 * 学习历史记录工具类。
 *
 * 每次用户点击：
 * - 认识
 * - 不认识
 *
 * 都会插入一条真实学习行为记录。
 *
 * StatisticsHelper 会用这张表统计：
 * - 今日学习数量
 * - 连续学习天数
 * - 近 7 天学习趋势
 */
public class StudyHistoryHelper {

    public static final String RESULT_KNOWN = "认识";
    public static final String RESULT_UNKNOWN = "不认识";

    private StudyHistoryHelper() {
        // 工具类不允许被实例化
    }

    /**
     * 写入一条学习历史。
     *
     * @param db       当前数据库对象
     * @param userId   当前用户 ID
     * @param word     本次学习的单词
     * @param knew     true 表示认识，false 表示不认识
     * @param oldLevel 操作前等级
     * @param newLevel 操作后等级
     * @return 是否写入成功
     */
    public static boolean recordStudy(
            SQLiteDatabase db,
            int userId,
            String word,
            boolean knew,
            int oldLevel,
            int newLevel
    ) {
        ContentValues values = new ContentValues();

        values.put("user_id", userId);
        values.put("word", word);
        values.put("study_time", System.currentTimeMillis());
        values.put(
                "result",
                knew ? RESULT_KNOWN : RESULT_UNKNOWN
        );
        values.put("old_level", oldLevel);
        values.put("new_level", newLevel);

        long result = db.insert(
                "study_history",
                null,
                values
        );

        return result != -1;
    }
}