package com.aims.gateway.controller.dashboard;

import com.aims.core.common.Result;
import com.aims.core.dashboard.DashboardStats;
import com.aims.infra.persistence.service.InterviewSessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 仪表盘统计 REST API。 */
@RestController
@RequestMapping("/api/v1/dashboard")
@Tag(name = "仪表盘")
public class DashboardController {

    private final InterviewSessionService interviewSessionService;

    public DashboardController(InterviewSessionService interviewSessionService) {
        this.interviewSessionService = interviewSessionService;
    }

    @Operation(summary = "仪表盘统计", description = "返回面试总数/状态分布/30 日趋势/得分分布/最近面试等聚合数据")
    @GetMapping("/stats")
    public Result<DashboardStats> stats() {
        return Result.ok(interviewSessionService.getDashboardStats());
    }

    @Operation(
            summary = "得分分布统计（平均分 + 五档分布，可按时间过滤）",
            description =
                    "返回平均分与五档区间分布；过滤优先级：start/end（yyyy-MM-dd，含当天）自定义区间 >" + " days 最近 N 天 > 全部时间")
    @GetMapping("/score-stats")
    public Result<DashboardStats.ScoreStats> scoreStats(
            @RequestParam(required = false) Integer days,
            @RequestParam(required = false)
                    @org.springframework.format.annotation.DateTimeFormat(
                            iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
                    LocalDate start,
            @RequestParam(required = false)
                    @org.springframework.format.annotation.DateTimeFormat(
                            iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
                    LocalDate end) {
        return Result.ok(interviewSessionService.getScoreStats(days, start, end));
    }

    @Operation(
            summary = "得分散点图数据（得分 + 时间，可按时间过滤）",
            description =
                    "返回已评分会话的得分与创建时间（按时间升序），供散点图使用；过滤优先级：start/end（yyyy-MM-dd，含当天）"
                            + "自定义区间 > days 最近 N 天 > 全部时间")
    @GetMapping("/score-points")
    public Result<List<DashboardStats.ScorePoint>> scorePoints(
            @RequestParam(required = false) Integer days,
            @RequestParam(required = false)
                    @org.springframework.format.annotation.DateTimeFormat(
                            iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
                    LocalDate start,
            @RequestParam(required = false)
                    @org.springframework.format.annotation.DateTimeFormat(
                            iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
                    LocalDate end) {
        return Result.ok(interviewSessionService.getScorePoints(days, start, end));
    }
}
