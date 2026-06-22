package gdufs.groupwork.WordApp.com;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
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
 * 创建自定义词书页面。
 *
 * 功能：
 * 1. 按 ECDICT 标签筛选单词；
 * 2. 根据选中的标签自动生成推荐词书名称；
 * 3. 用户仍可手动修改词书名称；
 * 4. 显示所选标签预计包含的单词数量；
 * 5. 创建后自动切换为当前学习词书。
 */
public class CreateVocabBookActivity extends AppCompatActivity {

    private EditText etBookName;
    private LinearLayout layoutTagChecks;
    private TextView tvPreviewWordCount;
    private MaterialButton btnCreateBook;

    private UserSessionManager sessionManager;
    private VocabBookManager vocabBookManager;

    private int currentUserId;

    /**
     * 所有可用标签。
     * 例如：zk、gk、cet4、cet6、ky、toefl、ielts、gre。
     */
    private final List<String> allTags = VocabBookManager.getAllTags();

    /**
     * 标签对应的复选框列表。
     */
    private final List<CheckBox> tagCheckBoxes = new ArrayList<>();

    /**
     * 防止代码调用 setText() 时，被误判成用户手动输入。
     */
    private boolean isUpdatingBookNameProgrammatically = false;

    /**
     * 用户是否手动修改过词书名称。
     *
     * true：
     * 标签变化时不再自动覆盖用户写的名称。
     *
     * false：
     * 标签变化时自动更新推荐名称。
     */
    private boolean isBookNameManuallyEdited = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_vocab_book);

        sessionManager = new UserSessionManager(this);
        vocabBookManager = new VocabBookManager(this);

        // 未登录时返回登录页
        if (!sessionManager.isLoggedIn()) {
            goToLogin();
            return;
        }

        currentUserId = sessionManager.getCurrentUserId();

        initViews();
        buildTagCheckboxes();

        // 初始状态下没有选标签，因此预览为 0
        updatePreviewCount();
    }

    /**
     * 绑定控件和按钮事件。
     */
    private void initViews() {
        etBookName = findViewById(R.id.etBookName);
        layoutTagChecks = findViewById(R.id.layoutTagChecks);
        tvPreviewWordCount = findViewById(R.id.tvPreviewWordCount);
        btnCreateBook = findViewById(R.id.btnCreateBook);

        // 返回上一页
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        // 创建词书
        btnCreateBook.setOnClickListener(v -> createBook());

        /*
         * 监听用户是否手动修改名称。
         *
         * 自动生成名称时会先把 isUpdatingBookNameProgrammatically 设为 true，
         * 因此不会把系统自动填入误认为是用户输入。
         */
        etBookName.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(
                    CharSequence s,
                    int start,
                    int count,
                    int after
            ) {
            }

            @Override
            public void onTextChanged(
                    CharSequence s,
                    int start,
                    int before,
                    int count
            ) {
            }

            @Override
            public void afterTextChanged(Editable editable) {
                if (isUpdatingBookNameProgrammatically) {
                    return;
                }

                /*
                 * 用户手动输入任意非空内容后，
                 * 后续标签变化不会再覆盖他的名称。
                 *
                 * 若用户把名称手动清空，
                 * 则恢复自动命名模式。
                 */
                isBookNameManuallyEdited =
                        !editable.toString().trim().isEmpty();
            }
        });
    }

    /**
     * 动态创建标签复选框。
     */
    private void buildTagCheckboxes() {
        layoutTagChecks.removeAllViews();
        tagCheckBoxes.clear();

        for (String tag : allTags) {
            CheckBox checkBox = new CheckBox(this);

            checkBox.setText(
                    VocabBookManager.getTagLabel(tag)
                            + " (" + tag + ")"
            );

            checkBox.setTextColor(0xFF2D3748);
            checkBox.setTag(tag);

            /*
             * 每次勾选状态变化时：
             * 1. 更新预计单词数量；
             * 2. 若用户未手动命名，则自动更新词书名称。
             */
            checkBox.setOnCheckedChangeListener(
                    (buttonView, isChecked) -> {
                        updatePreviewCount();
                        updateAutoBookName();
                    }
            );

            tagCheckBoxes.add(checkBox);
            layoutTagChecks.addView(checkBox);
        }
    }

    /**
     * 获取当前已勾选的标签。
     */
    private List<String> getSelectedTags() {
        List<String> selectedTags = new ArrayList<>();

        for (CheckBox checkBox : tagCheckBoxes) {
            if (checkBox.isChecked()) {
                selectedTags.add((String) checkBox.getTag());
            }
        }

        return selectedTags;
    }

    /**
     * 根据当前选中的标签自动生成建议词书名称。
     *
     * 规则：
     *
     * 只选中考：
     * 中考核心词汇
     *
     * 选中中考、高考、六级：
     * 中考+高考+六级词汇
     *
     * 全选：
     * ECDICT词汇
     *
     * 没有选择：
     * 留空
     */
    private void updateAutoBookName() {
        /*
         * 用户已经手动改过名称时，
         * 系统不再覆盖。
         */
        if (isBookNameManuallyEdited) {
            return;
        }

        List<String> selectedTags = getSelectedTags();

        String suggestedName = buildSuggestedBookName(selectedTags);

        String currentName = etBookName.getText()
                .toString()
                .trim();

        // 名称本来就相同，不重复设置
        if (suggestedName.equals(currentName)) {
            return;
        }

        isUpdatingBookNameProgrammatically = true;

        etBookName.setText(suggestedName);

        // 自动填入后把光标放到末尾
        etBookName.setSelection(suggestedName.length());

        isUpdatingBookNameProgrammatically = false;
    }

    /**
     * 根据标签组合生成词书名称。
     */
    private String buildSuggestedBookName(List<String> selectedTags) {
        // 没选任何标签时保持空白
        if (selectedTags == null || selectedTags.isEmpty()) {
            return "";
        }

        // 所有标签都被选中时，视为全词库
        if (selectedTags.size() == allTags.size()) {
            return "ECDICT词汇";
        }

        // 只选择一个标签时：中考核心词汇、六级核心词汇等
        if (selectedTags.size() == 1) {
            String label = VocabBookManager.getTagLabel(
                    selectedTags.get(0)
            );

            return label + "核心词汇";
        }

        /*
         * 多选时：
         * 中考+高考+六级词汇
         */
        StringBuilder builder = new StringBuilder();

        for (int i = 0; i < selectedTags.size(); i++) {
            if (i > 0) {
                builder.append("+");
            }

            builder.append(
                    VocabBookManager.getTagLabel(
                            selectedTags.get(i)
                    )
            );
        }

        builder.append("词汇");

        return builder.toString();
    }

    /**
     * 根据所选标签预览预计词数。
     */
    private void updatePreviewCount() {
        List<String> tags = getSelectedTags();

        if (tags.isEmpty()) {
            tvPreviewWordCount.setText("0");
            return;
        }

        VocabBookManager.VocabBook preview =
                new VocabBookManager.VocabBook();

        preview.tags = tags;

        int count = vocabBookManager.countWordsInBook(preview);

        tvPreviewWordCount.setText(String.valueOf(count));
    }

    /**
     * 创建词书并自动设为当前使用的词书。
     */
    private void createBook() {
        String bookName = etBookName.getText()
                .toString()
                .trim();

        List<String> tags = getSelectedTags();

        if (bookName.isEmpty()) {
            Toast.makeText(
                    this,
                    "请输入词书名称",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        /*
         * 默认词书名称已被系统占用。
         *
         * 注意：
         * 自动全选名称是“ECDICT词汇”，
         * 与系统默认词书“ECDICT全词库”不同，
         * 因此可以正常创建。
         */
        if (VocabBookManager.DEFAULT_BOOK_NAME.equals(bookName)) {
            Toast.makeText(
                    this,
                    "该名称已被默认词书使用",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        if (tags.isEmpty()) {
            Toast.makeText(
                    this,
                    "请至少选择一个标签",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        if (!vocabBookManager.canCreateMoreBooks(currentUserId)) {
            Toast.makeText(
                    this,
                    "词书数量已达上限（"
                            + VocabBookManager.MAX_BOOK_COUNT
                            + " 本）",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        VocabBookManager.VocabBook preview =
                new VocabBookManager.VocabBook();

        preview.tags = tags;

        if (vocabBookManager.countWordsInBook(preview) <= 0) {
            Toast.makeText(
                    this,
                    "所选标签下没有可用单词",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        long bookId = vocabBookManager.createCustomBook(
                currentUserId,
                bookName,
                tags
        );

        if (bookId == -1) {
            Toast.makeText(
                    this,
                    "创建失败，名称可能已存在",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        // 新创建的词书自动成为当前学习词书
        vocabBookManager.setCurrentBookId(
                currentUserId,
                (int) bookId
        );

        Toast.makeText(
                this,
                "词书创建成功",
                Toast.LENGTH_SHORT
        ).show();

        finish();
    }

    /**
     * 返回登录页。
     */
    private void goToLogin() {
        Intent intent = new Intent(
                this,
                LoginActivity.class
        );

        intent.setFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TASK
        );

        startActivity(intent);
        finish();
    }
}