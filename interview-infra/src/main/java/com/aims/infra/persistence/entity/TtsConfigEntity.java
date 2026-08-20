package com.aims.infra.persistence.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;

/** TTS 连接配置实体，映射 {@code tts_config} 表（单行，id 恒为 1；DB 覆盖 yml 的增量层）。 */
@TableName("tts_config")
public class TtsConfigEntity {

    @TableId(value = "id", type = IdType.INPUT)
    private Short id;

    private Boolean enabled;

    private String provider;

    private String baseUrl;

    private String apiKeyEnc;

    private String resourceId;

    private String defaultSpeaker;

    private String format;

    private Integer sampleRate;

    private Integer speechRate;

    private Boolean personaVoiceLink;

    private String updatedBy;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private Instant updatedAt;

    public Short getId() {
        return id;
    }

    public void setId(Short id) {
        this.id = id;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiKeyEnc() {
        return apiKeyEnc;
    }

    public void setApiKeyEnc(String apiKeyEnc) {
        this.apiKeyEnc = apiKeyEnc;
    }

    public String getResourceId() {
        return resourceId;
    }

    public void setResourceId(String resourceId) {
        this.resourceId = resourceId;
    }

    public String getDefaultSpeaker() {
        return defaultSpeaker;
    }

    public void setDefaultSpeaker(String defaultSpeaker) {
        this.defaultSpeaker = defaultSpeaker;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public Integer getSampleRate() {
        return sampleRate;
    }

    public void setSampleRate(Integer sampleRate) {
        this.sampleRate = sampleRate;
    }

    public Integer getSpeechRate() {
        return speechRate;
    }

    public void setSpeechRate(Integer speechRate) {
        this.speechRate = speechRate;
    }

    public Boolean getPersonaVoiceLink() {
        return personaVoiceLink;
    }

    public void setPersonaVoiceLink(Boolean personaVoiceLink) {
        this.personaVoiceLink = personaVoiceLink;
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
