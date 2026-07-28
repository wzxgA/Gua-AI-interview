package com.aims.core.evaluation;

/** 评估维度（含中文名与权重）。 */
public enum EvaluationDimension {

    /** 专业能力 */
    PROFESSIONAL("专业能力", 0.40),
    /** 逻辑思维 */
    LOGIC("逻辑思维", 0.20),
    /** 沟通表达 */
    COMMUNICATION("沟通表达", 0.15),
    /** 岗位匹配 */
    JOB_MATCH("岗位匹配", 0.15),
    /** 学习与潜力 */
    POTENTIAL("学习与潜力", 0.10);

    private final String label;
    private final double weight;

    EvaluationDimension(String label, double weight) {
        this.label = label;
        this.weight = weight;
    }

    public String getLabel() {
        return label;
    }

    public double getWeight() {
        return weight;
    }
}
