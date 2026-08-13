package com.aims.agent.orchestration.checkpoint;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Checkpoint 相关可配置项。
 *
 * <p>对应 {@code application.yml} 中 {@code interview.checkpoint.*}：
 *
 * <pre>
 *   interview:
 *     checkpoint:
 *       ttl-hours: 24
 *       history-enabled: true
 * </pre>
 *
 * @since 1.1.0
 */
@ConfigurationProperties(prefix = "interview.checkpoint")
public class CheckpointProperties {

    /** Checkpoint 在 Redis 中的过期时长（小时），与面试会话生命周期对齐。 */
    private int ttlHours = 24;

    /** 是否保留历史快照（关闭可减少 Redis 占用）。 */
    private boolean historyEnabled = true;

    public int getTtlHours() {
        return ttlHours;
    }

    public void setTtlHours(int ttlHours) {
        this.ttlHours = ttlHours;
    }

    public boolean isHistoryEnabled() {
        return historyEnabled;
    }

    public void setHistoryEnabled(boolean historyEnabled) {
        this.historyEnabled = historyEnabled;
    }
}
