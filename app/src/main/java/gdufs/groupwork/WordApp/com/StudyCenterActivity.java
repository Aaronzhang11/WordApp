package gdufs.groupwork.WordApp.com;

import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

/**
 * 学习中心页面：今日学习任务、学习新词、复习旧词、设置四个模块。
 */
public class StudyCenterActivity extends AppCompatActivity {

    private TextView tvStartTodayDescription;
    private TextView tvReviewProgressLabel;
    private TextView tvNewProgressLabel;
    private ProgressBar progressTodayReview;
    private ProgressBar progressTodayNew;
    private TextView tvLearnNewDescription;
    private TextView tvReviewOldDescription;
    private TextView tvSettingsDescription;

    private MaterialButton btnStartToday;
    private MaterialButton btnLearnNew;
    private MaterialButton btnReviewOld;

    private DatabaseHelper dbHelper;
    private UserSessionManager sessionManager;
    private StudyPlanManager planManager;
    private VocabBookManager vocabBookManager;

    private int currentUserId;
    private List<String> currentBookTags = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_study_center);

        dbHelper = new DatabaseHelper(this);
        sessionManager = new UserSessionManager(this);
        planManager = new StudyPlanManager(this);
        vocabBookManager = new VocabBookManager(this);

        if (!sessionManager.isLoggedIn()) {
            goToLogin();
            return;
        }

        currentUserId = sessionManager.getCurrentUserId();

        initViews();
        refreshLearningInfo();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (dbHelper != null && sessionManager != null && sessionManager.isLoggedIn()) {
            refreshLearningInfo();
        }
    }

    /**
     * 绑定四个功能模块的控件与点击事件。
     */
    private void initViews() {
        tvStartTodayDescription = findViewById(R.id.tvStartTodayDescription);
        tvReviewProgressLabel = findViewById(R.id.tvReviewProgressLabel);
        tvNewProgressLabel = findViewById(R.id.tvNewProgressLabel);
        progressTodayReview = findViewById(R.id.progressTodayReview);
        progressTodayNew = findViewById(R.id.progressTodayNew);
        tvLearnNewDescription = findViewById(R.id.tvLearnNewDescription);
        tvReviewOldDescription = findViewById(R.id.tvReviewOldDescription);
        tvSettingsDescription = findViewById(R.id.tvSettingsDescription);

        btnStartToday = findViewById(R.id.btnStartToday);
        btnLearnNew = findViewById(R.id.btnLearnNew);
        btnReviewOld = findViewById(R.id.btnReviewOld);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        btnStartToday.setOnClickListener(v -> startTodayTask());
        btnLearnNew.setOnClickListener(v -> startLearnNewWords());
        btnReviewOld.setOnClickListener(v -> startReviewOldWords());

        findViewById(R.id.btnSettings).setOnClickListener(v -> {
            Intent intent = new Intent(StudyCenterActivity.this, StudySettingsActivity.class);
            startActivity(intent);
        });
    }

    /**
     * 刷新今日进度与各模块说明文字。
     */
    private void refreshLearningInfo() {
        planManager.ensureToday(currentUserId);
        currentBookTags = vocabBookManager.getCurrentBook(currentUserId).tags;

        SQLiteDatabase db = dbHelper.getReadableDatabase();
        long now = System.currentTimeMillis();

        int dailyNew = planManager.getDailyNewWordCount(currentUserId);
        int dailyReview = planManager.getDailyReviewLimit(currentUserId);
        int todayNewDone = planManager.getTodayNewCompleted(currentUserId);
        int todayReviewDone = planManager.getTodayReviewCompleted(currentUserId);
        int remainingNew = planManager.getRemainingNewQuota(currentUserId);
        int remainingReview = planManager.getRemainingReviewQuota(currentUserId);

        int dueReviewCount = StudyTaskHelper.countDueReviewWords(db, currentUserId, now);
        int unfamiliarCount = StudyTaskHelper.countUnfamiliarWords(db, currentUserId);
        int availableNewCount = StudyTaskHelper.countAvailableNewWords(
                db, currentUserId, currentBookTags
        );
        int learnedCount = StudyTaskHelper.countLearnedWords(db, currentUserId);

        if (planManager.isTodayTaskComplete(currentUserId, dueReviewCount)) {
            tvStartTodayDescription.setText("今日学习任务已全部完成，可以继续自由学习");
        } else {
            tvStartTodayDescription.setText(
                    "先复习学习中的单词（第 2/4/8 天到期，最多 " + remainingReview + " 个），"
                            + "再学习新词（最多 " + remainingNew + " 个）"
            );
        }

        updateTodayProgressBars(
                todayReviewDone,
                dailyReview,
                todayNewDone,
                dailyNew
        );

        tvLearnNewDescription.setText(
                "随机抽取陌生单词卡片记忆 · 计划中陌生词 "
                        + unfamiliarCount + " 个 · 词库可学 "
                        + availableNewCount + " 个 · 无上限"
        );

        tvReviewOldDescription.setText(
                "看英选中随机复习 · 已学单词 "
                        + learnedCount + " 个 · 无上限 · 答对不重复"
        );

        tvSettingsDescription.setText(
                "每日新词 " + dailyNew + " 个 · 复习上限 " + dailyReview + " 个"
        );

        boolean todayAvailable = !planManager.isTodayTaskComplete(currentUserId, dueReviewCount)
                && (remainingReview > 0 || remainingNew > 0);

        btnStartToday.setEnabled(todayAvailable);
        btnStartToday.setAlpha(todayAvailable ? 1f : 0.55f);

        if (planManager.isTodayTaskComplete(currentUserId, dueReviewCount)) {
            btnStartToday.setText("今日任务已完成");
        } else if (remainingReview <= 0 && remainingNew > 0) {
            btnStartToday.setText("今日学习任务（仅新词 " + remainingNew + " 个）");
        } else if (remainingReview > 0 && remainingNew <= 0) {
            btnStartToday.setText("今日学习任务（仅复习 " + Math.min(remainingReview, dueReviewCount) + " 个）");
        } else {
            btnStartToday.setText("今日学习任务");
        }

        boolean canLearnNew = unfamiliarCount > 0 || availableNewCount > 0;
        btnLearnNew.setEnabled(canLearnNew);
        btnLearnNew.setAlpha(canLearnNew ? 1f : 0.55f);

        boolean canReviewOld = learnedCount > 0;
        btnReviewOld.setEnabled(canReviewOld);
        btnReviewOld.setAlpha(canReviewOld ? 1f : 0.55f);
    }

    /**
     * 更新「今日学习任务」模块中的复习 / 新词进度条。
     */
    private void updateTodayProgressBars(
            int reviewDone,
            int reviewTotal,
            int newDone,
            int newTotal
    ) {
        progressTodayReview.setMax(Math.max(1, reviewTotal));
        progressTodayReview.setProgress(Math.min(reviewDone, reviewTotal));
        tvReviewProgressLabel.setText("复习进度 " + reviewDone + "/" + reviewTotal);

        progressTodayNew.setMax(Math.max(1, newTotal));
        progressTodayNew.setProgress(Math.min(newDone, newTotal));
        tvNewProgressLabel.setText("新词进度 " + newDone + "/" + newTotal);
    }

    /**
     * 开始今日学习任务：先复习旧词，后学习新词。
     */
    private void startTodayTask() {
        planManager.ensureToday(currentUserId);

        SQLiteDatabase db = dbHelper.getReadableDatabase();
        long now = System.currentTimeMillis();
        int dueReview = StudyTaskHelper.countDueReviewWords(db, currentUserId, now);

        if (planManager.isTodayTaskComplete(currentUserId, dueReview)) {
            Toast.makeText(this, "今日学习任务已全部完成", Toast.LENGTH_SHORT).show();
            refreshLearningInfo();
            return;
        }

        int remainingReview = planManager.getRemainingReviewQuota(currentUserId);
        int remainingNew = planManager.getRemainingNewQuota(currentUserId);

        if (remainingReview <= 0 && remainingNew <= 0) {
            Toast.makeText(this, "今日学习任务已全部完成", Toast.LENGTH_SHORT).show();
            refreshLearningInfo();
            return;
        }

        if (remainingReview <= 0 && remainingNew > 0) {
            int generated = ensureNewWordsForToday(db, remainingNew);
            if (generated <= 0 && StudyTaskHelper.countUnfamiliarWords(db, currentUserId) <= 0) {
                Toast.makeText(this, "词库中没有可学习的新词", Toast.LENGTH_LONG).show();
                refreshLearningInfo();
                return;
            }
        } else if (remainingReview > 0 && dueReview <= 0 && remainingNew <= 0) {
            Toast.makeText(this, "当前没有到期的学习中单词，今日复习任务已完成", Toast.LENGTH_SHORT).show();
            refreshLearningInfo();
            return;
        } else if (remainingReview > 0 && dueReview <= 0 && remainingNew > 0) {
            ensureNewWordsForToday(db, remainingNew);
        }

        Intent intent = new Intent(this, StudyActivity.class);
        intent.putExtra(StudyActivity.EXTRA_STUDY_MODE, StudyActivity.MODE_TODAY);
        startActivity(intent);
    }

    /**
     * 学习新词：无每日上限，随机抽取陌生单词。
     */
    private void startLearnNewWords() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        int unfamiliar = StudyTaskHelper.countUnfamiliarWords(db, currentUserId);
        int available = StudyTaskHelper.countAvailableNewWords(db, currentUserId, currentBookTags);

        if (unfamiliar <= 0 && available <= 0) {
            Toast.makeText(this, "没有可学习的陌生单词", Toast.LENGTH_LONG).show();
            refreshLearningInfo();
            return;
        }

        if (unfamiliar <= 0) {
            StudyTaskHelper.generateNewWords(db, currentUserId, 1, currentBookTags);
        }

        Intent intent = new Intent(this, StudyActivity.class);
        intent.putExtra(StudyActivity.EXTRA_STUDY_MODE, StudyActivity.MODE_NEW_WORDS);
        startActivity(intent);
    }

    /**
     * 复习旧词：进入独立复习页，看英选中，不增加熟练度。
     */
    private void startReviewOldWords() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        if (StudyTaskHelper.countLearnedWords(db, currentUserId) <= 0) {
            Toast.makeText(this, "还没有已学单词，请先学习新词", Toast.LENGTH_LONG).show();
            refreshLearningInfo();
            return;
        }

        Intent intent = new Intent(this, ReviewOldActivity.class);
        startActivity(intent);
    }

    /**
     * 为今日新词任务预生成不足的新词。
     */
    private int ensureNewWordsForToday(SQLiteDatabase db, int needed) {
        int unfamiliar = StudyTaskHelper.countUnfamiliarWords(db, currentUserId);

        if (unfamiliar >= needed) {
            return 0;
        }

        return StudyTaskHelper.generateNewWords(
                db, currentUserId, needed - unfamiliar, currentBookTags
        );
    }

    private void goToLogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
