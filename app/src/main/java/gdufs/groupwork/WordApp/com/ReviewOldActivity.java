package gdufs.groupwork.WordApp.com;

import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.text.method.TransformationMethod;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 复习旧词页面：看英选中，独立实现。
 *
 * 规则：
 * 1. 随机抽取学习中 / 已掌握单词（master_level > 0）；
 * 2. 无题目上限，可持续复习；
 * 3. 本轮答对的单词不再出现；
 * 4. 退出后本轮进度清空，不更新熟练度。
 */
public class ReviewOldActivity extends AppCompatActivity {

    private TextView tvSessionStats;
    private TextView tvQuestionContent;
    private Button btnOptionA;
    private Button btnOptionB;
    private Button btnOptionC;
    private Button btnOptionD;

    private DatabaseHelper dbHelper;
    private UserSessionManager sessionManager;

    private int currentUserId;

    /** 本轮已答对的单词，退出后清空 */
    private final Set<String> correctWordsInSession = new HashSet<>();

    private int sessionCorrectCount;
    private int sessionWrongCount;

    /** 当前题目数据 */
    private String currentWord;
    private String currentCorrectOption;

    private static class ReviewQuestion {
        String word;
        String correctOption;
        List<String> options = new ArrayList<>();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_review_old);

        dbHelper = new DatabaseHelper(this);
        sessionManager = new UserSessionManager(this);

        if (!sessionManager.isLoggedIn()) {
            goToLogin();
            return;
        }

        currentUserId = sessionManager.getCurrentUserId();

        initViews();
        updateSessionStats();

        if (!loadNextQuestion()) {
            return;
        }

        showCurrentQuestion();
    }

    /**
     * 绑定界面控件与事件。
     */
    private void initViews() {
        tvSessionStats = findViewById(R.id.tvSessionStats);
        tvQuestionContent = findViewById(R.id.tvQuestionContent);
        btnOptionA = findViewById(R.id.btnOptionA);
        btnOptionB = findViewById(R.id.btnOptionB);
        btnOptionC = findViewById(R.id.btnOptionC);
        btnOptionD = findViewById(R.id.btnOptionD);

        disableButtonAllCaps(btnOptionA);
        disableButtonAllCaps(btnOptionB);
        disableButtonAllCaps(btnOptionC);
        disableButtonAllCaps(btnOptionD);
        disableButtonAllCaps(findViewById(R.id.btnExitReview));

        View.OnClickListener optionListener = v -> {
            Button button = (Button) v;
            checkAnswer(button.getText().toString());
        };

        btnOptionA.setOnClickListener(optionListener);
        btnOptionB.setOnClickListener(optionListener);
        btnOptionC.setOnClickListener(optionListener);
        btnOptionD.setOnClickListener(optionListener);

        findViewById(R.id.btnExitReview).setOnClickListener(v -> showExitConfirmDialog());
    }

    /**
     * 随机加载下一题；排除本轮已答对的单词。
     */
    private boolean loadNextQuestion() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        ReviewQuestion question = pickRandomQuestion(db);

        if (question == null) {
            if (correctWordsInSession.isEmpty()) {
                Toast.makeText(this, "没有可复习的单词", Toast.LENGTH_LONG).show();
                finish();
            } else {
                showAllCorrectDialog();
            }
            return false;
        }

        currentWord = question.word;
        currentCorrectOption = question.correctOption;

        tvQuestionContent.setText(currentWord);
        btnOptionA.setText(question.options.get(0));
        btnOptionB.setText(question.options.get(1));
        btnOptionC.setText(question.options.get(2));
        btnOptionD.setText(question.options.get(3));

        return true;
    }

    /**
     * 展示当前题目（加载后刷新统计）。
     */
    private void showCurrentQuestion() {
        updateSessionStats();
    }

    /**
     * 从学习中 / 已掌握词库随机抽一题。
     */
    private ReviewQuestion pickRandomQuestion(SQLiteDatabase db) {
        String sql =
                "SELECT e.word, e.translation "
                        + "FROM study_record s "
                        + "JOIN ecdict e ON s.word = e.word "
                        + "WHERE s.user_id = ? "
                        + "AND s.is_ignored = 0 "
                        + "AND s.master_level > 0 ";

        List<String> args = new ArrayList<>();
        args.add(String.valueOf(currentUserId));

        if (!correctWordsInSession.isEmpty()) {
            sql += "AND s.word NOT IN (";
            sql += buildPlaceholders(correctWordsInSession.size());
            sql += ") ";
            args.addAll(correctWordsInSession);
        }

        sql += "ORDER BY RANDOM() LIMIT 1";

        Cursor cursor = db.rawQuery(sql, args.toArray(new String[0]));

        ReviewQuestion question = null;

        if (cursor.moveToFirst()) {
            String word = safeText(cursor.getString(0));
            String translation = safeText(cursor.getString(1));

            if (!word.isEmpty() && !translation.isEmpty()) {
                question = buildQuestion(db, word, translation);
            }
        }

        cursor.close();
        return question;
    }

    /**
     * 构建看英选中题目与四个选项。
     */
    private ReviewQuestion buildQuestion(SQLiteDatabase db, String word, String translation) {
        ReviewQuestion question = new ReviewQuestion();
        question.word = word;
        question.correctOption = compactTranslation(translation);
        question.options.add(question.correctOption);

        appendDistractors(db, question);

        if (question.options.size() < 4) {
            return null;
        }

        Collections.shuffle(question.options);
        return question;
    }

    /**
     * 从词库补充干扰项。
     */
    private void appendDistractors(SQLiteDatabase db, ReviewQuestion question) {
        Cursor cursor = db.rawQuery(
                "SELECT translation FROM ecdict "
                        + "WHERE word != ? "
                        + "AND translation IS NOT NULL "
                        + "AND TRIM(translation) != '' "
                        + "ORDER BY RANDOM() LIMIT 80",
                new String[]{question.word}
        );

        while (cursor.moveToNext() && question.options.size() < 4) {
            String candidate = compactTranslation(cursor.getString(0));

            if (!candidate.isEmpty()
                    && !candidate.equals(question.correctOption)
                    && !question.options.contains(candidate)) {
                question.options.add(candidate);
            }
        }

        cursor.close();
    }

    /**
     * 校验答案并进入下一题。
     */
    private void checkAnswer(String selectedOption) {
        if (currentWord == null) {
            return;
        }

        if (currentCorrectOption.equals(selectedOption.trim())) {
            sessionCorrectCount++;
            correctWordsInSession.add(currentWord);

            Toast.makeText(this, "回答正确！", Toast.LENGTH_SHORT).show();
        } else {
            sessionWrongCount++;

            Toast.makeText(
                    this,
                    "回答错误，正确答案是：" + currentCorrectOption,
                    Toast.LENGTH_SHORT
            ).show();
        }

        currentWord = null;
        currentCorrectOption = null;

        if (!loadNextQuestion()) {
            return;
        }

        showCurrentQuestion();
    }

    /**
     * 更新本轮复习统计显示。
     */
    private void updateSessionStats() {
        int remaining = countRemainingReviewWords();

        tvSessionStats.setText(
                "本轮已答对 " + sessionCorrectCount + " 题"
                        + " · 待复习 " + remaining + " 词"
        );
    }

    /**
     * 统计本轮仍可出现的单词数量。
     */
    private int countRemainingReviewWords() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        String sql =
                "SELECT COUNT(*) FROM study_record "
                        + "WHERE user_id = ? "
                        + "AND is_ignored = 0 "
                        + "AND master_level > 0 ";

        List<String> args = new ArrayList<>();
        args.add(String.valueOf(currentUserId));

        if (!correctWordsInSession.isEmpty()) {
            sql += "AND word NOT IN (";
            sql += buildPlaceholders(correctWordsInSession.size());
            sql += ") ";
            args.addAll(correctWordsInSession);
        }

        Cursor cursor = db.rawQuery(sql, args.toArray(new String[0]));
        int count = 0;

        if (cursor.moveToFirst()) {
            count = cursor.getInt(0);
        }

        cursor.close();
        return count;
    }

    /**
     * 本轮所有可复习词均已答对。
     */
    private void showAllCorrectDialog() {
        new AlertDialog.Builder(this)
                .setTitle("本轮复习完成")
                .setMessage(
                        "已答对 " + sessionCorrectCount + " 题"
                                + "，答错 " + sessionWrongCount + " 题。\n\n"
                                + "所有可复习单词本轮均已答对。"
                )
                .setCancelable(false)
                .setPositiveButton("返回学习中心", (dialog, which) -> finish())
                .show();
    }

    /**
     * 退出确认：本轮进度不保留。
     */
    private void showExitConfirmDialog() {
        new AlertDialog.Builder(this)
                .setTitle("退出复习")
                .setMessage(
                        "退出后，本轮复习进度将清空（已答对 "
                                + sessionCorrectCount + " 题不会保留）。\n\n"
                                + "是否确认退出？"
                )
                .setNegativeButton("继续复习", null)
                .setPositiveButton("确认退出", (dialog, which) -> {
                    clearSession();
                    finish();
                })
                .show();
    }

    /**
     * 清空本轮内存进度。
     */
    private void clearSession() {
        correctWordsInSession.clear();
        sessionCorrectCount = 0;
        sessionWrongCount = 0;
        currentWord = null;
        currentCorrectOption = null;
    }

    @Override
    public void onBackPressed() {
        showExitConfirmDialog();
    }

    @Override
    protected void onDestroy() {
        clearSession();
        super.onDestroy();
    }

    private String buildPlaceholders(int count) {
        StringBuilder builder = new StringBuilder();

        for (int i = 0; i < count; i++) {
            if (i > 0) {
                builder.append(",");
            }
            builder.append("?");
        }

        return builder.toString();
    }

    private void disableButtonAllCaps(Button button) {
        if (button == null) {
            return;
        }

        button.setAllCaps(false);

        TransformationMethod method = button.getTransformationMethod();

        if (method != null) {
            button.setTransformationMethod(null);
        }
    }

    private String safeText(String value) {
        return value == null ? "" : value.trim();
    }

    /**
     * 压缩释义为选项文本，避免按钮溢出。
     */
    private String compactTranslation(String translation) {
        if (translation == null || translation.trim().isEmpty()) {
            return "暂无释义";
        }

        String formatted = translation.trim()
                .replace("\\r", "")
                .replace("\\n", "\n");

        String[] lines = formatted.split("\n");
        String firstLine = "";

        for (String line : lines) {
            if (line != null && !line.trim().isEmpty()) {
                firstLine = line.trim();
                break;
            }
        }

        if (firstLine.isEmpty()) {
            firstLine = formatted.replace("\n", " ").trim();
        }

        if (firstLine.length() > 42) {
            return firstLine.substring(0, 42) + "…";
        }

        return firstLine;
    }

    private void goToLogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
