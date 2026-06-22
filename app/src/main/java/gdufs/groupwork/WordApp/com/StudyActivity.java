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

    public static final String EXTRA_STUDY_MODE = "study_mode";
    /** 今日任务：先复习旧词，再学新词（受每日配额限制） */
    public static final String MODE_TODAY = "TODAY";
    /** 学习新词：随机陌生词，无每日上限 */
    public static final String MODE_NEW_WORDS = "NEW_WORDS";

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

    // 今日任务进度（仅 MODE_TODAY）
    private View layoutTodayProgress;
    private ProgressBar progressTodayTask;
    private TextView tvTodayTaskProgress;
    private int todayTaskTotal;

    // 卡片与操作区域
    private View cardFront;
    private View cardBack;
    private View flipContainer;
    private LinearLayout controlPanel;
    private MaterialCardView wordCardView;

    // 顶部操作按钮
    private ImageButton btnFavorite;
    private ImageButton btnDelete;

    private DatabaseHelper dbHelper;
    private UserSessionManager sessionManager;
    private StudyPlanManager planManager;
    private VocabBookManager vocabBookManager;

    private int currentUserId;
    private String studyMode;
    /** 当前词书标签，空列表表示全词库 */
    private List<String> currentBookTags = new ArrayList<>();

    // 当前卡片是否已经翻到背面
    private boolean isBackVisible = false;

    // 防止动画过程中重复点击卡片
    private boolean isFlipping = false;

    // 当前待学习队列
    private final List<WordItem> studyQueue = new ArrayList<>();

    // 当前正在学习第几个单词
    private int currentIndex = 0;

    // 艾宾浩斯记忆曲线：学习日为第 1 天，复习安排在第 2 / 4 / 8 天
    private static final long ONE_DAY_MS = 24L * 60 * 60 * 1000;
    private static final long SEVEN_DAYS_MS = 7L * ONE_DAY_MS;

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
        planManager = new StudyPlanManager(this);
        vocabBookManager = new VocabBookManager(this);
        currentBookTags = vocabBookManager.getCurrentBook(currentUserId).tags;

        studyMode = getIntent().getStringExtra(EXTRA_STUDY_MODE);
        if (studyMode == null) {
            studyMode = MODE_NEW_WORDS;
        }

        initViews();
        loadStudyQueue();
        setupTodayTaskProgress();
        showCurrentWord();
    }

    /**
     * 今日任务模式下显示本轮队列进度条。
     */
    private void setupTodayTaskProgress() {
        if (MODE_TODAY.equals(studyMode)) {
            todayTaskTotal = studyQueue.size();
            layoutTodayProgress.setVisibility(View.VISIBLE);
            updateTodayTaskProgress();
        } else {
            layoutTodayProgress.setVisibility(View.GONE);
        }
    }

    /**
     * 刷新今日任务进度条。
     */
    private void updateTodayTaskProgress() {
        if (!MODE_TODAY.equals(studyMode) || todayTaskTotal <= 0) {
            return;
        }

        progressTodayTask.setMax(todayTaskTotal);
        progressTodayTask.setProgress(Math.min(currentIndex, todayTaskTotal));
        tvTodayTaskProgress.setText(
                "今日任务 " + currentIndex + " / " + todayTaskTotal
        );
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
        layoutTodayProgress = findViewById(R.id.layoutTodayProgress);
        progressTodayTask = findViewById(R.id.progressTodayTask);
        tvTodayTaskProgress = findViewById(R.id.tvTodayTaskProgress);
        cardFront = findViewById(R.id.layoutCardFront);
        cardBack = findViewById(R.id.layoutCardBack);
        flipContainer = findViewById(R.id.flipContainer);
        controlPanel = findViewById(R.id.controlPanel);
        wordCardView = findViewById(R.id.wordCardView);

        // 顶部操作按钮
        btnFavorite = findViewById(R.id.btnFavorite);
        btnDelete = findViewById(R.id.btnDelete);

        // 设置翻转动画的“镜头距离”，仅作用于下方内容区，单词与音标保持不动
        flipContainer.post(() -> {
            float density = getResources().getDisplayMetrics().density;
            flipContainer.setCameraDistance(8000 * density);
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
     * 按学习模式加载待学队列。
     */
    private void loadStudyQueue() {
        studyQueue.clear();
        currentIndex = 0;

        if (MODE_TODAY.equals(studyMode)) {
            loadTodayTaskQueue();
        } else if (MODE_NEW_WORDS.equals(studyMode)) {
            appendRandomUnfamiliarWord();
        }
    }

    /**
     * 今日任务队列：先加载到期复习词，再加载新词。
     */
    private void loadTodayTaskQueue() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        long now = System.currentTimeMillis();
        planManager.ensureToday(currentUserId);

        int reviewLimit = planManager.getRemainingReviewQuota(currentUserId);

        if (reviewLimit > 0) {
            Cursor reviewCursor = db.rawQuery(
                    "SELECT e.word, e.phonetic, e.translation, s.master_level "
                            + "FROM study_record s "
                            + "JOIN ecdict e ON s.word = e.word "
                            + "WHERE s.user_id = ? "
                            + "AND s.is_ignored = 0 "
                            + "AND s.master_level >= " + StudyTaskHelper.LEVEL_REVIEW_FIRST + " "
                            + "AND s.master_level < " + StudyTaskHelper.LEVEL_MASTERED + " "
                            + "AND s.next_review_time <= ? "
                            + "ORDER BY s.next_review_time ASC "
                            + "LIMIT ?",
                    new String[]{
                            String.valueOf(currentUserId),
                            String.valueOf(now),
                            String.valueOf(reviewLimit)
                    }
            );

            appendWordsFromCursor(reviewCursor);
        }

        int newLimit = planManager.getRemainingNewQuota(currentUserId);

        if (newLimit > 0) {
            int unfamiliarInPlan = countUnfamiliarInPlan(db);

            if (unfamiliarInPlan < newLimit) {
                StudyTaskHelper.generateNewWords(
                        db,
                        currentUserId,
                        newLimit - unfamiliarInPlan,
                        currentBookTags
                );
            }

            Cursor newCursor = db.rawQuery(
                    "SELECT e.word, e.phonetic, e.translation, s.master_level "
                            + "FROM study_record s "
                            + "JOIN ecdict e ON s.word = e.word "
                            + "WHERE s.user_id = ? "
                            + "AND s.is_ignored = 0 "
                            + "AND s.master_level = 0 "
                            + "ORDER BY RANDOM() "
                            + "LIMIT ?",
                    new String[]{
                            String.valueOf(currentUserId),
                            String.valueOf(newLimit)
                    }
            );

            appendWordsFromCursor(newCursor);
        }
    }

    /**
     * 学习新词模式：随机追加一个陌生单词。
     */
    private boolean appendRandomUnfamiliarWord() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        Cursor cursor = db.rawQuery(
                "SELECT e.word, e.phonetic, e.translation, s.master_level "
                        + "FROM study_record s "
                        + "JOIN ecdict e ON s.word = e.word "
                        + "WHERE s.user_id = ? "
                        + "AND s.is_ignored = 0 "
                        + "AND s.master_level = 0 "
                        + "ORDER BY RANDOM() "
                        + "LIMIT 1",
                new String[]{String.valueOf(currentUserId)}
        );

        if (cursor.moveToNext()) {
            studyQueue.add(buildWordItem(cursor));
            cursor.close();
            return true;
        }

        cursor.close();

        if (StudyTaskHelper.generateNewWords(db, currentUserId, 1, currentBookTags) <= 0) {
            return false;
        }

        Cursor retryCursor = db.rawQuery(
                "SELECT e.word, e.phonetic, e.translation, s.master_level "
                        + "FROM study_record s "
                        + "JOIN ecdict e ON s.word = e.word "
                        + "WHERE s.user_id = ? "
                        + "AND s.is_ignored = 0 "
                        + "AND s.master_level = 0 "
                        + "ORDER BY RANDOM() "
                        + "LIMIT 1",
                new String[]{String.valueOf(currentUserId)}
        );

        if (retryCursor.moveToNext()) {
            studyQueue.add(buildWordItem(retryCursor));
            retryCursor.close();
            return true;
        }

        retryCursor.close();
        return false;
    }

    private int countUnfamiliarInPlan(SQLiteDatabase db) {
        return StudyTaskHelper.countUnfamiliarWords(db, currentUserId);
    }

    private void appendWordsFromCursor(Cursor cursor) {
        while (cursor.moveToNext()) {
            studyQueue.add(buildWordItem(cursor));
        }
        cursor.close();
    }

    private WordItem buildWordItem(Cursor cursor) {
        WordItem item = new WordItem();
        item.word = cursor.getString(0);
        item.phonetic = cursor.getString(1);
        item.translation = cursor.getString(2);
        item.level = cursor.getInt(3);
        return item;
    }

    /**
     * 显示当前单词，同时重置卡片到正面状态。
     */
    private void showCurrentWord() {
        if (currentIndex >= studyQueue.size()) {
            if (MODE_NEW_WORDS.equals(studyMode)) {
                if (appendRandomUnfamiliarWord()) {
                    // 继续展示新追加的单词
                } else {
                    Toast.makeText(
                            this,
                            "没有更多可学习的陌生单词",
                            Toast.LENGTH_LONG
                    ).show();
                    finish();
                    return;
                }
            } else if (MODE_TODAY.equals(studyMode)) {
                Toast.makeText(
                        this,
                        "今日的学习任务已完成！",
                        Toast.LENGTH_LONG
                ).show();
                finish();
                return;
            } else {
                Toast.makeText(
                        this,
                        "当前没有待学习单词",
                        Toast.LENGTH_LONG
                ).show();
                finish();
                return;
            }
        }

        if (studyQueue.isEmpty()) {
            Toast.makeText(
                    this,
                    "当前没有待学习单词",
                    Toast.LENGTH_LONG
            ).show();
            finish();
            return;
        }

        WordItem item = studyQueue.get(currentIndex);

        tvWord.setText(safeText(item.word));
        tvPhonetic.setText(formatPhonetic(item.phonetic));

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

        // 今日任务模式更新队列进度
        updateTodayTaskProgress();
    }

    /**
     * 根据 master_level 更新正面徽章、背面说明和进度条颜色。
     * 0=陌生，1-3=学习中三次复习，4=已掌握。
     */
    private void updateLevelIndicator(int rawLevel) {
        int level = Math.max(0, rawLevel);
        int progress = Math.min(3, StudyTaskHelper.isMastered(level) ? 3 : level);

        String badgeText;
        String detailText;
        int textColor;
        int backgroundColor;

        if (StudyTaskHelper.isMastered(level)) {
            badgeText = "已掌握";
            detailText = "三次复习已全部完成，该单词已标记为已掌握";
            textColor = Color.parseColor("#15803D");
            backgroundColor = Color.parseColor("#DCFCE7");
        } else {
            switch (level) {
                case 0:
                    badgeText = "陌生 · 待学习";
                    detailText = "当前掌握程度：陌生词，首次学习后第 2 天进行第 1 次复习";
                    textColor = Color.parseColor("#475569");
                    backgroundColor = Color.parseColor("#E2E8F0");
                    break;

                case StudyTaskHelper.LEVEL_REVIEW_FIRST:
                    badgeText = "学习中 · 第 1 次复习";
                    detailText = "学习后第 2 天复习，答对后第 4 天进行第 2 次复习";
                    textColor = Color.parseColor("#2563EB");
                    backgroundColor = Color.parseColor("#DBEAFE");
                    break;

                case StudyTaskHelper.LEVEL_REVIEW_SECOND:
                    badgeText = "学习中 · 第 2 次复习";
                    detailText = "第 4 天复习，答对后第 8 天进行第 3 次复习";
                    textColor = Color.parseColor("#C2410C");
                    backgroundColor = Color.parseColor("#FFEDD5");
                    break;

                case StudyTaskHelper.LEVEL_REVIEW_THIRD:
                    badgeText = "学习中 · 第 3 次复习";
                    detailText = "第 8 天复习，答对后将标记为已掌握";
                    textColor = Color.parseColor("#7C3AED");
                    backgroundColor = Color.parseColor("#EDE9FE");
                    break;

                default:
                    badgeText = "学习中";
                    detailText = "按记忆曲线复习中";
                    textColor = Color.parseColor("#2563EB");
                    backgroundColor = Color.parseColor("#DBEAFE");
                    break;
            }
        }

        // 正面等级徽章
        tvLevelBadge.setText(badgeText);
        tvLevelBadge.setTextColor(textColor);
        tvLevelBadge.setBackground(createRoundedDrawable(backgroundColor, 18));

        // 背面等级说明
        tvLevelDetail.setText(detailText);
        tvLevelDetail.setTextColor(textColor);
        tvLevelDetail.setBackground(createRoundedDrawable(backgroundColor, 14));

        // 进度条：陌生 0/3，三次复习 1/3、2/3、3/3，已掌握 3/3
        progressLevel.setMax(3);
        progressLevel.setProgress(progress);
        progressLevel.setProgressTintList(ColorStateList.valueOf(textColor));

        tvProgressText.setText(
                StudyTaskHelper.isMastered(level)
                        ? "掌握进度 3 / 3 · 已掌握"
                        : "掌握进度 " + progress + " / 3"
        );
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

        flipContainer.animate().cancel();
        flipContainer.setRotationY(0f);

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
                flipContainer,
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
                flipContainer.setRotationY(-90f);

                ObjectAnimator flipIn = ObjectAnimator.ofFloat(
                        flipContainer,
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
     * 答对后按目标记忆等级安排下次复习间隔（学习日为第 1 天）。
     * 升至 1 级 → 第 2 天，升至 2 级 → 第 4 天，升至 3 级 → 第 8 天。
     */
    private long getSuccessIntervalMs(int targetLevel) {
        switch (targetLevel) {
            case StudyTaskHelper.LEVEL_REVIEW_FIRST:
                return ONE_DAY_MS;
            case StudyTaskHelper.LEVEL_REVIEW_SECOND:
                return 2L * ONE_DAY_MS;
            case StudyTaskHelper.LEVEL_REVIEW_THIRD:
                return 4L * ONE_DAY_MS;
            default:
                return SEVEN_DAYS_MS;
        }
    }

    /**
     * 答错跌落后，按跌落等级的记忆曲线重新安排复习间隔。
     */
    private long getFallIntervalMs(int fallenLevel) {
        switch (fallenLevel) {
            case StudyTaskHelper.LEVEL_UNFAMILIAR:
            case StudyTaskHelper.LEVEL_REVIEW_FIRST:
                return ONE_DAY_MS;
            case StudyTaskHelper.LEVEL_REVIEW_SECOND:
                return 2L * ONE_DAY_MS;
            default:
                return 4L * ONE_DAY_MS;
        }
    }

    /**
     * 答错时按记忆曲线跌落一级，并安排对应级别的复习间隔。
     */
    private int getFallenLevel(int currentLevel) {
        return Math.max(0, currentLevel - 1);
    }

    /**
     * 点击“认识”或“不认识”后：
     *
     * 1. 更新 study_record 中的掌握等级和下次复习时间；
     * 2. 写入 study_history，记录真实学习行为；
     * 3. 更新今日任务进度；
     * 4. 显示下一张单词卡。
     *
     * 记忆曲线：
     * level 0：陌生词
     * level 1：第 1 次复习阶段
     * level 2：第 2 次复习阶段
     * level 3：第 3 次复习阶段
     * level 4：已掌握
     */
    private void processAnswer(boolean knew) {
        if (studyQueue.isEmpty() || currentIndex >= studyQueue.size()) {
            return;
        }

        WordItem item = studyQueue.get(currentIndex);

        /*
         * 保存操作前等级。
         *
         * study_history 需要同时记录：
         * old_level：操作前等级
         * new_level：操作后等级
         */
        int oldLevel = item.level;

        int nextLevel;
        long interval;

        // 点击“认识”
        if (knew) {
            if (StudyTaskHelper.isMastered(item.level)) {
                nextLevel = StudyTaskHelper.LEVEL_MASTERED;
                interval = SEVEN_DAYS_MS;
            } else {
                nextLevel = item.level + 1;

                if (nextLevel >= StudyTaskHelper.LEVEL_MASTERED) {
                    nextLevel = StudyTaskHelper.LEVEL_MASTERED;
                    interval = SEVEN_DAYS_MS;
                } else {
                    interval = getSuccessIntervalMs(nextLevel);
                }
            }
        } else {
            // 点击“不认识”：掌握等级下降一级
            nextLevel = getFallenLevel(item.level);

            interval = getFallIntervalMs(nextLevel);
        }

        long nextReviewTime =
                System.currentTimeMillis() + interval;

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

        boolean savedSuccessfully = false;

        /*
         * 使用事务：
         *
         * study_record 更新成功
         * study_history 写入成功
         *
         * 两者要么同时成功，要么同时失败。
         */
        db.beginTransaction();

        try {
            int rows = db.update(
                    "study_record",
                    values,
                    "user_id = ? AND word = ?",
                    new String[]{
                            String.valueOf(currentUserId),
                            item.word
                    }
            );

            if (rows > 0) {
                savedSuccessfully = true;
            } else {
                /*
                 * 理论上单词已经在 study_record 中。
                 * 这里保留兜底逻辑，避免异常情况导致学习失败。
                 */
                long insertResult = db.insertWithOnConflict(
                        "study_record",
                        null,
                        values,
                        SQLiteDatabase.CONFLICT_IGNORE
                );

                savedSuccessfully = insertResult != -1;
            }

            /*
             * 只有学习状态更新成功后，
             * 才写入真实学习历史。
             */
            if (savedSuccessfully) {
                boolean historySaved =
                        StudyHistoryHelper.recordStudy(
                                db,
                                currentUserId,
                                item.word,
                                knew,
                                oldLevel,
                                nextLevel
                        );

                /*
                 * 学习历史写入失败时，
                 * 不提交事务，避免“等级变了但统计没有记录”。
                 */
                if (!historySaved) {
                    savedSuccessfully = false;
                    return;
                }
            }

            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }

        /*
         * 数据库更新失败时，不切换下一张卡片。
         *
         * 用户可以重新点击一次。
         */
        if (!savedSuccessfully) {
            Toast.makeText(
                    this,
                    "学习记录保存失败，请重试",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        /*
         * 今日任务模式下，继续保留你原来的
         * 新词 / 复习任务计数逻辑。
         */
        if (MODE_TODAY.equals(studyMode)) {
            if (StudyTaskHelper.isLearningInProgress(item.level)) {
                planManager.incrementTodayReviewCompleted(currentUserId);
            } else if (item.level
                    == StudyTaskHelper.LEVEL_UNFAMILIAR) {
                planManager.incrementTodayNewCompleted(currentUserId);
            }
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

    /**
     * 将音标格式化为中括号包裹的形式，例如 [ˈspelɪŋ]。
     */
    private String formatPhonetic(String phonetic) {
        if (phonetic == null || phonetic.trim().isEmpty()) {
            return "[暂无音标]";
        }

        String trimmed = phonetic.trim();

        // 数据库中可能已带括号，避免重复包裹
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            return trimmed;
        }

        return "[" + trimmed + "]";
    }
}