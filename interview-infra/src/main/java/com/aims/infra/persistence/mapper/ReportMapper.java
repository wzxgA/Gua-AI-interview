package com.aims.infra.persistence.mapper;

import com.aims.infra.persistence.entity.ReportEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** 面试报告 Mapper。 */
public interface ReportMapper extends BaseMapper<ReportEntity> {

    /** 保存报告（dimensions_json 为 JSONB，需 ::jsonb 转型；ON CONFLICT 支持重新生成）。 */
    @Update(
            "INSERT INTO interview_report (session_id, summary, dimensions_json, recommendation,"
                    + " report_pdf_url) VALUES (#{sessionId}, #{summary}, #{dimensionsJson}::jsonb,"
                    + " #{recommendation}, #{reportPdfUrl}) ON CONFLICT (session_id) DO UPDATE SET"
                    + " summary = EXCLUDED.summary, dimensions_json = EXCLUDED.dimensions_json,"
                    + " recommendation = EXCLUDED.recommendation, report_pdf_url ="
                    + " EXCLUDED.report_pdf_url")
    int upsert(ReportEntity entity);

    /** 查询会话报告。 */
    @Select("SELECT * FROM interview_report WHERE session_id = #{sessionId}")
    ReportEntity findBySession(@Param("sessionId") Long sessionId);

    /** 删除会话报告（级联删除面试时清理）。 */
    @Delete("DELETE FROM interview_report WHERE session_id = #{sessionId}")
    int deleteBySession(@Param("sessionId") Long sessionId);
}
