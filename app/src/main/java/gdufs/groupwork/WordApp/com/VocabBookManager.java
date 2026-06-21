package gdufs.groupwork.WordApp.com;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 词书模块：管理默认全词库与用户自定义标签词书。
 * 默认词书为 ECDICT 全词库，自定义词书按 ECDICT tag 筛选，上限 5 本（含默认）。
 */
public class VocabBookManager {

    /** 默认词书 ID（虚拟，不存数据库） */
    public static final int DEFAULT_BOOK_ID = 0;

    /** 默认词书名称 */
    public static final String DEFAULT_BOOK_NAME = "ECDICT全词库";

    /** 含默认词书在内的最大词书数量 */
    public static final int MAX_BOOK_COUNT = 5;

    private static final String PREF_NAME = "vocab_book";
    private static final String KEY_CURRENT_BOOK_PREFIX = "current_book_";

    /** ECDICT 支持的标签及中文名 */
    private static final Map<String, String> TAG_LABELS = new LinkedHashMap<>();

    static {
        TAG_LABELS.put("zk", "中考");
        TAG_LABELS.put("gk", "高考");
        TAG_LABELS.put("cet4", "四级");
        TAG_LABELS.put("cet6", "六级");
        TAG_LABELS.put("ky", "考研");
        TAG_LABELS.put("toefl", "托福");
        TAG_LABELS.put("ielts", "雅思");
        TAG_LABELS.put("gre", "GRE");
    }

    private final Context context;
    private final SharedPreferences preferences;
    private final DatabaseHelper dbHelper;

    /**
     * 词书数据模型。
     */
    public static class VocabBook {
        public int bookId;
        public String bookName;
        /** 逗号分隔的标签，默认词书为空 */
        public List<String> tags = new ArrayList<>();
        public boolean isDefault;
        public int wordCount;
        public int learnedCount;
    }

    public VocabBookManager(Context context) {
        this.context = context.getApplicationContext();
        preferences = this.context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        dbHelper = new DatabaseHelper(this.context);
    }

    /**
     * 获取所有可用标签。
     */
    public static List<String> getAllTags() {
        return new ArrayList<>(TAG_LABELS.keySet());
    }

    /**
     * 标签对应的中文显示名。
     */
    public static String getTagLabel(String tag) {
        String label = TAG_LABELS.get(tag);
        return label != null ? label : tag;
    }

    /**
     * 将标签列表格式化为可读描述。
     */
    public static String formatTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return "全词库";
        }

        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < tags.size(); i++) {
            if (i > 0) {
                sb.append("、");
            }
            sb.append(getTagLabel(tags.get(i)));
        }

        return sb.toString();
    }

    /**
     * 获取当前用户选中的词书 ID。
     */
    public int getCurrentBookId(int userId) {
        return preferences.getInt(KEY_CURRENT_BOOK_PREFIX + userId, DEFAULT_BOOK_ID);
    }

    /**
     * 设置当前用户使用的词书。
     */
    public void setCurrentBookId(int userId, int bookId) {
        preferences.edit()
                .putInt(KEY_CURRENT_BOOK_PREFIX + userId, bookId)
                .apply();
    }

    /**
     * 获取当前词书信息（含词数与学习进度）。
     */
    public VocabBook getCurrentBook(int userId) {
        int bookId = getCurrentBookId(userId);
        return getBookById(userId, bookId);
    }

    /**
     * 根据 ID 获取词书详情。
     */
    public VocabBook getBookById(int userId, int bookId) {
        if (bookId == DEFAULT_BOOK_ID) {
            return buildDefaultBook(userId);
        }

        SQLiteDatabase db = dbHelper.getReadableDatabase();

        Cursor cursor = db.rawQuery(
                "SELECT book_id, book_name, tags FROM vocab_book "
                        + "WHERE book_id = ? AND user_id = ?",
                new String[]{String.valueOf(bookId), String.valueOf(userId)}
        );

        VocabBook book = null;

        if (cursor.moveToFirst()) {
            book = new VocabBook();
            book.bookId = cursor.getInt(0);
            book.bookName = cursor.getString(1);
            book.tags = parseTags(cursor.getString(2));
            book.isDefault = false;
        }

        cursor.close();

        if (book == null) {
            setCurrentBookId(userId, DEFAULT_BOOK_ID);
            return buildDefaultBook(userId);
        }

        fillBookStats(db, userId, book);
        return book;
    }

    /**
     * 获取用户全部词书列表（默认词书始终在首位）。
     */
    public List<VocabBook> getAllBooks(int userId) {
        List<VocabBook> books = new ArrayList<>();
        books.add(buildDefaultBook(userId));

        SQLiteDatabase db = dbHelper.getReadableDatabase();

        Cursor cursor = db.rawQuery(
                "SELECT book_id, book_name, tags FROM vocab_book "
                        + "WHERE user_id = ? ORDER BY create_time ASC",
                new String[]{String.valueOf(userId)}
        );

        while (cursor.moveToNext()) {
            VocabBook book = new VocabBook();
            book.bookId = cursor.getInt(0);
            book.bookName = cursor.getString(1);
            book.tags = parseTags(cursor.getString(2));
            book.isDefault = false;
            fillBookStats(db, userId, book);
            books.add(book);
        }

        cursor.close();
        return books;
    }

    /**
     * 当前是否还能创建新词书。
     */
    public boolean canCreateMoreBooks(int userId) {
        return getAllBooks(userId).size() < MAX_BOOK_COUNT;
    }

    /**
     * 创建自定义词书。
     *
     * @return 新词书 ID，失败返回 -1
     */
    public long createCustomBook(int userId, String bookName, List<String> tags) {
        if (bookName == null || bookName.trim().isEmpty()) {
            return -1;
        }

        if (tags == null || tags.isEmpty()) {
            return -1;
        }

        if (!canCreateMoreBooks(userId)) {
            return -1;
        }

        if (DEFAULT_BOOK_NAME.equals(bookName.trim())) {
            return -1;
        }

        SQLiteDatabase db = dbHelper.getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put("user_id", userId);
        values.put("book_name", bookName.trim());
        values.put("tags", joinTags(tags));
        values.put("create_time", System.currentTimeMillis());

        return db.insert("vocab_book", null, values);
    }

    /**
     * 删除自定义词书；若删除的是当前词书则回退到默认词书。
     */
    public boolean deleteCustomBook(int userId, int bookId) {
        if (bookId == DEFAULT_BOOK_ID) {
            return false;
        }

        SQLiteDatabase db = dbHelper.getWritableDatabase();

        int rows = db.delete(
                "vocab_book",
                "book_id = ? AND user_id = ?",
                new String[]{String.valueOf(bookId), String.valueOf(userId)}
        );

        if (rows > 0 && getCurrentBookId(userId) == bookId) {
            setCurrentBookId(userId, DEFAULT_BOOK_ID);
        }

        return rows > 0;
    }

    /**
     * 统计词书内总词数。
     */
    public int countWordsInBook(VocabBook book) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        TagFilter filter = buildTagFilter(book.tags);

        String sql = "SELECT COUNT(*) FROM ecdict WHERE " + filter.whereClause;

        Cursor cursor = db.rawQuery(sql, filter.args);

        int count = 0;

        if (cursor.moveToFirst()) {
            count = cursor.getInt(0);
        }

        cursor.close();
        return count;
    }

    /**
     * 统计词书内已加入学习计划的词数。
     */
    public int countLearnedInBook(int userId, VocabBook book) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        TagFilter joinFilter = buildTagFilter(book.tags, "e");

        String sql = "SELECT COUNT(*) FROM study_record s "
                + "JOIN ecdict e ON s.word = e.word "
                + "WHERE s.user_id = ? AND s.is_ignored = 0 AND "
                + joinFilter.whereClause;

        String[] args = prependArg(String.valueOf(userId), joinFilter.args);

        Cursor cursor = db.rawQuery(sql, args);

        int count = 0;

        if (cursor.moveToFirst()) {
            count = cursor.getInt(0);
        }

        cursor.close();
        return count;
    }

    /**
     * 构建词书范围的 ecdict 子查询 WHERE 条件（仅 ecdict 字段）。
     */
    public static TagFilter buildTagFilter(List<String> tags) {
        return buildTagFilter(tags, null);
    }

    /**
     * 构建标签筛选 WHERE 条件，可指定表别名前缀。
     */
    public static TagFilter buildTagFilter(List<String> tags, String tableAlias) {
        if (tags == null || tags.isEmpty()) {
            return new TagFilter("1=1", new String[0]);
        }

        String tagColumn = (tableAlias == null || tableAlias.isEmpty())
                ? "tag"
                : tableAlias + ".tag";

        StringBuilder where = new StringBuilder("(");
        String[] args = new String[tags.size()];

        for (int i = 0; i < tags.size(); i++) {
            if (i > 0) {
                where.append(" OR ");
            }
            where.append("(' ' || COALESCE(")
                    .append(tagColumn)
                    .append(", '') || ' ') LIKE ?");
            args[i] = "% " + tags.get(i).toLowerCase(Locale.US) + " %";
        }

        where.append(")");
        return new TagFilter(where.toString(), args);
    }

    /**
     * 构建「词书内尚未学习」的 ecdict 查询片段。
     */
    public static String buildAvailableWordQuery(List<String> tags) {
        TagFilter filter = buildTagFilter(tags);
        return "SELECT word FROM ecdict WHERE " + filter.whereClause
                + " AND word NOT IN (SELECT word FROM study_record WHERE user_id = ?)";
    }

    /**
     * 标签筛选条件封装。
     */
    public static class TagFilter {
        public final String whereClause;
        public final String[] args;

        TagFilter(String whereClause, String[] args) {
            this.whereClause = whereClause;
            this.args = args;
        }
    }

    private VocabBook buildDefaultBook(int userId) {
        VocabBook book = new VocabBook();
        book.bookId = DEFAULT_BOOK_ID;
        book.bookName = DEFAULT_BOOK_NAME;
        book.tags = new ArrayList<>();
        book.isDefault = true;
        fillBookStats(dbHelper.getReadableDatabase(), userId, book);
        return book;
    }

    private void fillBookStats(SQLiteDatabase db, int userId, VocabBook book) {
        book.wordCount = countWordsInBook(book);
        book.learnedCount = countLearnedInBook(userId, book);
    }

    private static List<String> parseTags(String raw) {
        List<String> tags = new ArrayList<>();

        if (raw == null || raw.trim().isEmpty()) {
            return tags;
        }

        for (String part : raw.split(",")) {
            String tag = part.trim().toLowerCase(Locale.US);

            if (!tag.isEmpty() && TAG_LABELS.containsKey(tag)) {
                tags.add(tag);
            }
        }

        return tags;
    }

    private static String joinTags(List<String> tags) {
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < tags.size(); i++) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append(tags.get(i).trim().toLowerCase(Locale.US));
        }

        return sb.toString();
    }

    private static String[] prependArg(String first, String[] rest) {
        String[] merged = new String[rest.length + 1];
        merged[0] = first;
        System.arraycopy(rest, 0, merged, 1, rest.length);
        return merged;
    }
}
