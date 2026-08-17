package com.aims.agent;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * 简历事实交叉验证工具（F2）：注册给追问决策 LLM，模型判定"需查证回答与简历一致性"时自主调用。
 *
 * <p>底层经 {@link ResumeCrossCheckExecutor} 端口检索简历（interview-infra 实现，复用 B §6 混合检索与可解释性字段）；
 * 工具异常/无结果返回 {@code null}，模型收到"无证据"继续决策，不阻断追问链路。
 */
@Component
public class ResumeCrossCheckTool {

    private final ResumeCrossCheckExecutor executor;

    public ResumeCrossCheckTool(ResumeCrossCheckExecutor executor) {
        this.executor = executor;
    }

    /**
     * 将候选人回答与简历经历做事实比对，返回匹配分数与命中片段，用于发现简历矛盾。
     *
     * @param candidateResumeId 候选人简历 ID
     * @param answerText 候选人本次回答文本
     * @return 比对证据；检索失败返回 null（模型视为无证据，继续原决策）
     */
    @Tool(
            description =
                    "将候选人回答与简历经历做事实比对，返回匹配分数(score)与命中片段，用于发现简历矛盾。"
                            + "score<0.5 表示回答在简历中缺乏对应支持（疑似未提及/夸大），score>=0.7 表示与简历一致")
    public ResumeCrossCheckResult crossCheck(
            @ToolParam(description = "候选人简历 ID") Long candidateResumeId,
            @ToolParam(description = "候选人本次回答文本") String answerText) {
        return executor.crossCheck(candidateResumeId, answerText);
    }
}
