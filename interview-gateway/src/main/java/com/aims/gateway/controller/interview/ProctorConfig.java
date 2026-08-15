package com.aims.gateway.controller.interview;

import com.aims.infra.persistence.entity.InterviewSessionEntity;
import com.fasterxml.jackson.databind.ObjectMapper;

/** 面试防作弊开关配置（生成候选人链接时可选开启）。 */
public record ProctorConfig(boolean tabSwitch, boolean gaze) {

    /** 默认配置：关闭。 */
    public static final ProctorConfig DISABLED = new ProctorConfig(false, false);

    /** 从实体解析；proctor_json 为空或非法时返回默认（关闭）。 */
    public static ProctorConfig from(InterviewSessionEntity entity) {
        String json = entity.getProctorJson();
        if (json == null || json.isBlank()) {
            return DISABLED;
        }
        try {
            return new ObjectMapper().readValue(json, ProctorConfig.class);
        } catch (Exception e) {
            return DISABLED;
        }
    }

    /** 序列化为 JSON 字符串；全部关闭时返回 null（表示清空配置）。 */
    public String toJson() {
        if (tabSwitch || gaze) {
            try {
                return new ObjectMapper().writeValueAsString(this);
            } catch (Exception ignored) {
                // 不满足开启条件时返回 null
            }
        }
        return null;
    }
}
