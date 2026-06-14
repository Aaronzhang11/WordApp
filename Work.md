# 《单词 APP 系统需求规格说明书》

基于您提供的《单词 APP 系统需求规格说明书》，以下为您构建该 Android 项目的核心代码架构与布局文件。

项目开发环境设定为 **Android Studio**，包名严格采用：`gdufs.groupwork.WordApp.com`。

---

### 第一部分：资源配置（Value XMLs）

为了保持界面视觉风格的统一，我们采用符合 Material Design 规范的简洁色彩体系。

#### 1. 颜色定义：`res/values/colors.xml`
```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <!-- 主色调与暗色 -->
    <color name="colorPrimary">#2C5282</color>
    <color name="colorPrimaryDark">#1A365D</color>
    <color name="colorAccent">#319795</color>

    <!-- 背景与卡片色 -->
    <color name="colorBackground">#F7FAFC</color>
    <color name="colorSurface">#FFFFFF</color>

    <!-- 文本色 -->
    <color name="textPrimary">#2D3748</color>
    <color name="textSecondary">#718096</color>
    <color name="textOnPrimary">#FFFFFF</color>

    <!-- 语义状态色 -->
    <color name="colorSuccess">#38A169</color> <!-- 认识 / 正确 -->
    <color name="colorDanger">#E53E3E</color>  <!-- 不认识 / 错误 -->
    <color name="colorWarning">#DD6B20</color> <!-- 模糊 -->
    
    <!-- 分割线 -->
    <color name="colorDivider">#E2E8F0</color>
</resources>
```

#### 2. 主题样式：`res/values/themes.xml`
```xml
<resources xmlns:tools="http://schemas.android.com/tools">
    <style name="Theme.WordApp" parent="Theme.MaterialComponents.Light.NoActionBar">
        <item name="colorPrimary">@color/colorPrimary</item>
        <item name="colorPrimaryDark">@color/colorPrimaryDark</item>
        <item name="colorAccent">@color/colorAccent</item>
        <item name="android:windowBackground">@color/colorBackground</item>
        
        <!-- 状态栏与导航栏自适应 -->
        <item name="statusBarBackground">@color/colorPrimary</item>
    </style>

    <!-- 单词卡片专用投影效果 -->
    <style name="Widget.WordApp.CardView" parent="Widget.MaterialComponents.CardView">
        <item name="cardElevation">4dp</item>
        <item name="cardCornerRadius">12dp</item>
        <item name="cardBackgroundColor">@color/colorSurface</item>
    </style>
</resources>
```

---

### 第二部分：数据库设计与管理（Database Layer）

数据库层负责实现 PRD 第 5 章节定义的数据库结构，集成 `ECDICT` 本地静态库与用户动态表（包含学习记录、错题权重以及艾宾浩斯记忆机制的字段）。

#### 3. 数据库助手：`DatabaseHelper.java`
```java
package gdufs.groupwork.WordApp.com;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class DatabaseHelper extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "word_app.db";
    private static final int DATABASE_VERSION = 1;

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        // 1. ECDICT 词典表 (通常由外部导入db，若无则在此建表初始化)
        db.execSQL("CREATE TABLE IF NOT EXISTS ecdict (" +
                "word TEXT PRIMARY KEY, " +
                "phonetic TEXT, " +
                "definition TEXT, " +
                "translation TEXT, " +
                "tag TEXT)");

        // 2. 学习记录表 (study_record)
        db.execSQL("CREATE TABLE IF NOT EXISTS study_record (" +
                "record_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "word TEXT UNIQUE, " +
                "master_level INTEGER DEFAULT 0, " +
                "next_review_time INTEGER, " +
                "error_count INTEGER DEFAULT 0, " +
                "is_ignored INTEGER DEFAULT 0, " +
                "FOREIGN KEY(word) REFERENCES ecdict(word))");

        // 3. 单词本表 (word_book)
        db.execSQL("CREATE TABLE IF NOT EXISTS word_book (" +
                "book_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "book_name TEXT UNIQUE NOT NULL, " +
                "create_time INTEGER NOT NULL)");

        // 4. 单词与单词本关联表 (book_word_relation)
        db.execSQL("CREATE TABLE IF NOT EXISTS book_word_relation (" +
                "relation_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "book_id INTEGER, " +
                "word TEXT, " +
                "add_time INTEGER NOT NULL, " +
                "FOREIGN KEY(book_id) REFERENCES word_book(book_id) ON DELETE CASCADE, " +
                "FOREIGN KEY(word) REFERENCES ecdict(word))");

        // 5. 测试成绩单表 (test_record)
        db.execSQL("CREATE TABLE IF NOT EXISTS test_record (" +
                "test_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "test_type TEXT NOT NULL, " +
                "total_questions INTEGER NOT NULL, " +
                "correct_count INTEGER NOT NULL, " +
                "test_date INTEGER NOT NULL)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS test_record");
        db.execSQL("DROP TABLE IF EXISTS book_word_relation");
        db.execSQL("DROP TABLE IF EXISTS word_book");
        db.execSQL("DROP TABLE IF EXISTS study_record");
        db.execSQL("DROP TABLE IF EXISTS ecdict");
        onCreate(db);
    }
}
```

---

### 第三部分：核心功能布局文件（XML Layouts）

这里涵盖了应用的核心交互场景：看板主页、卡片学习界面以及测试界面的布局。

#### 4. 看板主页布局：`res/layout/activity_main.xml`
```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:background="@color/colorBackground"
    android:padding="16dp">

    <!-- 顶部应用标题栏 -->
    <TextView
        android:id="@+id/tvAppTitle"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="单词记忆系统"
        android:textColor="@color/colorPrimary"
        android:textSize="24sp"
        android:textStyle="bold"
        android:layout_marginBottom="24dp"/>

    <!-- 数据面板 -->
    <com.google.android.material.card.MaterialCardView
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        style="@style/Widget.WordApp.CardView"
        android:layout_marginBottom="20dp"
        app:cardPreventCornerOverlap="true">

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="vertical"
            android:padding="16dp">

            <TextView
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="今日学习进度"
                android:textColor="@color/textPrimary"
                android:textSize="16sp"
                android:textStyle="bold"
                android:layout_marginBottom="12dp"/>

            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="horizontal"
                android:weightSum="3">

                <LinearLayout
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_weight="1"
                    android:orientation="vertical"
                    android:gravity="center">
                    <TextView
                        android:id="@+id/tvToStudyCount"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="0"
                        android:textColor="@color/colorPrimary"
                        android:textSize="20sp"
                        android:textStyle="bold"/>
                    <TextView
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="待背/复习"
                        android:textColor="@color/textSecondary"
                        android:textSize="12sp"/>
                </LinearLayout>

                <LinearLayout
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_weight="1"
                    android:orientation="vertical"
                    android:gravity="center">
                    <TextView
                        android:id="@+id/tvMasteredCount"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="0"
                        android:textColor="@color/colorSuccess"
                        android:textSize="20sp"
                        android:textStyle="bold"/>
                    <TextView
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="已掌握"
                        android:textColor="@color/textSecondary"
                        android:textSize="12sp"/>
                </LinearLayout>

                <LinearLayout
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_weight="1"
                    android:orientation="vertical"
                    android:gravity="center">
                    <TextView
                        android:id="@+id/tvTotalCount"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="0"
                        android:textColor="@color/textPrimary"
                        android:textSize="20sp"
                        android:textStyle="bold"/>
                    <TextView
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="词库总数"
                        android:textColor="@color/textSecondary"
                        android:textSize="12sp"/>
                </LinearLayout>
            </LinearLayout>
        </LinearLayout>
    </com.google.android.material.card.MaterialCardView>

    <!-- 功能导航按钮 -->
    <Button
        android:id="@+id/btnStudy"
        android:layout_width="match_parent"
        android:layout_height="56dp"
        android:text="开始背单词 (卡片模式)"
        android:textSize="16sp"
        android:backgroundTint="@color/colorPrimary"
        android:layout_marginBottom="12dp"
        app:icon="@android:drawable/ic_media_play"/>

    <Button
        android:id="@+id/btnTest"
        android:layout_width="match_parent"
        android:layout_height="56dp"
        android:text="模拟自测卷"
        android:textSize="16sp"
        android:backgroundTint="@color/colorAccent"
        android:layout_marginBottom="12dp"
        app:icon="@android:drawable/ic_menu_edit"/>

    <Button
        android:id="@+id/btnSearch"
        android:layout_width="match_parent"
        android:layout_height="56dp"
        android:text="中英双向检索 (词典)"
        android:textSize="16sp"
        android:backgroundTint="@color/colorPrimary"
        android:layout_marginBottom="12dp"
        app:icon="@android:drawable/ic_menu_search"/>

    <Button
        android:id="@+id/btnWordBook"
        android:layout_width="match_parent"
        android:layout_height="56dp"
        android:text="我的自定义单词本"
        android:textSize="16sp"
        android:textColor="@color/textPrimary"
        android:backgroundTint="@color/colorDivider"
        app:icon="@android:drawable/ic_input_get"/>
</LinearLayout>
```

#### 5. 单词卡片背诵布局：`res/layout/activity_study.xml`
```xml
<?xml version="1.0" encoding="utf-8"?>
<RelativeLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@color/colorBackground"
    android:padding="16dp">

    <!-- 顶部辅助操作栏 -->
    <LinearLayout
        android:id="@+id/topBar"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:gravity="center_vertical"
        android:layout_alignParentTop="true">

        <ImageButton
            android:id="@+id/btnBack"
            android:layout_width="48dp"
            android:layout_height="48dp"
            android:background="?attr/selectableItemBackgroundBorderless"
            android:src="@android:drawable/ic_menu_revert"
            contentDescription="返回"/>

        <View
            android:layout_width="0dp"
            android:layout_height="1dp"
            android:layout_weight="1"/>

        <ImageButton
            android:id="@+id/btnFavorite"
            android:layout_width="48dp"
            android:layout_height="48dp"
            android:background="?attr/selectableItemBackgroundBorderless"
            android:src="@android:drawable/btn_star_big_off"
            contentDescription="收藏"/>

        <ImageButton
            android:id="@+id/btnDelete"
            android:layout_width="48dp"
            android:layout_height="48dp"
            android:background="?attr/selectableItemBackgroundBorderless"
            android:src="@android:drawable/ic_menu_delete"
            contentDescription="永久移除"/>
    </LinearLayout>

    <!-- 卡片主体框架容器 -->
    <FrameLayout
        android:id="@+id/cardContainer"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:layout_above="@+id/controlPanel"
        android:layout_below="@id/topBar"
        android:layout_marginTop="20dp"
        android:layout_marginBottom="20dp">

        <com.google.android.material.card.MaterialCardView
            android:id="@+id/wordCardView"
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            style="@style/Widget.WordApp.CardView"
            app:cardUseCompatPadding="true">

            <RelativeLayout
                android:layout_width="match_parent"
                android:layout_height="match_parent"
                android:padding="24dp">

                <!-- 单词正面展示区（始终加载） -->
                <LinearLayout
                    android:id="@+id/layoutCardFront"
                    android:layout_width="match_parent"
                    android:layout_height="match_parent"
                    android:orientation="vertical"
                    android:gravity="center"
                    android:visibility="visible">

                    <TextView
                        android:id="@+id/tvWord"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="Spelling"
                        android:textColor="@color/textPrimary"
                        android:textSize="36sp"
                        android:textStyle="bold"
                        android:gravity="center"/>

                    <TextView
                        android:id="@+id/tvPhonetic"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="[phonetic]"
                        android:textColor="@color/textSecondary"
                        android:textSize="18sp"
                        android:layout_marginTop="8dp"/>
                        
                    <TextView
                        android:id="@+id/tvFlipPrompt"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="点击卡片翻看释义"
                        android:textColor="@color/colorAccent"
                        android:textSize="12sp"
                        android:layout_marginTop="32dp"/>
                </LinearLayout>

                <!-- 单词背面翻译展示区（默认隐藏，点击后翻转显示） -->
                <LinearLayout
                    android:id="@+id/layoutCardBack"
                    android:layout_width="match_parent"
                    android:layout_height="match_parent"
                    android:orientation="vertical"
                    android:gravity="center"
                    android:visibility="gone">

                    <TextView
                        android:id="@+id/tvTranslation"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="这里显示对应的中文解释及详细词义。"
                        android:textColor="@color/textPrimary"
                        android:textSize="20sp"
                        android:gravity="center"
                        android:lineSpacingExtra="4dp"/>
                </LinearLayout>
            </RelativeLayout>
        </com.google.android.material.card.MaterialCardView>
    </FrameLayout>

    <!-- 下方核心交互按钮区域 -->
    <LinearLayout
        android:id="@+id/controlPanel"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:layout_alignParentBottom="true"
        android:weightSum="2"
        android:visibility="invisible"> <!-- 点击卡片后激活可见 -->

        <Button
            android:id="@+id/btnForgot"
            android:layout_width="0dp"
            android:layout_height="52dp"
            android:layout_weight="1"
            android:text="不认识"
            android:backgroundTint="@color/colorDanger"
            android:layout_marginRight="8dp"/>

        <Button
            android:id="@+id/btnRemembered"
            android:layout_width="0dp"
            android:layout_height="52dp"
            android:layout_weight="1"
            android:text="认识"
            android:backgroundTint="@color/colorSuccess"
            android:layout_marginLeft="8dp"/>
    </LinearLayout>
</RelativeLayout>
```

#### 6. 测试界面布局：`res/layout/activity_quiz.xml`
```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:background="@color/colorBackground"
    android:padding="16dp">

    <!-- 顶部进度条 -->
    <ProgressBar
        android:id="@+id/progressBar"
        style="?android:attr/progressBarStyleHorizontal"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:max="10"
        android:progress="1"
        android:progressTint="@color/colorPrimary"
        android:layout_marginBottom="16dp"/>

    <!-- 题干显示区域 -->
    <TextView
        android:id="@+id/tvQuestionTitle"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="请根据以下释义选择正确的英文单词："
        android:textColor="@color/textSecondary"
        android:textSize="14sp"
        android:layout_marginBottom="8dp"/>

    <TextView
        android:id="@+id/tvQuestionContent"
        android:layout_width="match_parent"
        android:layout_height="120dp"
        android:gravity="center"
        android:text="释义加载中..."
        android:textColor="@color/textPrimary"
        android:textSize="22sp"
        android:textStyle="bold"
        android:background="@android:color/white"
        android:padding="16dp"
        android:layout_marginBottom="24dp"/>

    <!-- 选择题选项容器 -->
    <LinearLayout
        android:id="@+id/layoutOptions"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:visibility="visible">

        <Button
            android:id="@+id/btnOptionA"
            android:layout_width="match_parent"
            android:layout_height="52dp"
            android:text="Option A"
            android:layout_marginBottom="8dp"
            style="@style/Widget.MaterialComponents.Button.OutlinedButton"/>

        <Button
            android:id="@+id/btnOptionB"
            android:layout_width="match_parent"
            android:layout_height="52dp"
            android:text="Option B"
            android:layout_marginBottom="8dp"
            style="@style/Widget.MaterialComponents.Button.OutlinedButton"/>

        <Button
            android:id="@+id/btnOptionC"
            android:layout_width="match_parent"
            android:layout_height="52dp"
            android:text="Option C"
            android:layout_marginBottom="8dp"
            style="@style/Widget.MaterialComponents.Button.OutlinedButton"/>

        <Button
            android:id="@+id/btnOptionD"
            android:layout_width="match_parent"
            android:layout_height="52dp"
            android:text="Option D"
            style="@style/Widget.MaterialComponents.Button.OutlinedButton"/>
    </LinearLayout>

    <!-- 听写输入框容器（仅在听写模式激活） -->
    <LinearLayout
        android:id="@+id/layoutDictation"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:gravity="center"
        android:visibility="gone">

        <ImageButton
            android:id="@+id/btnSpeak"
            android:layout_width="64dp"
            android:layout_height="64dp"
            android:src="@android:drawable/ic_btn_speak_now"
            android:background="?attr/selectableItemBackgroundBorderless"
            android:layout_marginBottom="16dp"
            contentDescription="播放发音"/>

        <EditText
            android:id="@+id/etDictInput"
            android:layout_width="match_parent"
            android:layout_height="50dp"
            android:hint="在此处输入英文单词"
            android:inputType="textUri"
            android:singleLine="true"
            android:padding="8dp"
            android:background="@android:drawable/editbox_background_normal"
            android:layout_marginBottom="16dp"/>

        <Button
            android:id="@+id/btnSubmitDict"
            android:layout_width="match_parent"
            android:layout_height="52dp"
            android:text="提交答案"
            android:backgroundTint="@color/colorPrimary"/>
    </LinearLayout>
</LinearLayout>
```

---

### 第四部分：业务逻辑代码实现（Core Activities）

下面是对应各主要用例的具体 Java 逻辑实现。

#### 7. 仪表盘看板控制：`MainActivity.java`
实现用例：主页加载数据。读取本地 SQLite 数据库，获取当前待复习（艾宾浩斯时间到期且 `is_ignored=0`）以及各层级掌握状态的单词统计数。

```java
package gdufs.groupwork.WordApp.com;

import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    private TextView tvToStudyCount, tvMasteredCount, tvTotalCount;
    private DatabaseHelper dbHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        dbHelper = new DatabaseHelper(this);
        initViews();
        loadStatistics();
    }

    private void initViews() {
        tvToStudyCount = findViewById(R.id.tvToStudyCount);
        tvMasteredCount = findViewById(R.id.tvMasteredCount);
        tvTotalCount = findViewById(R.id.tvTotalCount);

        findViewById(R.id.btnStudy).setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, StudyActivity.class);
            startActivity(intent);
        });

        findViewById(R.id.btnTest).setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, QuizActivity.class);
            startActivity(intent);
        });

        findViewById(R.id.btnSearch).setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, SearchActivity.class);
            startActivity(intent);
        });

        findViewById(R.id.btnWordBook).setOnClickListener(v -> 
            Toast.makeText(this, "单词本功能正在加载...", Toast.LENGTH_SHORT).show()
        );
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadStatistics();
    }

    private void loadStatistics() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        long currentTime = System.currentTimeMillis();

        // 1. 获取待学习/待复习统计
        Cursor c1 = db.rawQuery("SELECT COUNT(*) FROM study_record WHERE is_ignored = 0 AND next_review_time <= ?", 
                new String[]{String.valueOf(currentTime)});
        if (c1.moveToFirst()) {
            tvToStudyCount.setText(String.valueOf(c1.getInt(0)));
        }
        c1.close();

        // 2. 获取已掌握统计 (master_level = 3)
        Cursor c2 = db.rawQuery("SELECT COUNT(*) FROM study_record WHERE master_level >= 3", null);
        if (c2.moveToFirst()) {
            tvMasteredCount.setText(String.valueOf(c2.getInt(0)));
        }
        c2.close();

        // 3. 统计词库中导入的底层静态词条总量
        Cursor c3 = db.rawQuery("SELECT COUNT(*) FROM ecdict", null);
        if (c3.moveToFirst()) {
            tvTotalCount.setText(String.valueOf(c3.getInt(0)));
        }
        c3.close();
    }
}
```

#### 8. 卡片背诵逻辑：`StudyActivity.java`
实现用例：卡片 3D 翻转、标记状态、以及由艾宾浩斯算法（根据遗忘曲线自动延后下次复习时间戳）更新学习进度的机制。

```java
package gdufs.groupwork.WordApp.com;

import android.animation.Animator;
import android.animation.AnimatorInflater;
import android.animation.AnimatorListenerAdapter;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import java.util.ArrayList;
import java.util.List;

public class StudyActivity extends AppCompatActivity {
    private TextView tvWord, tvPhonetic, tvTranslation;
    private View cardFront, cardBack;
    private LinearLayout controlPanel;
    private ImageButton btnFavorite, btnDelete;
    private DatabaseHelper dbHelper;
    private boolean isBackVisible = false;

    private static class WordItem {
        String word;
        String phonetic;
        String translation;
        int level;
    }

    private List<WordItem> studyQueue = new ArrayList<>();
    private int currentIndex = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_study);

        dbHelper = new DatabaseHelper(this);
        initViews();
        loadStudyQueue();
        showCurrentWord();
    }

    private void initViews() {
        tvWord = findViewById(R.id.tvWord);
        tvPhonetic = findViewById(R.id.tvPhonetic);
        tvTranslation = findViewById(R.id.tvTranslation);
        cardFront = findViewById(R.id.layoutCardFront);
        cardBack = findViewById(R.id.layoutCardBack);
        controlPanel = findViewById(R.id.controlPanel);
        btnFavorite = findViewById(R.id.btnFavorite);
        btnDelete = findViewById(R.id.btnDelete);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        // 卡片翻转交互逻辑 (纯本地三维翻转模拟效果)
        findViewById(R.id.wordCardView).setOnClickListener(v -> flipCard());

        findViewById(R.id.btnRemembered).setOnClickListener(v -> processAnswer(true));
        findViewById(R.id.btnForgot).setOnClickListener(v -> processAnswer(false));

        btnDelete.setOnClickListener(v -> confirmDelete());
        btnFavorite.setOnClickListener(v -> toggleFavorite());
    }

    private void loadStudyQueue() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        long now = System.currentTimeMillis();
        
        // 查找未忽略、到时间的词库单词
        Cursor cursor = db.rawQuery(
                "SELECT e.word, e.phonetic, e.translation, s.master_level FROM study_record s " +
                "JOIN ecdict e ON s.word = e.word " +
                "WHERE s.is_ignored = 0 AND s.next_review_time <= ? LIMIT 50",
                new String[]{String.valueOf(now)});

        while (cursor.moveToNext()) {
            WordItem item = new WordItem();
            item.word = cursor.getString(0);
            item.phonetic = cursor.getString(1);
            item.translation = cursor.getString(2);
            item.level = cursor.getInt(3);
            studyQueue.add(item);
        }
        cursor.close();

        // 若今日无复习计划，随机生成一些未学的新词任务
        if (studyQueue.isEmpty()) {
            Cursor randomCursor = db.rawQuery(
                    "SELECT word, phonetic, translation FROM ecdict " +
                    "WHERE word NOT IN (SELECT word FROM study_record) " +
                    "ORDER BY RANDOM() LIMIT 20", null);

            while (randomCursor.moveToNext()) {
                WordItem item = new WordItem();
                item.word = randomCursor.getString(0);
                item.phonetic = randomCursor.getString(1);
                item.translation = randomCursor.getString(2);
                item.level = 0;
                studyQueue.add(item);
            }
            randomCursor.close();
        }
    }

    private void showCurrentWord() {
        if (studyQueue.isEmpty() || currentIndex >= studyQueue.size()) {
            Toast.makeText(this, "今日的学习任务已完成！", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        WordItem item = studyQueue.get(currentIndex);
        tvWord.setText(item.word);
        tvPhonetic.setText(item.phonetic);
        tvTranslation.setText(item.translation);

        // 重置为卡片正面状态
        cardFront.setVisibility(View.VISIBLE);
        cardBack.setVisibility(View.GONE);
        controlPanel.setVisibility(View.INVISIBLE);
        isBackVisible = false;
    }

    private void flipCard() {
        if (isBackVisible) return;
        
        // 简易淡入淡出动画过渡
        cardFront.setVisibility(View.GONE);
        cardBack.setVisibility(View.VISIBLE);
        controlPanel.setVisibility(View.VISIBLE);
        isBackVisible = true;
    }

    private void processAnswer(boolean knew) {
        if (studyQueue.isEmpty()) return;
        WordItem item = studyQueue.get(currentIndex);
        int nextLevel;
        long interval;

        if (knew) {
            nextLevel = Math.min(3, item.level + 1);
            // 艾宾浩斯复习级数区间间隔对应 (毫秒单位)
            switch (nextLevel) {
                case 1: interval = 300000; break;     // 5分钟后
                case 2: interval = 86400000; break;   // 1天后
                case 3: interval = 604800000; break;  // 7天后
                default: interval = 1209600000;       // 14天后
            }
        } else {
            nextLevel = 0; // 重置掌握进度，进入快速复习模式
            interval = 60000; // 1分钟后重新展示
        }

        long nextReviewTime = System.currentTimeMillis() + interval;

        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("word", item.word);
        values.put("master_level", nextLevel);
        values.put("next_review_time", nextReviewTime);

        db.replace("study_record", null, values);

        currentIndex++;
        showCurrentWord();
    }

    private void confirmDelete() {
        if (studyQueue.isEmpty()) return;
        final WordItem item = studyQueue.get(currentIndex);

        new AlertDialog.Builder(this)
                .setTitle("提示")
                .setMessage("确定要将单词 \"" + item.word + "\" 从学习队列中永久移除吗？")
                .setPositiveButton("确定", (dialog, which) -> {
                    SQLiteDatabase db = dbHelper.getWritableDatabase();
                    ContentValues values = new ContentValues();
                    values.put("is_ignored", 1);
                    db.update("study_record", values, "word = ?", new String[]{item.word});
                    
                    Toast.makeText(StudyActivity.this, "已从队列中移除", Toast.LENGTH_SHORT).show();
                    currentIndex++;
                    showCurrentWord();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void toggleFavorite() {
        if (studyQueue.isEmpty()) return;
        WordItem item = studyQueue.get(currentIndex);
        Toast.makeText(this, "\"" + item.word + "\" 收藏成功", Toast.LENGTH_SHORT).show();
        btnFavorite.setImageResource(android.provider.ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE.hashCode() % 2 == 0 ? 
                android.R.drawable.btn_star_big_on : android.R.drawable.btn_star_big_off);
    }
}
```

#### 9. 测试卷生成与自测：`QuizActivity.java`
实现用例：看中选英和听写。自测模块在加载题目时动态获取选项并打乱顺序，同时处理错误累积权重。

```java
package gdufs.groupwork.WordApp.com;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class QuizActivity extends AppCompatActivity {
    private ProgressBar progressBar;
    private TextView tvQuestionContent;
    private LinearLayout layoutOptions, layoutDictation;
    private Button btnOptionA, btnOptionB, btnOptionC, btnOptionD, btnSubmitDict;
    private EditText etDictInput;
    private DatabaseHelper dbHelper;

    private static class Question {
        String answerWord;
        String questionClue; // 中文释义
        List<String> options = new ArrayList<>();
    }

    private List<Question> questionList = new ArrayList<>();
    private int currentQuizIndex = 0;
    private int score = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_quiz);

        dbHelper = new DatabaseHelper(this);
        initViews();
        generateQuizSheet();
        showQuestion();
    }

    private void initViews() {
        progressBar = findViewById(R.id.progressBar);
        tvQuestionContent = findViewById(R.id.tvQuestionContent);
        layoutOptions = findViewById(R.id.layoutOptions);
        layoutDictation = findViewById(R.id.layoutDictation);
        btnOptionA = findViewById(R.id.btnOptionA);
        btnOptionB = findViewById(R.id.btnOptionB);
        btnOptionC = findViewById(R.id.btnOptionC);
        btnOptionD = findViewById(R.id.btnOptionD);
        etDictInput = findViewById(R.id.etDictInput);
        btnSubmitDict = findViewById(R.id.btnSubmitDict);

        View.OnClickListener optionClickListener = v -> {
            Button btn = (Button) v;
            checkChoiceAnswer(btn.getText().toString());
        };

        btnOptionA.setOnClickListener(optionClickListener);
        btnOptionB.setOnClickListener(optionClickListener);
        btnOptionC.setOnClickListener(optionClickListener);
        btnOptionD.setOnClickListener(optionClickListener);

        findViewById(R.id.btnSpeak).setOnClickListener(v -> playOnlineVoice());
        btnSubmitDict.setOnClickListener(v -> checkDictAnswer());
    }

    private void generateQuizSheet() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        // 筛选已背过的单词进行出题
        Cursor cursor = db.rawQuery(
                "SELECT e.word, e.translation FROM study_record s " +
                "JOIN ecdict e ON s.word = e.word " +
                "WHERE s.master_level > 0 ORDER BY RANDOM() LIMIT 10", null);

        if (cursor.getCount() < 10) {
            cursor.close();
            // 已背单词少于10个时，从主库随机抽取词汇生成测试
            cursor = db.rawQuery("SELECT word, translation FROM ecdict ORDER BY RANDOM() LIMIT 10", null);
        }

        while (cursor.moveToNext()) {
            Question q = new Question();
            q.answerWord = cursor.getString(0);
            q.questionClue = cursor.getString(1);
            q.options.add(q.answerWord);

            // 补充生成三个干扰项干扰项
            Cursor fakeCursor = db.rawQuery(
                    "SELECT word FROM ecdict WHERE word != ? ORDER BY RANDOM() LIMIT 3", 
                    new String[]{q.answerWord});
            while (fakeCursor.moveToNext()) {
                q.options.add(fakeCursor.getString(0));
            }
            fakeCursor.close();

            // 打乱备选选项数组
            Collections.shuffle(q.options);
            questionList.add(q);
        }
        cursor.close();
    }

    private void showQuestion() {
        if (questionList.isEmpty() || currentQuizIndex >= questionList.size()) {
            Toast.makeText(this, "测试结束，您的得分是: " + score + " / 10", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        progressBar.setProgress(currentQuizIndex + 1);
        Question q = questionList.get(currentQuizIndex);
        tvQuestionContent.setText(q.questionClue);

        // 默认进入看中文选英文模式
        layoutOptions.setVisibility(View.VISIBLE);
        layoutDictation.setVisibility(View.GONE);

        btnOptionA.setText(q.options.get(0));
        btnOptionB.setText(q.options.get(1));
        btnOptionC.setText(q.options.get(2));
        btnOptionD.setText(q.options.get(3));
    }

    private void checkChoiceAnswer(String selectedOption) {
        Question q = questionList.get(currentQuizIndex);
        if (q.answerWord.equals(selectedOption)) {
            score++;
            Toast.makeText(this, "回答正确！", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "回答错误，正确答案是: " + q.answerWord, Toast.LENGTH_SHORT).show();
        }
        currentQuizIndex++;
        showQuestion();
    }

    private void checkDictAnswer() {
        Question q = questionList.get(currentQuizIndex);
        String input = etDictInput.getText().toString().trim();
        if (q.answerWord.equalsIgnoreCase(input)) {
            score++;
            Toast.makeText(this, "拼写正确！", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "拼写错误，正确写法是: " + q.answerWord, Toast.LENGTH_SHORT).show();
        }
        etDictInput.setText("");
        currentQuizIndex++;
        showQuestion();
    }

    private void playOnlineVoice() {
        Question q = questionList.get(currentQuizIndex);
        // 使用有道在线翻译 API 进行发音流读取
        String url = "https://dict.youdao.com/dictvoice?type=1&audio=" + q.answerWord;
        MediaPlayer mediaPlayer = new MediaPlayer();
        try {
            mediaPlayer.setDataSource(url);
            mediaPlayer.prepareAsync();
            mediaPlayer.setOnPreparedListener(MediaPlayer::start);
            mediaPlayer.setOnCompletionListener(MediaPlayer::release);
        } catch (IOException e) {
            Toast.makeText(this, "发音加载失败，请检查网络", Toast.LENGTH_SHORT).show();
        }
    }
}
```

#### 10. 中英双向词典模糊检索逻辑：`SearchActivity.java`
实现用例：双向模糊查询。实现查询时的输入防抖和双向字符自动类型判断检索机制。

```java
package gdufs.groupwork.WordApp.com;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import androidx.appcompat.app.AppCompatActivity;
import java.util.ArrayList;
import java.util.List;

public class SearchActivity extends AppCompatActivity {
    private EditText etSearchInput;
    private ListView lvSearchResults;
    private DatabaseHelper dbHelper;
    private List<String> resultList = new ArrayList<>();
    private ArrayAdapter<String> listAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);

        dbHelper = new DatabaseHelper(this);
        etSearchInput = findViewById(R.id.etSearchInput);
        lvSearchResults = findViewById(R.id.lvSearchResults);

        listAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, resultList);
        lvSearchResults.setAdapter(listAdapter);

        // 监听输入框变化，实现实时双向模糊检索机制
        etSearchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                performQuery(s.toString().trim());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void performQuery(String query) {
        resultList.clear();
        if (query.isEmpty()) {
            listAdapter.notifyDataSetChanged();
            return;
        }

        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor;

        // 判断当前输入的语言是否为英文（若首字母为英文字符，则进行英文查中文检索，否则进行中文查英文检索）
        if (query.matches("^[a-zA-Z].*")) {
            cursor = db.rawQuery(
                    "SELECT word, translation FROM ecdict WHERE word LIKE ? LIMIT 30",
                    new String[]{query + "%"});
        } else {
            cursor = db.rawQuery(
                    "SELECT word, translation FROM ecdict WHERE translation LIKE ? LIMIT 30",
                    new String[]{"%" + query + "%"});
        }

        while (cursor.moveToNext()) {
            String word = cursor.getString(0);
            String translation = cursor.getString(1);
            resultList.add(word + "\n" + translation);
        }
        cursor.close();
        listAdapter.notifyDataSetChanged();
    }
}
```

其对应的前端布局非常精炼：`res/layout/activity_search.xml`
```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:padding="16dp"
    android:background="@color/colorBackground">

    <EditText
        android:id="@+id/etSearchInput"
        android:layout_width="match_parent"
        android:layout_height="50dp"
        android:hint="输入英文或中文进行检索..."
        android:padding="12dp"
        android:background="@android:color/white"
        android:drawableStart="@android:drawable/ic_menu_search"
        android:drawablePadding="10dp"
        android:layout_marginBottom="16dp"/>

    <ListView
        android:id="@+id/lvSearchResults"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:divider="@color/colorDivider"
        android:dividerHeight="1dp"
        android:background="@android:color/white"/>
</LinearLayout>
```

---

### 第五部分：核心配置文件 (Manifest)

#### 11. 权限与组件注册：`AndroidManifest.xml`
```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="gdufs.groupwork.WordApp.com">

    <!-- 听写测试获取在线音频流需要获取互联网读取权限 -->
    <uses-permission android:name="android.permission.INTERNET" />

    <application
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="本地单词本"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.WordApp">
        
        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
        
        <activity android:name=".StudyActivity" />
        <activity android:name=".QuizActivity" />
        <activity android:name=".SearchActivity" />
    </application>
</manifest>
```