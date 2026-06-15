package gdufs.groupwork.WordApp.com;

import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;


public class MainActivity extends AppCompatActivity {
    private TextView tvToStudyCount, tvMasteredCount, tvTotalCount;
    private DatabaseHelper dbHelper;
    private UserSessionManager sessionManager;
    private TextView tvCurrentUser;

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

        findViewById(R.id.btnLogout).setOnClickListener(v -> {
            sessionManager.logout();

            Intent intent = new Intent(MainActivity.this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
        });

        findViewById(R.id.btnStudy).setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, StudyActivity.class);
            startActivity(intent);
        });

        findViewById(R.id.btnTest).setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, QuizActivity.class);
            startActivity(intent);
        });

        findViewById(R.id.btnSearch).setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, SearchActivity.class);
            startActivity(intent);
        });

        findViewById(R.id.btnWordBook).setOnClickListener(v ->
                Toast.makeText(this, "单词本功能正在加载...", Toast.LENGTH_SHORT).show()
        );
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadStatistics();
    }

    private void loadStatistics() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        long currentTime = System.currentTimeMillis();

        // 1. 获取待学习/待复习统计
        Cursor c1 = db.rawQuery("SELECT COUNT(*) FROM study_record WHERE is_ignored = 0 AND next_review_time <= ?",
                new String[]{String.valueOf(currentTime)});
        if (c1.moveToFirst()) {
            tvToStudyCount.setText(String.valueOf(c1.getInt(0)));
        }
        c1.close();

        // 2. 获取已掌握统计 (master_level = 3)
        Cursor c2 = db.rawQuery("SELECT COUNT(*) FROM study_record WHERE master_level >= 3", null);
        if (c2.moveToFirst()) {
            tvMasteredCount.setText(String.valueOf(c2.getInt(0)));
        }
        c2.close();

        //3.统计词库中导入的底层静态词条总量
        Cursor c3 = db.rawQuery("SELECT COUNT(*) FROM ecdict", null);
        if (c3.moveToFirst()) {
            tvTotalCount.setText(String.valueOf(c3.getInt(0)));
        }
        c3.close();
    }
}