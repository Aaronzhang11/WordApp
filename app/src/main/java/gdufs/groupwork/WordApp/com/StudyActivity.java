package gdufs.groupwork.WordApp.com;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.content.ContentValues;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.List;

public class StudyActivity extends AppCompatActivity {

    // 正面内容
    private TextView tvWord;
    private TextView tvPhonetic;
    private TextView tvLevelBadge;
    private TextView tvProgressText;

    // 背面内容
    private TextView tvTranslation;
    private TextView tvLevelDetail;

    // 等级进度条
    private ProgressBar progressLevel;

    // 卡片与操作区域
    private View cardFront;
    private View cardBack;
    private LinearLayout controlPanel;
    private MaterialCardView wordCardView;

    // 顶部操作按钮
    private ImageButton btnFavorite;
    private ImageButton btnDelete;

    private DatabaseHelper dbHelper;
    private UserSessionManager sessionManager;

    private int currentUserId;

    // 当前卡片是否已经翻到背面
    private boolean isBackVisible = false;

    // 防止动画过程中重复点击卡片
    private boolean isFlipping = false;

    // 当前待学习队列
    private final List<WordItem> studyQueue = new ArrayList<>();

    // 当前正在学习第几个单词
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

        // 未登录时返回登录页
        if (!sessionManager.isLoggedIn()) {
            Intent intent = new Intent(StudyActivity.this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
            return;
        }

        currentUserId = sessionManager.getCurrentUserId();

        initViews();
        loadStudyQueue();
        showCurrentWord();
    }

    private void initViews() {
        // 正面文字
        tvWord = findViewById(R.id.tvWord);
        tvPhonetic = findViewById(R.id.tvPhonetic);
        tvLevelBadge = findViewById(R.id.tvLevelBadge);
        tvProgressText = findViewById(R.id.tvProgressText);

        // 背面文字
        tvTranslation = findViewById(R.id.tvTranslation);
        tvLevelDetail = findViewById(R.id.tvLevelDetail);

        // 进度条与卡片
        progressLevel = findViewById(R.id.progressLevel);
        cardFront = findViewById(R.id.layoutCardFront);
        cardBack = findViewById(R.id.layoutCardBack);
        controlPanel = findViewById(R.id.controlPanel);
        wordCardView = findViewById(R.id.wordCardView);

        // 顶部操作按钮
        btnFavorite = findViewById(R.id.btnFavorite);
        btnDelete = findViewById(R.id.btnDelete);

        // 设置翻转动画的“镜头距离”，避免翻转时有过强透视变形
        wordCardView.post(() -> {
            float density = getResources().getDisplayMetrics().density;
            wordCardView.setCameraDistance(8000 * density);
        });

        // 返回主页
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        // 点击卡片翻转
        wordCardView.setOnClickListener(v -> flipCardWithAnimation());

        // 认识 / 不认识
        findViewById(R.id.btnRemembered).setOnClickListener(v -> processAnswer(true));
        findViewById(R.id.btnForgot).setOnClickListener(v -> processAnswer(false));

        // 删除当前单词
        btnDelete.setOnClickListener(v -> confirmDelete());

        // 收藏 / 取消收藏
        btnFavorite.setOnClickListener(v -> toggleFavorite());
    }

    /**
     * 从当前用户的学习记录中读取“到期需要复习”的单词。
     */
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

    /**
     * 显示当前单词，同时重置卡片到正面状态。
     */
    private void showCurrentWord() {
        if (studyQueue.isEmpty()) {
            Toast.makeText(
                    this,
                    "当前没有待学习单词，请先生成词书",
                    Toast.LENGTH_LONG
            ).show();

            finish();
            return;
        }

        if (currentIndex >= studyQueue.size()) {
            Toast.makeText(
                    this,
                    "今日的学习任务已完成！",
                    Toast.LENGTH_LONG
            ).show();

            finish();
            return;
        }

        WordItem item = studyQueue.get(currentIndex);

        tvWord.setText(safeText(item.word));

        if (item.phonetic == null || item.phonetic.trim().isEmpty()) {
            tvPhonetic.setText("暂无音标");
        } else {
            tvPhonetic.setText(item.phonetic.trim());
        }

        if (item.translation == null || item.translation.trim().isEmpty()) {
            tvTranslation.setText("暂无释义");
        } else {
            // 同时兼容数据库中真实换行和字符串形式的 \n
            tvTranslation.setText(
                    item.translation.trim()
                            .replace("\\r", "")
                            .replace("\\n", "\n")
            );
        }

        // 更新等级标识与进度条
        updateLevelIndicator(item.level);

        // 切换新单词时恢复成正面
        resetCardToFront();

        // 更新收藏图标
        updateFavoriteIcon(item.word);
    }

    /**
     * 根据 master_level 更新正面徽章、背面说明和进度条颜色。
     */
    private void updateLevelIndicator(int rawLevel) {
        int level = Math.max(0, Math.min(3, rawLevel));

        String badgeText;
        String detailText;
        int textColor;
        int backgroundColor;

        switch (level) {
            case 0:
                badgeText = "等级 0 · 待学习";
                detailText = "当前掌握程度：0级 · 新词，建议重点记忆";
                textColor = Color.parseColor("#475569");
                backgroundColor = Color.parseColor("#E2E8F0");
                break;

            case 1:
                badgeText = "等级 1 · 陌生";
                detailText = "当前掌握程度：1级 · 已学习过，建议尽快复习";
                textColor = Color.parseColor("#2563EB");
                backgroundColor = Color.parseColor("#DBEAFE");
                break;

            case 2:
                badgeText = "等级 2 · 模糊";
                detailText = "当前掌握程度：2级 · 已有印象，继续巩固";
                textColor = Color.parseColor("#C2410C");
                backgroundColor = Color.parseColor("#FFEDD5");
                break;

            default:
                badgeText = "等级 3 · 已掌握";
                detailText = "当前掌握程度：3级 · 掌握良好，进入长期复习";
                textColor = Color.parseColor("#15803D");
                backgroundColor = Color.parseColor("#DCFCE7");
                break;
        }

        // 正面等级徽章
        tvLevelBadge.setText(badgeText);
        tvLevelBadge.setTextColor(textColor);
        tvLevelBadge.setBackground(createRoundedDrawable(backgroundColor, 18));

        // 背面等级说明
        tvLevelDetail.setText(detailText);
        tvLevelDetail.setTextColor(textColor);
        tvLevelDetail.setBackground(createRoundedDrawable(backgroundColor, 14));

        // 进度条显示 0 / 3、1 / 3、2 / 3、3 / 3
        progressLevel.setMax(3);
        progressLevel.setProgress(level);
        progressLevel.setProgressTintList(ColorStateList.valueOf(textColor));

        tvProgressText.setText("掌握进度 " + level + " / 3");
        tvProgressText.setTextColor(textColor);
    }

    /**
     * 创建圆角背景，用于等级标签。
     */
    private GradientDrawable createRoundedDrawable(int backgroundColor, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(backgroundColor);

        float density = getResources().getDisplayMetrics().density;
        drawable.setCornerRadius(radiusDp * density);

        return drawable;
    }

    /**
     * 新单词进入时，恢复卡片正面且隐藏操作按钮。
     */
    private void resetCardToFront() {
        isBackVisible = false;
        isFlipping = false;

        wordCardView.animate().cancel();
        wordCardView.setRotationY(0f);

        cardFront.setVisibility(View.VISIBLE);
        cardFront.setAlpha(1f);

        cardBack.setVisibility(View.GONE);
        cardBack.setAlpha(0f);

        controlPanel.animate().cancel();
        controlPanel.setAlpha(0f);
        controlPanel.setVisibility(View.INVISIBLE);
    }

    /**
     * 执行两段式 3D 翻转动画：
     * 第一段：正面旋转 0° → 90°
     * 中间：切换为背面
     * 第二段：背面旋转 -90° → 0°
     */
    private void flipCardWithAnimation() {
        if (isBackVisible || isFlipping) {
            return;
        }

        if (studyQueue.isEmpty() || currentIndex >= studyQueue.size()) {
            return;
        }

        isFlipping = true;

        ObjectAnimator flipOut = ObjectAnimator.ofFloat(
                wordCardView,
                "rotationY",
                0f,
                90f
        );

        flipOut.setDuration(180);
        flipOut.setInterpolator(new AccelerateInterpolator());

        flipOut.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                // 卡片转到侧面时切换显示内容
                cardFront.setVisibility(View.GONE);
                cardFront.setAlpha(0f);

                cardBack.setVisibility(View.VISIBLE);
                cardBack.setAlpha(1f);

                // 背面从另一侧转回来
                wordCardView.setRotationY(-90f);

                ObjectAnimator flipIn = ObjectAnimator.ofFloat(
                        wordCardView,
                        "rotationY",
                        -90f,
                        0f
                );

                flipIn.setDuration(220);
                flipIn.setInterpolator(new DecelerateInterpolator());

                flipIn.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        isBackVisible = true;
                        isFlipping = false;

                        // 翻到背面后淡入“认识 / 不认识”操作区
                        controlPanel.setVisibility(View.VISIBLE);
                        controlPanel.setAlpha(0f);

                        controlPanel.animate()
                                .alpha(1f)
                                .setDuration(180)
                                .start();
                    }
                });

                flipIn.start();
            }
        });

        flipOut.start();
    }

    /**
     * 点击“认识”或“不认识”后更新掌握等级和复习时间。
     */
    private void processAnswer(boolean knew) {
        if (studyQueue.isEmpty() || currentIndex >= studyQueue.size()) {
            return;
        }

        WordItem item = studyQueue.get(currentIndex);

        int nextLevel;
        long interval;

        if (knew) {
            // 认识：等级最高到 3
            nextLevel = Math.min(3, item.level + 1);

            switch (nextLevel) {
                case 1:
                    // 第一次认识：5 分钟后复习
                    interval = 5 * 60 * 1000L;
                    break;

                case 2:
                    // 第二次认识：1 天后复习
                    interval = 24 * 60 * 60 * 1000L;
                    break;

                case 3:
                    // 已掌握：7 天后复习
                    interval = 7 * 24 * 60 * 60 * 1000L;
                    break;

                default:
                    interval = 14 * 24 * 60 * 60 * 1000L;
                    break;
            }
        } else {
            // 不认识：降为 0 级，1 分钟后重新进入复习队列
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

        // 理论上已有记录；这里保留兜底逻辑
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

    /**
     * 确认是否从学习计划中移除当前单词。
     */
    private void confirmDelete() {
        if (studyQueue.isEmpty() || currentIndex >= studyQueue.size()) {
            return;
        }

        final WordItem item = studyQueue.get(currentIndex);

        new AlertDialog.Builder(this)
                .setTitle("移除单词")
                .setMessage(
                        "确定要将单词 \"" + item.word + "\" 从当前学习计划中移除吗？\n\n" +
                                "移除后，它不会再进入后续学习和复习队列。"
                )
                .setPositiveButton("确定移除", (dialog, which) -> {
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

                    Toast.makeText(
                            StudyActivity.this,
                            "已从当前用户学习队列中移除",
                            Toast.LENGTH_SHORT
                    ).show();

                    currentIndex++;
                    showCurrentWord();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /**
     * 收藏或取消收藏当前单词。
     */
    private void toggleFavorite() {
        if (studyQueue.isEmpty() || currentIndex >= studyQueue.size()) {
            return;
        }

        WordItem item = studyQueue.get(currentIndex);
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        // 已收藏：询问是否取消
        if (isCurrentUserFavorite(db, item.word)) {
            new AlertDialog.Builder(this)
                    .setTitle("取消收藏")
                    .setMessage(
                            "确定要取消收藏单词 \"" + item.word + "\" 吗？\n\n" +
                                    "该操作会从当前用户所有单词本中移除此单词。"
                    )
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
                            Toast.makeText(
                                    this,
                                    "已取消收藏",
                                    Toast.LENGTH_SHORT
                            ).show();
                        } else {
                            Toast.makeText(
                                    this,
                                    "当前单词未收藏",
                                    Toast.LENGTH_SHORT
                            ).show();
                        }

                        btnFavorite.setImageResource(
                                android.R.drawable.btn_star_big_off
                        );
                    })
                    .setNegativeButton("取消", null)
                    .show();

            return;
        }

        // 未收藏：读取用户已有单词本
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

        // 没有单词本时，自动提供默认单词本
        if (bookIds.isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle("还没有单词本")
                    .setMessage(
                            "当前账号还没有创建单词本，是否自动创建一个“默认单词本”并收藏该单词？"
                    )
                    .setPositiveButton("创建并收藏", (dialog, which) -> {
                        int defaultBookId = createDefaultBook(db);

                        if (defaultBookId != -1) {
                            addWordToBook(db, defaultBookId, item.word);
                        } else {
                            Toast.makeText(
                                    this,
                                    "默认单词本创建失败",
                                    Toast.LENGTH_SHORT
                            ).show();
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

    /**
     * 判断当前用户是否已收藏该单词。
     */
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

    /**
     * 创建或获取默认单词本。
     */
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
                "SELECT book_id FROM word_book " +
                        "WHERE user_id = ? AND book_name = ?",
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

    /**
     * 将单词加入指定单词本。
     */
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
            Toast.makeText(
                    this,
                    "该单词已在此单词本中",
                    Toast.LENGTH_SHORT
            ).show();
        } else {
            Toast.makeText(
                    this,
                    "收藏成功",
                    Toast.LENGTH_SHORT
            ).show();
        }

        btnFavorite.setImageResource(
                android.R.drawable.btn_star_big_on
        );
    }

    /**
     * 根据收藏状态刷新星标图标。
     */
    private void updateFavoriteIcon(String word) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        if (isCurrentUserFavorite(db, word)) {
            btnFavorite.setImageResource(
                    android.R.drawable.btn_star_big_on
            );
        } else {
            btnFavorite.setImageResource(
                    android.R.drawable.btn_star_big_off
            );
        }
    }

    private String safeText(String value) {
        return value == null ? "" : value.trim();
    }
}