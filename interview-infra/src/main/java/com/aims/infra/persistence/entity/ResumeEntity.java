package com.aims.infra.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;

/**
 * 简历持久化实体（对应 resume 表）。
 *
 * <p>parsedJson 字段存储 {@link com.aims.core.resume.ParsedResume} 的 JSON 字符串。 embedding 字段为 pgvector
 * vector(1024) 类型，MyBatis-Plus 无法直接映射，标记 {@code exist=false}， 通过 {@link
 * com.aims.infra.persistence.mapper.ResumeMapper#updateEmbedding} 自定义 SQL 读写。
 */
@TableName("resume")
public class ResumeEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("candidate_name")
    private String candidateName;

    private String phone;

    private String email;

    @TableField("raw_text")
    private String rawText;

    /** {@link com.aims.core.resume.ParsedResume} 的 JSON 字符串。 */
    @TableField("parsed_json")
    private String parsedJson;

    @TableField("file_url")
    private String fileUrl;

    @TableField("parse_status")
    private String parseStatus;

    /** pgvector 向量字段，MyBatis-Plus 不自动映射，通过自定义 SQL 读写。 */
    @TableField(exist = false)
    private String embedding;

    @TableField("created_at")
    private Instant createdAt;

    @TableField("updated_at")
    private Instant updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCandidateName() {
        return candidateName;
    }

    public void setCandidateName(String candidateName) {
        this.candidateName = candidateName;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getRawText() {
        return rawText;
    }

    public void setRawText(String rawText) {
        this.rawText = rawText;
    }

    public String getParsedJson() {
        return parsedJson;
    }

    public void setParsedJson(String parsedJson) {
        this.parsedJson = parsedJson;
    }

    public String getFileUrl() {
        return fileUrl;
    }

    public void setFileUrl(String fileUrl) {
        this.fileUrl = fileUrl;
    }

    public String getParseStatus() {
        return parseStatus;
    }

    public void setParseStatus(String parseStatus) {
        this.parseStatus = parseStatus;
    }

    public String getEmbedding() {
        return embedding;
    }

    public void setEmbedding(String embedding) {
        this.embedding = embedding;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
