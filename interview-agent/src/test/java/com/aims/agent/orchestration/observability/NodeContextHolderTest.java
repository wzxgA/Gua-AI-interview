package com.aims.agent.orchestration.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.aims.core.common.NodeContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link NodeContextHolder} 单元测试：验证 set/get/clear 与 ThreadLocal 隔离。
 *
 * <p>测试目标：确认进入 Node 时 set 的节点名能被同线程读取，退出时被清理。
 *
 * @since 1.2.0 Phase 6
 */
class NodeContextHolderTest {

    @AfterEach
    void cleanup() {
        // 防御性清理，防止 ThreadLocal 泄漏影响其他测试
        NodeContextHolder.clear();
    }

    @Test
    @DisplayName("set 后 current 可读取，clear 后返回 null")
    void set_get_clear_lifecycle() {
        assertNull(NodeContextHolder.current(), "初始状态应为 null");

        NodeContextHolder.set("ask");
        assertEquals("ask", NodeContextHolder.current(), "set 后应可读取");

        NodeContextHolder.clear();
        assertNull(NodeContextHolder.current(), "clear 后应返回 null");
    }

    @Test
    @DisplayName("ThreadLocal 隔离：子线程看不到主线程 set 的值")
    void threadLocal_isolation_betweenThreads() throws Exception {
        NodeContextHolder.set("plan");

        Thread t = new Thread(() -> assertNull(NodeContextHolder.current(), "子线程应看不到主线程 set 的值"));
        t.start();
        t.join();

        assertEquals("plan", NodeContextHolder.current(), "主线程的值不受子线程影响");
    }
}
