package com.aims.ai.advisor;

/** Advisor 上下文参数 key：由 AiChatFacade 统一写入，Advisor 读取做计量归因与日志。 */
public final class AiAdvisorContext {

    /** 模型档位（ModelTier.name()） */
    public static final String TIER = "aims.tier";

    /** 当前生效模型名（降级后为 fallback 模型） */
    public static final String MODEL = "aims.model";

    private AiAdvisorContext() {}
}
