# 单词APP系统开发工作记录与项目说明（完善版）

> 本文档基于《单词APP系统需求规格说明书》和当前Android Studio项目实际开发过程整理，用于记录项目结构、已完成功能、数据库接入过程、Git/GitHub协作流程以及后续开发计划。  
> 项目开发环境：Android Studio  
> 主要语言：Java  
> 包名：`gdufs.groupwork.WordApp.com`  
> GitHub仓库：`https://github.com/Aaronzhang11/WordApp`（Private）

---

## 1. 项目当前状态总结

目前项目已经从最初的AI生成框架，推进到可以运行和演示基础功能的版本。当前已经完成的核心内容如下：

| 模块 | 当前状态 | 说明 |
|---|---|---|
| 首页/学习看板 | 基础可用 | 可以显示待背/复习数量、已掌握数量、词库总数 |
| 本地词库数据库 | 已接入 | 已将ECDICT词库整理为SQLite数据库，并放入`app/src/main/assets/word_app.db` |
| 开始背单词模式 | 基础可用 | 可以随机显示英文、音标、中文释义，并支持认识/不认识操作 |
| 模拟自测卷模式 | 基础可用 | 可以生成选择题并判断答案对错 |
| 中英双向检索模式 | 基础可用 | 可以输入英文或中文进行本地模糊查询 |
| 自定义单词本 | 暂未完成 | 首页入口已存在，但目前仍显示“正在加载” |
| GitHub协作 | 已完成初始上传 | 项目已上传至GitHub Private仓库，后续组员可通过`git clone`协作 |

当前项目已经具备课程大作业的基础演示能力，但要达到较完整的版本，后续还需要继续完善单词本、单词详情页、收藏功能、测试模式选择和界面细节。

---

## 2. 项目文件结构说明

当前Android Studio项目根目录为：

```text
D:\AndroidStudioProject\WordApp
```

主要结构如下：

```text
WordApp
├─ app
│  ├─ src
│  │  ├─ main
│  │  │  ├─ assets
│  │  │  │  └─ word_app.db
│  │  │  ├─ java
│  │  │  │  └─ gdufs/groupwork/WordApp/com
│  │  │  │     ├─ DatabaseHelper.java
│  │  │  │     ├─ MainActivity.java
│  │  │  │     ├─ StudyActivity.java
│  │  │  │     ├─ QuizActivity.java
│  │  │  │     └─ SearchActivity.java
│  │  │  ├─ res
│  │  │  │  ├─ layout
│  │  │  │  │  ├─ activity_main.xml
│  │  │  │  │  ├─ activity_study.xml
│  │  │  │  │  ├─ activity_quiz.xml
│  │  │  │  │  └─ activity_search.xml
│  │  │  │  ├─ values
│  │  │  │  │  ├─ colors.xml
│  │  │  │  │  ├─ colors_night.xml
│  │  │  │  │  ├─ strings.xml
│  │  │  │  │  └─ themes.xml
│  │  │  │  └─ drawable / mipmap / xml
│  │  │  └─ AndroidManifest.xml
│  │  ├─ androidTest
│  │  └─ test
│  └─ build.gradle.kts
├─ gradle
├─ build.gradle.kts
├─ settings.gradle.kts
├─ gradle.properties
├─ gradlew
├─ gradlew.bat
├─ .gitignore
└─ Work.md
```

### 2.1 `java`目录

`java`目录用于存放Java业务代码。当前主要包含：

| 文件 | 作用 |
|---|---|
| `DatabaseHelper.java` | 负责复制并打开本地SQLite数据库，同时补齐用户学习记录表 |
| `MainActivity.java` | 首页/学习看板，负责显示统计信息和跳转到各功能页面 |
| `StudyActivity.java` | 单词卡片背诵页面，负责显示单词、翻面、认识/不认识、移除等逻辑 |
| `QuizActivity.java` | 模拟自测卷页面，负责生成选择题、打乱选项、判断对错 |
| `SearchActivity.java` | 中英双向检索页面，负责监听输入框并查询本地词库 |

### 2.2 `res`目录

`res`目录用于存放项目资源，主要包括布局文件、颜色、主题、图标等。

| 子目录 | 作用 |
|---|---|
| `res/layout` | 存放页面XML布局文件 |
| `res/values` | 存放颜色、主题、字符串等全局资源 |
| `res/drawable` | 存放图片、背景、形状资源 |
| `res/mipmap` | 存放APP图标 |
| `res/xml` | 存放系统生成的备份配置等XML文件 |

### 2.3 `assets`目录

`assets`目录用于存放需要原样打包进APP的文件。本项目中最重要的是：

```text
app/src/main/assets/word_app.db
```

该文件是已经整理好的SQLite词库数据库，APP第一次运行时会将它复制到手机内部数据库目录中。

---

## 3. 数据库接入过程记录

### 3.1 初始测试阶段

项目最初的`DatabaseHelper.java`只负责创建空表，没有真实单词数据。因此首页显示为：

```text
0，0，0
```

为了先测试功能流程，曾在`DatabaseHelper.java`中临时插入20个测试单词，例如：

```text
apple
banana
computer
student
teacher
```

这一步只用于验证页面能否正常读取数据库、查询单词、进入背单词和自测功能。后期正式接入词库后，不再手动在Java代码里添加单词。

### 3.2 正式词库来源

正式词库使用开源英汉词典项目ECDICT。下载了以下文件：

```text
ecdict.csv
ecdict.mini.csv
```

随后将`ecdict.csv`筛选整理为SQLite数据库，重点保留字段：

```text
word
phonetic
definition
translation
tag
```

最终生成：

```text
word_app.db
```

当前导入的词条数量约为：

```text
14927
```

数据库大小约：

```text
4.60MB
```

该数据库已放入：

```text
app/src/main/assets/word_app.db
```

### 3.3 数据库表结构

当前数据库主要包含以下表：

| 表名 | 作用 |
|---|---|
| `ecdict` | 静态词库表，保存单词、音标、英文释义、中文释义、标签 |
| `study_record` | 学习记录表，保存掌握程度、下次复习时间、错误次数、是否忽略 |
| `word_book` | 单词本表，保存用户创建的自定义单词本 |
| `book_word_relation` | 单词与单词本关系表 |
| `test_record` | 测试记录表，保存测试类型、题目数、正确数和日期 |

### 3.4 关键问题与解决

#### 问题1：`no such table: study_record`

接入预置数据库后，第一次运行出现：

```text
no such table: study_record
```

原因是导入的数据库中只有`ecdict`词库表，没有用户学习记录表。解决方法是在`DatabaseHelper.java`中增加`createUserTables()`方法，每次打开数据库时自动创建用户数据表。

#### 问题2：`no such table: ecdict`

后续又出现：

```text
no such table: ecdict
```

原因是数据库文件放错位置，误放到了：

```text
app/src/assets/word_app.db
```

正确位置应为：

```text
app/src/main/assets/word_app.db
```

移动到正确目录并卸载旧APP重新运行后，问题解决。

---

## 4. 当前`DatabaseHelper.java`说明

当前版本的`DatabaseHelper.java`不再手动插入测试单词，而是完成以下工作：

1. 检查手机内部数据库目录中是否已有`word_app.db`；
2. 如果没有，则从`assets/word_app.db`复制数据库；
3. 打开数据库时自动补齐用户数据表；
4. 启用外键约束；
5. 不再随意删除用户数据，避免学习记录丢失。

当前推荐版本如下：

```java
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
            throw new RuntimeException("复制assets中的word_app.db失败，请检查app/src/main/assets/word_app.db是否存在", e);
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
```

---

## 5. 当前主要页面说明

### 5.1 首页：`MainActivity.java` + `activity_main.xml`

首页用于展示当前学习状态并提供功能入口。页面包含：

```text
单词记忆系统
今日学习进度
待背/复习
已掌握
词库总数
开始背单词（卡片模式）
模拟自测卷
中英双向检索（词典）
我的自定义单词本
```

当前首页会从数据库中读取：

```sql
SELECT COUNT(*) FROM study_record WHERE is_ignored = 0 AND next_review_time <= ?
SELECT COUNT(*) FROM study_record WHERE master_level >= 3
SELECT COUNT(*) FROM ecdict
```

因此接入正式词库后，首页词库总数会显示约`14927`。

### 5.2 背单词页面：`StudyActivity.java` + `activity_study.xml`

背单词页面实现卡片式学习。当前逻辑为：

1. 优先查询到期复习单词；
2. 如果没有复习任务，则从`ecdict`中随机抽取未学习单词；
3. 正面显示英文和音标；
4. 点击卡片后显示中文释义；
5. 点击“认识”后提高掌握等级；
6. 点击“不认识”后降低或重置掌握等级；
7. 更新`study_record`表中的`master_level`和`next_review_time`。

当前艾宾浩斯逻辑为简化版本：

| 操作 | 掌握等级变化 | 下次复习时间 |
|---|---|---|
| 认识 | `master_level + 1`，最高为3 | 5分钟、1天、7天 |
| 不认识 | `master_level = 0` | 1分钟后 |

### 5.3 自测页面：`QuizActivity.java` + `activity_quiz.xml`

自测页面当前主要实现“看中文选英文”的基础模式。

当前逻辑为：

1. 优先从`study_record`中抽取`master_level > 0`的单词；
2. 如果已学习单词不足10个，则直接从`ecdict`词库中随机抽题；
3. 每题1个正确答案和3个干扰项；
4. 使用`Collections.shuffle()`打乱选项；
5. 点击选项后判断正确或错误；
6. 测试结束后显示分数。

当前已有听写界面布局和在线发音代码框架，但暂未完善测试模式选择。

### 5.4 查询页面：`SearchActivity.java` + `activity_search.xml`

查询页面实现中英双向模糊检索。

当前逻辑为：

| 输入类型 | 查询字段 |
|---|---|
| 英文输入 | 查询`ecdict.word` |
| 中文输入 | 查询`ecdict.translation` |

英文查询示例：

```sql
SELECT word, translation FROM ecdict WHERE word LIKE ? LIMIT 30
```

中文查询示例：

```sql
SELECT word, translation FROM ecdict WHERE translation LIKE ? LIMIT 30
```

当前查询结果以`ListView`显示，后续可增加单词详情页和收藏入口。

---

## 6. Git与GitHub协作记录

### 6.1 安装Git

最初在Android Studio Terminal中输入：

```bash
git init
```

系统提示：

```text
git不是内部或外部命令，也不是可运行的程序或批处理文件
```

因此安装了Git for Windows。

下载地址：

```text
https://gitforwindows.org/
```

安装过程中关键选择：

| 安装步骤 | 选择 |
|---|---|
| Select Components | 基本保持默认 |
| Default editor | 优先选择VS Code或Notepad，不建议Vim |
| Initial branch name | 选择`main` |
| PATH environment | 选择`Git from the command line and also from 3rd-party software` |
| SSH executable | 选择`Use bundled OpenSSH` |
| HTTPS backend | 选择`Use the native Windows Secure Channel library` |
| Line endings | 选择`Checkout Windows-style, commit Unix-style line endings` |

安装后重启Android Studio，在Terminal中执行：

```bash
git --version
```

显示：

```text
git version 2.54.0.windows.1
```

说明安装成功。

### 6.2 初始化并上传项目

在项目根目录：

```text
D:\AndroidStudioProject\WordApp
```

执行：

```bash
git init
git add .
git status
git commit -m "Initial Android project"
git branch -M main
git remote add origin https://github.com/Aaronzhang11/WordApp.git
git push -u origin main
```

其中曾误输入：

```bash
git add.
```

正确命令应为：

```bash
git add .
```

即`add`和`.`之间需要有空格。

### 6.3 `.gitignore`说明

当前`.gitignore`用于避免上传本机配置和编译缓存。推荐内容如下：

```gitignore
*.iml

.gradle/
build/
*/build/

local.properties

.idea/caches/
.idea/libraries/
.idea/modules.xml
.idea/workspace.xml
.idea/navEditor.xml
.idea/assetWizardSettings.xml

.DS_Store
Thumbs.db

/captures/
.externalNativeBuild/
.cxx/
```

注意：不要加入：

```gitignore
*.db
```

因为本项目需要上传：

```text
app/src/main/assets/word_app.db
```

该数据库是项目运行所需文件，同伴克隆项目后需要直接使用。

### 6.4 日常协作流程

每次开始改代码前建议先执行：

```bash
git pull
```

用于同步GitHub上的最新代码。

完成一个功能并测试可以运行后，再执行：

```bash
git add .
git commit -m "说明本次修改内容"
git push
```

三个命令含义如下：

| 命令 | 作用 |
|---|---|
| `git add .` | 将当前修改加入暂存区 |
| `git commit -m "说明"` | 生成一次本地版本记录 |
| `git push` | 上传到GitHub |

如果同伴第一次下载项目，可以执行：

```bash
git clone https://github.com/Aaronzhang11/WordApp.git
```

然后用Android Studio打开`WordApp`文件夹。

---

## 7. 已知问题与后续计划

### 7.1 当前已知问题

| 问题 | 当前状态 |
|---|---|
| 自定义单词本不可用 | 首页按钮仍为Toast提示 |
| 收藏功能未真正写入数据库 | `StudyActivity`中目前只是Toast提示 |
| 查询结果没有详情页 | 当前只显示单词和释义列表 |
| 测试模式选择不完整 | 当前默认看中选英，听写和看英选中未完全完成 |
| 测试结果未保存 | `test_record`表已存在，但未真正写入 |
| 页面存在少量UI/交互bug | 暂时不影响基础功能演示 |

### 7.2 下一步开发优先级

建议按照以下顺序继续完善：

1. **实现自定义单词本页面**
   - 新增`WordBookActivity.java`
   - 新增`activity_word_book.xml`
   - 支持创建、显示、删除单词本

2. **实现单词详情页**
   - 新增`WordDetailActivity.java`
   - 新增`activity_word_detail.xml`
   - 显示单词、音标、英文释义、中文释义、标签

3. **实现收藏功能**
   - 从背单词页面和查询详情页加入单词本
   - 写入`book_word_relation`表

4. **完善测试模块**
   - 增加测试模式选择页面
   - 实现看英选中
   - 完善听写测试和发音失败提示
   - 将测试结果保存到`test_record`

5. **完善首页统计**
   - 显示今日已学习数量
   - 显示掌握等级分布
   - 增加单词本快捷入口

6. **最后统一UI和报告**
   - 修复界面细节
   - 录制演示视频
   - 编写实验报告
   - 整理小组分工和GitHub协作记录

---

## 8. 四人分工建议

| 成员 | 负责模块 | 主要任务 |
|---|---|---|
| 成员A | 数据库与项目整合 | 维护`DatabaseHelper`、词库、GitHub、整体联调 |
| 成员B | 背单词模块 | 完善`StudyActivity`、卡片交互、艾宾浩斯复习逻辑 |
| 成员C | 测试模块 | 完善`QuizActivity`、测试模式、错题与测试记录 |
| 成员D | 查询与单词本模块 | 完成`SearchActivity`、详情页、收藏、单词本 |

对接时需要重点统一：

```text
表名
字段名
Activity跳转参数
数据库查询方法
页面ID命名
```

尤其不要多人同时修改：

```text
DatabaseHelper.java
AndroidManifest.xml
build.gradle.kts
activity_main.xml
```

这些公共文件容易产生Git冲突。

---

## 9. 当前阶段结论

当前项目已经完成从需求文档到Android Studio基础项目的搭建，并成功接入本地ECDICT词库数据库。首页、背单词、模拟自测和中英双向查询三个核心模块已经具备基础可运行能力。项目也已经上传至GitHub Private仓库，具备小组协作条件。

后续开发重点应从“能运行”转向“能完整展示”，优先补齐自定义单词本、单词详情页和收藏功能，再继续完善测试模式与界面体验。
