package gdufs.groupwork.WordApp.com;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class DatabaseHelper extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "word_app.db";
    private static final int DATABASE_VERSION = 1;

    private final Context context;
    private final String dbPath;

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        this.context = context;
        this.dbPath = context.getDatabasePath(DATABASE_NAME).getPath();

        try {
            copyDatabaseIfNeeded();
        } catch (IOException e) {
            throw new RuntimeException("复制 assets 中的 word_app.db 失败，请检查 app/src/main/assets/word_app.db 是否存在", e);
        }
    }

    private void copyDatabaseIfNeeded() throws IOException {
        File dbFile = new File(dbPath);

        if (dbFile.exists()) {
            return;
        }

        File dbDir = dbFile.getParentFile();
        if (dbDir != null && !dbDir.exists()) {
            dbDir.mkdirs();
        }

        InputStream inputStream = context.getAssets().open(DATABASE_NAME);
        FileOutputStream outputStream = new FileOutputStream(dbFile);

        byte[] buffer = new byte[4096];
        int length;

        while ((length = inputStream.read(buffer)) > 0) {
            outputStream.write(buffer, 0, length);
        }

        outputStream.flush();
        outputStream.close();
        inputStream.close();
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        db.setForeignKeyConstraintsEnabled(true);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        createUserTables(db);
    }

    @Override
    public void onOpen(SQLiteDatabase db) {
        super.onOpen(db);
        createUserTables(db);
    }

    private void createUserTables(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS study_record (" +
                "record_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "word TEXT UNIQUE, " +
                "master_level INTEGER DEFAULT 0, " +
                "next_review_time INTEGER DEFAULT 0, " +
                "error_count INTEGER DEFAULT 0, " +
                "is_ignored INTEGER DEFAULT 0, " +
                "FOREIGN KEY(word) REFERENCES ecdict(word))");

        db.execSQL("CREATE TABLE IF NOT EXISTS word_book (" +
                "book_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "book_name TEXT UNIQUE NOT NULL, " +
                "create_time INTEGER NOT NULL)");

        db.execSQL("CREATE TABLE IF NOT EXISTS book_word_relation (" +
                "relation_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "book_id INTEGER, " +
                "word TEXT, " +
                "add_time INTEGER NOT NULL, " +
                "FOREIGN KEY(book_id) REFERENCES word_book(book_id) ON DELETE CASCADE, " +
                "FOREIGN KEY(word) REFERENCES ecdict(word))");

        db.execSQL("CREATE TABLE IF NOT EXISTS test_record (" +
                "test_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "test_type TEXT NOT NULL, " +
                "total_questions INTEGER NOT NULL, " +
                "correct_count INTEGER NOT NULL, " +
                "test_date INTEGER NOT NULL)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        createUserTables(db);
    }
}