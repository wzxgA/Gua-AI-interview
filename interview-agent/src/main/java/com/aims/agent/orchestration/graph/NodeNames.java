package com.aims.agent.orchestration.graph;

/** 面试流程 StateGraph 中所有节点的名称常量，供 Graph 工厂、Engine 层和测试代码引用。 */
public final class NodeNames {

    private NodeNames() {}

    public static final String PLAN = "plan";
    public static final String ASK = "ask";
    public static final String ANSWER = "answer";
    public static final String FOLLOW_UP_DECISION = "followUpDecision";
    public static final String FOLLOW_UP = "followUp";
    public static final String EVALUATE = "evaluate";
    public static final String SUMMARY = "summary";
    public static final String SUPERVISE = "supervise";
    public static final String END_CHECK = "endCheck";
    public static final String REPORT = "report";
}
