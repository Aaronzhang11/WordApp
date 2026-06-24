# 本地单词本（WordApp）

一款基于 Android 的离线英语单词学习应用，内置 ECDICT 词库，支持背单词、复习、词典查询、模拟测试与个人单词本管理。

## 功能概览

### 用户系统
- 注册 / 登录 / 游客模式
- 个人资料与头像展示

### 首页
- 当前词书信息与学习进度
- 今日新词、复习任务进度
- 学习数据看板（连续学习天数、近 7 天趋势等）

### 学习中心
- **今日学习**：按学习计划自动安排新词与复习
- **学习新词**：卡片式背单词，支持「认识 / 不认识」
- **复习旧词**：基于掌握等级与下次复习时间的间隔复习
- **学习设置**：自定义每日新词数（10–100）与复习上限（20–200）

### 词书管理
- 默认词书：ECDICT 全词库
- 自定义词书：按标签筛选（中考、高考、四级、六级、考研、托福、雅思、GRE）
- 最多创建 5 本词书（含默认）

### 词典
- 按英文或中文释义搜索 ECDICT 词库
- 查看单词详情，支持加入个人单词本

### 模拟测试
- 中译英选择题
- 英译中选择题
- 听写测试

### 单词本
- 创建多个收藏单词本
- 管理已收藏单词

## 技术栈

| 项目 | 说明 |
|------|------|
| 语言 | Java |
| 最低 SDK | Android 7.0（API 24） |
| 目标 SDK | API 33 |
| 数据库 | SQLite（本地 `word_app.db`，首次启动从 assets 复制） |
| UI | Material Design、BottomNavigationView、Fragment |

### 主要依赖

- AndroidX AppCompat
- Material Components
- ConstraintLayout
- Fragment

## 项目结构

```
WordApp/
├── app/
│   └── src/main/
│       ├── assets/              # 预置词库 word_app.db
│       ├── java/gdufs/groupwork/WordApp/com/
│       │   ├── LoginActivity.java          # 登录
│       │   ├── RegisterActivity.java       # 注册
│       │   ├── MainActivity.java           # 主界面（底部导航）
│       │   ├── HomeFragment.java           # 首页
│       │   ├── StudyCenterActivity.java    # 学习中心
│       │   ├── StudyActivity.java          # 背单词
│       │   ├── ReviewOldActivity.java      # 复习旧词
│       │   ├── StudySettingsActivity.java  # 学习设置
│       │   ├── SearchFragment.java         # 词典搜索
│       │   ├── WordBookFragment.java       # 单词本
│       │   ├── TestModeFragment.java       # 模拟测试
│       │   ├── QuizActivity.java           # 选择题测试
│       │   ├── DictationActivity.java      # 听写测试
│       │   ├── VocabBookManager.java       # 词书管理
│       │   ├── StudyPlanManager.java       # 学习计划
│       │   ├── DatabaseHelper.java         # 数据库
│       │   ├── StatisticsHelper.java       # 统计
│       │   └── ...
│       └── res/                 # 布局与资源文件
├── build.gradle.kts
├── settings.gradle.kts
└── gradle/wrapper/
```

## 环境要求

- **JDK**：8 或以上
- **Android Studio**：推荐 Hedgehog 或更新版本
- **Gradle**：7.3.3（项目已包含 Wrapper，无需单独安装）

## 构建与运行

### 使用 Android Studio

1. 克隆或下载本项目
2. 用 Android Studio 打开项目根目录
3. 等待 Gradle 同步完成
4. 连接 Android 设备或启动模拟器（API 24+）
5. 点击 **Run** 运行应用

### 使用命令行

在项目根目录执行：

```powershell
# Windows
.\gradlew.bat assembleDebug

# 安装到已连接设备
.\gradlew.bat installDebug
```

生成的 Debug APK 位于：

```
app/build/outputs/apk/debug/app-debug.apk
```

## 使用说明

1. 首次启动进入登录页，可注册账号或以游客身份进入
2. 在首页查看当前词书与学习进度，点击「开始学习」进入学习中心
3. 在「学习设置」中调整每日新词与复习数量
4. 通过底部导航切换：**首页**、**模拟测试**、**词典**、**单词本**
5. 在词书设置中创建或切换自定义词书

## 数据库说明

应用使用 SQLite 本地存储，主要数据表包括：

| 表名 | 用途 |
|------|------|
| `ecdict` | ECDICT 词库（预置） |
| `user_info` | 用户账户 |
| `study_record` | 单词学习状态（掌握等级、复习时间等） |
| `study_history` | 学习行为记录（用于统计与趋势） |
| `vocab_book` | 自定义词书 |
| `word_book` / `book_word_relation` | 收藏单词本 |
| `test_record` | 测试记录 |

> **注意**：首次运行需确保 `app/src/main/assets/word_app.db` 存在，否则应用会因词库复制失败而无法启动。

## 许可证

本项目为广东外语外贸大学移动软件开发课程小组作业，仅供学习交流使用。
