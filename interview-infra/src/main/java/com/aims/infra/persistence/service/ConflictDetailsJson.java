package com.aims.infra.persistence.service;

import com.aims.core.interview.ConflictDetail;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 简历交叉验证矛盾点 JSON 序列化工具（v1.1-F4）：interview_round.conflict_details JSONB 列与 {@link ConflictDetail}
 * 列表互转。解析失败返回空列表（不阻断评估/报告）。
 */
public final class ConflictDetailsJson {

    private static final Logger log = LoggerFactory.getLogger(ConflictDetailsJson.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final TypeReference<List<ConflictDetail>> LIST_TYPE = new TypeReference<>() {};

    private ConflictDetailsJson() {}

    /** 序列化矛盾点列表（null → "[]"）。 */
    public static String serialize(List<ConflictDetail> conflicts) {
        try {
            return MAPPER.writeValueAsString(conflicts == null ? List.of() : conflicts);
        } catch (Exception e) {
            log.warn("冲突点序列化失败，回退空数组", e);
            return "[]";
        }
    }

    /** 反序列化矛盾点列表（null/空/解析失败 → 空列表）。 */
    public static List<ConflictDetail> parse(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<ConflictDetail> list = MAPPER.readValue(json, LIST_TYPE);
            return list == null ? List.of() : list;
        } catch (Exception e) {
            log.warn("冲突点反序列化失败，回退空列表 json={}", json, e);
            return List.of();
        }
    }
}
