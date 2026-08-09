package com.aims.agent.orchestration.checkpoint;

import java.time.Duration;
import java.util.LinkedList;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.checkpoint.AbstractCheckpointSaver;
import org.bsc.langgraph4j.checkpoint.BaseCheckpointSaver;
import org.bsc.langgraph4j.checkpoint.Checkpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 基于 {@link StringRedisTemplate} 的 Checkpoint 持久化实现。
 *
 * <p>继承 langgraph4j {@link AbstractCheckpointSaver}，使用项目已有 Lettuce 栈，不引入 Redisson。 每个 threadId（即面试
 * sessionId）对应一个 Redis Key，仅存储最新 Checkpoint；可选保留历史快照。
 *
 * <h2>Redis 数据结构</h2>
 *
 * <pre>
 *   interview:checkpoint:{threadId}            → String (JSON)   最新 checkpoint
 *   interview:checkpoint:{threadId}:history    → List  (JSON)   checkpoint 历史（可选）
 *   TTL: 默认 24h，由 interview.checkpoint.ttl-hours 配置
 * </pre>
 *
 * <h2>与框架的交互</h2>
 *
 * <p>{@link AbstractCheckpointSaver#put} 在持有内置锁的状态下调用本类的 {@link #insertedCheckpoint} 或 {@link
 * #updatedCheckpoint}，二者均把最新 Checkpoint 覆盖写入 Redis；{@link #loadCheckpoints}
 * 仅返回单元素列表（最新快照），框架据此恢复图状态。
 *
 * @since 1.1.0
 */
public class RedisCheckpointSaver extends AbstractCheckpointSaver {

    private static final Logger log = LoggerFactory.getLogger(RedisCheckpointSaver.class);

    private static final String KEY_PREFIX = "interview:checkpoint:";
    private static final String HISTORY_SUFFIX = ":history";

    private final StringRedisTemplate redis;
    private final CheckpointSerializer serializer;
    private final Duration ttl;
    private final boolean historyEnabled;

    public RedisCheckpointSaver(
            StringRedisTemplate redis,
            CheckpointSerializer serializer,
            Duration ttl,
            boolean historyEnabled) {
        this.redis = redis;
        this.serializer = serializer;
        this.ttl = ttl;
        this.historyEnabled = historyEnabled;
    }

    @Override
    protected LinkedList<Checkpoint> loadCheckpoints(RunnableConfig config) throws Exception {
        String threadId = requireThreadId(config);
        String json = redis.opsForValue().get(key(threadId));
        if (json == null) {
            return new LinkedList<>();
        }
        CheckpointRecord record = serializer.deserialize(json);
        LinkedList<Checkpoint> checkpoints = new LinkedList<>();
        checkpoints.add(
                Checkpoint.builder()
                        .id(record.checkpointId())
                        .nodeId(record.nodeId())
                        .nextNodeId(record.nextNodeId())
                        .state(record.stateData())
                        .build());
        return checkpoints;
    }

    @Override
    protected void insertedCheckpoint(
            RunnableConfig config, LinkedList<Checkpoint> checkpoints, Checkpoint checkpoint)
            throws Exception {
        saveCheckpoint(config, checkpoint);
    }

    @Override
    protected void updatedCheckpoint(
            RunnableConfig config, LinkedList<Checkpoint> checkpoints, Checkpoint checkpoint)
            throws Exception {
        saveCheckpoint(config, checkpoint);
    }

    @Override
    protected BaseCheckpointSaver.Tag releaseCheckpoints(
            RunnableConfig config, LinkedList<Checkpoint> checkpoints) throws Exception {
        String threadId = requireThreadId(config);
        String latestKey = key(threadId);
        String historyKey = latestKey + HISTORY_SUFFIX;
        redis.delete(latestKey);
        redis.delete(historyKey);
        log.debug("Released checkpoints for threadId={}", threadId);
        return new BaseCheckpointSaver.Tag(threadId, checkpoints);
    }

    private void saveCheckpoint(RunnableConfig config, Checkpoint checkpoint) {
        String threadId = requireThreadId(config);
        String latestKey = key(threadId);
        CheckpointRecord record =
                new CheckpointRecord(
                        checkpoint.getId(),
                        checkpoint.getNodeId(),
                        checkpoint.getNextNodeId(),
                        checkpoint.getState(),
                        System.currentTimeMillis());
        String json = serializer.serialize(record);
        redis.opsForValue().set(latestKey, json, ttl);
        if (historyEnabled) {
            String historyKey = latestKey + HISTORY_SUFFIX;
            redis.opsForList().rightPush(historyKey, json);
            redis.expire(historyKey, ttl);
        }
        log.debug(
                "Saved checkpoint id={} nodeId={} for threadId={}",
                checkpoint.getId(),
                checkpoint.getNodeId(),
                threadId);
    }

    private String requireThreadId(RunnableConfig config) {
        return config.threadId()
                .orElseThrow(
                        () ->
                                new IllegalStateException(
                                        "threadId (sessionId) required for checkpoint operation"));
    }

    private String key(String threadId) {
        return KEY_PREFIX + threadId;
    }
}
