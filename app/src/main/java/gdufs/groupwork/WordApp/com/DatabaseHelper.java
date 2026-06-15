package gdufs.groupwork.WordApp.com;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "word_app.db";

    // 原来是 1，这里改成 2，表示数据库结构升级了
    private static final int DATABASE_VERSION = 2;

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

        /*
         * 1. 本地用户表
         * 注意：这里不是云端账号，只是本地账号。
         * 不同用户登录后，可以拥有不同的学习记录、单词本、测试记录。
         */
        db.execSQL("CREATE TABLE IF NOT EXISTS user_info (" +
                "user_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "username TEXT UNIQUE NOT NULL, " +
                "password_hash TEXT NOT NULL, " +
                "create_time INTEGER NOT NULL, " +
                "last_login_time INTEGER DEFAULT 0)");

        /*
         * 插入一个默认本地用户。
         * 作用：在登录注册页面还没写完之前，旧的学习功能暂时不会因为 user_id 为空而报错。
         * 后面真正登录后，会使用当前登录用户的 user_id。
         */
        db.execSQL("INSERT OR IGNORE INTO user_info " +
                "(user_id, username, password_hash, create_time, last_login_time) " +
                "VALUES (0, 'guest', 'guest', 0, 0)");

        /*
         * 2. 学习记录表
         * 关键变化：
         * 原来是 word TEXT UNIQUE
         * 现在改成 user_id + word 联合唯一
         *
         * 意思是：
         * 同一个用户不能重复学习同一个单词；
         * 但不同用户可以分别拥有 apple 的学习记录。
         */
        db.execSQL("CREATE TABLE IF NOT EXISTS study_record (" +
                "record_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "user_id INTEGER NOT NULL DEFAULT 0, " +
                "word TEXT NOT NULL, " +
                "master_level INTEGER DEFAULT 0, " +
                "next_review_time INTEGER DEFAULT 0, " +
                "error_count INTEGER DEFAULT 0, " +
                "is_ignored INTEGER DEFAULT 0, " +
                "UNIQUE(user_id, word), " +
                "FOREIGN KEY(user_id) REFERENCES user_info(user_id) ON DELETE CASCADE, " +
                "FOREIGN KEY(word) REFERENCES ecdict(word))");

        /*
         * 3. 单词本表
         * 关键变化：
         * 原来是 book_name TEXT UNIQUE
         * 现在改成 user_id + book_name 联合唯一
         *
         * 意思是：
         * A 用户可以创建“四级错词本”；
         * B 用户也可以创建“四级错词本”；
         * 两者互不冲突。
         */
        db.execSQL("CREATE TABLE IF NOT EXISTS word_book (" +
                "book_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "user_id INTEGER NOT NULL DEFAULT 0, " +
                "book_name TEXT NOT NULL, " +
                "create_time INTEGER NOT NULL, " +
                "UNIQUE(user_id, book_name), " +
                "FOREIGN KEY(user_id) REFERENCES user_info(user_id) ON DELETE CASCADE)");

        /*
         * 4. 单词本与单词的关联表
         * 这里不直接加 user_id，因为 book_id 已经属于某个用户。
         */
        db.execSQL("CREATE TABLE IF NOT EXISTS book_word_relation (" +
                "relation_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "book_id INTEGER NOT NULL, " +
                "word TEXT NOT NULL, " +
                "add_time INTEGER NOT NULL, " +
                "UNIQUE(book_id, word), " +
                "FOREIGN KEY(book_id) REFERENCES word_book(book_id) ON DELETE CASCADE, " +
                "FOREIGN KEY(word) REFERENCES ecdict(word))");

        /*
         * 5. 测试记录表
         * 每条测试成绩都绑定 user_id。
         */
        db.execSQL("CREATE TABLE IF NOT EXISTS test_record (" +
                "test_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "user_id INTEGER NOT NULL DEFAULT 0, " +
                "test_type TEXT NOT NULL, " +
                "total_questions INTEGER NOT NULL, " +
                "correct_count INTEGER NOT NULL, " +
                "test_date INTEGER NOT NULL, " +
                "FOREIGN KEY(user_id) REFERENCES user_info(user_id) ON DELETE CASCADE)");
    }

    /*
     * 判断用户名是否已经存在
     */
    public boolean isUsernameExists(String username) {
        SQLiteDatabase db = getReadableDatabase();

        Cursor cursor = db.rawQuery(
                "SELECT user_id FROM user_info WHERE username = ?",
                new String[]{username}
        );

        boolean exists = cursor.moveToFirst();
        cursor.close();

        return exists;
    }

    /*
     * 注册本地用户
     * passwordHash 是加密后的密码，不建议保存明文密码。
     */
    public boolean registerUser(String username, String passwordHash) {
        if (isUsernameExists(username)) {
            return false;
        }

        SQLiteDatabase db = getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put("username", username);
        values.put("password_hash", passwordHash);
        values.put("create_time", System.currentTimeMillis());
        values.put("last_login_time", 0);

        long result = db.insert("user_info", null, values);

        return result != -1;
    }

    /*
     * 检查登录是否成功
     */
    public boolean checkLogin(String username, String passwordHash) {
        SQLiteDatabase db = getReadableDatabase();

        Cursor cursor = db.rawQuery(
                "SELECT user_id FROM user_info WHERE username = ? AND password_hash = ?",
                new String[]{username, passwordHash}
        );

        boolean success = cursor.moveToFirst();
        cursor.close();

        if (success) {
            updateLastLoginTime(username);
        }

        return success;
    }

    /*
     * 根据用户名获取 user_id
     * 登录成功后需要保存这个 user_id，后续学习记录、单词本、测试记录都靠它区分用户。
     */
    public int getUserIdByUsername(String username) {
        SQLiteDatabase db = getReadableDatabase();

        Cursor cursor = db.rawQuery(
                "SELECT user_id FROM user_info WHERE username = ?",
                new String[]{username}
        );

        int userId = -1;

        if (cursor.moveToFirst()) {
            userId = cursor.getInt(0);
        }

        cursor.close();

        return userId;
    }

    /*
     * 根据 user_id 获取用户名
     * 后面首页可以用它显示“当前用户：xxx”。
     */
    public String getUsernameById(int userId) {
        SQLiteDatabase db = getReadableDatabase();

        Cursor cursor = db.rawQuery(
                "SELECT username FROM user_info WHERE user_id = ?",
                new String[]{String.valueOf(userId)}
        );

        String username = "";

        if (cursor.moveToFirst()) {
            username = cursor.getString(0);
        }

        cursor.close();

        return username;
    }

    /*
     * 更新最后登录时间
     */
    private void updateLastLoginTime(String username) {
        SQLiteDatabase db = getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put("last_login_time", System.currentTimeMillis());

        db.update(
                "user_info",
                values,
                "username = ?",
                new String[]{username}
        );
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        createUserTables(db);
    }
}