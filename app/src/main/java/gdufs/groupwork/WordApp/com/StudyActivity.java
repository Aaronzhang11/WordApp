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

        cardFront.setVisibility(View.VISIBLE);
        cardBack.setVisibility(View.GONE);
        controlPanel.setVisibility(View.INVISIBLE);
        isBackVisible = false;

        updateFavoriteIcon(item.word);
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

            switch (nextLevel) {
                case 1:
                    interval = 5 * 60 * 1000L;
                    break;
                case 2:
                    interval = 24 * 60 * 60 * 1000L;
                    break;
                case 3:
                    interval = 7 * 24 * 60 * 60 * 1000L;
                    break;
                default:
                    interval = 14 * 24 * 60 * 60 * 1000L;
                    break;
            }
        } else {
            nextLevel = 0;
            interval = 60 * 1000L;
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

        int rows = db.update(
                "study_record",
                values,
                "user_id = ? AND word = ?",
                new String[]{
                        String.valueOf(currentUserId),
                        item.word
                }
        );

        if (rows == 0) {
            db.insertWithOnConflict(
                    "study_record",
                    null,
                    values,
                    SQLiteDatabase.CONFLICT_IGNORE
            );
        }

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

                    if (rows == 0) {
                        values.put("user_id", currentUserId);
                        values.put("word", item.word);
                        values.put("master_level", 0);
                        values.put("next_review_time", 0);
                        values.put("error_count", 0);

                        db.insertWithOnConflict(
                                "study_record",
                                null,
                                values,
                                SQLiteDatabase.CONFLICT_IGNORE
                        );
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
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        if (isCurrentUserFavorite(db, item.word)) {
            new AlertDialog.Builder(this)
                    .setTitle("取消收藏")
                    .setMessage("确定要取消收藏单词 \"" + item.word + "\" 吗？\n\n该操作会从当前用户所有单词本中移除此单词。")
                    .setPositiveButton("确定", (dialog, which) -> {
                        int rows = db.delete(
                                "book_word_relation",
                                "word = ? AND book_id IN (" +
                                        "SELECT book_id FROM word_book WHERE user_id = ?" +
                                        ")",
                                new String[]{
                                        item.word,
                                        String.valueOf(currentUserId)
                                }
                        );

                        if (rows > 0) {
                            Toast.makeText(this, "已取消收藏", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(this, "当前单词未收藏", Toast.LENGTH_SHORT).show();
                        }

                        btnFavorite.setImageResource(android.R.drawable.btn_star_big_off);
                    })
                    .setNegativeButton("取消", null)
                    .show();

            return;
        }

        List<Integer> bookIds = new ArrayList<>();
        List<String> bookNames = new ArrayList<>();

        Cursor cursor = db.rawQuery(
                "SELECT book_id, book_name FROM word_book " +
                        "WHERE user_id = ? " +
                        "ORDER BY create_time DESC",
                new String[]{String.valueOf(currentUserId)}
        );

        while (cursor.moveToNext()) {
            bookIds.add(cursor.getInt(0));
            bookNames.add(cursor.getString(1));
        }

        cursor.close();

        if (bookIds.isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle("还没有单词本")
                    .setMessage("当前账号还没有创建单词本，是否自动创建一个“默认单词本”并收藏该单词？")
                    .setPositiveButton("创建并收藏", (dialog, which) -> {
                        int defaultBookId = createDefaultBook(db);

                        if (defaultBookId != -1) {
                            addWordToBook(db, defaultBookId, item.word);
                        } else {
                            Toast.makeText(this, "默认单词本创建失败", Toast.LENGTH_SHORT).show();
                        }
                    })
                    .setNegativeButton("取消", null)
                    .show();

            return;
        }

        String[] bookNameArray = bookNames.toArray(new String[0]);

        new AlertDialog.Builder(this)
                .setTitle("选择要收藏到的单词本")
                .setItems(bookNameArray, (dialog, which) -> {
                    int selectedBookId = bookIds.get(which);
                    addWordToBook(db, selectedBookId, item.word);
                })
                .show();
    }

    private boolean isCurrentUserFavorite(SQLiteDatabase db, String word) {
        Cursor cursor = db.rawQuery(
                "SELECT r.relation_id " +
                        "FROM book_word_relation r " +
                        "JOIN word_book b ON r.book_id = b.book_id " +
                        "WHERE b.user_id = ? AND r.word = ? " +
                        "LIMIT 1",
                new String[]{
                        String.valueOf(currentUserId),
                        word
                }
        );

        boolean exists = cursor.moveToFirst();
        cursor.close();

        return exists;
    }

    private int createDefaultBook(SQLiteDatabase db) {
        ContentValues values = new ContentValues();
        values.put("user_id", currentUserId);
        values.put("book_name", "默认单词本");
        values.put("create_time", System.currentTimeMillis());

        long result = db.insertWithOnConflict(
                "word_book",
                null,
                values,
                SQLiteDatabase.CONFLICT_IGNORE
        );

        if (result != -1) {
            return (int) result;
        }

        Cursor cursor = db.rawQuery(
                "SELECT book_id FROM word_book WHERE user_id = ? AND book_name = ?",
                new String[]{
                        String.valueOf(currentUserId),
                        "默认单词本"
                }
        );

        int bookId = -1;

        if (cursor.moveToFirst()) {
            bookId = cursor.getInt(0);
        }

        cursor.close();

        return bookId;
    }

    private void addWordToBook(SQLiteDatabase db, int bookId, String word) {
        ContentValues values = new ContentValues();
        values.put("book_id", bookId);
        values.put("word", word);
        values.put("add_time", System.currentTimeMillis());

        long result = db.insertWithOnConflict(
                "book_word_relation",
                null,
                values,
                SQLiteDatabase.CONFLICT_IGNORE
        );

        if (result == -1) {
            Toast.makeText(this, "该单词已在此单词本中", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "收藏成功", Toast.LENGTH_SHORT).show();
        }

        btnFavorite.setImageResource(android.R.drawable.btn_star_big_on);
    }

    private void updateFavoriteIcon(String word) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        if (isCurrentUserFavorite(db, word)) {
            btnFavorite.setImageResource(android.R.drawable.btn_star_big_on);
        } else {
            btnFavorite.setImageResource(android.R.drawable.btn_star_big_off);
        }
    }
}