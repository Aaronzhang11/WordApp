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

/**
 * SQLite 数据库管理类。
 *
 * 本次新增：
 * study_history 表
 *
 * 该表用于记录用户每一次真实学习行为：
 * - 点击“认识”
 * - 点击“不认识”
 *
 * 用于修复：
 * - 今日学习数量
 * - 连续学习天数
 * - 近 7 天学习趋势
 */
public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "word_app.db";

    /*
     * 数据库版本从 3 升级为 4。
     *
     * 已安装旧版本 App 的用户，会触发 onUpgrade()，
     * 并自动创建 study_history 表。
     */
    private static final int DATABASE_VERSION = 4;

    private final Context context;
    private final String dbPath;

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);

        this.context = context;
        this.dbPath = context.getDatabasePath(DATABASE_NAME).getPath();

        try {
            copyDatabaseIfNeeded();
        } catch (IOException e) {
            throw new RuntimeException(
                    "复制 assets 中的 word_app.db 失败，请检查 app/src/main/assets/word_app.db 是否存在",
                    e
            );
        }
    }

    /**
     * 首次运行时，把 assets 中的基础词库数据库复制到 App 私有数据库目录。
     */
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

        // 启用外键约束
        db.setForeignKeyConstraintsEnabled(true);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        createUserTables(db);
    }

    @Override
    public void onOpen(SQLiteDatabase db) {
        super.onOpen(db);

        /*
         * 即使用户已有旧数据库，
         * 每次打开时也会检查并补齐缺失表。
         */
        createUserTables(db);
    }

    /**
     * 创建所有用户相关表。
     */
    private void createUserTables(SQLiteDatabase db) {

        // 1. 本地账户表
        db.execSQL(
                "CREATE TABLE IF NOT EXISTS user_info ("
                        + "user_id INTEGER PRIMARY KEY AUTOINCREMENT, "
                        + "username TEXT UNIQUE NOT NULL, "
                        + "password_hash TEXT NOT NULL, "
                        + "create_time INTEGER NOT NULL, "
                        + "last_login_time INTEGER DEFAULT 0)"
        );

        // 默认游客用户
        db.execSQL(
                "INSERT OR IGNORE INTO user_info "
                        + "(user_id, username, password_hash, create_time, last_login_time) "
                        + "VALUES (0, 'guest', 'guest', 0, 0)"
        );

        // 2. 单词学习状态表
        db.execSQL(
                "CREATE TABLE IF NOT EXISTS study_record ("
                        + "record_id INTEGER PRIMARY KEY AUTOINCREMENT, "
                        + "user_id INTEGER NOT NULL DEFAULT 0, "
                        + "word TEXT NOT NULL, "
                        + "master_level INTEGER DEFAULT 0, "
                        + "next_review_time INTEGER DEFAULT 0, "
                        + "error_count INTEGER DEFAULT 0, "
                        + "is_ignored INTEGER DEFAULT 0, "
                        + "UNIQUE(user_id, word), "
                        + "FOREIGN KEY(user_id) REFERENCES user_info(user_id) ON DELETE CASCADE, "
                        + "FOREIGN KEY(word) REFERENCES ecdict(word))"
        );

        // 3. 用户收藏单词本表
        db.execSQL(
                "CREATE TABLE IF NOT EXISTS word_book ("
                        + "book_id INTEGER PRIMARY KEY AUTOINCREMENT, "
                        + "user_id INTEGER NOT NULL DEFAULT 0, "
                        + "book_name TEXT NOT NULL, "
                        + "create_time INTEGER NOT NULL, "
                        + "UNIQUE(user_id, book_name), "
                        + "FOREIGN KEY(user_id) REFERENCES user_info(user_id) ON DELETE CASCADE)"
        );

        // 4. 收藏词与单词本关系表
        db.execSQL(
                "CREATE TABLE IF NOT EXISTS book_word_relation ("
                        + "relation_id INTEGER PRIMARY KEY AUTOINCREMENT, "
                        + "book_id INTEGER NOT NULL, "
                        + "word TEXT NOT NULL, "
                        + "add_time INTEGER NOT NULL, "
                        + "UNIQUE(book_id, word), "
                        + "FOREIGN KEY(book_id) REFERENCES word_book(book_id) ON DELETE CASCADE, "
                        + "FOREIGN KEY(word) REFERENCES ecdict(word))"
        );

        // 5. 测试记录表
        db.execSQL(
                "CREATE TABLE IF NOT EXISTS test_record ("
                        + "test_id INTEGER PRIMARY KEY AUTOINCREMENT, "
                        + "user_id INTEGER NOT NULL DEFAULT 0, "
                        + "test_type TEXT NOT NULL, "
                        + "total_questions INTEGER NOT NULL, "
                        + "correct_count INTEGER NOT NULL, "
                        + "test_date INTEGER NOT NULL, "
                        + "FOREIGN KEY(user_id) REFERENCES user_info(user_id) ON DELETE CASCADE)"
        );

        // 6. 自定义学习词书表
        db.execSQL(
                "CREATE TABLE IF NOT EXISTS vocab_book ("
                        + "book_id INTEGER PRIMARY KEY AUTOINCREMENT, "
                        + "user_id INTEGER NOT NULL DEFAULT 0, "
                        + "book_name TEXT NOT NULL, "
                        + "tags TEXT NOT NULL, "
                        + "create_time INTEGER NOT NULL, "
                        + "UNIQUE(user_id, book_name), "
                        + "FOREIGN KEY(user_id) REFERENCES user_info(user_id) ON DELETE CASCADE)"
        );

        /*
         * 7. 新增：真实学习历史表
         *
         * 一次点击“认识”或“不认识”对应一条记录。
         *
         * 注意：
         * next_review_time 只表示未来复习时间；
         * study_time 才表示用户实际学习发生的时间。
         */
        db.execSQL(
                "CREATE TABLE IF NOT EXISTS study_history ("
                        + "history_id INTEGER PRIMARY KEY AUTOINCREMENT, "
                        + "user_id INTEGER NOT NULL, "
                        + "word TEXT NOT NULL, "
                        + "study_time INTEGER NOT NULL, "
                        + "result TEXT NOT NULL, "
                        + "old_level INTEGER NOT NULL, "
                        + "new_level INTEGER NOT NULL, "
                        + "FOREIGN KEY(user_id) REFERENCES user_info(user_id) ON DELETE CASCADE, "
                        + "FOREIGN KEY(word) REFERENCES ecdict(word))"
        );

        /*
         * 给“按用户 + 时间统计”建立索引。
         *
         * 首页今日学习、连续天数、趋势图都会频繁使用这两个字段。
         */
        db.execSQL(
                "CREATE INDEX IF NOT EXISTS idx_study_history_user_time "
                        + "ON study_history(user_id, study_time)"
        );

        db.execSQL(
                "CREATE INDEX IF NOT EXISTS idx_study_history_word_time "
                        + "ON study_history(word, study_time)"
        );

        /*
         * 兼容旧数据库：
         * 老版本的部分表可能缺少 user_id。
         */
        upgradeOldUserTables(db);
    }

    /**
     * 兼容旧数据库表结构。
     */
    private void upgradeOldUserTables(SQLiteDatabase db) {
        addColumnIfNotExists(
                db,
                "study_record",
                "user_id",
                "INTEGER NOT NULL DEFAULT 0"
        );

        addColumnIfNotExists(
                db,
                "word_book",
                "user_id",
                "INTEGER NOT NULL DEFAULT 0"
        );

        addColumnIfNotExists(
                db,
                "test_record",
                "user_id",
                "INTEGER NOT NULL DEFAULT 0"
        );
    }

    /**
     * 如果表中缺少字段，则自动添加。
     */
    private void addColumnIfNotExists(
            SQLiteDatabase db,
            String tableName,
            String columnName,
            String columnType
    ) {
        if (!tableExists(db, tableName)) {
            return;
        }

        if (!columnExists(db, tableName, columnName)) {
            db.execSQL(
                    "ALTER TABLE " + tableName
                            + " ADD COLUMN "
                            + columnName
                            + " "
                            + columnType
            );
        }
    }

    /**
     * 判断表是否存在。
     */
    private boolean tableExists(SQLiteDatabase db, String tableName) {
        Cursor cursor = db.rawQuery(
                "SELECT name FROM sqlite_master "
                        + "WHERE type='table' AND name=?",
                new String[]{tableName}
        );

        boolean exists = cursor.moveToFirst();
        cursor.close();

        return exists;
    }

    /**
     * 判断某个字段是否存在。
     */
    private boolean columnExists(
            SQLiteDatabase db,
            String tableName,
            String columnName
    ) {
        Cursor cursor = db.rawQuery(
                "PRAGMA table_info(" + tableName + ")",
                null
        );

        boolean exists = false;

        while (cursor.moveToNext()) {
            String name = cursor.getString(
                    cursor.getColumnIndexOrThrow("name")
            );

            if (columnName.equals(name)) {
                exists = true;
                break;
            }
        }

        cursor.close();

        return exists;
    }

    /**
     * 判断用户名是否已经存在。
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

    /**
     * 注册本地用户。
     */
    public boolean registerUser(
            String username,
            String passwordHash
    ) {
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

    /**
     * 检查登录是否成功。
     */
    public boolean checkLogin(
            String username,
            String passwordHash
    ) {
        SQLiteDatabase db = getReadableDatabase();

        Cursor cursor = db.rawQuery(
                "SELECT user_id FROM user_info "
                        + "WHERE username = ? AND password_hash = ?",
                new String[]{username, passwordHash}
        );

        boolean success = cursor.moveToFirst();
        cursor.close();

        if (success) {
            updateLastLoginTime(username);
        }

        return success;
    }

    /**
     * 根据用户名获取 user_id。
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

    /**
     * 根据 user_id 获取用户名。
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

    /**
     * 更新最后登录时间。
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
    public void onUpgrade(
            SQLiteDatabase db,
            int oldVersion,
            int newVersion
    ) {
        createUserTables(db);
    }
}