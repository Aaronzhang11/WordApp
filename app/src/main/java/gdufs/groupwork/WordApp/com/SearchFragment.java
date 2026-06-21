package gdufs.groupwork.WordApp.com;

import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.List;

/**
 * 词典模块：复用 activity_search 布局与原有检索逻辑。
 */
public class SearchFragment extends Fragment {

    private static final String ARG_STANDALONE = "standalone";

    private View btnBack;
    private View btnClearSearch;
    private EditText etSearchInput;
    private ListView lvSearchResults;
    private TextView tvSearchHint;
    private TextView tvEmpty;

    private DatabaseHelper dbHelper;
    private UserSessionManager sessionManager;
    private int currentUserId;

    private final List<SearchResult> resultList = new ArrayList<>();
    private SearchResultAdapter resultAdapter;

    private static class SearchResult {
        String word;
        String phonetic;
        String translation;
    }

    private static class BookChoice {
        int bookId;
        String bookName;
    }

    public static SearchFragment newInstance(boolean standalone) {
        SearchFragment fragment = new SearchFragment();
        Bundle args = new Bundle();
        args.putBoolean(ARG_STANDALONE, standalone);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        return inflater.inflate(R.layout.activity_search, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        dbHelper = new DatabaseHelper(requireContext());
        sessionManager = new UserSessionManager(requireContext());
        currentUserId = sessionManager.getCurrentUserId();

        initViews(view);
    }

    /**
     * 绑定词典页控件与事件。
     */
    private void initViews(View view) {
        boolean standalone = getArguments() != null
                && getArguments().getBoolean(ARG_STANDALONE, false);

        btnBack = view.findViewById(R.id.btnBack);
        btnClearSearch = view.findViewById(R.id.btnClearSearch);
        etSearchInput = view.findViewById(R.id.etSearchInput);
        lvSearchResults = view.findViewById(R.id.lvSearchResults);
        tvSearchHint = view.findViewById(R.id.tvSearchHint);
        tvEmpty = view.findViewById(R.id.tvEmpty);

        resultAdapter = new SearchResultAdapter();
        lvSearchResults.setAdapter(resultAdapter);
        lvSearchResults.setDivider(null);
        lvSearchResults.setDividerHeight(0);

        if (standalone) {
            btnBack.setOnClickListener(v -> requireActivity().finish());
        } else {
            btnBack.setVisibility(View.GONE);
        }

        btnClearSearch.setOnClickListener(v -> {
            etSearchInput.setText("");
            etSearchInput.requestFocus();
        });

        lvSearchResults.setOnItemClickListener((parent, itemView, position, id) -> {
            SearchResult item = resultList.get(position);
            openWordDetail(item.word);
        });

        etSearchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                performQuery(s.toString().trim());
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        showInitialState();
    }

    private void showInitialState() {
        tvSearchHint.setText("支持英文单词匹配与中文释义模糊检索");
        tvEmpty.setText("输入英文单词或中文释义\n开始查询词典");
        tvEmpty.setVisibility(View.VISIBLE);
        lvSearchResults.setVisibility(View.GONE);
        btnClearSearch.setVisibility(View.INVISIBLE);
    }

    /**
     * 根据关键词执行词典检索。
     */
    private void performQuery(String query) {
        resultList.clear();

        if (query.isEmpty()) {
            resultAdapter.notifyDataSetChanged();
            showInitialState();
            return;
        }

        btnClearSearch.setVisibility(View.VISIBLE);

        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor;

        boolean containsChinese = query.matches(".*[\\u4E00-\\u9FFF].*");

        if (containsChinese) {
            cursor = db.rawQuery(
                    "SELECT word, phonetic, translation " +
                            "FROM ecdict " +
                            "WHERE translation LIKE ? " +
                            "ORDER BY word COLLATE NOCASE " +
                            "LIMIT 50",
                    new String[]{"%" + query + "%"}
            );
        } else {
            cursor = db.rawQuery(
                    "SELECT word, phonetic, translation " +
                            "FROM ecdict " +
                            "WHERE word LIKE ? OR word LIKE ? " +
                            "ORDER BY " +
                            "CASE WHEN word LIKE ? THEN 0 ELSE 1 END, " +
                            "LENGTH(word), word COLLATE NOCASE " +
                            "LIMIT 50",
                    new String[]{
                            query + "%",
                            "%" + query + "%",
                            query + "%"
                    }
            );
        }

        while (cursor.moveToNext()) {
            SearchResult item = new SearchResult();
            item.word = safeText(cursor.getString(0));
            item.phonetic = safeText(cursor.getString(1));
            item.translation = safeText(cursor.getString(2));

            if (!item.word.isEmpty()) {
                resultList.add(item);
            }
        }

        cursor.close();

        resultAdapter.notifyDataSetChanged();

        if (resultList.isEmpty()) {
            tvSearchHint.setText("没有找到匹配结果");
            tvEmpty.setText("没有找到相关单词\n试试更短的关键词或其他释义");
            tvEmpty.setVisibility(View.VISIBLE);
            lvSearchResults.setVisibility(View.GONE);
        } else {
            tvSearchHint.setText(
                    "找到 " + resultList.size() + " 个结果 · 点击卡片查看详情"
            );
            tvEmpty.setVisibility(View.GONE);
            lvSearchResults.setVisibility(View.VISIBLE);
        }
    }

    private String safeText(String value) {
        return value == null ? "" : value.trim();
    }

    private void openWordDetail(String word) {
        Intent intent = new Intent(requireContext(), WordDetailActivity.class);
        intent.putExtra(WordDetailActivity.EXTRA_WORD, word);
        startActivity(intent);
    }

    private void showBookPicker(String word) {
        List<BookChoice> books = loadBookChoices();

        List<String> optionNames = new ArrayList<>();

        for (BookChoice book : books) {
            optionNames.add(book.bookName);
        }

        optionNames.add("＋ 新建单词本");

        new AlertDialog.Builder(requireContext())
                .setTitle("收藏「" + word + "」")
                .setItems(optionNames.toArray(new String[0]), (dialog, which) -> {
                    if (which == books.size()) {
                        showCreateBookAndCollectDialog(word);
                    } else {
                        BookChoice selectedBook = books.get(which);
                        addWordToBook(
                                selectedBook.bookId,
                                selectedBook.bookName,
                                word
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

    private void showCreateBookAndCollectDialog(String word) {
        EditText input = new EditText(requireContext());
        input.setSingleLine(true);
        input.setHint("例如：四级易错词");
        input.setPadding(dp(12), dp(6), dp(12), dp(6));

        LinearLayout container = new LinearLayout(requireContext());
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(24), dp(6), dp(24), 0);
        container.addView(
                input,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                )
        );

        AlertDialog createDialog = new AlertDialog.Builder(requireContext())
                .setTitle("新建单词本")
                .setMessage("创建后会自动收藏「" + word + "」")
                .setView(container)
                .setNegativeButton("取消", null)
                .setPositiveButton("创建并收藏", null)
                .create();

        createDialog.setOnShowListener(dialog -> {
            createDialog.getButton(AlertDialog.BUTTON_POSITIVE)
                    .setOnClickListener(v -> {
                        String bookName = input.getText().toString().trim();

                        if (bookName.isEmpty()) {
                            Toast.makeText(
                                    requireContext(),
                                    "请输入单词本名称",
                                    Toast.LENGTH_SHORT
                            ).show();
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
                                Toast.makeText(
                                        requireContext(),
                                        "创建单词本失败",
                                        Toast.LENGTH_SHORT
                                ).show();
                                return;
                            }
                        } else {
                            bookId = (int) result;
                        }

                        addWordToBook(bookId, bookName, word);
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

    private void addWordToBook(int bookId, String bookName, String word) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put("book_id", bookId);
        values.put("word", word);
        values.put("add_time", System.currentTimeMillis());

        long result = db.insertWithOnConflict(
                "book_word_relation",
                null,
                values,
                SQLiteDatabase.CONFLICT_IGNORE
        );

        if (result == -1) {
            Toast.makeText(
                    requireContext(),
                    "该单词已收藏在「" + bookName + "」中",
                    Toast.LENGTH_SHORT
            ).show();
        } else {
            Toast.makeText(
                    requireContext(),
                    "已收藏到「" + bookName + "」",
                    Toast.LENGTH_SHORT
            ).show();
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private MaterialCardView createCard() {
        MaterialCardView card = new MaterialCardView(requireContext());
        card.setCardBackgroundColor(0xFFFFFFFF);
        card.setCardElevation(dp(1));
        card.setRadius(dp(16));
        card.setStrokeColor(0xFFE2E8F0);
        card.setStrokeWidth(dp(1));
        card.setUseCompatPadding(true);

        return card;
    }

    /**
     * 检索结果列表适配器。
     */
    private class SearchResultAdapter extends BaseAdapter {

        @Override
        public int getCount() {
            return resultList.size();
        }

        @Override
        public Object getItem(int position) {
            return resultList.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ResultHolder holder;

            if (convertView == null) {
                LinearLayout wrapper = new LinearLayout(requireContext());
                wrapper.setOrientation(LinearLayout.VERTICAL);
                wrapper.setPadding(0, dp(5), 0, dp(5));
                wrapper.setLayoutParams(
                        new AbsListView.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                );

                MaterialCardView card = createCard();

                LinearLayout content = new LinearLayout(requireContext());
                content.setOrientation(LinearLayout.HORIZONTAL);
                content.setGravity(Gravity.CENTER_VERTICAL);
                content.setPadding(dp(16), dp(14), dp(10), dp(14));

                LinearLayout textContainer = new LinearLayout(requireContext());
                textContainer.setOrientation(LinearLayout.VERTICAL);

                TextView tvWord = new TextView(requireContext());
                tvWord.setTextColor(0xFF1F3B5B);
                tvWord.setTextSize(19);
                tvWord.setTypeface(null, android.graphics.Typeface.BOLD);

                TextView tvPhonetic = new TextView(requireContext());
                tvPhonetic.setTextColor(0xFF718096);
                tvPhonetic.setTextSize(13);
                tvPhonetic.setPadding(0, dp(4), 0, 0);

                TextView tvTranslation = new TextView(requireContext());
                tvTranslation.setTextColor(0xFF4A5568);
                tvTranslation.setTextSize(14);
                tvTranslation.setPadding(0, dp(7), 0, 0);
                tvTranslation.setMaxLines(2);
                tvTranslation.setEllipsize(TextUtils.TruncateAt.END);

                textContainer.addView(tvWord);
                textContainer.addView(tvPhonetic);
                textContainer.addView(tvTranslation);

                content.addView(
                        textContainer,
                        new LinearLayout.LayoutParams(
                                0,
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                                1
                        )
                );

                MaterialButton btnFavorite = new MaterialButton(requireContext());
                btnFavorite.setText("收藏");
                btnFavorite.setTextSize(13);
                btnFavorite.setAllCaps(false);
                btnFavorite.setTextColor(0xFF2C5282);
                btnFavorite.setCornerRadius(dp(18));
                btnFavorite.setInsetTop(0);
                btnFavorite.setInsetBottom(0);
                btnFavorite.setMinHeight(0);
                btnFavorite.setMinWidth(0);
                btnFavorite.setBackgroundTintList(
                        android.content.res.ColorStateList.valueOf(0xFFE6EEF8)
                );

                content.addView(
                        btnFavorite,
                        new LinearLayout.LayoutParams(dp(70), dp(38))
                );

                card.addView(content);

                wrapper.addView(
                        card,
                        new LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                );

                holder = new ResultHolder();
                holder.tvWord = tvWord;
                holder.tvPhonetic = tvPhonetic;
                holder.tvTranslation = tvTranslation;
                holder.btnFavorite = btnFavorite;

                wrapper.setTag(holder);
                convertView = wrapper;
            } else {
                holder = (ResultHolder) convertView.getTag();
            }

            SearchResult item = resultList.get(position);

            holder.tvWord.setText(item.word);

            if (item.phonetic.isEmpty()) {
                holder.tvPhonetic.setVisibility(View.GONE);
            } else {
                holder.tvPhonetic.setVisibility(View.VISIBLE);
                holder.tvPhonetic.setText(formatPhonetic(item.phonetic));
            }

            if (item.translation.isEmpty()) {
                holder.tvTranslation.setText("暂无释义");
            } else {
                holder.tvTranslation.setText(
                        item.translation.replace("\\n", "\n")
                );
            }

            holder.btnFavorite.setOnClickListener(v -> showBookPicker(item.word));

            return convertView;
        }
    }

    private String formatPhonetic(String phonetic) {
        String value = phonetic.trim();

        if (value.startsWith("[") || value.startsWith("/")) {
            return value;
        }

        return "/" + value + "/";
    }

    private static class ResultHolder {
        TextView tvWord;
        TextView tvPhonetic;
        TextView tvTranslation;
        MaterialButton btnFavorite;
    }
}
