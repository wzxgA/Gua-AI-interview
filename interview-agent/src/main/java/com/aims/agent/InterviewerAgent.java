package com.aims.agent;

import reactor.core.publisher.Flux;

/**
 * 面试官 Agent（P3）：基于 Spring AI 的单 Agent，负责生成面试问题。
 *
 * <p>不负责评分、动态追问或报告。
 */
public interface InterviewerAgent {

    /** 生成当前轮次问题（阻塞）。 */
    String nextQuestion(InterviewContext context);

    /** 流式生成当前轮次问题。 */
    Flux<String> streamQuestion(InterviewContext context);
}
