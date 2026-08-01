package com.aims.agent;

import com.aims.core.interview.FollowUpContext;
import com.aims.core.interview.FollowUpDecision;
import reactor.core.publisher.Flux;

/** 追问 Agent：评估回答质量，决定是否追问。 */
public interface FollowUpAgent {

    /** 评估回答并决定是否追问。 */
    FollowUpDecision evaluate(FollowUpContext context);

    /** 流式生成追问问题。 */
    Flux<String> streamFollowUp(FollowUpContext context, FollowUpDecision decision);
}
