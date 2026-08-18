package com.aims.agent;

/**
 * 代码判题工具结果（F2 预留）：占位实现，未接入沙箱时返回明确错误，不影响主流程。
 *
 * @param passed 是否通过全部测试用例
 * @param output 运行输出（通过时为简要结果）
 * @param error 错误信息（未配置沙箱时为 "sandbox not configured"）
 */
public record CodeJudgeResult(boolean passed, String output, String error) {}
