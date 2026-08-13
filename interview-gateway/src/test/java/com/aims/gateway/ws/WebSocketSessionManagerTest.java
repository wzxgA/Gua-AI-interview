package com.aims.gateway.ws;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

/** {@link WebSocketSessionManager} 测试：FE.16 A2 优雅停机关闭会话。 */
@ExtendWith(MockitoExtension.class)
class WebSocketSessionManagerTest {

    @Mock private WebSocketSession openSession;
    @Mock private WebSocketSession closedSession;

    private WebSocketSessionManager manager;

    @BeforeEach
    void setUp() {
        manager = new WebSocketSessionManager();
    }

    @Test
    @DisplayName("closeAll：关闭所有 open 会话并清空注册表")
    void closeAll_closesOpenSessions_andClears() throws Exception {
        when(openSession.isOpen()).thenReturn(true);
        manager.register(1L, openSession);

        manager.closeAll(CloseStatus.SERVICE_RESTARTED);

        verify(openSession).close(CloseStatus.SERVICE_RESTARTED);
        org.junit.jupiter.api.Assertions.assertFalse(manager.hasActiveSession(1L));
    }

    @Test
    @DisplayName("closeAll：跳过已关闭会话")
    void closeAll_skipsClosedSession() throws Exception {
        when(closedSession.isOpen()).thenReturn(false);
        manager.register(2L, closedSession);

        manager.closeAll(CloseStatus.SERVICE_RESTARTED);

        verify(closedSession, never()).close(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("closeAll：close 抛 IOException 不中断其余会话")
    void closeAll_closeFailure_continues() throws Exception {
        when(openSession.isOpen()).thenReturn(true);
        doThrow(new IOException("tcp reset"))
                .when(openSession)
                .close(org.mockito.ArgumentMatchers.any());
        manager.register(1L, openSession);

        // 不抛异常即通过
        manager.closeAll(CloseStatus.SERVICE_RESTARTED);
    }
}
