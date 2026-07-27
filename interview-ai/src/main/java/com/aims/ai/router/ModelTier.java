package com.aims.ai.router;

/** 模型档位（与技术方案 3.2 对齐）：按用途路由到不同提供商/模型/参数。 */
public enum ModelTier {
    /** 旗舰档：面试对话主模型 */
    FLAGSHIP,
    /** 标准档：追问决策 / 评估打分 */
    STANDARD,
    /** 经济档：摘要 / 报告初稿 */
    ECONOMY,
    /** 向量化档：题库/简历/JD Embedding */
    EMBEDDING
}
