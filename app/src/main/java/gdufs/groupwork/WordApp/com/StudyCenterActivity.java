package gdufs.groupwork.WordApp.com;

import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

/**
 * 学习中心页面。
 *
 * 保留原有功能：
 * 1. 继续背词
 * 2. 快速开始 20 词
 * 3. 自定义生成词书
 *
 * 新增功能：
 * 4. 删除尚未真正学习过的新词
 */
public class StudyCenterActivity extends AppCompatActivity {

    // 顶部学习任务提示
    private TextView tvPendingSummary;

    // “继续背词”下方的统计说明
    private TextView tvContinueDescription;

    // 功能按钮
    private MaterialButton btnContinueStudy;
    private MaterialButton btnQuickStart;
    private MaterialButton btnCustomBook;

    // 新增：删除新词按钮
    private MaterialButton btnClearNewWords;

    private DatabaseHelper dbHelper;
    private UserSessionManager sessionManager;

    private int currentUserId;

    // 快速开始时默认生成的新词数量
    private static final int QUICK_START_COUNT = 20;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_study_center);

        dbHelper = new DatabaseHelper(this);
        sessionManager = new UserSessionManager(this);

        // 未登录时直接返回登录页
        if (!sessionManager.isLoggedIn()) {
            Intent intent = new Intent(
                    StudyCenterActivity.this,
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
        refreshLearningInfo();
    }

    @Override
    protected void onResume() {
        super.onResume();

        // 从背词页、生成词书页返回时，重新刷新数量
        if (dbHelper != null
                && sessionManager != null
                && sessionManager.isLoggedIn()) {
            refreshLearningInfo();
        }
    }

    private void initViews() {
        tvPendingSummary = findViewById(R.id.tvPendingSummary);
        tvContinueDescription = findViewById(R.id.tvContinueDescription);

        btnContinueStudy = findViewById(R.id.btnContinueStudy);
        btnQuickStart = findViewById(R.id.btnQuickStart);
        btnCustomBook = findViewById(R.id.btnCustomBook);

        // 新增按钮
        btnClearNewWords = findViewById(R.id.btnClearNewWords);

        // 返回首页
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        // 继续背词
        btnContinueStudy.setOnClickListener(v -> startContinueStudy());

        // 快速开始 20 词
        btnQuickStart.setOnClickListener(v -> quickStartTwentyWords());

        // 自定义生成词书
        btnCustomBook.setOnClickListener(v -> {
            Intent intent = new Intent(
                    StudyCenterActivity.this,
                    GenerateBookActivity.class
            );
            startActivity(intent);
        });

        // 新增：删除新词
        btnClearNewWords.setOnClickListener(v -> confirmClearNewWords());
    }

    /**
     * 刷新学习中心中的数字、提示文本、按钮状态。
     */
    private void refreshLearningInfo() {
        int dueCount = getDueStudyCount();
        int dueNewWordCount = getDueNewWordCount();
        int dueReviewCount = dueCount - dueNewWordCount;
        int removableNewWordCount = getRemovableNewWordCount();
        int availableNewWordCount = getAvailableNewWordCount();

        // 顶部学习摘要
        if (dueCount > 0) {
            tvPendingSummary.setText(
                    "今天有 " + dueCount + " 个单词等待你学习"
            );
        } else {
            tvPendingSummary.setText(
                    "当前没有到期复习任务，可以学习新词"
            );
        }

        // 继续背词按钮
        btnContinueStudy.setText(
                "继续背词（" + dueCount + " 个待学）"
        );

        if (dueCount > 0) {
            btnContinueStudy.setEnabled(true);
            btnContinueStudy.setAlpha(1f);

            tvContinueDescription.setText(
                    "其中新词 " + dueNewWordCount
                            + " 个 · 到期复习 "
                            + dueReviewCount + " 个"
            );
        } else {
            btnContinueStudy.setEnabled(false);
            btnContinueStudy.setAlpha(0.55f);

            tvContinueDescription.setText(
                    "当前没有待背或待复习单词"
            );
        }

        /*
         * 新增：
         * 只要存在尚未学习的新词，就显示删除按钮。
         *
         * 例如：
         * 删除新词（40）
         */
        if (removableNewWordCount > 0) {
            btnClearNewWords.setVisibility(View.VISIBLE);
            btnClearNewWords.setText(
                    "删除新词（" + removableNewWordCount + "）"
            );
        } else {
            btnClearNewWords.setVisibility(View.GONE);
        }

        // 保留原有快速开始按钮状态
        boolean quickStartAvailable = availableNewWordCount > 0;

        btnQuickStart.setEnabled(quickStartAvailable);
        btnQuickStart.setAlpha(
                quickStartAvailable ? 1f : 0.55f
        );
    }

    /**
     * 获取当前到期的待学习 / 待复习单词数量。
     */
    private int getDueStudyCount() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        Cursor cursor = db.rawQuery(
                "SELECT COUNT(*) FROM study_record "
                        + "WHERE user_id = ? "
                        + "AND is_ignored = 0 "
                        + "AND next_review_time <= ?",
                new String[]{
                        String.valueOf(currentUserId),
                        String.valueOf(System.currentTimeMillis())
                }
        );

        int count = 0;

        if (cursor.moveToFirst()) {
            count = cursor.getInt(0);
        }

        cursor.close();

        return count;
    }

    /**
     * 获取到期单词中的“新词”数量。
     *
     * 新词条件：
     * master_level = 0
     * error_count = 0
     *
     * 这样答错后重新复习的词，不会被当成新词。
     */
    private int getDueNewWordCount() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        Cursor cursor = db.rawQuery(
                "SELECT COUNT(*) FROM study_record "
                        + "WHERE user_id = ? "
                        + "AND is_ignored = 0 "
                        + "AND master_level = 0 "
                        + "AND error_count = 0 "
                        + "AND next_review_time <= ?",
                new String[]{
                        String.valueOf(currentUserId),
                        String.valueOf(System.currentTimeMillis())
                }
        );

        int count = 0;

        if (cursor.moveToFirst()) {
            count = cursor.getInt(0);
        }

        cursor.close();

        return count;
    }

    /**
     * 获取“可删除的新词”数量。
     *
     * 不限制 next_review_time，
     * 因为用户刚生成新词但还没开始背时，也应该能删除。
     */
    private int getRemovableNewWordCount() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        Cursor cursor = db.rawQuery(
                "SELECT COUNT(*) FROM study_record "
                        + "WHERE user_id = ? "
                        + "AND is_ignored = 0 "
                        + "AND master_level = 0 "
                        + "AND error_count = 0",
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

    /**
     * 获取还未加入当前用户学习计划的新词数量。
     */
    private int getAvailableNewWordCount() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();

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

    /**
     * 保留原有逻辑：
     * 点击后进入当前待背/待复习队列。
     */
    private void startContinueStudy() {
        int dueCount = getDueStudyCount();

        if (dueCount <= 0) {
            Toast.makeText(
                    this,
                    "当前没有待背或待复习单词",
                    Toast.LENGTH_SHORT
            ).show();

            refreshLearningInfo();
            return;
        }

        Intent intent = new Intent(
                StudyCenterActivity.this,
                StudyActivity.class
        );

        startActivity(intent);
    }

    /**
     * 保留原有逻辑：
     * 快速开始时，如果当前有待背词则优先进入学习队列；
     * 没有待背词时才随机生成 20 个新词。
     */
    private void quickStartTwentyWords() {
        int dueCount = getDueStudyCount();

        // 有待背/待复习词时，优先进入当前队列
        if (dueCount > 0) {
            Toast.makeText(
                    this,
                    "当前有 " + dueCount + " 个待学单词，已优先进入学习队列",
                    Toast.LENGTH_SHORT
            ).show();

            startContinueStudy();
            return;
        }

        int insertCount = generateQuickWords(QUICK_START_COUNT);

        if (insertCount <= 0) {
            Toast.makeText(
                    this,
                    "当前没有可生成的新词",
                    Toast.LENGTH_LONG
            ).show();

            refreshLearningInfo();
            return;
        }

        Toast.makeText(
                this,
                "已生成 " + insertCount + " 个新词，开始学习",
                Toast.LENGTH_SHORT
        ).show();

        Intent intent = new Intent(
                StudyCenterActivity.this,
                StudyActivity.class
        );

        startActivity(intent);
    }

    /**
     * 保留原有逻辑：
     * 从词库随机生成指定数量的新词。
     */
    private int generateQuickWords(int targetCount) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        int availableCount = getAvailableNewWordCount();

        if (availableCount <= 0) {
            return 0;
        }

        int actualCount = Math.min(targetCount, availableCount);

        Cursor cursor = db.rawQuery(
                "SELECT word FROM ecdict "
                        + "WHERE word NOT IN ("
                        + "SELECT word FROM study_record WHERE user_id = ?"
                        + ") "
                        + "ORDER BY RANDOM() LIMIT ?",
                new String[]{
                        String.valueOf(currentUserId),
                        String.valueOf(actualCount)
                }
        );

        int insertCount = 0;
        long now = System.currentTimeMillis();

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

        return insertCount;
    }

    /**
     * 删除前二次确认。
     */
    private void confirmClearNewWords() {
        int newWordCount = getRemovableNewWordCount();

        if (newWordCount <= 0) {
            Toast.makeText(
                    this,
                    "当前没有可删除的新词",
                    Toast.LENGTH_SHORT
            ).show();

            refreshLearningInfo();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("删除新词")
                .setMessage(
                        "确定要删除 " + newWordCount
                                + " 个尚未学习的新词吗？\n\n"
                                + "已学习、答错待复习、已掌握的单词不会受影响。"
                )
                .setNegativeButton("取消", null)
                .setPositiveButton("确认删除", (dialog, which) ->
                        clearNewWords()
                )
                .show();
    }

    /**
     * 将尚未学习的新词标记为忽略。
     *
     * 不直接物理删除记录，而是设置 is_ignored = 1：
     * 1. 它们不再出现在继续背词列表；
     * 2. 不影响复习词、已掌握词；
     * 3. 后续随机生成时也不会重复抽到。
     */
    private void clearNewWords() {
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put("is_ignored", 1);

        int changedRows = db.update(
                "study_record",
                values,
                "user_id = ? "
                        + "AND is_ignored = 0 "
                        + "AND master_level = 0 "
                        + "AND error_count = 0",
                new String[]{
                        String.valueOf(currentUserId)
                }
        );

        if (changedRows > 0) {
            Toast.makeText(
                    this,
                    "已删除 " + changedRows + " 个新词",
                    Toast.LENGTH_SHORT
            ).show();
        } else {
            Toast.makeText(
                    this,
                    "没有可删除的新词",
                    Toast.LENGTH_SHORT
            ).show();
        }

        refreshLearningInfo();
    }
}