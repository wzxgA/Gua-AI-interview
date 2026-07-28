package com.aims.core.question;

/** 题目分类。 */
public enum QuestionCategory {

    /** 技术类 */
    TECHNICAL("技术类"),
    /** 行为类 */
    BEHAVIORAL("行为类"),
    /** 项目类 */
    PROJECT("项目类");

    private final String label;

    QuestionCategory(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
