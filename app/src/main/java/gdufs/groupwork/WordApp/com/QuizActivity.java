package gdufs.groupwork.WordApp.com;

import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.text.method.TransformationMethod;
import android.util.TypedValue;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class QuizActivity extends AppCompatActivity {

    public static final String EXTRA_QUIZ_MODE = "quiz_mode";

    public static final String MODE_CHINESE_TO_ENGLISH = "CHINESE_TO_ENGLISH";
    public static final String MODE_ENGLISH_TO_CHINESE = "ENGLISH_TO_CHINESE";

    private static final int QUIZ_QUESTION_COUNT = 10;

    private ProgressBar progressBar;
    private TextView tvQuestionTitle;
    private TextView tvQuestionContent;
    private LinearLayout layoutOptions;

    private Button btnOptionA;
    private Button btnOptionB;
    private Button btnOptionC;
    private Button btnOptionD;
    private Button btnExitQuiz;

    private DatabaseHelper dbHelper;
    private UserSessionManager sessionManager;

    private int currentUserId;
    private String quizMode;

    private final List<Question> questionList = new ArrayList<>();

    private int currentQuizIndex = 0;
    private int score = 0;

    private boolean testRecordSaved = false;
    private boolean resultDialogShown = false;
    private boolean quizCancelled = false;

    private static class Question {
        String answerWord;
        String questionClue;
        String correctOption;
        List<String> options = new ArrayList<>();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_quiz);

        dbHelper = new DatabaseHelper(this);
        sessionManager = new UserSessionManager(this);

        if (!sessionManager.isLoggedIn()) {
            Intent intent = new Intent(QuizActivity.this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
            return;
        }

        currentUserId = sessionManager.getCurrentUserId();

        quizMode = getIntent().getStringExtra(EXTRA_QUIZ_MODE);

        if (!MODE_ENGLISH_TO_CHINESE.equals(quizMode)) {
            quizMode = MODE_CHINESE_TO_ENGLISH;
        }

        initViews();

        if (!generateQuizSheet()) {
            return;
        }

        showQuestion();
    }

    private void initViews() {
        progressBar = findViewById(R.id.progressBar);
        tvQuestionTitle = findViewById(R.id.tvQuestionTitle);
        tvQuestionContent = findViewById(R.id.tvQuestionContent);
        layoutOptions = findViewById(R.id.layoutOptions);

        btnOptionA = findViewById(R.id.btnOptionA);
        btnOptionB = findViewById(R.id.btnOptionB);
        btnOptionC = findViewById(R.id.btnOptionC);
        btnOptionD = findViewById(R.id.btnOptionD);
        btnExitQuiz = findViewById(R.id.btnExitQuiz);

        disableButtonAllCaps(btnOptionA);
        disableButtonAllCaps(btnOptionB);
        disableButtonAllCaps(btnOptionC);
        disableButtonAllCaps(btnOptionD);
        disableButtonAllCaps(btnExitQuiz);

        View.OnClickListener optionClickListener = v -> {
            Button button = (Button) v;
            checkChoiceAnswer(button.getText().toString());
        };

        btnOptionA.setOnClickListener(optionClickListener);
        btnOptionB.setOnClickListener(optionClickListener);
        btnOptionC.setOnClickListener(optionClickListener);
        btnOptionD.setOnClickListener(optionClickListener);

        btnExitQuiz.setOnClickListener(v -> showExitConfirmDialog());
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

    private boolean generateQuizSheet() {
        questionList.clear();

        SQLiteDatabase db = dbHelper.getReadableDatabase();

        Cursor cursor = db.rawQuery(
                "SELECT e.word, e.translation " +
                        "FROM study_record s " +
                        "JOIN ecdict e ON s.word = e.word " +
                        "WHERE s.user_id = ? " +
                        "AND s.master_level > 0 " +
                        "AND s.is_ignored = 0 " +
                        "ORDER BY RANDOM() " +
                        "LIMIT " + QUIZ_QUESTION_COUNT,
                new String[]{String.valueOf(currentUserId)}
        );

        if (cursor.getCount() < QUIZ_QUESTION_COUNT) {
            cursor.close();

            Toast.makeText(
                    this,
                    "已学习单词不足 10 个，请先去背诵单词",
                    Toast.LENGTH_LONG
            ).show();

            finish();
            return false;
        }

        while (cursor.moveToNext()) {
            String word = safeText(cursor.getString(0));
            String translation = safeText(cursor.getString(1));

            if (word.isEmpty() || translation.isEmpty()) {
                continue;
            }

            Question question = new Question();
            question.answerWord = word;

            if (MODE_CHINESE_TO_ENGLISH.equals(quizMode)) {
                question.questionClue = formatQuestionClue(translation);
                question.correctOption = word;
                question.options.add(word);
            } else {
                question.questionClue = word;
                question.correctOption = compactTranslation(translation);
                question.options.add(question.correctOption);
            }

            appendDistractorOptions(db, question);

            if (question.options.size() == 4) {
                Collections.shuffle(question.options);
                questionList.add(question);
            }
        }

        cursor.close();

        if (questionList.size() < QUIZ_QUESTION_COUNT) {
            Toast.makeText(
                    this,
                    "测试题生成失败，请稍后重试",
                    Toast.LENGTH_LONG
            ).show();

            finish();
            return false;
        }

        progressBar.setMax(questionList.size());

        return true;
    }

    private void appendDistractorOptions(SQLiteDatabase db, Question question) {
        Cursor fakeCursor = db.rawQuery(
                "SELECT word, translation " +
                        "FROM ecdict " +
                        "WHERE word != ? " +
                        "AND translation IS NOT NULL " +
                        "AND TRIM(translation) != '' " +
                        "ORDER BY RANDOM() " +
                        "LIMIT 80",
                new String[]{question.answerWord}
        );

        while (fakeCursor.moveToNext() && question.options.size() < 4) {
            String candidate;

            if (MODE_CHINESE_TO_ENGLISH.equals(quizMode)) {
                candidate = safeText(fakeCursor.getString(0));
            } else {
                candidate = compactTranslation(fakeCursor.getString(1));
            }

            if (!candidate.isEmpty()
                    && !candidate.equals(question.correctOption)
                    && !question.options.contains(candidate)) {
                question.options.add(candidate);
            }
        }

        fakeCursor.close();
    }

    private String safeText(String value) {
        return value == null ? "" : value.trim();
    }

    private String formatQuestionClue(String clue) {
        if (clue == null || clue.trim().isEmpty()) {
            return "暂无释义";
        }

        return clue.trim()
                .replace("\\r", "")
                .replace("\\n", "\n");
    }

    /*
     * 看英选中时，不直接把极长释义塞进按钮。
     * 优先取第一条释义，并限制长度，避免按钮内容溢出。
     */
    private String compactTranslation(String translation) {
        String formatted = formatQuestionClue(translation);

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

    private void showQuestion() {
        if (questionList.isEmpty()) {
            Toast.makeText(this, "暂无可用测试题目", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        if (currentQuizIndex >= questionList.size()) {
            saveTestRecord();
            showResultDialog();
            return;
        }

        Question question = questionList.get(currentQuizIndex);

        progressBar.setProgress(currentQuizIndex + 1);

        if (MODE_CHINESE_TO_ENGLISH.equals(quizMode)) {
            tvQuestionTitle.setText(
                    "第 " + (currentQuizIndex + 1) + " / " + questionList.size()
                            + " 题 · 请根据中文释义选择正确的英文单词："
            );

            tvQuestionContent.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        } else {
            tvQuestionTitle.setText(
                    "第 " + (currentQuizIndex + 1) + " / " + questionList.size()
                            + " 题 · 请根据英文单词选择正确的中文释义："
            );

            tvQuestionContent.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28);
        }

        tvQuestionContent.setText(question.questionClue);

        layoutOptions.setVisibility(View.VISIBLE);

        btnOptionA.setText(question.options.get(0));
        btnOptionB.setText(question.options.get(1));
        btnOptionC.setText(question.options.get(2));
        btnOptionD.setText(question.options.get(3));
    }

    private void checkChoiceAnswer(String selectedOption) {
        if (currentQuizIndex >= questionList.size()) {
            return;
        }

        Question question = questionList.get(currentQuizIndex);

        if (question.correctOption.equals(selectedOption.trim())) {
            score++;

            Toast.makeText(this, "回答正确！", Toast.LENGTH_SHORT).show();
        } else {
            String answerText;

            if (MODE_CHINESE_TO_ENGLISH.equals(quizMode)) {
                answerText = question.answerWord;
            } else {
                answerText = question.correctOption;
            }

            Toast.makeText(
                    this,
                    "回答错误，正确答案是：" + answerText,
                    Toast.LENGTH_SHORT
            ).show();
        }

        currentQuizIndex++;
        showQuestion();
    }

    private void showResultDialog() {
        if (resultDialogShown) {
            return;
        }

        resultDialogShown = true;

        int total = questionList.size();
        int wrong = total - score;
        int percent = total == 0 ? 0 : Math.round(score * 100f / total);

        String modeName = MODE_CHINESE_TO_ENGLISH.equals(quizMode)
                ? "看中选英"
                : "看英选中";

        String message =
                "测试模式：" + modeName + "\n\n" +
                        "总题数：" + total + " 题\n" +
                        "答对：" + score + " 题\n" +
                        "答错：" + wrong + " 题\n" +
                        "正确率：" + percent + "%";

        new AlertDialog.Builder(this)
                .setTitle("本次测试成绩")
                .setMessage(message)
                .setCancelable(false)
                .setNegativeButton("返回模式选择", (dialog, which) -> finish())
                .setPositiveButton("返回主页", (dialog, which) -> {
                    Intent intent = new Intent(QuizActivity.this, MainActivity.class);
                    intent.setFlags(
                            Intent.FLAG_ACTIVITY_CLEAR_TOP
                                    | Intent.FLAG_ACTIVITY_SINGLE_TOP
                    );

                    startActivity(intent);
                    finish();
                })
                .show();
    }

    private void showExitConfirmDialog() {
        if (resultDialogShown) {
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("退出测试")
                .setMessage("退出后，本次测试不会保存成绩和记录，是否确认退出？")
                .setNegativeButton("继续测试", null)
                .setPositiveButton("确认退出", (dialog, which) -> exitQuizWithoutSaving())
                .show();
    }

    private void exitQuizWithoutSaving() {
        quizCancelled = true;
        testRecordSaved = true;

        Toast.makeText(this, "已退出测试，本次成绩未保存", Toast.LENGTH_SHORT).show();

        // 直接回到测试模式选择页
        finish();
    }

    @Override
    public void onBackPressed() {
        showExitConfirmDialog();
    }

    private void saveTestRecord() {
        if (quizCancelled || testRecordSaved) {
            return;
        }

        testRecordSaved = true;

        SQLiteDatabase db = dbHelper.getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put("user_id", currentUserId);

        if (MODE_CHINESE_TO_ENGLISH.equals(quizMode)) {
            values.put("test_type", "看中选英");
        } else {
            values.put("test_type", "看英选中");
        }

        values.put("total_questions", questionList.size());
        values.put("correct_count", score);
        values.put("test_date", System.currentTimeMillis());

        db.insert("test_record", null, values);
    }
}