package gdufs.groupwork.WordApp.com;

import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteConstraintException;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.List;

public class WordBookActivity extends AppCompatActivity {

    private EditText etBookName;
    private View btnCreateBook;
    private View btnBack;
    private ListView lvWordBooks;
    private TextView tvEmptyBooks;

    private DatabaseHelper dbHelper;
    private UserSessionManager sessionManager;
    private int currentUserId;

    private final List<BookItem> bookItems = new ArrayList<>();
    private BookAdapter bookAdapter;

    private static class BookItem {
        int bookId;
        String bookName;
        int wordCount;
    }

    private static class BookWordItem {
        int relationId;
        String word;
        String phonetic;
        String translation;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_word_book);

        dbHelper = new DatabaseHelper(this);
        sessionManager = new UserSessionManager(this);

        if (!sessionManager.isLoggedIn()) {
            Intent intent = new Intent(WordBookActivity.this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
            return;
        }

        currentUserId = sessionManager.getCurrentUserId();

        initViews();
        loadWordBooks();
    }

    private void initViews() {
        etBookName = findViewById(R.id.etBookName);
        btnCreateBook = findViewById(R.id.btnCreateBook);
        btnBack = findViewById(R.id.btnBack);
        lvWordBooks = findViewById(R.id.lvWordBooks);
        tvEmptyBooks = findViewById(R.id.tvEmptyBooks);

        bookAdapter = new BookAdapter();
        lvWordBooks.setAdapter(bookAdapter);
        lvWordBooks.setDivider(null);
        lvWordBooks.setDividerHeight(0);

        btnBack.setOnClickListener(v -> finish());

        btnCreateBook.setOnClickListener(v -> createWordBook());

        lvWordBooks.setOnItemClickListener((parent, view, position, id) -> {
            BookItem item = bookItems.get(position);
            showBookWords(item.bookId, item.bookName);
        });

        /*
         * 长按单词本后显示管理菜单：
         * 1. 重命名
         * 2. 删除
         */
        lvWordBooks.setOnItemLongClickListener((parent, view, position, id) -> {
            BookItem item = bookItems.get(position);
            showBookManageDialog(item);
            return true;
        });
    }

    private void createWordBook() {
        String bookName = etBookName.getText().toString().trim();

        if (bookName.isEmpty()) {
            Toast.makeText(this, "请输入单词本名称", Toast.LENGTH_SHORT).show();
            return;
        }

        SQLiteDatabase db = dbHelper.getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put("user_id", currentUserId);
        values.put("book_name", bookName);
        values.put("create_time", System.currentTimeMillis());

        long result = db.insertWithOnConflict(
                "word_book",
                null,
                values,
                SQLiteDatabase.CONFLICT_IGNORE
        );

        if (result == -1) {
            Toast.makeText(this, "该单词本已存在", Toast.LENGTH_SHORT).show();
            return;
        }

        etBookName.setText("");
        Toast.makeText(this, "创建成功", Toast.LENGTH_SHORT).show();
        loadWordBooks();
    }

    private void loadWordBooks() {
        bookItems.clear();

        SQLiteDatabase db = dbHelper.getReadableDatabase();

        Cursor cursor = db.rawQuery(
                "SELECT b.book_id, b.book_name, COUNT(r.relation_id) AS word_count " +
                        "FROM word_book b " +
                        "LEFT JOIN book_word_relation r ON b.book_id = r.book_id " +
                        "WHERE b.user_id = ? " +
                        "GROUP BY b.book_id, b.book_name " +
                        "ORDER BY b.create_time DESC",
                new String[]{String.valueOf(currentUserId)}
        );

        while (cursor.moveToNext()) {
            BookItem item = new BookItem();
            item.bookId = cursor.getInt(0);
            item.bookName = cursor.getString(1);
            item.wordCount = cursor.getInt(2);

            bookItems.add(item);
        }

        cursor.close();

        boolean isEmpty = bookItems.isEmpty();

        tvEmptyBooks.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        lvWordBooks.setVisibility(isEmpty ? View.GONE : View.VISIBLE);

        bookAdapter.notifyDataSetChanged();
    }

    private void showBookManageDialog(BookItem item) {
        String[] options = {
                "重命名单词本",
                "删除单词本"
        };

        new AlertDialog.Builder(this)
                .setTitle("管理「" + item.bookName + "」")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        showRenameBookDialog(item);
                    } else {
                        confirmDeleteBook(item);
                    }
                })
                .show();
    }

    private void showRenameBookDialog(BookItem item) {
        EditText input = new EditText(this);
        input.setText(item.bookName);
        input.setSingleLine(true);
        input.setSelectAllOnFocus(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setHint("请输入新的单词本名称");
        input.setPadding(dp(16), dp(8), dp(16), dp(8));

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(24), dp(6), dp(24), 0);
        container.addView(
                input,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                )
        );

        AlertDialog renameDialog = new AlertDialog.Builder(this)
                .setTitle("重命名单词本")
                .setMessage("修改后，原单词本内的收藏单词会保留。")
                .setView(container)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", null)
                .create();

        renameDialog.setOnShowListener(dialog -> {
            renameDialog.getButton(AlertDialog.BUTTON_POSITIVE)
                    .setOnClickListener(v -> {
                        String newName = input.getText().toString().trim();

                        if (newName.isEmpty()) {
                            Toast.makeText(this, "单词本名称不能为空", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        if (newName.equals(item.bookName)) {
                            renameDialog.dismiss();
                            return;
                        }

                        SQLiteDatabase db = dbHelper.getWritableDatabase();

                        ContentValues values = new ContentValues();
                        values.put("book_name", newName);

                        try {
                            int changedRows = db.update(
                                    "word_book",
                                    values,
                                    "book_id = ? AND user_id = ?",
                                    new String[]{
                                            String.valueOf(item.bookId),
                                            String.valueOf(currentUserId)
                                    }
                            );

                            if (changedRows > 0) {
                                Toast.makeText(this, "重命名成功", Toast.LENGTH_SHORT).show();
                                renameDialog.dismiss();
                                loadWordBooks();
                            } else {
                                Toast.makeText(this, "重命名失败", Toast.LENGTH_SHORT).show();
                            }
                        } catch (SQLiteConstraintException e) {
                            Toast.makeText(this, "该单词本名称已存在", Toast.LENGTH_SHORT).show();
                        }
                    });
        });

        renameDialog.show();
    }

    private void confirmDeleteBook(BookItem item) {
        new AlertDialog.Builder(this)
                .setTitle("删除单词本")
                .setMessage(
                        "确定要删除「" + item.bookName + "」吗？\n\n" +
                                "删除后，词本中的收藏关系会被清除，" +
                                "但不会影响你的学习记录。"
                )
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (dialog, which) -> deleteBook(item.bookId))
                .show();
    }

    private void deleteBook(int bookId) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        db.beginTransaction();

        try {
            db.delete(
                    "book_word_relation",
                    "book_id = ?",
                    new String[]{String.valueOf(bookId)}
            );

            db.delete(
                    "word_book",
                    "book_id = ? AND user_id = ?",
                    new String[]{
                            String.valueOf(bookId),
                            String.valueOf(currentUserId)
                    }
            );

            db.setTransactionSuccessful();

            Toast.makeText(this, "单词本已删除", Toast.LENGTH_SHORT).show();
        } finally {
            db.endTransaction();
        }

        loadWordBooks();
    }

    private void showBookWords(int bookId, String bookName) {
        List<BookWordItem> wordItems = loadBookWordItems(bookId);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(8), dp(20), 0);

        TextView title = new TextView(this);
        title.setText(bookName);
        title.setTextColor(Color.parseColor("#1F3B5B"));
        title.setTextSize(22);
        title.setTypeface(null, android.graphics.Typeface.BOLD);

        root.addView(
                title,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                )
        );

        TextView subtitle = new TextView(this);
        subtitle.setText("共 " + wordItems.size() + " 个收藏单词");
        subtitle.setTextColor(Color.parseColor("#718096"));
        subtitle.setTextSize(14);
        subtitle.setPadding(0, dp(6), 0, dp(12));

        root.addView(
                subtitle,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                )
        );

        if (wordItems.isEmpty()) {
            TextView emptyText = new TextView(this);
            emptyText.setText("这个单词本里还没有收藏单词");
            emptyText.setTextColor(Color.parseColor("#718096"));
            emptyText.setTextSize(16);
            emptyText.setGravity(Gravity.CENTER);
            emptyText.setPadding(0, dp(24), 0, dp(30));

            root.addView(
                    emptyText,
                    new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                    )
            );
        } else {
            ListView wordListView = new ListView(this);
            wordListView.setDivider(null);
            wordListView.setDividerHeight(0);
            wordListView.setAdapter(new BookWordAdapter(wordItems));

            root.addView(
                    wordListView,
                    new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            dp(420)
                    )
            );
        }

        new AlertDialog.Builder(this)
                .setView(root)
                .setPositiveButton("关闭", null)
                .show();
    }

    private List<BookWordItem> loadBookWordItems(int bookId) {
        List<BookWordItem> wordItems = new ArrayList<>();

        SQLiteDatabase db = dbHelper.getReadableDatabase();

        Cursor cursor = db.rawQuery(
                "SELECT r.relation_id, e.word, e.phonetic, e.translation " +
                        "FROM book_word_relation r " +
                        "JOIN ecdict e ON r.word = e.word " +
                        "JOIN word_book b ON r.book_id = b.book_id " +
                        "WHERE r.book_id = ? AND b.user_id = ? " +
                        "ORDER BY r.add_time DESC",
                new String[]{
                        String.valueOf(bookId),
                        String.valueOf(currentUserId)
                }
        );

        while (cursor.moveToNext()) {
            BookWordItem item = new BookWordItem();
            item.relationId = cursor.getInt(0);
            item.word = cursor.getString(1);
            item.phonetic = cursor.getString(2);
            item.translation = cursor.getString(3);

            wordItems.add(item);
        }

        cursor.close();

        return wordItems;
    }

    private void confirmDeleteWord(BookWordItem item, BookWordAdapter adapter) {
        new AlertDialog.Builder(this)
                .setTitle("删除收藏单词")
                .setMessage("确定要从单词本中删除 \"" + item.word + "\" 吗？")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (dialog, which) -> {
                    if (deleteWordFromBook(item.relationId)) {
                        adapter.removeItem(item);
                        loadWordBooks();
                    }
                })
                .show();
    }

    private boolean deleteWordFromBook(int relationId) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        int rows = db.delete(
                "book_word_relation",
                "relation_id = ?",
                new String[]{String.valueOf(relationId)}
        );

        if (rows > 0) {
            Toast.makeText(this, "已从单词本删除", Toast.LENGTH_SHORT).show();
            return true;
        }

        Toast.makeText(this, "删除失败，请重试", Toast.LENGTH_SHORT).show();
        return false;
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

    private MaterialCardView createCard() {
        MaterialCardView cardView = new MaterialCardView(this);
        cardView.setCardBackgroundColor(Color.WHITE);
        cardView.setCardElevation(dp(1));
        cardView.setRadius(dp(16));
        cardView.setStrokeColor(Color.parseColor("#E2E8F0"));
        cardView.setStrokeWidth(dp(1));
        cardView.setUseCompatPadding(true);

        return cardView;
    }

    private class BookAdapter extends BaseAdapter {

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
                LinearLayout wrapper = new LinearLayout(WordBookActivity.this);
                wrapper.setOrientation(LinearLayout.VERTICAL);
                wrapper.setPadding(0, dp(6), 0, dp(6));
                wrapper.setLayoutParams(
                        new AbsListView.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                );

                MaterialCardView card = createCard();

                LinearLayout content = new LinearLayout(WordBookActivity.this);
                content.setGravity(Gravity.CENTER_VERTICAL);
                content.setOrientation(LinearLayout.HORIZONTAL);
                content.setPadding(dp(16), dp(14), dp(12), dp(14));

                TextView badge = new TextView(WordBookActivity.this);
                badge.setText("词本");
                badge.setTextColor(Color.parseColor("#2C5282"));
                badge.setTextSize(12);
                badge.setGravity(Gravity.CENTER);
                badge.setBackground(roundedBackground(Color.parseColor("#E6EEF8"), 12));

                content.addView(
                        badge,
                        new LinearLayout.LayoutParams(dp(44), dp(32))
                );

                LinearLayout textContainer = new LinearLayout(WordBookActivity.this);
                textContainer.setOrientation(LinearLayout.VERTICAL);
                textContainer.setPadding(dp(12), 0, dp(8), 0);

                TextView tvBookName = new TextView(WordBookActivity.this);
                tvBookName.setTextColor(Color.parseColor("#1F2937"));
                tvBookName.setTextSize(17);
                tvBookName.setTypeface(null, android.graphics.Typeface.BOLD);
                tvBookName.setMaxLines(1);

                TextView tvBookInfo = new TextView(WordBookActivity.this);
                tvBookInfo.setTextColor(Color.parseColor("#718096"));
                tvBookInfo.setTextSize(13);
                tvBookInfo.setPadding(0, dp(4), 0, 0);

                textContainer.addView(tvBookName);
                textContainer.addView(tvBookInfo);

                content.addView(
                        textContainer,
                        new LinearLayout.LayoutParams(
                                0,
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                                1
                        )
                );

                TextView arrow = new TextView(WordBookActivity.this);
                arrow.setText("›");
                arrow.setTextColor(Color.parseColor("#94A3B8"));
                arrow.setTextSize(30);
                arrow.setGravity(Gravity.CENTER);

                content.addView(
                        arrow,
                        new LinearLayout.LayoutParams(dp(28), dp(40))
                );

                card.addView(content);

                wrapper.addView(
                        card,
                        new LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                );

                holder = new BookRowHolder();
                holder.tvBookName = tvBookName;
                holder.tvBookInfo = tvBookInfo;

                wrapper.setTag(holder);
                convertView = wrapper;
            } else {
                holder = (BookRowHolder) convertView.getTag();
            }

            BookItem item = bookItems.get(position);

            holder.tvBookName.setText(item.bookName);
            holder.tvBookInfo.setText(
                    "收藏 " + item.wordCount + " 个单词 · 长按可管理"
            );

            return convertView;
        }
    }

    private static class BookRowHolder {
        TextView tvBookName;
        TextView tvBookInfo;
    }

    private class BookWordAdapter extends BaseAdapter {

        private final List<BookWordItem> wordItems;

        BookWordAdapter(List<BookWordItem> wordItems) {
            this.wordItems = wordItems;
        }

        @Override
        public int getCount() {
            return wordItems.size();
        }

        @Override
        public Object getItem(int position) {
            return wordItems.get(position);
        }

        @Override
        public long getItemId(int position) {
            return wordItems.get(position).relationId;
        }

        void removeItem(BookWordItem item) {
            wordItems.remove(item);
            notifyDataSetChanged();

            if (wordItems.isEmpty()) {
                Toast.makeText(
                        WordBookActivity.this,
                        "这个单词本已经没有收藏单词了",
                        Toast.LENGTH_SHORT
                ).show();
            }
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            WordRowHolder holder;

            if (convertView == null) {
                LinearLayout wrapper = new LinearLayout(WordBookActivity.this);
                wrapper.setOrientation(LinearLayout.VERTICAL);
                wrapper.setPadding(0, dp(5), 0, dp(5));
                wrapper.setLayoutParams(
                        new AbsListView.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                );

                MaterialCardView card = createCard();

                LinearLayout content = new LinearLayout(WordBookActivity.this);
                content.setOrientation(LinearLayout.HORIZONTAL);
                content.setGravity(Gravity.CENTER_VERTICAL);
                content.setPadding(dp(16), dp(13), dp(8), dp(13));

                LinearLayout textContainer = new LinearLayout(WordBookActivity.this);
                textContainer.setOrientation(LinearLayout.VERTICAL);

                TextView tvWord = new TextView(WordBookActivity.this);
                tvWord.setTextColor(Color.parseColor("#1F3B5B"));
                tvWord.setTextSize(17);
                tvWord.setTypeface(null, android.graphics.Typeface.BOLD);

                TextView tvTranslation = new TextView(WordBookActivity.this);
                tvTranslation.setTextColor(Color.parseColor("#4A5568"));
                tvTranslation.setTextSize(14);
                tvTranslation.setPadding(0, dp(5), 0, 0);

                textContainer.addView(tvWord);
                textContainer.addView(tvTranslation);

                content.addView(
                        textContainer,
                        new LinearLayout.LayoutParams(
                                0,
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                                1
                        )
                );

                ImageButton btnDeleteWord = new ImageButton(WordBookActivity.this);
                btnDeleteWord.setImageResource(android.R.drawable.ic_menu_delete);
                btnDeleteWord.setColorFilter(Color.parseColor("#E45858"));
                btnDeleteWord.setBackgroundColor(Color.TRANSPARENT);
                btnDeleteWord.setPadding(dp(10), dp(10), dp(10), dp(10));
                btnDeleteWord.setContentDescription("删除收藏单词");

                content.addView(
                        btnDeleteWord,
                        new LinearLayout.LayoutParams(dp(48), dp(48))
                );

                card.addView(content);

                wrapper.addView(
                        card,
                        new LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                );

                holder = new WordRowHolder();
                holder.tvWord = tvWord;
                holder.tvTranslation = tvTranslation;
                holder.btnDeleteWord = btnDeleteWord;

                wrapper.setTag(holder);
                convertView = wrapper;
            } else {
                holder = (WordRowHolder) convertView.getTag();
            }

            BookWordItem item = wordItems.get(position);

            StringBuilder wordText = new StringBuilder(item.word);

            if (item.phonetic != null && !item.phonetic.trim().isEmpty()) {
                wordText.append("  [")
                        .append(item.phonetic.trim())
                        .append("]");
            }

            holder.tvWord.setText(wordText.toString());

            if (item.translation == null || item.translation.trim().isEmpty()) {
                holder.tvTranslation.setText("暂无释义");
            } else {
                holder.tvTranslation.setText(item.translation.trim());
            }

            holder.btnDeleteWord.setContentDescription("删除 " + item.word);

            holder.btnDeleteWord.setOnClickListener(v ->
                    confirmDeleteWord(item, BookWordAdapter.this)
            );

            return convertView;
        }
    }

    private static class WordRowHolder {
        TextView tvWord;
        TextView tvTranslation;
        ImageButton btnDeleteWord;
    }
}