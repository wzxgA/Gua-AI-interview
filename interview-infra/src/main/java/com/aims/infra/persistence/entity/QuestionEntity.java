package com.aims.infra.persistence.entity;

import com.aims.infra.persistence.handler.StringArrayTypeHandler;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;

/**
 * 题库实体（对应 question_bank 表）。
 *
 * <p>embedding 列为 pgvector 的 vector(2048) 类型，使用 {@link FieldStrategy#NEVER} 策略 禁止 MyBatis-Plus 自动
 * INSERT/UPDATE（halfvec 需要 {@code ::halfvec} 转型语法）， 但允许 SELECT 读取以判断是否已向量化（hasEmbedding）。
 */
@TableName(value = "question_bank", autoResultMap = true)
public class QuestionEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String category;

    private String topic;

    private String difficulty;

    private String content;

    @TableField("standard_answer")
    private String standardAnswer;

    @TableField(typeHandler = StringArrayTypeHandler.class)
    private String[] tags;

    @TableField(
            value = "embedding",
            insertStrategy = FieldStrategy.NEVER,
            updateStrategy = FieldStrategy.NEVER)
    private String embedding;

    @TableField("created_at")
    private Instant createdAt;

    @TableField("updated_at")
    private Instant updatedAt;

    public QuestionEntity() {}

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public String getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(String difficulty) {
        this.difficulty = difficulty;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getStandardAnswer() {
        return standardAnswer;
    }

    public void setStandardAnswer(String standardAnswer) {
        this.standardAnswer = standardAnswer;
    }

    public String[] getTags() {
        return tags;
    }

    public void setTags(String[] tags) {
        this.tags = tags;
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
