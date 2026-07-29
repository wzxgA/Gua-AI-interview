package com.aims.core.resume;

/** 简历解析状态。 */
public enum ResumeStatus {
    /** 待解析 */
    PENDING,
    /** 解析处理中 */
    PROCESSING,
    /** 解析成功 */
    PARSED,
    /** 解析失败 */
    FAILED
}
