package gdufs.groupwork.WordApp.com;

import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

public class MainActivity extends AppCompatActivity {

    // 首页统计数字
    private TextView tvToStudyCount;
    private TextView tvMasteredCount;
    private TextView tvTotalCount;

    // 右上角头像按钮
    private MaterialButton btnProfileAvatar;

    private DatabaseHelper dbHelper;
    private UserSessionManager sessionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        sessionManager = new UserSessionManager(this);

        // 未登录时直接回到登录页
        if (!sessionManager.isLoggedIn()) {
            Intent intent = new Intent(MainActivity.this, LoginActivity.class);
            intent.setFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK
                            | Intent.FLAG_ACTIVITY_CLEAR_TASK
            );
            startActivity(intent);
            finish();
            return;
        }

        dbHelper = new DatabaseHelper(this);

        initViews();
        loadStatistics();
    }

    private void initViews() {
        // 首页统计控件
        tvToStudyCount = findViewById(R.id.tvToStudyCount);
        tvMasteredCount = findViewById(R.id.tvMasteredCount);
        tvTotalCount = findViewById(R.id.tvTotalCount);

        // 右上角用户头像
        btnProfileAvatar = findViewById(R.id.btnProfileAvatar);

        String username = sessionManager.getCurrentUsername();

        // 头像显示用户名最后两个字符
        btnProfileAvatar.setText(getAvatarText(username));
        btnProfileAvatar.setContentDescription("打开用户信息：" + username);

        // 点击头像进入用户信息页
        btnProfileAvatar.setOnClickListener(v -> {
            Intent intent = new Intent(
                    MainActivity.this,
                    UserProfileActivity.class
            );
            startActivity(intent);
        });

        // 生成词书
        findViewById(R.id.btnGenerateBook).setOnClickListener(v -> {
            Intent intent = new Intent(
                    MainActivity.this,
                    GenerateBookActivity.class
            );
            startActivity(intent);
        });

        // 背单词
        findViewById(R.id.btnStudy).setOnClickListener(v -> {
            Intent intent = new Intent(
                    MainActivity.this,
                    StudyActivity.class
            );
            startActivity(intent);
        });

        // 模拟自测卷：先进入模式选择页
        findViewById(R.id.btnTest).setOnClickListener(v -> {
            Intent intent = new Intent(
                    MainActivity.this,
                    TestModeActivity.class
            );
            startActivity(intent);
        });

        // 中英双向词典
        findViewById(R.id.btnSearch).setOnClickListener(v -> {
            Intent intent = new Intent(
                    MainActivity.this,
                    SearchActivity.class
            );
            startActivity(intent);
        });

        // 自定义单词本
        findViewById(R.id.btnWordBook).setOnClickListener(v -> {
            Intent intent = new Intent(
                    MainActivity.this,
                    WordBookActivity.class
            );
            startActivity(intent);
        });

        // 学习数据看板
        findViewById(R.id.btnDashboardInline).setOnClickListener(v -> {
            Intent intent = new Intent(
                    MainActivity.this,
                    DashboardActivity.class
            );
            startActivity(intent);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();

        // 从背词、测试、用户信息等页面返回首页后刷新数据
        if (dbHelper != null
                && sessionManager != null
                && sessionManager.isLoggedIn()) {
            loadStatistics();

            // 用户名虽然当前不支持修改，但这里顺便刷新头像更稳妥
            if (btnProfileAvatar != null) {
                btnProfileAvatar.setText(
                        getAvatarText(sessionManager.getCurrentUsername())
                );
            }
        }
    }

    /**
     * 按要求获取用户名最后两个字符。
     *
     * test01 -> 01
     * test   -> st
     * 张三丰  -> 三丰
     * A      -> A
     *
     * 这里按 Unicode 字符处理，避免中文或 Emoji 截断异常。
     */
    private String getAvatarText(String username) {
        if (username == null || username.trim().isEmpty()) {
            return "?";
        }

        String value = username.trim();

        int codePointCount = value.codePointCount(0, value.length());

        if (codePointCount <= 2) {
            return value;
        }

        int startIndex = value.offsetByCodePoints(
                0,
                codePointCount - 2
        );

        return value.substring(startIndex);
    }

    /**
     * 加载首页统计：
     * 1. 待背/待复习
     * 2. 已掌握
     * 3. 词库总数
     */
    private void loadStatistics() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        long currentTime = System.currentTimeMillis();
        int currentUserId = sessionManager.getCurrentUserId();

        // 待学习 / 待复习数量
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

        // 已掌握数量：等级达到 3
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

        // 公共词库总量
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