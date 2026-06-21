package gdufs.groupwork.WordApp.com;

import android.content.Intent;
import android.os.Bundle;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

/**
 * 学习设置页面：配置每日新词数与每日复习上限。
 */
public class StudySettingsActivity extends AppCompatActivity {

    private TextView tvDailyNewValue;
    private TextView tvDailyReviewValue;
    private SeekBar seekDailyNew;
    private SeekBar seekDailyReview;

    private StudyPlanManager planManager;
    private UserSessionManager sessionManager;

    private int currentUserId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_study_settings);

        planManager = new StudyPlanManager(this);
        sessionManager = new UserSessionManager(this);

        if (!sessionManager.isLoggedIn()) {
            goToLogin();
            return;
        }

        currentUserId = sessionManager.getCurrentUserId();

        initViews();
        loadSettings();
    }

    /**
     * 绑定控件与事件。
     */
    private void initViews() {
        tvDailyNewValue = findViewById(R.id.tvDailyNewValue);
        tvDailyReviewValue = findViewById(R.id.tvDailyReviewValue);
        seekDailyNew = findViewById(R.id.seekDailyNew);
        seekDailyReview = findViewById(R.id.seekDailyReview);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        seekDailyNew.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                updateDailyNewLabel(progressToDailyNew(progress));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        seekDailyReview.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                updateDailyReviewLabel(progressToDailyReview(progress));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        MaterialButton btnSave = findViewById(R.id.btnSaveSettings);
        btnSave.setOnClickListener(v -> saveSettings());
    }

    /**
     * 从偏好设置加载当前值。
     */
    private void loadSettings() {
        int dailyNew = planManager.getDailyNewWordCount(currentUserId);
        int dailyReview = planManager.getDailyReviewLimit(currentUserId);

        seekDailyNew.setProgress(dailyNewToProgress(dailyNew));
        seekDailyReview.setProgress(dailyReviewToProgress(dailyReview));

        updateDailyNewLabel(dailyNew);
        updateDailyReviewLabel(dailyReview);
    }

    /**
     * 保存设置并返回。
     */
    private void saveSettings() {
        int dailyNew = progressToDailyNew(seekDailyNew.getProgress());
        int dailyReview = progressToDailyReview(seekDailyReview.getProgress());

        planManager.setDailyNewWordCount(currentUserId, dailyNew);
        planManager.setDailyReviewLimit(currentUserId, dailyReview);

        Toast.makeText(this, "学习设置已保存", Toast.LENGTH_SHORT).show();
        finish();
    }

    private void updateDailyNewLabel(int value) {
        tvDailyNewValue.setText(value + " 个 / 天");
    }

    private void updateDailyReviewLabel(int value) {
        tvDailyReviewValue.setText(value + " 个 / 天");
    }

    private int progressToDailyNew(int progress) {
        return StudyPlanManager.MIN_DAILY_NEW + progress;
    }

    private int dailyNewToProgress(int value) {
        return value - StudyPlanManager.MIN_DAILY_NEW;
    }

    private int progressToDailyReview(int progress) {
        return StudyPlanManager.MIN_DAILY_REVIEW + progress;
    }

    private int dailyReviewToProgress(int value) {
        return value - StudyPlanManager.MIN_DAILY_REVIEW;
    }

    private void goToLogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
