package com.aims.infra.persistence.mapper;

import com.aims.infra.persistence.entity.ProctorEventEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 面试防作弊事件 Mapper。 */
public interface ProctorEventMapper extends BaseMapper<ProctorEventEntity> {

    /** 批量插入事件（detail 为 JSONB 列，需 ::jsonb 转型；null 参数即写入 NULL）。 */
    @Insert(
            "<script>"
                    + "INSERT INTO interview_proctor_event (session_id, event_type, occurred_at,"
                    + " duration_ms, detail) VALUES "
                    + "<foreach collection='list' item='e' separator=','>"
                    + " (#{e.sessionId}, #{e.eventType}, #{e.occurredAt}, #{e.durationMs},"
                    + " #{e.detail}::jsonb)"
                    + "</foreach>"
                    + "</script>")
    int batchInsert(@Param("list") List<ProctorEventEntity> events);

    /** 按类型聚合事件数与总时长（管理端摘要）。 */
    @Select(
            "SELECT event_type AS type, COUNT(*) AS cnt, COALESCE(SUM(duration_ms), 0) AS"
                    + " total_duration_ms FROM interview_proctor_event WHERE session_id ="
                    + " #{sessionId} GROUP BY event_type")
    List<Map<String, Object>> countByType(@Param("sessionId") Long sessionId);
}
