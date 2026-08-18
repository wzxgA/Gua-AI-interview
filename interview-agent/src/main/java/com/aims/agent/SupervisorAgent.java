package com.aims.agent;

import com.aims.core.interview.SupervisorContext;
import com.aims.core.interview.SupervisorDecision;

/** 面试总指挥：基于会话实时状态给出节奏建议（继续 / 收紧 / 提前结束）。 */
public interface SupervisorAgent {

    /** 基于会话状态快照给出节奏建议。 */
    SupervisorDecision supervise(SupervisorContext ctx);
}
