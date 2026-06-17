package gdufs.groupwork.WordApp.com;

import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class QuizActivity extends AppCompatActivity {

    private ProgressBar progressBar;
    private TextView tvQuestionContent;
    private LinearLayout layoutOptions, layoutDictation;
    private Button btnOptionA, btnOptionB, btnOptionC, btnOptionD, btnSubmitDict;
    private EditText etDictInput;

    private DatabaseHelper dbHelper;
    private UserSessionManager sessionManager;
    private int currentUserId;

    private final List<Question> questionList = new ArrayList<>();
    private int currentQuizIndex = 0;
    private int score = 0;
    private boolean testRecordSaved = false;

    private static class Question {
        String answerWord;
        String questionClue; // 中文释义
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

        View.OnClickListener optionClickListener = v -> {
            Button btn = (Button) v;
            checkChoiceAnswer(btn.getText().toString());
        };

        btnOptionA.setOnClickListener(optionClickListener);
        btnOptionB.setOnClickListener(optionClickListener);
        btnOptionC.setOnClickListener(optionClickListener);
        btnOptionD.setOnClickListener(optionClickListener);

        findViewById(R.id.btnSpeak).setOnClickListener(v -> playOnlineVoice());

        btnSubmitDict.setOnClickListener(v -> checkDictAnswer());
    }

    private void generateQuizSheet() {
        questionList.clear();

        SQLiteDatabase db = dbHelper.getReadableDatabase();

        /*
         * 1. 优先从当前用户已经学过的单词里抽题。
         * 重点：必须加 s.user_id = ?
         * 否则 A 用户会抽到 B 用户学过的单词。
         */
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

        /*
         * 2. 如果当前用户已学单词不足 10 个，
         * 为了保证测试页面能运行，就从公共词库随机抽 10 个。
         */
        if (cursor.getCount() < 10) {
            cursor.close();

            cursor = db.rawQuery(
                    "SELECT word, translation FROM ecdict ORDER BY RANDOM() LIMIT 10",
                    null
            );
        }

        while (cursor.moveToNext()) {
            Question q = new Question();
            q.answerWord = cursor.getString(0);
            q.questionClue = cursor.getString(1);

            if (q.questionClue == null || q.questionClue.trim().isEmpty()) {
                q.questionClue = "暂无释义";
            }

            q.options.add(q.answerWord);

            // 生成 3 个英文干扰项
            Cursor fakeCursor = db.rawQuery(
                    "SELECT word FROM ecdict " +
                            "WHERE word != ? " +
                            "ORDER BY RANDOM() LIMIT 3",
                    new String[]{q.answerWord}
            );

            while (fakeCursor.moveToNext()) {
                q.options.add(fakeCursor.getString(0));
            }

            fakeCursor.close();

            // 防止极端情况下干扰项不足 3 个导致按钮越界
            if (q.options.size() == 4) {
                Collections.shuffle(q.options);
                questionList.add(q);
            }
        }

        cursor.close();

        if (!questionList.isEmpty()) {
            progressBar.setMax(questionList.size());
        }
    }

    private void showQuestion() {
        if (questionList.isEmpty()) {
            Toast.makeText(this, "暂无可用测试题目", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        if (currentQuizIndex >= questionList.size()) {
            saveTestRecord();

            Toast.makeText(
                    this,
                    "测试结束，您的得分是: " + score + " / " + questionList.size(),
                    Toast.LENGTH_LONG
            ).show();

            finish();
            return;
        }

        progressBar.setProgress(currentQuizIndex + 1);

        Question q = questionList.get(currentQuizIndex);

        tvQuestionContent.setText(q.questionClue);

        /*
         * 当前默认模式：看中文选英文。
         * 听写模式和看英选中模式后面可以继续扩展。
         */
        layoutOptions.setVisibility(View.VISIBLE);
        layoutDictation.setVisibility(View.GONE);

        btnOptionA.setText(q.options.get(0));
        btnOptionB.setText(q.options.get(1));
        btnOptionC.setText(q.options.get(2));
        btnOptionD.setText(q.options.get(3));
    }

    private void checkChoiceAnswer(String selectedOption) {
        if (currentQuizIndex >= questionList.size()) {
            return;
        }

        Question q = questionList.get(currentQuizIndex);

        if (q.answerWord.equals(selectedOption)) {
            score++;
            Toast.makeText(this, "回答正确！", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "回答错误，正确答案是: " + q.answerWord, Toast.LENGTH_SHORT).show();
        }

        currentQuizIndex++;
        showQuestion();
    }

    private void checkDictAnswer() {
        if (currentQuizIndex >= questionList.size()) {
            return;
        }

        Question q = questionList.get(currentQuizIndex);
        String input = etDictInput.getText().toString().trim();

        if (input.isEmpty()) {
            Toast.makeText(this, "请输入单词拼写", Toast.LENGTH_SHORT).show();
            return;
        }

        if (q.answerWord.equalsIgnoreCase(input)) {
            score++;
            Toast.makeText(this, "拼写正确！", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "拼写错误，正确写法是: " + q.answerWord, Toast.LENGTH_SHORT).show();
        }

        etDictInput.setText("");

        currentQuizIndex++;
        showQuestion();
    }

    private void playOnlineVoice() {
        if (questionList.isEmpty() || currentQuizIndex >= questionList.size()) {
            return;
        }

        Question q = questionList.get(currentQuizIndex);

        String url = "https://dict.youdao.com/dictvoice?type=1&audio=" + q.answerWord;

        MediaPlayer mediaPlayer = new MediaPlayer();

        try {
            mediaPlayer.setDataSource(url);
            mediaPlayer.prepareAsync();
            mediaPlayer.setOnPreparedListener(MediaPlayer::start);
            mediaPlayer.setOnCompletionListener(MediaPlayer::release);
        } catch (IOException e) {
            Toast.makeText(this, "发音加载失败，请检查网络", Toast.LENGTH_SHORT).show();
            mediaPlayer.release();
        }
    }

    private void saveTestRecord() {
        if (testRecordSaved) {
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