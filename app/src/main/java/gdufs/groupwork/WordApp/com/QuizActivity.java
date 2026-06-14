package gdufs.groupwork.WordApp.com;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.media.MediaPlayer;
import android.net.Uri;
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

    private static class Question {
        String answerWord;
        String questionClue; // 中文释义
        List<String> options = new ArrayList<>();
    }

    private List<Question> questionList = new ArrayList<>();
    private int currentQuizIndex = 0;
    private int score = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_quiz);

        dbHelper = new DatabaseHelper(this);
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
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        // 筛选已背过的单词进行出题
        Cursor cursor = db.rawQuery(
                "SELECT e.word, e.translation FROM study_record s " +
                        "JOIN ecdict e ON s.word = e.word " +
                        "WHERE s.master_level > 0 ORDER BY RANDOM() LIMIT 10", null);

        if (cursor.getCount() < 10) {
            cursor.close();
            // 已背单词少于10个时，从主库随机抽取词汇生成测试
            cursor = db.rawQuery("SELECT word, translation FROM ecdict ORDER BY RANDOM() LIMIT 10", null);
        }

        while (cursor.moveToNext()) {
            Question q = new Question();
            q.answerWord = cursor.getString(0);
            q.questionClue = cursor.getString(1);
            q.options.add(q.answerWord);

            // 补充生成三个干扰项干扰项
            Cursor fakeCursor = db.rawQuery(
                    "SELECT word FROM ecdict WHERE word != ? ORDER BY RANDOM() LIMIT 3",
                    new String[]{q.answerWord});
            while (fakeCursor.moveToNext()) {
                q.options.add(fakeCursor.getString(0));
            }
            fakeCursor.close();

            // 打乱备选选项数组
            Collections.shuffle(q.options);
            questionList.add(q);
        }
        cursor.close();
    }

    private void showQuestion() {
        if (questionList.isEmpty() || currentQuizIndex >= questionList.size()) {
            Toast.makeText(this, "测试结束，您的得分是: " + score + " / 10", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        progressBar.setProgress(currentQuizIndex + 1);
        Question q = questionList.get(currentQuizIndex);
        tvQuestionContent.setText(q.questionClue);

        // 默认进入看中文选英文模式
        layoutOptions.setVisibility(View.VISIBLE);
        layoutDictation.setVisibility(View.GONE);

        btnOptionA.setText(q.options.get(0));
        btnOptionB.setText(q.options.get(1));
        btnOptionC.setText(q.options.get(2));
        btnOptionD.setText(q.options.get(3));
    }

    private void checkChoiceAnswer(String selectedOption) {
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
        Question q = questionList.get(currentQuizIndex);
        String input = etDictInput.getText().toString().trim();
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
        Question q = questionList.get(currentQuizIndex);
        // 使用有道在线翻译 API 进行发音流读取
        String url = "https://dict.youdao.com/dictvoice?type=1&audio=" + q.answerWord;
        MediaPlayer mediaPlayer = new MediaPlayer();
        try {
            mediaPlayer.setDataSource(url);
            mediaPlayer.prepareAsync();
            mediaPlayer.setOnPreparedListener(MediaPlayer::start);
            mediaPlayer.setOnCompletionListener(MediaPlayer::release);
        } catch (IOException e) {
            Toast.makeText(this, "发音加载失败，请检查网络", Toast.LENGTH_SHORT).show();
        }
    }
}