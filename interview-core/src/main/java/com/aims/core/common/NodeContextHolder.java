package com.aims.core.common;

/**
 * 当前执行 Node 名称的线程上下文持有者。
 *
 * <p>由 {@code GraphTraceAspect} 进入 Node 时 {@link #set}，退出时 {@link #clear}。 供 {@code AiChatFacade}
 * 读取并透传到 Advisor context，实现 per-node Token 归因。
 *
 * <p>放在 core 模块避免 interview-ai 反向依赖 interview-agent。底层用 {@link ThreadLocal}，在 Node 执行线程内同步传播；流式场景若
 * Flux 切换线程可能导致丢失，后续 P7 改用 {@code ScopedValue}。
 *
 * @since 1.2.0 Phase 6
 */
public final class NodeContextHolder {

    private static final ThreadLocal<String> HOLDER = new ThreadLocal<>();

    private NodeContextHolder() {}

    /** 进入 Node 时设置当前节点名。 */
    public static void set(String nodeName) {
        HOLDER.set(nodeName);
    }

    /** 获取当前线程绑定的 Node 名；未设置时返回 null。 */
    public static String current() {
        return HOLDER.get();
    }

    /** Node 执行结束时清理，防止线程池复用污染。 */
    public static void clear() {
        HOLDER.remove();
    }
}
