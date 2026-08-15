package com.aims.gateway.controller.interview;

import java.util.List;

/** 防作弊摘要响应：按事件类型聚合。 */
public record ProctorSummaryResponse(List<ProctorTypeSummary> items) {

    /** 单类型聚合项。 */
    public record ProctorTypeSummary(String type, long count, long totalDurationMs) {}
}
