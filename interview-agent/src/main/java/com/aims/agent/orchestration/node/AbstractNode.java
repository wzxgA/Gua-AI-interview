package com.aims.agent.orchestration.node;

import com.aims.agent.orchestration.state.InterviewState;
import org.bsc.langgraph4j.action.NodeAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Node 基类：提供日志和节点名称。
 *
 * @param <S> 状态类型
 * @since 1.1.0
 */
public abstract class AbstractNode<S extends InterviewState> implements NodeAction<S> {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    /** 用于日志和 Metrics 的节点名称。 */
    public abstract String nodeName();
}
