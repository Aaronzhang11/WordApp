package gdufs.groupwork.WordApp.com;

import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private TextView tvToStudyCount;
    private TextView tvMasteredCount;
    private TextView tvTotalCount;
    private TextView tvCurrentUser;

    private DatabaseHelper dbHelper;
    private UserSessionManager sessionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        sessionManager = new UserSessionManager(this);

        if (!sessionManager.isLoggedIn()) {
            Intent intent = new Intent(MainActivity.this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            return;
        }

        dbHelper = new DatabaseHelper(this);
        initViews();
        loadStatistics();
    }

    private void initViews() {
        tvToStudyCount = findViewById(R.id.tvToStudyCount);
        tvMasteredCount = findViewById(R.id.tvMasteredCount);
        tvTotalCount = findViewById(R.id.tvTotalCount);
        tvCurrentUser = findViewById(R.id.tvCurrentUser);

        tvCurrentUser.setText("当前用户：" + sessionManager.getCurrentUsername());

        // 退出登录
        findViewById(R.id.btnLogout).setOnClickListener(v -> {
            sessionManager.logout();

            Intent intent = new Intent(MainActivity.this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
        });

        // 生成词书
        findViewById(R.id.btnGenerateBook).setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, GenerateBookActivity.class);
            startActivity(intent);
        });

        // 开始背单词
        findViewById(R.id.btnStudy).setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, StudyActivity.class);
            startActivity(intent);
        });

        // 模拟自测卷
        findViewById(R.id.btnTest).setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, QuizActivity.class);
            startActivity(intent);
        });

        // 中英双向检索
        findViewById(R.id.btnSearch).setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, SearchActivity.class);
            startActivity(intent);
        });

        // 我的自定义单词本
        findViewById(R.id.btnWordBook).setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, WordBookActivity.class);
            startActivity(intent);
        });

        findViewById(R.id.btnDashboardInline).setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, DashboardActivity.class);
            startActivity(intent);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();

        // 从背单词、测试等页面返回主页后，刷新统计数据
        if (dbHelper != null
                && sessionManager != null
                && sessionManager.isLoggedIn()) {
            loadStatistics();
        }
    }

    private void loadStatistics() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        long currentTime = System.currentTimeMillis();
        int currentUserId = sessionManager.getCurrentUserId();

        // 1. 待学习 / 待复习数量
        Cursor c1 = db.rawQuery(
                "SELECT COUNT(*) FROM study_record " +
                        "WHERE user_id = ? " +
                        "AND is_ignored = 0 " +
                        "AND next_review_time <= ?",
                new String[]{
                        String.valueOf(currentUserId),
                        String.valueOf(currentTime)
                }
        );

        if (c1.moveToFirst()) {
            tvToStudyCount.setText(String.valueOf(c1.getInt(0)));
        }

        c1.close();

        // 2. 已掌握数量：掌握等级大于等于 3
        Cursor c2 = db.rawQuery(
                "SELECT COUNT(*) FROM study_record " +
                        "WHERE user_id = ? " +
                        "AND is_ignored = 0 " +
                        "AND master_level >= 3",
                new String[]{String.valueOf(currentUserId)}
        );

        if (c2.moveToFirst()) {
            tvMasteredCount.setText(String.valueOf(c2.getInt(0)));
        }

        c2.close();

        // 3. 词库总量：所有用户共用 ecdict 表
        Cursor c3 = db.rawQuery(
                "SELECT COUNT(*) FROM ecdict",
                null
        );

        if (c3.moveToFirst()) {
            tvTotalCount.setText(String.valueOf(c3.getInt(0)));
        }

        c3.close();
    }
}