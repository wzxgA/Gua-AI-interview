package com.aims.agent;

import com.aims.core.report.ReportContext;
import com.aims.core.report.ReportResult;

/**
 * 报告 Agent：基于全部评分和对话摘要，生成综合面试报告。
 *
 * <p>使用 STANDARD 档位模型，温度 0.3，允许一定生成灵活性但保持稳定。
 */
public interface ReportAgent {

    /**
     * 生成综合面试报告。
     *
     * @param context 报告上下文
     * @return 结构化面试报告
     */
    ReportResult generate(ReportContext context);
}
