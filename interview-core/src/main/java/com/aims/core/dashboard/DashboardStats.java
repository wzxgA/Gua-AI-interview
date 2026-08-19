package com.aims.core.dashboard;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * 仪表盘聚合统计结果。
 *
 * <p>所有字段均在 Service 层一次性组装，避免 Controller 多次调用远程/DB。
 *
 * @param statusCounts 按会话状态分组的数量分布（对齐 {@link com.aims.core.session.SessionStatus} 顺序）
 * @param dailyTrend 近 30 天每日创建数趋势（升序，无数据日期补 0）
 * @param scoreStats 得分统计（平均分 + 五档区间分布）
 * @param recentInterviews 最近 8 条面试摘要（按创建时间倒序）
 */
public record DashboardStats(
        List<StatusCount> statusCounts,
        List<TrendPoint> dailyTrend,
        ScoreStats scoreStats,
        List<RecentInterview> recentInterviews) {

    /**
     * 状态分布项。
     *
     * <p>count 使用 int 而非 long：全局 Jackson 配置将 Long/long 序列化为字符串 （用于保护大 ID 精度），统计数值必须保持 JSON
     * 数字类型，前端才能正确求和/绘图。
     */
    public record StatusCount(String status, int count) {}

    /** 趋势点（date 为 yyyy-MM-dd）；count 用 int 原因同 {@link StatusCount}。 */
    public record TrendPoint(String date, int count) {}

    /**
     * 得分统计（5 分制）。
     *
     * @param avgScore 平均分
     * @param distribution 五档区间计数分布
     */
    public record ScoreStats(BigDecimal avgScore, List<ScoreRange> distribution) {}

    /** 得分区间项（如 "3-4"）；count 用 int 原因同 {@link StatusCount}。 */
    public record ScoreRange(String range, int count) {}

    /**
     * 得分点（散点图，5 分制）。
     *
     * <p>score 为该会话综合得分，createdAt 为创建时间，前端按时间横轴绘制散点分布。
     */
    public record ScorePoint(double score, Instant createdAt) {}

    /** 最近面试摘要。 */
    public record RecentInterview(
            Long id,
            String candidateName,
            String positionTitle,
            String status,
            BigDecimal totalScore,
            Instant createdAt) {}
}
