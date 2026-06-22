package gdufs.groupwork.WordApp.com;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 听写测试页面
 *
 * 功能：
 * 1. 从已学习单词中随机抽取 10 个单词；
 * 2. 使用 Android TextToSpeech 播放单词发音；
 * 3. 用户输入英文拼写；
 * 4. 忽略大小写判断答案；
 * 5. 答对后“下一题”按钮为绿色；
 * 6. 答错后“下一题”按钮保持蓝色；
 * 7. 完成后保存测试记录。
 */
public class DictationActivity extends AppCompatActivity {

    private static final int QUESTION_COUNT = 10;

    // 颜色统一配置
    private static final String COLOR_BLUE = "#2F5E97";
    private static final String COLOR_GREEN = "#2FA866";

    private ProgressBar progressBar;
    private TextView tvQuestionTitle;
    private TextView tvHint;

    private EditText etAnswer;

    private Button btnPlayWord;
    private Button btnSubmit;
    private Button btnNext;
    private Button btnExit;

    private DatabaseHelper dbHelper;
    private UserSessionManager sessionManager;
    private TextToSpeech textToSpeech;

    private int currentUserId;

    private int currentIndex = 0;
    private int correctCount = 0;

    private boolean ttsReady = false;
    private boolean answeredCurrentQuestion = false;
    private boolean testRecordSaved = false;
    private boolean resultDialogShown = false;
    private boolean testCancelled = false;

    private final List<String> wordList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dictation);

        dbHelper = new DatabaseHelper(this);
        sessionManager = new UserSessionManager(this);

        // 未登录时返回登录页面
        if (!sessionManager.isLoggedIn()) {
            Intent intent = new Intent(
                    DictationActivity.this,
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
        initTextToSpeech();

        if (!loadDictationWords()) {
            return;
        }

        showCurrentQuestion();
    }

    /**
     * 初始化控件和点击事件
     */
    private void initViews() {
        progressBar = findViewById(R.id.progressBarDictation);
        tvQuestionTitle = findViewById(R.id.tvDictationQuestionTitle);
        tvHint = findViewById(R.id.tvDictationHint);

        etAnswer = findViewById(R.id.etDictationAnswer);

        btnPlayWord = findViewById(R.id.btnPlayWord);
        btnSubmit = findViewById(R.id.btnSubmitDictation);
        btnNext = findViewById(R.id.btnNextDictation);
        btnExit = findViewById(R.id.btnExitDictation);

        btnPlayWord.setOnClickListener(v -> playCurrentWord());

        btnSubmit.setOnClickListener(v -> checkAnswer());

        btnNext.setOnClickListener(v -> {
            currentIndex++;
            showCurrentQuestion();
        });

        btnExit.setOnClickListener(v -> showExitDialog());
    }

    /**
     * 初始化 Android 系统英文发音引擎
     */
    private void initTextToSpeech() {
        textToSpeech = new TextToSpeech(this, status -> {
            if (status != TextToSpeech.SUCCESS) {
                ttsReady = false;

                Toast.makeText(
                        DictationActivity.this,
                        "语音引擎初始化失败",
                        Toast.LENGTH_LONG
                ).show();

                return;
            }

            int languageResult = textToSpeech.setLanguage(Locale.US);

            if (languageResult == TextToSpeech.LANG_MISSING_DATA
                    || languageResult == TextToSpeech.LANG_NOT_SUPPORTED) {

                ttsReady = false;

                Toast.makeText(
                        DictationActivity.this,
                        "当前设备不支持英语语音，请安装英语语音包",
                        Toast.LENGTH_LONG
                ).show();

            } else {
                ttsReady = true;
            }
        });
    }

    /**
     * 从已学习单词中随机抽取 10 题
     */
    private boolean loadDictationWords() {
        wordList.clear();

        SQLiteDatabase db = dbHelper.getReadableDatabase();

        Cursor cursor = db.rawQuery(
                "SELECT e.word "
                        + "FROM study_record s "
                        + "JOIN ecdict e ON s.word = e.word "
                        + "WHERE s.user_id = ? "
                        + "AND s.master_level > 0 "
                        + "AND s.is_ignored = 0 "
                        + "ORDER BY RANDOM() "
                        + "LIMIT " + QUESTION_COUNT,
                new String[]{String.valueOf(currentUserId)}
        );

        while (cursor.moveToNext()) {
            String word = cursor.getString(0);

            if (word != null && !word.trim().isEmpty()) {
                wordList.add(word.trim());
            }
        }

        cursor.close();

        if (wordList.size() < QUESTION_COUNT) {
            Toast.makeText(
                    this,
                    "已学习单词不足 10 个，请先去背诵单词",
                    Toast.LENGTH_LONG
            ).show();

            finish();
            return false;
        }

        progressBar.setMax(wordList.size());

        return true;
    }

    /**
     * 显示当前题目
     */
    private void showCurrentQuestion() {
        if (currentIndex >= wordList.size()) {
            saveTestRecord();
            showResultDialog();
            return;
        }

        answeredCurrentQuestion = false;

        progressBar.setProgress(currentIndex + 1);

        tvQuestionTitle.setText(
                "第 "
                        + (currentIndex + 1)
                        + " / "
                        + wordList.size()
                        + " 题 · 听写测试"
        );

        tvHint.setText("请点击播放按钮，听清单词后输入正确拼写");

        etAnswer.setText("");
        etAnswer.setEnabled(true);
        etAnswer.requestFocus();

        btnPlayWord.setEnabled(true);

        btnSubmit.setVisibility(View.VISIBLE);
        btnNext.setVisibility(View.GONE);

        // 每次进入新题时恢复默认颜色
        setButtonColor(btnSubmit, COLOR_BLUE);
        setButtonColor(btnNext, COLOR_BLUE);

        // 自动播放一次单词
        playCurrentWord();
    }

    /**
     * 播放当前单词
     */
    private void playCurrentWord() {
        if (currentIndex >= wordList.size()) {
            return;
        }

        if (!ttsReady) {
            Toast.makeText(
                    this,
                    "英语语音暂不可用，请检查设备的文字转语音服务",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        String currentWord = wordList.get(currentIndex);

        textToSpeech.stop();

        textToSpeech.speak(
                currentWord,
                TextToSpeech.QUEUE_FLUSH,
                null,
                "dictation_word_" + currentIndex
        );
    }

    /**
     * 判断用户输入的答案
     */
    private void checkAnswer() {
        if (answeredCurrentQuestion || currentIndex >= wordList.size()) {
            return;
        }

        String userAnswer = etAnswer.getText().toString().trim();
        String correctAnswer = wordList.get(currentIndex);

        if (userAnswer.isEmpty()) {
            Toast.makeText(
                    this,
                    "请输入你的答案",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        answeredCurrentQuestion = true;

        String normalizedUserAnswer = normalizeWord(userAnswer);
        String normalizedCorrectAnswer = normalizeWord(correctAnswer);

        boolean isCorrect = normalizedCorrectAnswer.equals(normalizedUserAnswer);

        if (isCorrect) {
            correctCount++;

            tvHint.setText("回答正确！正确答案是：" + correctAnswer);

            Toast.makeText(
                    this,
                    "回答正确！",
                    Toast.LENGTH_SHORT
            ).show();

            // 答对：下一题显示绿色
            setButtonColor(btnNext, COLOR_GREEN);

        } else {
            tvHint.setText("回答错误，正确答案是：" + correctAnswer);

            Toast.makeText(
                    this,
                    "回答错误，正确答案是：" + correctAnswer,
                    Toast.LENGTH_SHORT
            ).show();

            // 答错：下一题保持蓝色
            setButtonColor(btnNext, COLOR_BLUE);
        }

        etAnswer.setEnabled(false);

        btnSubmit.setVisibility(View.GONE);
        btnNext.setVisibility(View.VISIBLE);

        hideKeyboard();
    }

    /**
     * 统一处理用户输入，忽略大小写与首尾空格
     */
    private String normalizeWord(String word) {
        if (word == null) {
            return "";
        }

        return word
                .trim()
                .toLowerCase(Locale.US)
                .replaceAll("\\s+", " ");
    }

    /**
     * 设置按钮背景颜色
     */
    private void setButtonColor(Button button, String color) {
        if (button == null) {
            return;
        }

        button.setBackgroundTintList(
                ColorStateList.valueOf(Color.parseColor(color))
        );
    }

    /**
     * 收起软键盘
     */
    private void hideKeyboard() {
        InputMethodManager inputMethodManager =
                (InputMethodManager) getSystemService(
                        Context.INPUT_METHOD_SERVICE
                );

        View currentFocus = getCurrentFocus();

        if (inputMethodManager != null && currentFocus != null) {
            inputMethodManager.hideSoftInputFromWindow(
                    currentFocus.getWindowToken(),
                    0
            );
        }
    }

    /**
     * 保存本次听写测试成绩
     */
    private void saveTestRecord() {
        if (testCancelled || testRecordSaved) {
            return;
        }

        testRecordSaved = true;

        SQLiteDatabase db = dbHelper.getWritableDatabase();

        ContentValues values = new ContentValues();

        values.put("user_id", currentUserId);
        values.put("test_type", "听写测试");
        values.put("total_questions", wordList.size());
        values.put("correct_count", correctCount);
        values.put("test_date", System.currentTimeMillis());

        db.insert("test_record", null, values);
    }

    /**
     * 测试完成后的成绩弹窗
     */
    private void showResultDialog() {
        if (resultDialogShown) {
            return;
        }

        resultDialogShown = true;

        int total = wordList.size();
        int wrong = total - correctCount;

        int percentage = total == 0
                ? 0
                : Math.round(correctCount * 100f / total);

        String message =
                "测试模式：听写测试\n\n"
                        + "总题数：" + total + " 题\n"
                        + "答对：" + correctCount + " 题\n"
                        + "答错：" + wrong + " 题\n"
                        + "正确率：" + percentage + "%";

        new AlertDialog.Builder(this)
                .setTitle("本次测试成绩")
                .setMessage(message)
                .setCancelable(false)
                .setNegativeButton("返回模式选择", (dialog, which) -> finish())
                .setPositiveButton("返回主页", (dialog, which) -> {
                    Intent intent = new Intent(
                            DictationActivity.this,
                            MainActivity.class
                    );

                    intent.setFlags(
                            Intent.FLAG_ACTIVITY_CLEAR_TOP
                                    | Intent.FLAG_ACTIVITY_SINGLE_TOP
                    );

                    startActivity(intent);
                    finish();
                })
                .show();
    }

    /**
     * 退出测试确认弹窗
     */
    private void showExitDialog() {
        if (resultDialogShown) {
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("退出听写测试")
                .setMessage("退出后，本次测试不会保存成绩，是否确认退出？")
                .setNegativeButton("继续测试", null)
                .setPositiveButton("确认退出", (dialog, which) -> {
                    testCancelled = true;
                    testRecordSaved = true;

                    Toast.makeText(
                            DictationActivity.this,
                            "已退出测试，本次成绩未保存",
                            Toast.LENGTH_SHORT
                    ).show();

                    finish();
                })
                .show();
    }

    @Override
    public void onBackPressed() {
        showExitDialog();
    }

    @Override
    protected void onDestroy() {
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }

        super.onDestroy();
    }
}