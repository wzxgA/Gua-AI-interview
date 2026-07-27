package com.aims.ai.advisor;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

/** RetryAdvisor 可重试判定与原地重试行为单测。 */
class RetryAdvisorTest {

    private final RetryAdvisor advisor = new RetryAdvisor(2, Duration.ofMillis(1));

    @Test
    void transientExceptionIsRetryable() {
        assertTrue(advisor.isRetryable(new TransientAiException("rate limited")));
        assertTrue(
                advisor.isRetryable(
                        HttpServerErrorException.create(
                                HttpStatus.BAD_GATEWAY, "bad gateway", null, null, null)));
        assertTrue(
                advisor.isRetryable(new RuntimeException(new java.net.SocketTimeoutException())));
    }

    @Test
    void nonTransientExceptionIsNotRetryable() {
        assertFalse(advisor.isRetryable(new NonTransientAiException("invalid api key")));
        assertFalse(
                advisor.isRetryable(
                        HttpClientErrorException.create(
                                HttpStatus.BAD_REQUEST, "bad request", null, null, null)));
        assertFalse(advisor.isRetryable(new IllegalArgumentException("bad arg")));
    }

    @Test
    void adviseCallRetriesUntilSuccess() {
        ChatClientResponse expected = mock(ChatClientResponse.class);
        CallAdvisorChain chain = mock(CallAdvisorChain.class);
        when(chain.nextCall(any(ChatClientRequest.class)))
                .thenThrow(new TransientAiException("boom"))
                .thenReturn(expected);

        ChatClientResponse actual = advisor.adviseCall(mock(ChatClientRequest.class), chain);

        assertSame(expected, actual);
        verify(chain, times(2)).nextCall(any(ChatClientRequest.class));
    }

    @Test
    void adviseCallDoesNotRetryNonRetryable() {
        CallAdvisorChain chain = mock(CallAdvisorChain.class);
        when(chain.nextCall(any(ChatClientRequest.class)))
                .thenThrow(new NonTransientAiException("invalid key"));

        assertThrows(
                NonTransientAiException.class,
                () -> advisor.adviseCall(mock(ChatClientRequest.class), chain));
        verify(chain, times(1)).nextCall(any(ChatClientRequest.class));
    }

    @Test
    void adviseCallGivesUpAfterMaxAttempts() {
        CallAdvisorChain chain = mock(CallAdvisorChain.class);
        when(chain.nextCall(any(ChatClientRequest.class)))
                .thenThrow(new TransientAiException("boom"));

        // maxAttempts=2 -> 共调用 3 次（1 次原始 + 2 次重试）后放弃
        assertThrows(
                TransientAiException.class,
                () -> advisor.adviseCall(mock(ChatClientRequest.class), chain));
        verify(chain, times(3)).nextCall(any(ChatClientRequest.class));
    }
}
