package com.aims.infra.persistence.mapper;

import com.aims.infra.persistence.entity.InterviewSessionEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** 面试会话 Mapper。 */
public interface InterviewSessionMapper extends BaseMapper<InterviewSessionEntity> {

    /** 保存面试计划 JSON（plan_json 为 JSONB 类型，需 ::jsonb 转型）。 */
    @Update(
            "UPDATE interview_session SET plan_json = #{planJson}::jsonb, updated_at = now()"
                    + " WHERE id = #{id}")
    int updatePlanJson(@Param("id") Long id, @Param("planJson") String planJson);

    /** 更新评估流程状态。 */
    @Update(
            "UPDATE interview_session SET evaluation_status = #{status}, updated_at = now() WHERE"
                    + " id = #{id}")
    int updateEvaluationStatus(@Param("id") Long id, @Param("status") String status);

    /** 更新已评估轮次数。 */
    @Update(
            "UPDATE interview_session SET evaluated_rounds = #{evaluatedRounds}, updated_at = now()"
                    + " WHERE id = #{id}")
    int updateEvaluatedRounds(@Param("id") Long id, @Param("evaluatedRounds") int evaluatedRounds);

    /** 更新需评估的总轮次数。 */
    @Update(
            "UPDATE interview_session SET total_rounds_to_evaluate = #{total}, updated_at = now()"
                    + " WHERE id = #{id}")
    int updateTotalRoundsToEvaluate(@Param("id") Long id, @Param("total") int total);

    /** 更新综合得分。 */
    @Update(
            "UPDATE interview_session SET total_score = #{score}, updated_at = now() WHERE id ="
                    + " #{id}")
    int updateTotalScore(@Param("id") Long id, @Param("score") BigDecimal score);

    /** 更新防作弊配置（proctor_json 为 JSONB 类型，需 ::jsonb 转型；null 参数即写入 NULL 清空）。 */
    @Update(
            "UPDATE interview_session SET proctor_json = #{proctorJson}::jsonb, updated_at = now()"
                    + " WHERE id = #{id}")
    int updateProctorJson(@Param("id") Long id, @Param("proctorJson") String proctorJson);

    /** 原子条件状态转移：仅当当前状态在 from 列表中时才更新为 target，返回受影响行数。 */
    @Update(
            "UPDATE interview_session SET status = #{target}, updated_at = now()"
                    + " WHERE id = #{id} AND status IN (#{from1}, #{from2})")
    int tryTransitionStatus(
            @Param("id") Long id,
            @Param("target") String target,
            @Param("from1") String from1,
            @Param("from2") String from2);

    // ---------- 仪表盘聚合查询 ----------

    /** 按状态分组统计会话数量。 */
    @Select("SELECT status AS status, COUNT(*) AS count FROM interview_session GROUP BY status")
    List<Map<String, Object>> countGroupByStatus();

    /** 近 30 天每日创建数（按东八区业务日期聚合，升序，含无数据日期由 Service 补 0）。 */
    @Select(
            "SELECT to_char(created_at AT TIME ZONE 'Asia/Shanghai', 'YYYY-MM-DD') AS date,"
                    + " COUNT(*) AS count FROM interview_session"
                    + " WHERE created_at >= now() - interval '30 days'"
                    + " GROUP BY date ORDER BY date")
    List<Map<String, Object>> countDailySince30Days();

    /**
     * 已评分会话的平均分（无数据返回 null，由 Service 兜底为 0）。
     *
     * <p>start/end 均非 null 时限定 created_at 时间区间，用于得分分布的时间过滤；任一为 null 表示不限制。
     */
    @Select(
            "<script>"
                    + "SELECT AVG(total_score) FROM interview_session WHERE total_score IS NOT NULL"
                    + "<if test='start != null'> AND created_at &gt;= #{start}</if>"
                    + "<if test='end != null'> AND created_at &lt;= #{end}</if>"
                    + "</script>")
    BigDecimal avgScoreOfScored(
            @Param("start") OffsetDateTime start, @Param("end") OffsetDateTime end);

    /**
     * 得分五档区间分布（0-1/1-2/2-3/3-4/4-5）。
     *
     * <p>start/end 均非 null 时限定 created_at 时间区间，用于得分分布的时间过滤；任一为 null 表示不限制。
     */
    @Select(
            "<script>"
                    + "SELECT CASE"
                    + "  WHEN total_score &lt; 1 THEN '0-1'"
                    + "  WHEN total_score &lt; 2 THEN '1-2'"
                    + "  WHEN total_score &lt; 3 THEN '2-3'"
                    + "  WHEN total_score &lt; 4 THEN '3-4'"
                    + "  ELSE '4-5' END AS range,"
                    + " COUNT(*) AS count FROM interview_session"
                    + " WHERE total_score IS NOT NULL"
                    + "<if test='start != null'> AND created_at &gt;= #{start}</if>"
                    + "<if test='end != null'> AND created_at &lt;= #{end}</if>"
                    + " GROUP BY range"
                    + "</script>")
    List<Map<String, Object>> countGroupByScoreRange(
            @Param("start") OffsetDateTime start, @Param("end") OffsetDateTime end);

    /**
     * 已评分会话的得分点列表（得分 + 创建时间，散点图数据）。
     *
     * <p>start/end 均非 null 时限定 created_at 时间区间；任一为 null 表示不限制。按创建时间升序，供前端散点图横轴使用。
     */
    @Select(
            "<script>"
                    + "SELECT total_score AS total_score, created_at AS created_at"
                    + " FROM interview_session WHERE total_score IS NOT NULL"
                    + "<if test='start != null'> AND created_at &gt;= #{start}</if>"
                    + "<if test='end != null'> AND created_at &lt;= #{end}</if>"
                    + " ORDER BY created_at ASC"
                    + "</script>")
    List<Map<String, Object>> scorePointsOfScored(
            @Param("start") OffsetDateTime start, @Param("end") OffsetDateTime end);

    /** 最近 N 条会话摘要（LEFT JOIN 候选人/岗位名称，按创建时间倒序）。 */
    @Select(
            "SELECT s.id AS id, c.candidate_name AS candidate_name,"
                    + " p.title AS position_title, s.status AS status,"
                    + " s.total_score AS total_score, s.created_at AS created_at"
                    + " FROM interview_session s"
                    + " LEFT JOIN candidate c ON s.candidate_id = c.id"
                    + " LEFT JOIN \"position\" p ON s.position_id = p.id"
                    + " ORDER BY s.created_at DESC LIMIT #{limit}")
    List<Map<String, Object>> selectRecentSessions(@Param("limit") int limit);
}
