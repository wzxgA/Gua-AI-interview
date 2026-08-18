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
     * 将候选人回答与简历经历做事实比对，返回匹配分数/命中片段与矛盾点明细，用于发现简历矛盾。
     *
     * @param candidateResumeId 候选人简历 ID
     * @param answerText 候选人本次回答文本
     * @param companyHint 可选：回答中提到的公司名（提供时优先定向比对"简历是否提及该公司"）
     * @return 比对证据（含 conflictDetails 矛盾点明细）；检索失败返回 null（模型视为无证据，继续原决策）
     */
    @Tool(
            description =
                    "将候选人回答与简历经历做事实比对，返回匹配分数(score)、命中片段与矛盾点明细(conflictDetails)，用于发现简历矛盾。score<0.5"
                            + " 或 conflictDetails 非空表示回答与简历不符（未提及/夸大/时间不一致），score>=0.7 且无矛盾点表示一致")
    public ResumeCrossCheckResult crossCheck(
            @ToolParam(description = "候选人简历 ID") Long candidateResumeId,
            @ToolParam(description = "候选人本次回答文本") String answerText,
            @ToolParam(description = "可选：回答中提到的公司名，用于定向比对简历是否提及") String companyHint) {
        return executor.crossCheck(candidateResumeId, answerText, companyHint);
    }
}
