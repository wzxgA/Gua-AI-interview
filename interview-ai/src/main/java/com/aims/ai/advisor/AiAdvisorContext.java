package com.aims.ai.advisor;

/** Advisor 上下文参数 key：由 AiChatFacade 统一写入，Advisor 读取做计量归因与日志。 */
public final class AiAdvisorContext {

    /** 模型档位（ModelTier.name()） */
    public static final String TIER = "aims.tier";

    /** 当前生效模型名（降级后为 fallback 模型） */
    public static final String MODEL = "aims.model";

    /**
     * 当前 Node 名称（Phase 6 新增）：由 {@code GraphTraceAspect} 经 {@code NodeContextHolder} 设置到
     * ThreadLocal，{@code DefaultAiChatFacade.newSpec} 透传到 Advisor context，用于 per-node Token
     * 归因（{@code aims.graph.node.tokens}）。
     *
     * <p>未在 Node 执行上下文中时为 null，Advisor 端用 "unknown" 兜底。
     *
     * @since 1.2.0 Phase 6
     */
    public static final String NODE = "aims.node";

    private AiAdvisorContext() {}
}
