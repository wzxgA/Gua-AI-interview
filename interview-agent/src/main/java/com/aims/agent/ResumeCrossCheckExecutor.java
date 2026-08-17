package com.aims.agent;

/**
 * 简历交叉验证执行端口（依赖倒置，F2）。
 *
 * <p>interview-agent 不依赖 interview-infra，因此 {@link ResumeCrossCheckTool} 面向该端口编程； 具体实现（走
 * ResumeRagService 混合检索）位于 interview-infra，由 Spring 容器注入。
 *
 * <p>实现约定：返回 {@code null} 表示检索失败（工具层转化为"无证据"，不阻断追问决策）。
 */
public interface ResumeCrossCheckExecutor {

    /** 将候选人回答与指定简历做事实比对，返回证据；检索失败返回 null。 */
    ResumeCrossCheckResult crossCheck(Long candidateResumeId, String answerText);
}
