package com.aims.agent;

import java.util.List;

/**
 * 简历实体提名端口：从候选人回答中提取"疑似公司/项目实体"提名。
 *
 * <p>两级判定设计：{@link RegexResumeMentionExtractor} 做低成本正则首轮提名；{@link AiResumeMentionExtractor}
 * 在正则命中候选时再交由 AI 做语义判定，过滤掉技术名词/职责短语等非真实体，消除"CLH等待队列""只处理网络"类假阳性。
 *
 * <p>interview-agent 不依赖 interview-infra，端口仅面向回答文本；实体与简历经历的匹配由 downstream（DB 比对）完成。
 */
public interface ResumeEntityMentionExtractor {

    /**
     * 从回答中提取真实实体提名。
     *
     * @return 非空列表为真实体提名；空列表表示"判定成功但无非真实体"（不产生矛盾点）；{@code null} 表示提取器不可用/失败（调用方回退）
     */
    List<ResumeMention> extract(String answer);

    /** 单个实体提名：仅含确认的真实体名称与证据片（候选原文）。 */
    record ResumeMention(String resolvedName, String evidenceSnippet) {}
}
