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
    private final List<String> bookNameList = new ArrayList<>();

    private ArrayAdapter<String> adapter;

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

            if (bookId == -1) {
                Toast.makeText(this, "请先创建单词本", Toast.LENGTH_SHORT).show();
                return;
            }

            showBookWords(bookId, bookNameList.get(position));
        });

        lvWordBooks.setOnItemLongClickListener((parent, view, position, id) -> {
            int bookId = bookIdList.get(position);

            if (bookId == -1) {
                return true;
            }

            confirmDeleteBook(bookId, bookNameList.get(position));
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
        } else {
            Toast.makeText(this, "创建成功", Toast.LENGTH_SHORT).show();
            etBookName.setText("");
            loadWordBooks();
        }
    }

    private void loadWordBooks() {
        displayList.clear();
        bookIdList.clear();
        bookNameList.clear();

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
            bookNameList.add(bookName);
            displayList.add(bookName + "\n共 " + wordCount + " 个单词\n长按可删除单词本");
        }

        cursor.close();

        if (displayList.isEmpty()) {
            displayList.add("暂无单词本，请先创建一个");
            bookIdList.add(-1);
            bookNameList.add("");
        }

        adapter.notifyDataSetChanged();
    }

    private void confirmDeleteBook(int bookId, String bookName) {
        new AlertDialog.Builder(this)
                .setTitle("删除单词本")
                .setMessage("确定要删除单词本 \"" + bookName + "\" 吗？\n\n删除后，该单词本内收藏的单词记录也会被删除，但不会影响学习记录。")
                .setPositiveButton("删除", (dialog, which) -> deleteBook(bookId))
                .setNegativeButton("取消", null)
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

        if (wordItems.isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle(bookName)
                    .setMessage("这个单词本里还没有收藏单词")
                    .setPositiveButton("确定", null)
                    .show();
            return;
        }

        String[] wordArray = new String[wordItems.size()];

        for (int i = 0; i < wordItems.size(); i++) {
            BookWordItem item = wordItems.get(i);

            StringBuilder builder = new StringBuilder();
            builder.append(item.word);

            if (item.phonetic != null && !item.phonetic.trim().isEmpty()) {
                builder.append("  [").append(item.phonetic).append("]");
            }

            builder.append("\n");

            if (item.translation != null && !item.translation.trim().isEmpty()) {
                builder.append(item.translation);
            } else {
                builder.append("暂无释义");
            }

            wordArray[i] = builder.toString();
        }

        new AlertDialog.Builder(this)
                .setTitle(bookName + " - 点击单词可删除")
                .setItems(wordArray, (dialog, which) -> {
                    BookWordItem selectedItem = wordItems.get(which);
                    confirmDeleteWord(bookId, bookName, selectedItem);
                })
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

    private void confirmDeleteWord(int bookId, String bookName, BookWordItem item) {
        new AlertDialog.Builder(this)
                .setTitle("删除收藏单词")
                .setMessage("确定要从单词本中删除 \"" + item.word + "\" 吗？")
                .setPositiveButton("删除", (dialog, which) -> {
                    deleteWordFromBook(item.relationId);
                    showBookWords(bookId, bookName);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void deleteWordFromBook(int relationId) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        int rows = db.delete(
                "book_word_relation",
                "relation_id = ?",
                new String[]{String.valueOf(relationId)}
        );

        if (rows > 0) {
            Toast.makeText(this, "已从单词本删除", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "删除失败", Toast.LENGTH_SHORT).show();
        }

        loadWordBooks();
    }
}