package gdufs.groupwork.WordApp.com;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.List;

/**
 * 词书设置页：展示已有词书、切换当前词书、添加/删除自定义词书。
 */
public class VocabBookSettingsActivity extends AppCompatActivity {

    private ListView lvVocabBooks;
    private TextView tvBookCountHint;
    private TextView tvEmptyBooks;
    private MaterialButton btnAddVocabBook;

    private UserSessionManager sessionManager;
    private VocabBookManager vocabBookManager;

    private int currentUserId;
    private int currentBookId;

    private final List<VocabBookManager.VocabBook> bookItems = new ArrayList<>();
    private VocabBookAdapter bookAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_vocab_book_settings);

        sessionManager = new UserSessionManager(this);
        vocabBookManager = new VocabBookManager(this);

        if (!sessionManager.isLoggedIn()) {
            goToLogin();
            return;
        }

        currentUserId = sessionManager.getCurrentUserId();
        initViews();
        loadBooks();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (vocabBookManager != null && sessionManager != null && sessionManager.isLoggedIn()) {
            loadBooks();
        }
    }

    /**
     * 绑定控件与事件。
     */
    private void initViews() {
        lvVocabBooks = findViewById(R.id.lvVocabBooks);
        tvBookCountHint = findViewById(R.id.tvBookCountHint);
        tvEmptyBooks = findViewById(R.id.tvEmptyBooks);
        btnAddVocabBook = findViewById(R.id.btnAddVocabBook);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        bookAdapter = new VocabBookAdapter();
        lvVocabBooks.setAdapter(bookAdapter);

        lvVocabBooks.setOnItemClickListener((parent, view, position, id) -> {
            VocabBookManager.VocabBook item = bookItems.get(position);
            vocabBookManager.setCurrentBookId(currentUserId, item.bookId);
            currentBookId = item.bookId;
            bookAdapter.notifyDataSetChanged();
            Toast.makeText(this, "已切换至「" + item.bookName + "」", Toast.LENGTH_SHORT).show();
        });

        lvVocabBooks.setOnItemLongClickListener((parent, view, position, id) -> {
            VocabBookManager.VocabBook item = bookItems.get(position);

            if (item.isDefault) {
                Toast.makeText(this, "默认词书不可删除", Toast.LENGTH_SHORT).show();
                return true;
            }

            confirmDeleteBook(item);
            return true;
        });

        btnAddVocabBook.setOnClickListener(v -> {
            if (!vocabBookManager.canCreateMoreBooks(currentUserId)) {
                Toast.makeText(
                        this,
                        "词书数量已达上限（" + VocabBookManager.MAX_BOOK_COUNT + " 本）",
                        Toast.LENGTH_SHORT
                ).show();
                return;
            }

            startActivity(new Intent(this, CreateVocabBookActivity.class));
        });
    }

    /**
     * 加载词书列表并刷新 UI。
     */
    private void loadBooks() {
        bookItems.clear();
        bookItems.addAll(vocabBookManager.getAllBooks(currentUserId));
        currentBookId = vocabBookManager.getCurrentBookId(currentUserId);

        int count = bookItems.size();
        tvBookCountHint.setText(
                "已有 " + count + " / " + VocabBookManager.MAX_BOOK_COUNT + " 本词书"
        );

        boolean isEmpty = bookItems.isEmpty();
        tvEmptyBooks.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        lvVocabBooks.setVisibility(isEmpty ? View.GONE : View.VISIBLE);

        btnAddVocabBook.setEnabled(vocabBookManager.canCreateMoreBooks(currentUserId));
        btnAddVocabBook.setAlpha(btnAddVocabBook.isEnabled() ? 1f : 0.5f);

        bookAdapter.notifyDataSetChanged();
    }

    /**
     * 确认删除自定义词书。
     */
    private void confirmDeleteBook(VocabBookManager.VocabBook item) {
        new AlertDialog.Builder(this)
                .setTitle("删除词书")
                .setMessage("确定要删除「" + item.bookName + "」吗？\n\n删除后不会影响已有学习记录。")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (dialog, which) -> {
                    if (vocabBookManager.deleteCustomBook(currentUserId, item.bookId)) {
                        Toast.makeText(this, "词书已删除", Toast.LENGTH_SHORT).show();
                        loadBooks();
                    } else {
                        Toast.makeText(this, "删除失败", Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
    }

    private void goToLogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private GradientDrawable roundedBackground(int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    /**
     * 词书列表适配器。
     */
    private class VocabBookAdapter extends BaseAdapter {

        @Override
        public int getCount() {
            return bookItems.size();
        }

        @Override
        public Object getItem(int position) {
            return bookItems.get(position);
        }

        @Override
        public long getItemId(int position) {
            return bookItems.get(position).bookId;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            BookRowHolder holder;

            if (convertView == null) {
                LinearLayout wrapper = new LinearLayout(VocabBookSettingsActivity.this);
                wrapper.setOrientation(LinearLayout.VERTICAL);
                wrapper.setLayoutParams(new AbsListView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                ));

                MaterialCardView card = new MaterialCardView(VocabBookSettingsActivity.this);
                card.setCardBackgroundColor(Color.WHITE);
                card.setCardElevation(dp(1));
                card.setRadius(dp(16));
                card.setStrokeColor(Color.parseColor("#E2E8F0"));
                card.setStrokeWidth(dp(1));
                card.setUseCompatPadding(true);

                LinearLayout content = new LinearLayout(VocabBookSettingsActivity.this);
                content.setGravity(Gravity.CENTER_VERTICAL);
                content.setOrientation(LinearLayout.HORIZONTAL);
                content.setPadding(dp(16), dp(14), dp(12), dp(14));

                TextView badge = new TextView(VocabBookSettingsActivity.this);
                badge.setText("词书");
                badge.setTextColor(Color.parseColor("#2C5282"));
                badge.setTextSize(12);
                badge.setGravity(Gravity.CENTER);
                badge.setBackground(roundedBackground(Color.parseColor("#E6EEF8"), 12));

                content.addView(badge, new LinearLayout.LayoutParams(dp(44), dp(32)));

                LinearLayout textContainer = new LinearLayout(VocabBookSettingsActivity.this);
                textContainer.setOrientation(LinearLayout.VERTICAL);
                textContainer.setPadding(dp(12), 0, dp(8), 0);

                TextView tvBookName = new TextView(VocabBookSettingsActivity.this);
                tvBookName.setTextColor(Color.parseColor("#1F2937"));
                tvBookName.setTextSize(17);
                tvBookName.setTypeface(null, android.graphics.Typeface.BOLD);
                tvBookName.setMaxLines(1);

                TextView tvBookInfo = new TextView(VocabBookSettingsActivity.this);
                tvBookInfo.setTextColor(Color.parseColor("#718096"));
                tvBookInfo.setTextSize(13);
                tvBookInfo.setPadding(0, dp(4), 0, 0);

                textContainer.addView(tvBookName);
                textContainer.addView(tvBookInfo);

                content.addView(textContainer, new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1
                ));

                TextView tvCurrent = new TextView(VocabBookSettingsActivity.this);
                tvCurrent.setText("当前");
                tvCurrent.setTextColor(Color.WHITE);
                tvCurrent.setTextSize(11);
                tvCurrent.setGravity(Gravity.CENTER);
                tvCurrent.setBackground(roundedBackground(Color.parseColor("#319795"), 10));
                tvCurrent.setPadding(dp(8), dp(4), dp(8), dp(4));

                content.addView(tvCurrent);

                card.addView(content);
                wrapper.addView(card, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                ));

                holder = new BookRowHolder();
                holder.tvBookName = tvBookName;
                holder.tvBookInfo = tvBookInfo;
                holder.tvCurrent = tvCurrent;

                wrapper.setTag(holder);
                convertView = wrapper;
            } else {
                holder = (BookRowHolder) convertView.getTag();
            }

            VocabBookManager.VocabBook item = bookItems.get(position);

            holder.tvBookName.setText(item.bookName);

            String tagDesc = item.isDefault
                    ? "全词库"
                    : VocabBookManager.formatTags(item.tags);

            holder.tvBookInfo.setText(
                    tagDesc + " · " + item.wordCount + " 词 · 已学 "
                            + item.learnedCount + " · 长按可删除"
            );

            boolean isCurrent = item.bookId == currentBookId;
            holder.tvCurrent.setVisibility(isCurrent ? View.VISIBLE : View.GONE);

            if (item.isDefault) {
                holder.tvBookInfo.setText(
                        tagDesc + " · " + item.wordCount + " 词 · 已学 " + item.learnedCount
                );
            }

            return convertView;
        }
    }

    private static class BookRowHolder {
        TextView tvBookName;
        TextView tvBookInfo;
        TextView tvCurrent;
    }
}
