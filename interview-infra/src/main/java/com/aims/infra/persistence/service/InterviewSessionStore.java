package com.aims.infra.persistence.service;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 面试会话 Redis 运行时存储：快照、连接锁。
 *
 * <p>Key 设计：
 *
 * <ul>
 *   <li>interview:snapshot:{sessionId} - Hash，TTL 24h
 *   <li>interview:lock:{sessionId} - String，TTL 60s
 * </ul>
 */
@Service
public class InterviewSessionStore {

    private static final Duration SNAPSHOT_TTL = Duration.ofHours(24);
    private static final Duration LOCK_TTL = Duration.ofSeconds(60);

    private static final String KEY_SNAPSHOT = "interview:snapshot:";
    private static final String KEY_LOCK = "interview:lock:";

    private final StringRedisTemplate redis;

    public InterviewSessionStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    // ---- 快照 ----

    /** 保存会话快照（覆盖式）。 */
    public void saveSnapshot(Long sessionId, Map<String, String> fields) {
        String key = KEY_SNAPSHOT + sessionId;
        redis.opsForHash().putAll(key, fields);
        redis.expire(key, SNAPSHOT_TTL);
    }

    /** 读取快照字段。 */
    public Optional<Map<Object, Object>> getSnapshot(Long sessionId) {
        String key = KEY_SNAPSHOT + sessionId;
        Map<Object, Object> entries = redis.opsForHash().entries(key);
        return entries.isEmpty() ? Optional.empty() : Optional.of(entries);
    }

    /** 更新快照中的单个字段。 */
    public void updateSnapshotField(Long sessionId, String field, String value) {
        String key = KEY_SNAPSHOT + sessionId;
        redis.opsForHash().put(key, field, value);
        redis.expire(key, SNAPSHOT_TTL);
    }

    /** 删除快照。 */
    public void deleteSnapshot(Long sessionId) {
        redis.delete(KEY_SNAPSHOT + sessionId);
    }

    // ---- 连接锁 ----

    /** 尝试获取连接锁。成功返回 true。 */
    public boolean tryLock(Long sessionId, String connectionId) {
        String key = KEY_LOCK + sessionId;
        Boolean ok = redis.opsForValue().setIfAbsent(key, connectionId, LOCK_TTL);
        return Boolean.TRUE.equals(ok);
    }

    /** 续租连接锁。 */
    public boolean renewLock(Long sessionId, String connectionId) {
        String key = KEY_LOCK + sessionId;
        String current = redis.opsForValue().get(key);
        if (connectionId.equals(current)) {
            redis.expire(key, LOCK_TTL);
            return true;
        }
        return false;
    }

    /** 释放连接锁（仅持有者可释放）。 */
    public void unlock(Long sessionId, String connectionId) {
        String key = KEY_LOCK + sessionId;
        String current = redis.opsForValue().get(key);
        if (connectionId.equals(current)) {
            redis.delete(key);
        }
    }

    /** 强制释放连接锁（用于取消/结束）。 */
    public void forceUnlock(Long sessionId) {
        redis.delete(KEY_LOCK + sessionId);
    }
}
