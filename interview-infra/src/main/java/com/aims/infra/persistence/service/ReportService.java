package com.aims.infra.persistence.service;

import com.aims.infra.persistence.entity.ReportEntity;

/** 报告服务。 */
public interface ReportService {

    /** 生成面试报告。 */
    void generateReport(Long sessionId);

    /** 查询会话报告，不存在返回 null。 */
    ReportEntity getBySession(Long sessionId);
}
