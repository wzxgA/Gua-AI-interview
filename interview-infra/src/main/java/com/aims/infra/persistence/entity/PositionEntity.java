package com.aims.infra.persistence.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;

/**
 * 岗位持久化实体，映射 {@code position} 表。
 *
 * <p>embedding 为 pgvector 类型，不走 MyBatis-Plus 自动映射（需 {@code ::vector} 转换）， 通过自定义 SQL 处理。
 */
@TableName("position")
public class PositionEntity {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String title;

    private String department;

    @TableField("jd_text")
    private String jdText;

    @TableField("requirements_json")
    private String requirementsJson;

    private String status;

    /** pgvector 类型，不参与 MyBatis-Plus 自动映射。 */
    @TableField(exist = false)
    private String embedding;

    /** 查询时由 Service 填充，表示是否已有向量。 */
    @TableField(exist = false)
    private Boolean hasEmbedding;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private Instant createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private Instant updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public String getJdText() {
        return jdText;
    }

    public void setJdText(String jdText) {
        this.jdText = jdText;
    }

    public String getRequirementsJson() {
        return requirementsJson;
    }

    public void setRequirementsJson(String requirementsJson) {
        this.requirementsJson = requirementsJson;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getEmbedding() {
        return embedding;
    }

    public void setEmbedding(String embedding) {
        this.embedding = embedding;
    }

    public Boolean getHasEmbedding() {
        return hasEmbedding;
    }

    public void setHasEmbedding(Boolean hasEmbedding) {
        this.hasEmbedding = hasEmbedding;
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
