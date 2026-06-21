package gdufs.groupwork.WordApp.com;

import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;  // 如果布局中用到了 CardView

import java.util.Collections;
import java.util.List;

/**
 * Created by Russell on 2026/6/19
 */
public class DashboardActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        // 显示返回箭头
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        // 获取数据
        StatisticsHelper helper = new StatisticsHelper(this);
        DashboardData data = helper.getDashboardData();

        // 绑定UI
        ((TextView) findViewById(R.id.tv_total)).setText(String.valueOf(data.totalWords));
        ((TextView) findViewById(R.id.tv_mastered)).setText(String.valueOf(data.mastered));
        ((TextView) findViewById(R.id.tv_learning)).setText(String.valueOf(data.learning));
        ((TextView) findViewById(R.id.tv_today)).setText(String.valueOf(data.todayNew));
        ((TextView) findViewById(R.id.tv_consecutive)).setText(String.valueOf(data.consecutiveDays));
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        // 绘制趋势（简单柱状图）
        drawTrend(data.last7DaysCount);
    }

    private void drawTrend(List<Integer> counts) {
        LinearLayout container = findViewById(R.id.ll_trend);
        container.removeAllViews();
        int max = Collections.max(counts);
        if (max == 0) max = 1; // 防止除零

        String[] days = {"7天前","6天前","5天前","4天前","3天前","2天前","昨天"};
        for (int i = 0; i < counts.size(); i++) {
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER_HORIZONTAL);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            item.setLayoutParams(params);

            // 柱状条
            View bar = new View(this);
            int height = (int) (counts.get(i) * 200f / max); // 最大高度200dp
            LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    Math.max(height, 10) // 最小高度10dp
            );
            bar.setLayoutParams(barParams);
            bar.setBackgroundColor(Color.parseColor("#42A5F5"));

            // 数值标签
            TextView tvCount = new TextView(this);
            tvCount.setText(String.valueOf(counts.get(i)));
            tvCount.setTextSize(12);
            tvCount.setGravity(Gravity.CENTER);
            tvCount.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            // 日期标签
            TextView tvDay = new TextView(this);
            tvDay.setText(days[i]);
            tvDay.setTextSize(10);
            tvDay.setTextColor(Color.GRAY);
            tvDay.setGravity(Gravity.CENTER);
            tvDay.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            item.addView(bar);
            item.addView(tvCount);
            item.addView(tvDay);
            container.addView(item);
        }
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
