package com.aims.infra.persistence.service.impl;

import com.aims.infra.persistence.entity.ProctorEventEntity;
import com.aims.infra.persistence.mapper.ProctorEventMapper;
import com.aims.infra.persistence.service.ProctorEventService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 面试防作弊事件服务实现。 */
@Service
public class ProctorEventServiceImpl implements ProctorEventService {

    private final ProctorEventMapper proctorEventMapper;

    public ProctorEventServiceImpl(ProctorEventMapper proctorEventMapper) {
        this.proctorEventMapper = proctorEventMapper;
    }

    @Override
    @Transactional
    public void saveEvents(Long sessionId, List<ProctorEventEntity> events) {
        if (events == null || events.isEmpty()) {
            return;
        }
        events.forEach(e -> e.setSessionId(sessionId));
        proctorEventMapper.batchInsert(events);
    }

    @Override
    public List<ProctorEventEntity> listAfter(Long sessionId, Long afterId, int limit) {
        return proctorEventMapper.selectList(
                new LambdaQueryWrapper<ProctorEventEntity>()
                        .eq(ProctorEventEntity::getSessionId, sessionId)
                        .gt(ProctorEventEntity::getId, afterId)
                        .orderByAsc(ProctorEventEntity::getId)
                        .last("LIMIT " + Math.min(limit, 200)));
    }

    @Override
    public List<Map<String, Object>> countByType(Long sessionId) {
        return proctorEventMapper.countByType(sessionId);
    }
}
