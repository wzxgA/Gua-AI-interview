package com.aims.core.interview;

import java.io.Serializable;

/** 面试计划中的一个模块。 */
public record PlanSection(String name, int questionCount, String objective)
        implements Serializable {

    public PlanSection {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("计划模块名称不能为空");
        }
        if (questionCount <= 0) {
            throw new IllegalArgumentException("计划模块题目数必须大于 0");
        }
        if (objective == null || objective.isBlank()) {
            throw new IllegalArgumentException("计划模块目标不能为空");
        }
    }
}
