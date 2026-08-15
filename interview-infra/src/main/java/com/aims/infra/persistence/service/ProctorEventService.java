package com.aims.infra.persistence.service;

import com.aims.infra.persistence.entity.ProctorEventEntity;
import java.util.List;
import java.util.Map;

/** 面试防作弊事件服务。 */
public interface ProctorEventService {

    /** 批量保存事件（自动绑定 sessionId，空集合直接忽略）。 */
    void saveEvents(Long sessionId, List<ProctorEventEntity> events);

    /** 增量查询指定 id 之后的事件（按 id 升序，limit 限制条数）。 */
    List<ProctorEventEntity> listAfter(Long sessionId, Long afterId, int limit);

    /** 按类型聚合：每条为 {type, cnt, total_duration_ms}。 */
    List<Map<String, Object>> countByType(Long sessionId);
}
