package gdufs.groupwork.WordApp.com;

import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;

public class StudyActivity extends AppCompatActivity {

    private TextView tvWord, tvPhonetic, tvTranslation;
    private View cardFront, cardBack;
    private LinearLayout controlPanel;
    private ImageButton btnFavorite, btnDelete;

    private DatabaseHelper dbHelper;
    private UserSessionManager sessionManager;
    private int currentUserId;

    private boolean isBackVisible = false;

    private final List<WordItem> studyQueue = new ArrayList<>();
    private int currentIndex = 0;

    private static class WordItem {
        String word;
        String phonetic;
        String translation;
        int level;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_study);

        dbHelper = new DatabaseHelper(this);
        sessionManager = new UserSessionManager(this);

        if (!sessionManager.isLoggedIn()) {
            Intent intent = new Intent(StudyActivity.this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            return;
        }

        currentUserId = sessionManager.getCurrentUserId();

        initViews();
        loadStudyQueue();
        showCurrentWord();
    }

    private void initViews() {
        tvWord = findViewById(R.id.tvWord);
        tvPhonetic = findViewById(R.id.tvPhonetic);
        tvTranslation = findViewById(R.id.tvTranslation);

        cardFront = findViewById(R.id.layoutCardFront);
        cardBack = findViewById(R.id.layoutCardBack);
        controlPanel = findViewById(R.id.controlPanel);

        btnFavorite = findViewById(R.id.btnFavorite);
        btnDelete = findViewById(R.id.btnDelete);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        // 点击单词卡片后翻转，显示中文释义和操作按钮
        findViewById(R.id.wordCardView).setOnClickListener(v -> flipCard());

        findViewById(R.id.btnRemembered).setOnClickListener(v -> processAnswer(true));
        findViewById(R.id.btnForgot).setOnClickListener(v -> processAnswer(false));

        btnDelete.setOnClickListener(v -> confirmDelete());
        btnFavorite.setOnClickListener(v -> toggleFavorite());
    }

    private void loadStudyQueue() {
        studyQueue.clear();

        SQLiteDatabase db = dbHelper.getReadableDatabase();
        long now = System.currentTimeMillis();

        // 1. 优先加载当前用户已经到复习时间的单词
        Cursor cursor = db.rawQuery(
                "SELECT e.word, e.phonetic, e.translation, s.master_level " +
                        "FROM study_record s " +
                        "JOIN ecdict e ON s.word = e.word " +
                        "WHERE s.user_id = ? " +
                        "AND s.is_ignored = 0 " +
                        "AND s.next_review_time <= ? " +
                        "LIMIT 50",
                new String[]{
                        String.valueOf(currentUserId),
                        String.valueOf(now)
                }
        );

        while (cursor.moveToNext()) {
            WordItem item = new WordItem();
            item.word = cursor.getString(0);
            item.phonetic = cursor.getString(1);
            item.translation = cursor.getString(2);
            item.level = cursor.getInt(3);
            studyQueue.add(item);
        }

        cursor.close();


    }

    private void showCurrentWord() {
        if (studyQueue.isEmpty()) {
            Toast.makeText(this, "当前没有待学习单词，请先生成词书", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        if (currentIndex >= studyQueue.size()) {
            Toast.makeText(this, "今日的学习任务已完成！", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        WordItem item = studyQueue.get(currentIndex);

        tvWord.setText(item.word);

        if (item.phonetic == null || item.phonetic.trim().isEmpty()) {
            tvPhonetic.setText("暂无音标");
        } else {
            tvPhonetic.setText(item.phonetic);
        }

        if (item.translation == null || item.translation.trim().isEmpty()) {
            tvTranslation.setText("暂无释义");
        } else {
            tvTranslation.setText(item.translation);
        }

        // 每次展示新单词时，重置为正面
        cardFront.setVisibility(View.VISIBLE);
        cardBack.setVisibility(View.GONE);
        controlPanel.setVisibility(View.INVISIBLE);
        isBackVisible = false;
    }

    private void flipCard() {
        if (isBackVisible) {
            return;
        }

        cardFront.setVisibility(View.GONE);
        cardBack.setVisibility(View.VISIBLE);
        controlPanel.setVisibility(View.VISIBLE);
        isBackVisible = true;
    }

    private void processAnswer(boolean knew) {
        if (studyQueue.isEmpty() || currentIndex >= studyQueue.size()) {
            return;
        }

        WordItem item = studyQueue.get(currentIndex);

        int nextLevel;
        long interval;

        if (knew) {
            nextLevel = Math.min(3, item.level + 1);

            // 简化版艾宾浩斯复习间隔
            switch (nextLevel) {
                case 1:
                    interval = 5 * 60 * 1000L;          // 5分钟
                    break;
                case 2:
                    interval = 24 * 60 * 60 * 1000L;    // 1天
                    break;
                case 3:
                    interval = 7 * 24 * 60 * 60 * 1000L; // 7天
                    break;
                default:
                    interval = 14 * 24 * 60 * 60 * 1000L; // 14天
                    break;
            }
        } else {
            nextLevel = 0;
            interval = 60 * 1000L; // 1分钟后重新复习
        }

        long nextReviewTime = System.currentTimeMillis() + interval;

        SQLiteDatabase db = dbHelper.getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put("user_id", currentUserId);
        values.put("word", item.word);
        values.put("master_level", nextLevel);
        values.put("next_review_time", nextReviewTime);
        values.put("is_ignored", 0);

        if (knew) {
            values.put("error_count", 0);
        } else {
            values.put("error_count", 1);
        }

        /*
         * DatabaseHelper 里 study_record 已经设置 UNIQUE(user_id, word)，
         * 所以这里 replace 时不会影响其他用户同一个单词的记录。
         */
        db.replace("study_record", null, values);

        currentIndex++;
        showCurrentWord();
    }

    private void confirmDelete() {
        if (studyQueue.isEmpty() || currentIndex >= studyQueue.size()) {
            return;
        }

        final WordItem item = studyQueue.get(currentIndex);

        new AlertDialog.Builder(this)
                .setTitle("提示")
                .setMessage("确定要将单词 \"" + item.word + "\" 从当前用户的学习队列中移除吗？")
                .setPositiveButton("确定", (dialog, which) -> {
                    SQLiteDatabase db = dbHelper.getWritableDatabase();

                    ContentValues values = new ContentValues();
                    values.put("is_ignored", 1);

                    int rows = db.update(
                            "study_record",
                            values,
                            "user_id = ? AND word = ?",
                            new String[]{
                                    String.valueOf(currentUserId),
                                    item.word
                            }
                    );

                    /*
                     * 如果这个词是随机新词，还没写入 study_record，
                     * update 会影响 0 行，所以这里手动插入一条当前用户的忽略记录。
                     */
                    if (rows == 0) {
                        values.put("user_id", currentUserId);
                        values.put("word", item.word);
                        values.put("master_level", 0);
                        values.put("next_review_time", 0);
                        values.put("error_count", 0);

                        db.insert("study_record", null, values);
                    }

                    Toast.makeText(StudyActivity.this, "已从当前用户队列中移除", Toast.LENGTH_SHORT).show();

                    currentIndex++;
                    showCurrentWord();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void toggleFavorite() {
        if (studyQueue.isEmpty() || currentIndex >= studyQueue.size()) {
            return;
        }

        WordItem item = studyQueue.get(currentIndex);

        /*
         * 这里目前只是临时提示。
         * 真正的收藏功能后面要写入 word_book 和 book_word_relation，
         * 并且也要绑定 currentUserId。
         */
        Toast.makeText(this, "\"" + item.word + "\" 收藏功能后续接入单词本", Toast.LENGTH_SHORT).show();
        btnFavorite.setImageResource(android.R.drawable.btn_star_big_on);
    }
}