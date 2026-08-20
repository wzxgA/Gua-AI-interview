package com.aims.infra.persistence.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.Instant;

/** AI 档位配置实体，映射 {@code ai_tier_config} 表（DB 覆盖 yml 的增量层）。 */
@TableName("ai_tier_config")
public class AiTierConfigEntity {

    @TableId(value = "tier", type = IdType.INPUT)
    private String tier;

    private String provider;

    private String model;

    private BigDecimal temperature;

    private Integer maxTokens;

    private Integer dimensions;

    private String fallback;

    private Boolean thinking;

    private String reasoningEffort;

    private String overrideBaseUrl;

    private String overrideApiKeyEnc;

    private String updatedBy;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private Instant updatedAt;

    public String getTier() {
        return tier;
    }

    public void setTier(String tier) {
        this.tier = tier;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public BigDecimal getTemperature() {
        return temperature;
    }

    public void setTemperature(BigDecimal temperature) {
        this.temperature = temperature;
    }

    public Integer getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(Integer maxTokens) {
        this.maxTokens = maxTokens;
    }

    public Integer getDimensions() {
        return dimensions;
    }

    public void setDimensions(Integer dimensions) {
        this.dimensions = dimensions;
    }

    public String getFallback() {
        return fallback;
    }

    public void setFallback(String fallback) {
        this.fallback = fallback;
    }

    public Boolean getThinking() {
        return thinking;
    }

    public void setThinking(Boolean thinking) {
        this.thinking = thinking;
    }

    public String getReasoningEffort() {
        return reasoningEffort;
    }

    public void setReasoningEffort(String reasoningEffort) {
        this.reasoningEffort = reasoningEffort;
    }

    public String getOverrideBaseUrl() {
        return overrideBaseUrl;
    }

    public void setOverrideBaseUrl(String overrideBaseUrl) {
        this.overrideBaseUrl = overrideBaseUrl;
    }

    public String getOverrideApiKeyEnc() {
        return overrideApiKeyEnc;
    }

    public void setOverrideApiKeyEnc(String overrideApiKeyEnc) {
        this.overrideApiKeyEnc = overrideApiKeyEnc;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
