package com.aims.core.interview;

/** 面试官人设类型。 */
public enum InterviewerPersona {
    /** 温和型：鼓励、引导，适合初级岗位 */
    FRIENDLY,
    /** 压力面型：追问、质疑，适合高压岗位 */
    PRESSURE,
    /** 深度技术型：原理深挖、场景设计，适合高级技术岗位 */
    TECHNICAL;

    /** 从字符串解析，非法值或 null 返回默认 FRIENDLY。 */
    public static InterviewerPersona fromString(String value) {
        if (value == null || value.isBlank()) return FRIENDLY;
        try {
            return InterviewerPersona.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return FRIENDLY;
        }
    }
}
