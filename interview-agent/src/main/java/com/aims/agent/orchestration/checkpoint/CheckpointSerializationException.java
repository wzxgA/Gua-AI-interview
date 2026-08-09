package com.aims.agent.orchestration.checkpoint;

/**
 * Checkpoint 序列化/反序列化失败时抛出。
 *
 * <p>作为 {@link RuntimeException} 子类，避免在 {@code AbstractCheckpointSaver} 的抽象方法签名上
 * 强制声明受检异常，同时保留原始异常链。
 *
 * @since 1.1.0
 */
public class CheckpointSerializationException extends RuntimeException {

    public CheckpointSerializationException(String message, Throwable cause) {
        super(message, cause);
    }
}
