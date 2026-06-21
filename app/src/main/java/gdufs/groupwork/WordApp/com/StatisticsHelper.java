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

public class StatisticsHelper {
    private SQLiteDatabase db;
    private int currentUserId;

    public StatisticsHelper(Context context) {
        DatabaseHelper helper = new DatabaseHelper(context);
        db = helper.getReadableDatabase();
        UserSessionManager sessionManager = new UserSessionManager(context);
        currentUserId = sessionManager.getCurrentUserId();
    }

    public DashboardData getDashboardData() {
        DashboardData data = new DashboardData();

        // 1. 总单词数（来自 ecdict 表）
        Cursor c = db.rawQuery("SELECT COUNT(*) FROM ecdict", null);
        if (c.moveToFirst()) {
            data.totalWords = c.getInt(0);
        }
        c.close();

        // 2. 各状态数量（来自 study_record 表）
        // master_level: 0=未学, 1=学习中, 2=已掌握, >=3 已掌握（与 MainActivity 逻辑一致）
        c = db.rawQuery(
                "SELECT master_level, COUNT(*) FROM study_record WHERE user_id = ? AND is_ignored = 0 GROUP BY master_level",
                new String[]{String.valueOf(currentUserId)}
        );
        while (c.moveToNext()) {
            int level = c.getInt(0);
            int count = c.getInt(1);
            if (level == 0) {
                data.unlearned = count;
            } else if (level == 1 || level == 2) {
                data.learning += count;
            } else if (level >= 3) {
                data.mastered += count;
            }
        }
        c.close();

        // 3. 今日新增学习（使用 next_review_time 作为最近学习时间）
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        c = db.rawQuery(
                "SELECT COUNT(*) FROM study_record WHERE user_id = ? AND is_ignored = 0 AND date(next_review_time/1000, 'unixepoch') = ?",
                new String[]{String.valueOf(currentUserId), today}
        );
        if (c.moveToFirst()) {
            data.todayNew = c.getInt(0);
        }
        c.close();

        // 4. 连续学习天数（从今天往前推，每天至少有一条学习记录）
        data.consecutiveDays = calculateConsecutiveDays();

        // 5. 近7天学习趋势
        data.last7DaysCount = getLast7DaysCount();

        return data;
    }

    private int calculateConsecutiveDays() {
        int days = 0;
        Calendar cal = Calendar.getInstance();
        while (true) {
            String dateStr = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.getTime());
            Cursor c = db.rawQuery(
                    "SELECT COUNT(*) FROM study_record WHERE user_id = ? AND is_ignored = 0 AND date(next_review_time/1000, 'unixepoch') = ?",
                    new String[]{String.valueOf(currentUserId), dateStr}
            );
            if (c.moveToFirst() && c.getInt(0) > 0) {
                days++;
                cal.add(Calendar.DAY_OF_YEAR, -1);
                c.close();
            } else {
                c.close();
                break;
            }
        }
        return days;
    }

    private List<Integer> getLast7DaysCount() {
        List<Integer> list = new ArrayList<>();
        Calendar cal = Calendar.getInstance();
        for (int i = 0; i < 7; i++) {
            String dateStr = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.getTime());
            Cursor c = db.rawQuery(
                    "SELECT COUNT(*) FROM study_record WHERE user_id = ? AND is_ignored = 0 AND date(next_review_time/1000, 'unixepoch') = ?",
                    new String[]{String.valueOf(currentUserId), dateStr}
            );
            if (c.moveToFirst()) {
                list.add(c.getInt(0));
            } else {
                list.add(0);
            }
            c.close();
            cal.add(Calendar.DAY_OF_YEAR, -1);
        }
        Collections.reverse(list); // 从远到近
        return list;
    }
}