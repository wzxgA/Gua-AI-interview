package com.aims.agent;

import java.util.List;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * 代码判题工具（F2 P2 预留）：在隔离沙箱运行候选人代码并跑测试用例（算法/编程题判题）。
 *
 * <p>本期仅注册框架、不接入执行服务（外部沙箱为独立子项目）；未配置沙箱时返回明确错误，不影响主流程。
 */
@Component
public class CodeJudgeTool {

    /**
     * 运行候选人代码并跑测试用例。
     *
     * @param language 编程语言
     * @param code 候选人代码
     * @param testCases 测试用例列表
     * @return 判题结果；本期恒返回 "sandbox not configured"
     */
    @Tool(description = "在隔离沙箱运行候选人代码并跑测试用例（算法/编程题判题）")
    public CodeJudgeResult judge(
            @ToolParam(description = "编程语言") String language,
            @ToolParam(description = "候选人代码") String code,
            @ToolParam(description = "测试用例列表") List<String> testCases) {
        return new CodeJudgeResult(false, null, "sandbox not configured");
    }
}
