package gdufs.groupwork.WordApp.com;

import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.text.InputType;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

import java.text.NumberFormat;
import java.util.Locale;

/**
 * 自定义生成词书页面。
 *
 * 功能：
 * 1. 显示总词库数量；
 * 2. 显示当前用户还可生成的新词数量；
 * 3. 提供 10 / 20 / 50 快捷选择；
 * 4. 支持输入任意正整数；
 * 5. 生成后直接进入 StudyActivity。
 */
public class GenerateBookActivity extends AppCompatActivity {

    // 词库统计
    private TextView tvTotalWords;
    private TextView tvAvailableWords;

    // 输入数量
    private EditText etWordCount;

    // 主按钮
    private MaterialButton btnGenerate;
    private MaterialButton btnBack;

    // 快捷数量按钮
    private MaterialButton btnPreset10;
    private MaterialButton btnPreset20;
    private MaterialButton btnPreset50;

    private DatabaseHelper dbHelper;
    private UserSessionManager sessionManager;

    private int currentUserId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_generate_book);

        dbHelper = new DatabaseHelper(this);
        sessionManager = new UserSessionManager(this);

        // 未登录时回登录页
        if (!sessionManager.isLoggedIn()) {
            Intent intent = new Intent(
                    GenerateBookActivity.this,
                    LoginActivity.class
            );

            intent.setFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK
                            | Intent.FLAG_ACTIVITY_CLEAR_TASK
            );

            startActivity(intent);
            finish();
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

        btnPreset10 = findViewById(R.id.btnPreset10);
        btnPreset20 = findViewById(R.id.btnPreset20);
        btnPreset50 = findViewById(R.id.btnPreset50);

        // 输入框只允许输入数字
        etWordCount.setInputType(InputType.TYPE_CLASS_NUMBER);

        // 返回学习中心
        btnBack.setOnClickListener(v -> finish());

        // 生成并开始背词
        btnGenerate.setOnClickListener(v -> generateBook());

        // 快捷数量按钮
        btnPreset10.setOnClickListener(v -> setPresetCount(10));
        btnPreset20.setOnClickListener(v -> setPresetCount(20));
        btnPreset50.setOnClickListener(v -> setPresetCount(50));
    }

    /**
     * 选择快捷数量。
     */
    private void setPresetCount(int count) {
        etWordCount.setText(String.valueOf(count));
        etWordCount.setSelection(
                etWordCount.getText().length()
        );
    }

    /**
     * 读取总词库数量与当前用户可生成的新词数量。
     */
    private void loadWordCounts() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        // 总词库数量
        Cursor totalCursor = db.rawQuery(
                "SELECT COUNT(*) FROM ecdict",
                null
        );

        int totalCount = 0;

        if (totalCursor.moveToFirst()) {
            totalCount = totalCursor.getInt(0);
        }

        totalCursor.close();

        // 当前用户还没有加入学习计划的新词数量
        Cursor availableCursor = db.rawQuery(
                "SELECT COUNT(*) FROM ecdict "
                        + "WHERE word NOT IN ("
                        + "SELECT word FROM study_record WHERE user_id = ?"
                        + ")",
                new String[]{
                        String.valueOf(currentUserId)
                }
        );

        int availableCount = 0;

        if (availableCursor.moveToFirst()) {
            availableCount = availableCursor.getInt(0);
        }

        availableCursor.close();

        NumberFormat numberFormat = NumberFormat.getInstance(
                Locale.getDefault()
        );

        tvTotalWords.setText(
                numberFormat.format(totalCount)
        );

        tvAvailableWords.setText(
                numberFormat.format(availableCount)
        );
    }

    /**
     * 根据输入数量，随机生成新词并写入 study_record。
     */
    private void generateBook() {
        String countText = etWordCount.getText()
                .toString()
                .trim();

        if (countText.isEmpty()) {
            Toast.makeText(
                    this,
                    "请输入要生成的单词数量",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        int targetCount;

        try {
            targetCount = Integer.parseInt(countText);
        } catch (NumberFormatException e) {
            Toast.makeText(
                    this,
                    "请输入有效数字",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        if (targetCount <= 0) {
            Toast.makeText(
                    this,
                    "单词数量必须大于 0",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        SQLiteDatabase db = dbHelper.getWritableDatabase();

        // 重新检查当前可用新词数量
        int availableCount = getAvailableNewWordCount(db);

        if (availableCount <= 0) {
            Toast.makeText(
                    this,
                    "当前用户已经没有可生成的新词",
                    Toast.LENGTH_LONG
            ).show();
            return;
        }

        // 输入超过剩余可用词数时，自动按最大数量生成
        if (targetCount > availableCount) {
            targetCount = availableCount;

            Toast.makeText(
                    this,
                    "剩余可生成新词不足，已按 "
                            + availableCount + " 个生成",
                    Toast.LENGTH_LONG
            ).show();
        }

        Cursor cursor = db.rawQuery(
                "SELECT word FROM ecdict "
                        + "WHERE word NOT IN ("
                        + "SELECT word FROM study_record WHERE user_id = ?"
                        + ") "
                        + "ORDER BY RANDOM() LIMIT ?",
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
            Toast.makeText(
                    this,
                    "已生成 " + insertCount + " 个新词，开始学习",
                    Toast.LENGTH_SHORT
            ).show();

            Intent intent = new Intent(
                    GenerateBookActivity.this,
                    StudyActivity.class
            );

            startActivity(intent);
            finish();
        } else {
            Toast.makeText(
                    this,
                    "没有生成新单词，请重试",
                    Toast.LENGTH_SHORT
            ).show();
        }
    }

    /**
     * 获取还未加入当前用户学习计划的单词数量。
     */
    private int getAvailableNewWordCount(SQLiteDatabase db) {
        Cursor cursor = db.rawQuery(
                "SELECT COUNT(*) FROM ecdict "
                        + "WHERE word NOT IN ("
                        + "SELECT word FROM study_record WHERE user_id = ?"
                        + ")",
                new String[]{
                        String.valueOf(currentUserId)
                }
        );

        int count = 0;

        if (cursor.moveToFirst()) {
            count = cursor.getInt(0);
        }

        cursor.close();

        return count;
    }
}