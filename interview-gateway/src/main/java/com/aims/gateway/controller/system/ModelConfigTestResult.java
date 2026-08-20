package com.aims.gateway.controller.system;

import java.util.List;

/**
 * 连通性测试结果（POST /api/v1/system/model-config/test 响应）。
 *
 * <p>每个档位一条：调用成功返回耗时（毫秒），失败返回原因（不含 API Key 明文）。
 *
 * @param results 各档位测试结果
 */
public record ModelConfigTestResult(List<TierResult> results) {

    public record TierResult(String tier, boolean success, Long latencyMs, String error) {}
}
