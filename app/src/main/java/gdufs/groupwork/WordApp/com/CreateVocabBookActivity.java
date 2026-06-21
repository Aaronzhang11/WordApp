package gdufs.groupwork.WordApp.com;

import android.content.Intent;
import android.os.Bundle;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

/**
 * 创建自定义词书：按 ECDICT 标签筛选词条。
 */
public class CreateVocabBookActivity extends AppCompatActivity {

    private EditText etBookName;
    private LinearLayout layoutTagChecks;
    private TextView tvPreviewWordCount;
    private MaterialButton btnCreateBook;

    private UserSessionManager sessionManager;
    private VocabBookManager vocabBookManager;

    private int currentUserId;

    /** 标签与对应复选框 */
    private final List<String> allTags = VocabBookManager.getAllTags();
    private final List<CheckBox> tagCheckBoxes = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_vocab_book);

        sessionManager = new UserSessionManager(this);
        vocabBookManager = new VocabBookManager(this);

        if (!sessionManager.isLoggedIn()) {
            goToLogin();
            return;
        }

        currentUserId = sessionManager.getCurrentUserId();

        initViews();
        buildTagCheckboxes();
        updatePreviewCount();
    }

    /**
     * 绑定控件与事件。
     */
    private void initViews() {
        etBookName = findViewById(R.id.etBookName);
        layoutTagChecks = findViewById(R.id.layoutTagChecks);
        tvPreviewWordCount = findViewById(R.id.tvPreviewWordCount);
        btnCreateBook = findViewById(R.id.btnCreateBook);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        btnCreateBook.setOnClickListener(v -> createBook());
    }

    /**
     * 动态生成标签复选框。
     */
    private void buildTagCheckboxes() {
        layoutTagChecks.removeAllViews();
        tagCheckBoxes.clear();

        for (String tag : allTags) {
            CheckBox checkBox = new CheckBox(this);
            checkBox.setText(VocabBookManager.getTagLabel(tag) + " (" + tag + ")");
            checkBox.setTextColor(0xFF2D3748);
            checkBox.setTag(tag);
            checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> updatePreviewCount());

            tagCheckBoxes.add(checkBox);
            layoutTagChecks.addView(checkBox);
        }
    }

    /**
     * 获取当前选中的标签。
     */
    private List<String> getSelectedTags() {
        List<String> selected = new ArrayList<>();

        for (CheckBox checkBox : tagCheckBoxes) {
            if (checkBox.isChecked()) {
                selected.add((String) checkBox.getTag());
            }
        }

        return selected;
    }

    /**
     * 根据选中标签预览词数。
     */
    private void updatePreviewCount() {
        List<String> tags = getSelectedTags();

        if (tags.isEmpty()) {
            tvPreviewWordCount.setText("0");
            return;
        }

        VocabBookManager.VocabBook preview = new VocabBookManager.VocabBook();
        preview.tags = tags;

        int count = vocabBookManager.countWordsInBook(preview);
        tvPreviewWordCount.setText(String.valueOf(count));
    }

    /**
     * 创建词书并返回设置页。
     */
    private void createBook() {
        String bookName = etBookName.getText().toString().trim();
        List<String> tags = getSelectedTags();

        if (bookName.isEmpty()) {
            Toast.makeText(this, "请输入词书名称", Toast.LENGTH_SHORT).show();
            return;
        }

        if (VocabBookManager.DEFAULT_BOOK_NAME.equals(bookName)) {
            Toast.makeText(this, "该名称已被默认词书使用", Toast.LENGTH_SHORT).show();
            return;
        }

        if (tags.isEmpty()) {
            Toast.makeText(this, "请至少选择一个标签", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!vocabBookManager.canCreateMoreBooks(currentUserId)) {
            Toast.makeText(
                    this,
                    "词书数量已达上限（" + VocabBookManager.MAX_BOOK_COUNT + " 本）",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        VocabBookManager.VocabBook preview = new VocabBookManager.VocabBook();
        preview.tags = tags;

        if (vocabBookManager.countWordsInBook(preview) <= 0) {
            Toast.makeText(this, "所选标签下没有可用单词", Toast.LENGTH_SHORT).show();
            return;
        }

        long bookId = vocabBookManager.createCustomBook(currentUserId, bookName, tags);

        if (bookId == -1) {
            Toast.makeText(this, "创建失败，名称可能已存在", Toast.LENGTH_SHORT).show();
            return;
        }

        vocabBookManager.setCurrentBookId(currentUserId, (int) bookId);

        Toast.makeText(this, "词书创建成功", Toast.LENGTH_SHORT).show();
        finish();
    }

    private void goToLogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
