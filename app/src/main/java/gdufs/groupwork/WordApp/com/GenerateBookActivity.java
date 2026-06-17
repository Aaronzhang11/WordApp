package gdufs.groupwork.WordApp.com;

import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class GenerateBookActivity extends AppCompatActivity {

    private TextView tvTotalWords, tvAvailableWords;
    private EditText etWordCount;
    private Button btnGenerate, btnBack;

    private DatabaseHelper dbHelper;
    private UserSessionManager sessionManager;
    private int currentUserId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_generate_book);

        dbHelper = new DatabaseHelper(this);
        sessionManager = new UserSessionManager(this);

        if (!sessionManager.isLoggedIn()) {
            Intent intent = new Intent(GenerateBookActivity.this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            return;
        }

        currentUserId = sessionManager.getCurrentUserId();

        initViews();
        loadWordCounts();
    }

    private void initViews() {
        tvTotalWords = findViewById(R.id.tvTotalWords);
        tvAvailableWords = findViewById(R.id.tvAvailableWords);
        etWordCount = findViewById(R.id.etWordCount);
        btnGenerate = findViewById(R.id.btnGenerate);
        btnBack = findViewById(R.id.btnBack);

        btnBack.setOnClickListener(v -> finish());

        btnGenerate.setOnClickListener(v -> generateBook());
    }

    private void loadWordCounts() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        Cursor totalCursor = db.rawQuery(
                "SELECT COUNT(*) FROM ecdict",
                null
        );

        int totalCount = 0;

        if (totalCursor.moveToFirst()) {
            totalCount = totalCursor.getInt(0);
        }

        totalCursor.close();

        Cursor availableCursor = db.rawQuery(
                "SELECT COUNT(*) FROM ecdict " +
                        "WHERE word NOT IN (" +
                        "SELECT word FROM study_record WHERE user_id = ?" +
                        ")",
                new String[]{String.valueOf(currentUserId)}
        );

        int availableCount = 0;

        if (availableCursor.moveToFirst()) {
            availableCount = availableCursor.getInt(0);
        }

        availableCursor.close();

        tvTotalWords.setText("词库总量：" + totalCount);
        tvAvailableWords.setText("当前用户可生成新词：" + availableCount);
    }

    private void generateBook() {
        String countText = etWordCount.getText().toString().trim();

        if (countText.isEmpty()) {
            Toast.makeText(this, "请输入要生成的单词数量", Toast.LENGTH_SHORT).show();
            return;
        }

        int targetCount;

        try {
            targetCount = Integer.parseInt(countText);
        } catch (NumberFormatException e) {
            Toast.makeText(this, "请输入有效数字", Toast.LENGTH_SHORT).show();
            return;
        }

        if (targetCount <= 0) {
            Toast.makeText(this, "单词数量必须大于0", Toast.LENGTH_SHORT).show();
            return;
        }

        SQLiteDatabase db = dbHelper.getWritableDatabase();

        Cursor availableCursor = db.rawQuery(
                "SELECT COUNT(*) FROM ecdict " +
                        "WHERE word NOT IN (" +
                        "SELECT word FROM study_record WHERE user_id = ?" +
                        ")",
                new String[]{String.valueOf(currentUserId)}
        );

        int availableCount = 0;

        if (availableCursor.moveToFirst()) {
            availableCount = availableCursor.getInt(0);
        }

        availableCursor.close();

        if (availableCount <= 0) {
            Toast.makeText(this, "当前用户已经没有可生成的新词", Toast.LENGTH_SHORT).show();
            return;
        }

        if (targetCount > availableCount) {
            targetCount = availableCount;
            Toast.makeText(this, "输入数量超过可用词数，已按最大可用数量生成", Toast.LENGTH_SHORT).show();
        }

        Cursor cursor = db.rawQuery(
                "SELECT word FROM ecdict " +
                        "WHERE word NOT IN (" +
                        "SELECT word FROM study_record WHERE user_id = ?" +
                        ") " +
                        "ORDER BY RANDOM() LIMIT ?",
                new String[]{
                        String.valueOf(currentUserId),
                        String.valueOf(targetCount)
                }
        );

        long now = System.currentTimeMillis();
        int insertCount = 0;

        db.beginTransaction();

        try {
            while (cursor.moveToNext()) {
                String word = cursor.getString(0);

                ContentValues values = new ContentValues();
                values.put("user_id", currentUserId);
                values.put("word", word);
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

        if (insertCount > 0) {
            Toast.makeText(this, "生成成功，共生成 " + insertCount + " 个待学单词", Toast.LENGTH_SHORT).show();

            Intent intent = new Intent(GenerateBookActivity.this, StudyActivity.class);
            startActivity(intent);
            finish();
        } else {
            Toast.makeText(this, "没有生成新单词，请重试", Toast.LENGTH_SHORT).show();
        }
    }
}