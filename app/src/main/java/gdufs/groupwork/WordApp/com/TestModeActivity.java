package gdufs.groupwork.WordApp.com;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class TestModeActivity extends AppCompatActivity {

    private UserSessionManager sessionManager;

    private View btnBack;
    private View btnChineseToEnglish;
    private View btnEnglishToChinese;
    private View btnListening;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_test_mode);

        sessionManager = new UserSessionManager(this);

        if (!sessionManager.isLoggedIn()) {
            Intent intent = new Intent(TestModeActivity.this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
            return;
        }

        initViews();
    }

    private void initViews() {
        btnBack = findViewById(R.id.btnBack);
        btnChineseToEnglish = findViewById(R.id.btnChineseToEnglish);
        btnEnglishToChinese = findViewById(R.id.btnEnglishToChinese);
        btnListening = findViewById(R.id.btnListening);

        btnBack.setOnClickListener(v -> finish());

        btnChineseToEnglish.setOnClickListener(v ->
                startQuiz(QuizActivity.MODE_CHINESE_TO_ENGLISH)
        );

        btnEnglishToChinese.setOnClickListener(v ->
                startQuiz(QuizActivity.MODE_ENGLISH_TO_CHINESE)
        );

        btnListening.setOnClickListener(v -> Toast.makeText(
                TestModeActivity.this,
                "听力模式正在优化中，暂未开放",
                Toast.LENGTH_SHORT
        ).show());
    }

    private void startQuiz(String quizMode) {
        Intent intent = new Intent(TestModeActivity.this, QuizActivity.class);
        intent.putExtra(QuizActivity.EXTRA_QUIZ_MODE, quizMode);
        startActivity(intent);
    }
}