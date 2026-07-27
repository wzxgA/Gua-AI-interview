package com.aims.ai.advisor;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.web.client.RestClientResponseException;
import reactor.core.publisher.Flux;
import reactor.util.retry.Retry;

/**
 * 原地重试 Advisor（顺序 300，最贴近模型调用）。
 *
 * <p>仅对<b>可重试异常</b>重试：网络超时、429、5xx；4xx（鉴权/参数错误）不重试。 策略：指数退避 {@code initialBackoff * 2^n}，默认最多重试 2
 * 次。 重试耗尽后异常上抛，交给 {@code ModelRouter} 的 fallback 换模型兜底，二者不重复触发。
 */
public class RetryAdvisor implements CallAdvisor, StreamAdvisor {

    private static final Logger log = LoggerFactory.getLogger(RetryAdvisor.class);

    private final int maxAttempts;
    private final Duration initialBackoff;

    public RetryAdvisor(int maxAttempts, Duration initialBackoff) {
        this.maxAttempts = maxAttempts;
        this.initialBackoff = initialBackoff;
    }

    @Override
    public String getName() {
        return "RetryAdvisor";
    }

    @Override
    public int getOrder() {
        return 300;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        int attempt = 0;
        while (true) {
            try {
                return chain.nextCall(request);
            } catch (Exception e) {
                if (!isRetryable(e) || attempt >= maxAttempts) {
                    throw e;
                }
                Duration backoff = initialBackoff.multipliedBy(1L << attempt);
                log.warn(
                        "模型调用可重试异常，{}ms 后第 {} 次重试: {}",
                        backoff.toMillis(),
                        attempt + 1,
                        e.toString());
                sleep(backoff);
                attempt++;
            }
        }
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(
            ChatClientRequest request, StreamAdvisorChain chain) {
        return chain.nextStream(request)
                .retryWhen(
                        Retry.backoff(maxAttempts, initialBackoff)
                                .filter(this::isRetryable)
                                .doBeforeRetry(
                                        signal ->
                                                log.warn(
                                                        "流式调用可重试异常，第 {} 次重试: {}",
                                                        signal.totalRetries() + 1,
                                                        signal.failure().toString())));
    }

    /** 可重试判定：沿异常链查找瞬时异常标记 / 429 / 5xx / 网络错误。 */
    boolean isRetryable(Throwable e) {
        Throwable current = e;
        while (current != null) {
            if (current instanceof TransientAiException) {
                return true;
            }
            if (current instanceof NonTransientAiException) {
                return false;
            }
            if (current instanceof RestClientResponseException rce) {
                int status = rce.getStatusCode().value();
                return status == 429 || status >= 500;
            }
            if (current instanceof java.io.IOException) {
                return true;
            }
            current = current.getCause();
        }
        // 兜底：Spring 包装的最具体原因再判一次
        Throwable root = NestedExceptionUtils.getMostSpecificCause(e);
        return root instanceof java.io.IOException;
    }

    private void sleep(Duration backoff) {
        try {
            Thread.sleep(backoff.toMillis());
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("重试等待被中断", ie);
        }
    }
}
