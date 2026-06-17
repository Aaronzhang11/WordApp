package gdufs.groupwork.WordApp.com;

import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;

public class WordBookActivity extends AppCompatActivity {

    private EditText etBookName;
    private Button btnCreateBook, btnBack;
    private ListView lvWordBooks;

    private DatabaseHelper dbHelper;
    private UserSessionManager sessionManager;
    private int currentUserId;

    private final List<String> displayList = new ArrayList<>();
    private final List<Integer> bookIdList = new ArrayList<>();

    private ArrayAdapter<String> adapter;

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

        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, displayList);
        lvWordBooks.setAdapter(adapter);

        btnBack.setOnClickListener(v -> finish());

        btnCreateBook.setOnClickListener(v -> createWordBook());

        lvWordBooks.setOnItemClickListener((parent, view, position, id) -> {
            int bookId = bookIdList.get(position);
            showBookWords(bookId);
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
        } else {
            Toast.makeText(this, "创建成功", Toast.LENGTH_SHORT).show();
            etBookName.setText("");
            loadWordBooks();
        }
    }

    private void loadWordBooks() {
        displayList.clear();
        bookIdList.clear();

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
            int bookId = cursor.getInt(0);
            String bookName = cursor.getString(1);
            int wordCount = cursor.getInt(2);

            bookIdList.add(bookId);
            displayList.add(bookName + "\n共 " + wordCount + " 个单词");
        }

        cursor.close();

        if (displayList.isEmpty()) {
            displayList.add("暂无单词本，请先创建一个");
            bookIdList.add(-1);
        }

        adapter.notifyDataSetChanged();
    }

    private void showBookWords(int bookId) {
        if (bookId == -1) {
            Toast.makeText(this, "请先创建单词本", Toast.LENGTH_SHORT).show();
            return;
        }

        SQLiteDatabase db = dbHelper.getReadableDatabase();

        Cursor cursor = db.rawQuery(
                "SELECT e.word, e.phonetic, e.translation " +
                        "FROM book_word_relation r " +
                        "JOIN ecdict e ON r.word = e.word " +
                        "WHERE r.book_id = ? " +
                        "ORDER BY r.add_time DESC",
                new String[]{String.valueOf(bookId)}
        );

        StringBuilder builder = new StringBuilder();

        while (cursor.moveToNext()) {
            String word = cursor.getString(0);
            String phonetic = cursor.getString(1);
            String translation = cursor.getString(2);

            builder.append(word);

            if (phonetic != null && !phonetic.trim().isEmpty()) {
                builder.append("  [").append(phonetic).append("]");
            }

            builder.append("\n");

            if (translation != null && !translation.trim().isEmpty()) {
                builder.append(translation).append("\n");
            } else {
                builder.append("暂无释义\n");
            }

            builder.append("\n");
        }

        cursor.close();

        String message = builder.toString().trim();

        if (message.isEmpty()) {
            message = "这个单词本里还没有收藏单词";
        }

        new AlertDialog.Builder(this)
                .setTitle("单词本内容")
                .setMessage(message)
                .setPositiveButton("确定", null)
                .show();
    }
}