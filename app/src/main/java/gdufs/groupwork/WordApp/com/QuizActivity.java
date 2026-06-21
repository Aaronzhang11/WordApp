package gdufs.groupwork.WordApp.com;

import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.text.method.TransformationMethod;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class QuizActivity extends AppCompatActivity {

    private ProgressBar progressBar;
    private TextView tvQuestionContent;
    private LinearLayout layoutOptions;
    private LinearLayout layoutDictation;

    private Button btnOptionA;
    private Button btnOptionB;
    private Button btnOptionC;
    private Button btnOptionD;
    private Button btnSubmitDict;
    private Button btnExitQuiz;

    private EditText etDictInput;

    private DatabaseHelper dbHelper;
    private UserSessionManager sessionManager;

    private int currentUserId;
    private final List<Question> questionList = new ArrayList<>();

    private int currentQuizIndex = 0;
    private int score = 0;

    private boolean testRecordSaved = false;
    private boolean resultDialogShown = false;
    private boolean quizCancelled = false;

    private static class Question {
        String answerWord;
        String questionClue;
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

        initViews();
        generateQuizSheet();
        showQuestion();
    }

    private void initViews() {
        progressBar = findViewById(R.id.progressBar);
        tvQuestionContent = findViewById(R.id.tvQuestionContent);

        layoutOptions = findViewById(R.id.layoutOptions);
        layoutDictation = findViewById(R.id.layoutDictation);

        btnOptionA = findViewById(R.id.btnOptionA);
        btnOptionB = findViewById(R.id.btnOptionB);
        btnOptionC = findViewById(R.id.btnOptionC);
        btnOptionD = findViewById(R.id.btnOptionD);

        etDictInput = findViewById(R.id.etDictInput);
        btnSubmitDict = findViewById(R.id.btnSubmitDict);
        btnExitQuiz = findViewById(R.id.btnExitQuiz);

        disableButtonAllCaps(btnOptionA);
        disableButtonAllCaps(btnOptionB);
        disableButtonAllCaps(btnOptionC);
        disableButtonAllCaps(btnOptionD);
        disableButtonAllCaps(btnSubmitDict);
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

        View btnSpeak = findViewById(R.id.btnSpeak);
        if (btnSpeak != null) {
            btnSpeak.setOnClickListener(v -> playOnlineVoice());
        }

        if (btnSubmitDict != null) {
            btnSubmitDict.setOnClickListener(v -> checkDictAnswer());
        }
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

    private void generateQuizSheet() {
        questionList.clear();

        SQLiteDatabase db = dbHelper.getReadableDatabase();

        Cursor cursor = db.rawQuery(
                "SELECT e.word, e.translation " +
                        "FROM study_record s " +
                        "JOIN ecdict e ON s.word = e.word " +
                        "WHERE s.user_id = ? " +
                        "AND s.master_level > 0 " +
                        "AND s.is_ignored = 0 " +
                        "ORDER BY RANDOM() LIMIT 10",
                new String[]{String.valueOf(currentUserId)}
        );

        // 当前用户的学习记录不足 10 个时，从整个词库随机取题
        if (cursor.getCount() < 10) {
            cursor.close();

            cursor = db.rawQuery(
                    "SELECT word, translation FROM ecdict ORDER BY RANDOM() LIMIT 10",
                    null
            );
        }

        while (cursor.moveToNext()) {
            Question question = new Question();

            question.answerWord = safeWord(cursor.getString(0));
            question.questionClue = formatQuestionClue(cursor.getString(1));

            if (question.answerWord.isEmpty()) {
                continue;
            }

            question.options.add(question.answerWord);

            Cursor fakeCursor = db.rawQuery(
                    "SELECT word FROM ecdict " +
                            "WHERE word != ? " +
                            "ORDER BY RANDOM() LIMIT 3",
                    new String[]{question.answerWord}
            );

            while (fakeCursor.moveToNext()) {
                String fakeWord = safeWord(fakeCursor.getString(0));

                if (!fakeWord.isEmpty() && !question.options.contains(fakeWord)) {
                    question.options.add(fakeWord);
                }
            }

            fakeCursor.close();

            if (question.options.size() == 4) {
                Collections.shuffle(question.options);
                questionList.add(question);
            }
        }

        cursor.close();

        if (!questionList.isEmpty()) {
            progressBar.setMax(questionList.size());
        }
    }

    private String safeWord(String word) {
        if (word == null) {
            return "";
        }

        return word.trim();
    }

    private String formatQuestionClue(String clue) {
        if (clue == null || clue.trim().isEmpty()) {
            return "暂无释义";
        }

        // 将词库中可能出现的字符“\n”真正显示为换行
        return clue.trim()
                .replace("\\r", "")
                .replace("\\n", "\n");
    }

    private void showQuestion() {
        if (questionList.isEmpty()) {
            Toast.makeText(this, "暂无可用测试题目", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        // 只有正常完成全部题目才保存成绩
        if (currentQuizIndex >= questionList.size()) {
            saveTestRecord();
            showResultDialog();
            return;
        }

        progressBar.setProgress(currentQuizIndex + 1);

        Question question = questionList.get(currentQuizIndex);

        tvQuestionContent.setText(question.questionClue);

        layoutOptions.setVisibility(View.VISIBLE);
        layoutDictation.setVisibility(View.GONE);

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

        if (question.answerWord.equalsIgnoreCase(selectedOption.trim())) {
            score++;
            Toast.makeText(this, "回答正确！", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(
                    this,
                    "回答错误，正确答案是：" + question.answerWord,
                    Toast.LENGTH_SHORT
            ).show();
        }

        currentQuizIndex++;
        showQuestion();
    }

    private void checkDictAnswer() {
        if (currentQuizIndex >= questionList.size()) {
            return;
        }

        Question question = questionList.get(currentQuizIndex);
        String input = etDictInput.getText().toString().trim();

        if (input.isEmpty()) {
            Toast.makeText(this, "请输入单词拼写", Toast.LENGTH_SHORT).show();
            return;
        }

        if (question.answerWord.equalsIgnoreCase(input)) {
            score++;
            Toast.makeText(this, "拼写正确！", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(
                    this,
                    "拼写错误，正确写法是：" + question.answerWord,
                    Toast.LENGTH_SHORT
            ).show();
        }

        etDictInput.setText("");

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

        String message =
                "总题数：" + total + " 题\n" +
                        "答对：" + score + " 题\n" +
                        "答错：" + wrong + " 题\n" +
                        "正确率：" + percent + "%";

        new AlertDialog.Builder(this)
                .setTitle("本次测试成绩")
                .setMessage(message)
                .setCancelable(false)
                .setPositiveButton("返回主页", (dialog, which) -> {
                    Intent intent = new Intent(QuizActivity.this, MainActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
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
        // 额外保险：禁止后续任何情况下写入测试记录
        quizCancelled = true;
        testRecordSaved = true;

        Toast.makeText(this, "已退出测试，本次成绩未保存", Toast.LENGTH_SHORT).show();

        // QuizActivity 是从 MainActivity 打开的，直接 finish 即可回到主页
        finish();
    }

    @Override
    public void onBackPressed() {
        showExitConfirmDialog();
    }

    private void playOnlineVoice() {
        if (questionList.isEmpty() || currentQuizIndex >= questionList.size()) {
            return;
        }

        Question question = questionList.get(currentQuizIndex);

        String url = "https://dict.youdao.com/dictvoice?type=1&audio=" + question.answerWord;
        MediaPlayer mediaPlayer = new MediaPlayer();

        try {
            mediaPlayer.setDataSource(url);
            mediaPlayer.prepareAsync();

            mediaPlayer.setOnPreparedListener(MediaPlayer::start);

            mediaPlayer.setOnCompletionListener(MediaPlayer::release);

            mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                Toast.makeText(this, "发音播放失败，请检查网络", Toast.LENGTH_SHORT).show();
                mp.release();
                return true;
            });
        } catch (IOException e) {
            Toast.makeText(this, "发音加载失败，请检查网络", Toast.LENGTH_SHORT).show();
            mediaPlayer.release();
        }
    }

    private void saveTestRecord() {
        // 中途退出时绝不写入 test_record
        if (quizCancelled || testRecordSaved) {
            return;
        }

        testRecordSaved = true;

        SQLiteDatabase db = dbHelper.getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put("user_id", currentUserId);
        values.put("test_type", "看中选英");
        values.put("total_questions", questionList.size());
        values.put("correct_count", score);
        values.put("test_date", System.currentTimeMillis());

        db.insert("test_record", null, values);
    }
}