package gdufs.groupwork.WordApp.com;

import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

public class WordDetailActivity extends AppCompatActivity {

    public static final String EXTRA_WORD = "extra_word";

    private View btnBack;
    private TextView tvWord;
    private TextView tvPhonetic;
    private TextView tvTranslation;
    private TextView tvCollectionStatus;
    private MaterialButton btnFavorite;

    private DatabaseHelper dbHelper;
    private UserSessionManager sessionManager;
    private int currentUserId;

    private String currentWord = "";

    private static class BookChoice {
        int bookId;
        String bookName;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_word_detail);

        dbHelper = new DatabaseHelper(this);
        sessionManager = new UserSessionManager(this);

        if (!sessionManager.isLoggedIn()) {
            Intent intent = new Intent(WordDetailActivity.this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
            return;
        }

        currentUserId = sessionManager.getCurrentUserId();

        currentWord = getIntent().getStringExtra(EXTRA_WORD);

        if (currentWord == null || currentWord.trim().isEmpty()) {
            Toast.makeText(this, "未找到单词信息", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        currentWord = currentWord.trim();

        initViews();
        loadWordDetails();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (!currentWord.isEmpty()) {
            updateCollectionStatus();
        }
    }

    private void initViews() {
        btnBack = findViewById(R.id.btnBack);
        tvWord = findViewById(R.id.tvWord);
        tvPhonetic = findViewById(R.id.tvPhonetic);
        tvTranslation = findViewById(R.id.tvTranslation);
        tvCollectionStatus = findViewById(R.id.tvCollectionStatus);
        btnFavorite = findViewById(R.id.btnFavorite);

        btnBack.setOnClickListener(v -> finish());

        btnFavorite.setOnClickListener(v -> showBookPicker());
    }

    private void loadWordDetails() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        Cursor cursor = db.rawQuery(
                "SELECT word, phonetic, translation " +
                        "FROM ecdict " +
                        "WHERE word = ? " +
                        "LIMIT 1",
                new String[]{currentWord}
        );

        if (!cursor.moveToFirst()) {
            cursor.close();
            Toast.makeText(this, "未找到该单词详情", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        String word = safeText(cursor.getString(0));
        String phonetic = safeText(cursor.getString(1));
        String translation = safeText(cursor.getString(2));

        cursor.close();

        tvWord.setText(word);
        tvPhonetic.setText(
                phonetic.isEmpty() ? "暂无音标" : formatPhonetic(phonetic)
        );

        if (translation.isEmpty()) {
            tvTranslation.setText("暂无中文释义");
        } else {
            tvTranslation.setText(
                    translation.replace("\\n", "\n")
            );
        }

        updateCollectionStatus();
    }

    private void updateCollectionStatus() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        Cursor cursor = db.rawQuery(
                "SELECT COUNT(*) " +
                        "FROM book_word_relation r " +
                        "JOIN word_book b ON r.book_id = b.book_id " +
                        "WHERE b.user_id = ? AND r.word = ?",
                new String[]{
                        String.valueOf(currentUserId),
                        currentWord
                }
        );

        int count = 0;

        if (cursor.moveToFirst()) {
            count = cursor.getInt(0);
        }

        cursor.close();

        if (count == 0) {
            tvCollectionStatus.setText("暂未收藏到单词本");
            btnFavorite.setText("收藏到单词本");
        } else {
            tvCollectionStatus.setText("已收藏到 " + count + " 个单词本");
            btnFavorite.setText("管理收藏");
        }
    }

    private void showBookPicker() {
        List<BookChoice> books = loadBookChoices();
        List<String> optionNames = new ArrayList<>();

        for (BookChoice book : books) {
            optionNames.add(book.bookName);
        }

        optionNames.add("＋ 新建单词本");

        new AlertDialog.Builder(this)
                .setTitle("收藏「" + currentWord + "」")
                .setItems(optionNames.toArray(new String[0]), (dialog, which) -> {
                    if (which == books.size()) {
                        showCreateBookAndCollectDialog();
                    } else {
                        BookChoice selectedBook = books.get(which);
                        addWordToBook(
                                selectedBook.bookId,
                                selectedBook.bookName
                        );
                    }
                })
                .show();
    }

    private List<BookChoice> loadBookChoices() {
        List<BookChoice> books = new ArrayList<>();

        SQLiteDatabase db = dbHelper.getReadableDatabase();

        Cursor cursor = db.rawQuery(
                "SELECT book_id, book_name " +
                        "FROM word_book " +
                        "WHERE user_id = ? " +
                        "ORDER BY create_time DESC",
                new String[]{String.valueOf(currentUserId)}
        );

        while (cursor.moveToNext()) {
            BookChoice book = new BookChoice();
            book.bookId = cursor.getInt(0);
            book.bookName = safeText(cursor.getString(1));
            books.add(book);
        }

        cursor.close();

        return books;
    }

    private void showCreateBookAndCollectDialog() {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint("例如：四级易错词");
        input.setPadding(dp(12), dp(6), dp(12), dp(6));

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

        AlertDialog createDialog = new AlertDialog.Builder(this)
                .setTitle("新建单词本")
                .setMessage("创建后会自动收藏「" + currentWord + "」")
                .setView(container)
                .setNegativeButton("取消", null)
                .setPositiveButton("创建并收藏", null)
                .create();

        createDialog.setOnShowListener(dialog -> {
            createDialog.getButton(AlertDialog.BUTTON_POSITIVE)
                    .setOnClickListener(v -> {
                        String bookName = input.getText().toString().trim();

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

                        int bookId;

                        if (result == -1) {
                            bookId = findBookIdByName(bookName);

                            if (bookId == -1) {
                                Toast.makeText(this, "创建单词本失败", Toast.LENGTH_SHORT).show();
                                return;
                            }
                        } else {
                            bookId = (int) result;
                        }

                        addWordToBook(bookId, bookName);
                        createDialog.dismiss();
                    });
        });

        createDialog.show();
    }

    private int findBookIdByName(String bookName) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        Cursor cursor = db.rawQuery(
                "SELECT book_id FROM word_book " +
                        "WHERE user_id = ? AND book_name = ?",
                new String[]{
                        String.valueOf(currentUserId),
                        bookName
                }
        );

        int bookId = -1;

        if (cursor.moveToFirst()) {
            bookId = cursor.getInt(0);
        }

        cursor.close();

        return bookId;
    }

    private void addWordToBook(int bookId, String bookName) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put("book_id", bookId);
        values.put("word", currentWord);
        values.put("add_time", System.currentTimeMillis());

        long result = db.insertWithOnConflict(
                "book_word_relation",
                null,
                values,
                SQLiteDatabase.CONFLICT_IGNORE
        );

        if (result == -1) {
            Toast.makeText(
                    this,
                    "该单词已收藏在「" + bookName + "」中",
                    Toast.LENGTH_SHORT
            ).show();
        } else {
            Toast.makeText(
                    this,
                    "已收藏到「" + bookName + "」",
                    Toast.LENGTH_SHORT
            ).show();
        }

        updateCollectionStatus();
    }

    private String safeText(String value) {
        return value == null ? "" : value.trim();
    }

    private String formatPhonetic(String phonetic) {
        String value = phonetic.trim();

        if (value.startsWith("[") || value.startsWith("/")) {
            return value;
        }

        return "/" + value + "/";
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}