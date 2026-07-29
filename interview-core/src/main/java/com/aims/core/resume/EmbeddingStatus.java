package com.aims.core.resume;

/** 简历向量化状态。 */
public enum EmbeddingStatus {
    /** 待向量化。 */
    PENDING,
    /** 向量化处理中。 */
    PROCESSING,
    /** 向量化成功。 */
    COMPLETED,
    /** 向量化失败，可重试。 */
    FAILED
}
