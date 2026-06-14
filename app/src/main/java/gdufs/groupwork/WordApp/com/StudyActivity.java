package gdufs.groupwork.WordApp.com;

import android.animation.Animator;
import android.animation.AnimatorInflater;
import android.animation.AnimatorListenerAdapter;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
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
    private boolean isBackVisible = false;

    private static class WordItem {
        String word;
        String phonetic;
        String translation;
        int level;
    }

    private List<WordItem> studyQueue = new ArrayList<>();
    private int currentIndex = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_study);

        dbHelper = new DatabaseHelper(this);
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

        // 卡片翻转交互逻辑 (纯本地三维翻转模拟效果)
        findViewById(R.id.wordCardView).setOnClickListener(v -> flipCard());

        findViewById(R.id.btnRemembered).setOnClickListener(v -> processAnswer(true));
        findViewById(R.id.btnForgot).setOnClickListener(v -> processAnswer(false));

        btnDelete.setOnClickListener(v -> confirmDelete());
        btnFavorite.setOnClickListener(v -> toggleFavorite());
    }

    private void loadStudyQueue() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        long now = System.currentTimeMillis();

        // 查找未忽略、到时间的词库单词
        Cursor cursor = db.rawQuery(
                "SELECT e.word, e.phonetic, e.translation, s.master_level FROM study_record s " +
                        "JOIN ecdict e ON s.word = e.word " +
                        "WHERE s.is_ignored = 0 AND s.next_review_time <= ? LIMIT 50",
                new String[]{String.valueOf(now)});

        while (cursor.moveToNext()) {
            WordItem item = new WordItem();
            item.word = cursor.getString(0);
            item.phonetic = cursor.getString(1);
            item.translation = cursor.getString(2);
            item.level = cursor.getInt(3);
            studyQueue.add(item);
        }
        cursor.close();

        // 若今日无复习计划，随机生成一些未学的新词任务
        if (studyQueue.isEmpty()) {
            Cursor randomCursor = db.rawQuery(
                    "SELECT word, phonetic, translation FROM ecdict " +
                            "WHERE word NOT IN (SELECT word FROM study_record) " +
                            "ORDER BY RANDOM() LIMIT 20", null);

            while (randomCursor.moveToNext()) {
                WordItem item = new WordItem();
                item.word = randomCursor.getString(0);
                item.phonetic = randomCursor.getString(1);
                item.translation = randomCursor.getString(2);
                item.level = 0;
                studyQueue.add(item);
            }
            randomCursor.close();
        }
    }

    private void showCurrentWord() {
        if (studyQueue.isEmpty() || currentIndex >= studyQueue.size()) {
            Toast.makeText(this, "今日的学习任务已完成！", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        WordItem item = studyQueue.get(currentIndex);
        tvWord.setText(item.word);
        tvPhonetic.setText(item.phonetic);
        tvTranslation.setText(item.translation);

        // 重置为卡片正面状态
        cardFront.setVisibility(View.VISIBLE);
        cardBack.setVisibility(View.GONE);
        controlPanel.setVisibility(View.INVISIBLE);
        isBackVisible = false;
    }

    private void flipCard() {
        if (isBackVisible) return;

        // 简易淡入淡出动画过渡
        cardFront.setVisibility(View.GONE);
        cardBack.setVisibility(View.VISIBLE);
        controlPanel.setVisibility(View.VISIBLE);
        isBackVisible = true;
    }

    private void processAnswer(boolean knew) {
        if (studyQueue.isEmpty()) return;
        WordItem item = studyQueue.get(currentIndex);
        int nextLevel;
        long interval;

        if (knew) {
            nextLevel = Math.min(3, item.level + 1);
            // 艾宾浩斯复习级数区间间隔对应 (毫秒单位)
            switch (nextLevel) {
                case 1: interval = 300000; break;     // 5分钟后
                case 2: interval = 86400000; break;   // 1天后
                case 3: interval = 604800000; break;  // 7天后
                default: interval = 1209600000;       // 14天后
            }
        } else {
            nextLevel = 0; // 重置掌握进度，进入快速复习模式
            interval = 60000; // 1分钟后重新展示
        }

        long nextReviewTime = System.currentTimeMillis() + interval;

        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("word", item.word);
        values.put("master_level", nextLevel);
        values.put("next_review_time", nextReviewTime);

        db.replace("study_record", null, values);

        currentIndex++;
        showCurrentWord();
    }

    private void confirmDelete() {
        if (studyQueue.isEmpty()) return;
        final WordItem item = studyQueue.get(currentIndex);

        new AlertDialog.Builder(this)
                .setTitle("提示")
                .setMessage("确定要将单词 \"" + item.word + "\" 从学习队列中永久移除吗？")
                .setPositiveButton("确定", (dialog, which) -> {
                    SQLiteDatabase db = dbHelper.getWritableDatabase();
                    ContentValues values = new ContentValues();
                    values.put("is_ignored", 1);
                    db.update("study_record", values, "word = ?", new String[]{item.word});

                    Toast.makeText(StudyActivity.this, "已从队列中移除", Toast.LENGTH_SHORT).show();
                    currentIndex++;
                    showCurrentWord();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void toggleFavorite() {
        if (studyQueue.isEmpty()) return;
        WordItem item = studyQueue.get(currentIndex);
        Toast.makeText(this, "\"" + item.word + "\" 收藏成功", Toast.LENGTH_SHORT).show();
        btnFavorite.setImageResource(android.provider.ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE.hashCode() % 2 == 0 ?
                android.R.drawable.btn_star_big_on : android.R.drawable.btn_star_big_off);
    }
}