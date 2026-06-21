package gdufs.groupwork.WordApp.com;

import android.os.Bundle;
import android.view.MenuItem;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

/**
 * 独立学习数据看板页（含近 7 天趋势图）。
 */
public class DashboardActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        StatisticsHelper helper = new StatisticsHelper(this);
        DashboardData data = helper.getDashboardData();

        ((TextView) findViewById(R.id.tv_total)).setText(String.valueOf(data.totalWords));
        ((TextView) findViewById(R.id.tv_mastered)).setText(String.valueOf(data.mastered));
        ((TextView) findViewById(R.id.tv_learning)).setText(String.valueOf(data.learning));
        ((TextView) findViewById(R.id.tv_today)).setText(String.valueOf(data.todayNew));
        ((TextView) findViewById(R.id.tv_consecutive)).setText(String.valueOf(data.consecutiveDays));

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        TrendChartHelper.render(
                this,
                (LinearLayout) findViewById(R.id.ll_trend),
                data.last7DaysCount
        );
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
