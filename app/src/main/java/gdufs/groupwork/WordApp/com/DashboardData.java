package gdufs.groupwork.WordApp.com;

import java.util.List;

/**
 * 首页与学习数据看板使用的统计数据对象。
 */
public class DashboardData {

    // 当前词书总词数
    public int totalWords;

    // 当前词书中已经达到 level 4 的单词数
    public int mastered;

    // 当前词书中处于 level 1、2、3 的单词数
    public int learning;

    // 当前词书中处于 level 0 的单词数
    public int unlearned;

    /*
     * 保留旧字段名，避免改 HomeFragment / DashboardActivity。
     *
     * 现在它不再表示“今日新词”，而是：
     * 今天真实完成的学习操作次数。
     */
    public int todayNew;

    // 当前用户连续学习天数
    public int consecutiveDays;

    // 近 7 天每天的真实学习操作次数
    public List<Integer> last7DaysCount;
}