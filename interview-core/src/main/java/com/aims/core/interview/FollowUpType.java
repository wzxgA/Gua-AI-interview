package com.aims.core.interview;

/** 追问类型枚举。 */
public enum FollowUpType {
    /** 非追问（计划内正常题目） */
    NONE,
    /** 澄清：回答模糊，要求具体化 */
    CLARIFY,
    /** 深挖：回答浅显，追问细节 */
    DEEPEN,
    /** 引导：偏题，引导回正轨 */
    REDIRECT
}
