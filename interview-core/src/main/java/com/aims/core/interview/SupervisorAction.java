package com.aims.core.interview;

/**
 * 面试总指挥（SupervisorAgent）动作建议。
 *
 * <p>一期仅作为建议字段驱动流程路由，不做自动减题。
 */
public enum SupervisorAction {

    /** 节奏正常，按计划继续。 */
    CONTINUE,

    /** 进度偏慢/回答质量下降，建议收敛当前话题（勿深挖）。 */
    TIGHTEN,

    /** 超时/进度严重异常，建议提前结束面试。 */
    END
}
