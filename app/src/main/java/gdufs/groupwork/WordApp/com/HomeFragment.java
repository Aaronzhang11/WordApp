package gdufs.groupwork.WordApp.com;

import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;

import java.util.List;

/**
 * 首页模块：顶部信息、学习进度、数据看板、开始学习入口。
 */
public class HomeFragment extends Fragment {

    private TextView tvCurrentBookName;
    private TextView tvBookWordCount;
    private TextView tvBookProgress;
    private ProgressBar progressBookLearned;
    private TextView tvTodayReviewProgress;
    private TextView tvTodayNewProgress;
    private TextView tvTodayTaskStatus;
    private TextView tvTodayDueHint;
    private ProgressBar progressTodayReview;
    private ProgressBar progressTodayNew;
    private MaterialButton btnProfileAvatar;

    private DatabaseHelper dbHelper;
    private UserSessionManager sessionManager;
    private VocabBookManager vocabBookManager;
    private StudyPlanManager planManager;

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        sessionManager = new UserSessionManager(requireContext());
        dbHelper = new DatabaseHelper(requireContext());
        vocabBookManager = new VocabBookManager(requireContext());
        planManager = new StudyPlanManager(requireContext());

        initViews(view);
        loadVocabBookInfo();
        loadTodayProgress();
        loadDashboard();
    }

    @Override
    public void onResume() {
        super.onResume();

        if (dbHelper != null && sessionManager != null && sessionManager.isLoggedIn()) {
            loadVocabBookInfo();
            loadTodayProgress();
            loadDashboard();

            if (btnProfileAvatar != null) {
                btnProfileAvatar.setText(
                        getAvatarText(sessionManager.getCurrentUsername())
                );
            }
        }
    }

    /**
     * 绑定首页控件与点击事件。
     */
    private void initViews(View view) {
        tvCurrentBookName = view.findViewById(R.id.tvCurrentBookName);
        tvBookWordCount = view.findViewById(R.id.tvBookWordCount);
        tvBookProgress = view.findViewById(R.id.tvBookProgress);
        progressBookLearned = view.findViewById(R.id.progressBookLearned);
        tvTodayReviewProgress = view.findViewById(R.id.tvTodayReviewProgress);
        tvTodayNewProgress = view.findViewById(R.id.tvTodayNewProgress);
        tvTodayTaskStatus = view.findViewById(R.id.tvTodayTaskStatus);
        tvTodayDueHint = view.findViewById(R.id.tvTodayDueHint);
        progressTodayReview = view.findViewById(R.id.progressTodayReview);
        progressTodayNew = view.findViewById(R.id.progressTodayNew);
        btnProfileAvatar = view.findViewById(R.id.btnProfileAvatar);

        String username = sessionManager.getCurrentUsername();

        btnProfileAvatar.setText(getAvatarText(username));
        btnProfileAvatar.setContentDescription("打开用户信息：" + username);

        btnProfileAvatar.setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), UserProfileActivity.class);
            startActivity(intent);
        });

        view.findViewById(R.id.btnStudy).setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), StudyCenterActivity.class);
            startActivity(intent);
        });

        view.findViewById(R.id.btnVocabBookSettings).setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), VocabBookSettingsActivity.class);
            startActivity(intent);
        });

        view.findViewById(R.id.btnShowTrend).setOnClickListener(v -> showTrendDialog());
    }

    /**
     * 加载当前词书名称、词数与学习进度。
     */
    private void loadVocabBookInfo() {
        if (tvCurrentBookName == null || vocabBookManager == null || sessionManager == null) {
            return;
        }

        int userId = sessionManager.getCurrentUserId();
        VocabBookManager.VocabBook book = vocabBookManager.getCurrentBook(userId);

        tvCurrentBookName.setText(book.bookName);
        tvBookWordCount.setText(String.valueOf(book.wordCount));
        tvBookProgress.setText(book.learnedCount + " / " + book.wordCount);

        int percent = 0;

        if (book.wordCount > 0) {
            percent = (int) (book.learnedCount * 100L / book.wordCount);
        }

        progressBookLearned.setProgress(percent);
    }

    /**
     * 加载今日学习任务进度：复习 / 新词配额与到期待复习提示。
     */
    private void loadTodayProgress() {
        if (planManager == null || sessionManager == null || tvTodayReviewProgress == null) {
            return;
        }

        int userId = sessionManager.getCurrentUserId();
        planManager.ensureToday(userId);

        int reviewDone = planManager.getTodayReviewCompleted(userId);
        int reviewTotal = planManager.getDailyReviewLimit(userId);
        int newDone = planManager.getTodayNewCompleted(userId);
        int newTotal = planManager.getDailyNewWordCount(userId);
        int remainingReview = planManager.getRemainingReviewQuota(userId);
        int remainingNew = planManager.getRemainingNewQuota(userId);

        tvTodayReviewProgress.setText(reviewDone + "/" + reviewTotal);
        progressTodayReview.setMax(Math.max(1, reviewTotal));
        progressTodayReview.setProgress(Math.min(reviewDone, reviewTotal));

        tvTodayNewProgress.setText(newDone + "/" + newTotal);
        progressTodayNew.setMax(Math.max(1, newTotal));
        progressTodayNew.setProgress(Math.min(newDone, newTotal));

        SQLiteDatabase db = dbHelper.getReadableDatabase();
        long now = System.currentTimeMillis();
        int dueReviewCount = countDueReviewInCurrentBook(db, userId, now);

        tvTodayDueHint.setText(
                "到期待复习 " + dueReviewCount + " 词 · 剩余配额 复习 "
                        + remainingReview + " · 新词 " + remainingNew
        );

        if (planManager.isTodayTaskComplete(userId)) {
            tvTodayTaskStatus.setText("已完成");
            tvTodayTaskStatus.setVisibility(View.VISIBLE);
        } else {
            tvTodayTaskStatus.setVisibility(View.GONE);
        }
    }

    /**
     * 统计当前词书内到期待复习的单词数量。
     */
    private int countDueReviewInCurrentBook(SQLiteDatabase db, int userId, long now) {
        VocabBookManager.VocabBook book = vocabBookManager.getCurrentBook(userId);
        VocabBookManager.TagFilter filter = VocabBookManager.buildTagFilter(book.tags, "e");

        String sql = "SELECT COUNT(*) FROM study_record s "
                + "JOIN ecdict e ON s.word = e.word "
                + "WHERE s.user_id = ? AND s.is_ignored = 0 "
                + "AND s.master_level > 0 AND s.next_review_time <= ? AND "
                + filter.whereClause;

        String[] args = new String[filter.args.length + 2];
        args[0] = String.valueOf(userId);
        args[1] = String.valueOf(now);
        System.arraycopy(filter.args, 0, args, 2, filter.args.length);

        Cursor cursor = db.rawQuery(sql, args);
        int count = 0;

        if (cursor.moveToFirst()) {
            count = cursor.getInt(0);
        }

        cursor.close();
        return count;
    }

    /**
     * 加载并展示内嵌数据看板。
     */
    private void loadDashboard() {
        View root = getView();

        if (root == null) {
            return;
        }

        StatisticsHelper helper = new StatisticsHelper(requireContext());
        DashboardData data = helper.getDashboardData();

        ((TextView) root.findViewById(R.id.tv_mastered)).setText(String.valueOf(data.mastered));
        ((TextView) root.findViewById(R.id.tv_learning)).setText(String.valueOf(data.learning));
        ((TextView) root.findViewById(R.id.tv_unlearned)).setText(String.valueOf(data.unlearned));
        ((TextView) root.findViewById(R.id.tv_today)).setText(String.valueOf(data.todayNew));
        ((TextView) root.findViewById(R.id.tv_consecutive))
                .setText(String.valueOf(data.consecutiveDays));
    }

    /**
     * 弹窗展示近 7 天学习趋势图。
     */
    private void showTrendDialog() {
        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_trend_chart, null, false);

        StatisticsHelper helper = new StatisticsHelper(requireContext());
        DashboardData data = helper.getDashboardData();
        List<Integer> counts = data.last7DaysCount;

        TrendChartHelper.TrendSummary summary = TrendChartHelper.buildSummary(counts);

        TextView tvTrendTotal = dialogView.findViewById(R.id.tvTrendTotal);
        TextView tvTrendAverage = dialogView.findViewById(R.id.tvTrendAverage);
        TextView tvTrendPeak = dialogView.findViewById(R.id.tvTrendPeak);
        TextView tvTrendEmpty = dialogView.findViewById(R.id.tvTrendEmpty);
        LinearLayout chartContainer = dialogView.findViewById(R.id.ll_trend_dialog);

        tvTrendTotal.setText(String.valueOf(summary.total));
        tvTrendAverage.setText(String.valueOf(summary.average));
        tvTrendPeak.setText(String.valueOf(summary.peak));

        VocabBookManager.VocabBook book = vocabBookManager.getCurrentBook(
                sessionManager.getCurrentUserId()
        );
        ((TextView) dialogView.findViewById(R.id.tvTrendSubtitle))
                .setText("按当前词书「" + book.bookName + "」统计");

        if (summary.total <= 0) {
            chartContainer.setVisibility(View.GONE);
            tvTrendEmpty.setVisibility(View.VISIBLE);
        } else {
            chartContainer.setVisibility(View.VISIBLE);
            tvTrendEmpty.setVisibility(View.GONE);
            TrendChartHelper.render(requireContext(), chartContainer, counts);
        }

        new AlertDialog.Builder(requireContext())
                .setView(dialogView)
                .setPositiveButton("关闭", null)
                .show();
    }

    /**
     * 根据用户名生成头像缩写文字。
     */
    private String getAvatarText(String username) {
        if (username == null || username.trim().isEmpty()) {
            return "?";
        }

        String value = username.trim();
        int count = value.codePointCount(0, value.length());

        if (count <= 2) {
            return value;
        }

        int startIndex = value.offsetByCodePoints(0, count - 2);
        return value.substring(startIndex);
    }
}
