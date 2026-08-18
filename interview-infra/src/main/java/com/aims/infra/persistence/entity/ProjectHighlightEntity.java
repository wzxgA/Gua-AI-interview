package com.aims.infra.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;

/**
 * 项目亮点表实体（v1.1-C，可选增强）：{@link com.aims.core.resume.ProjectExperience#highlights} 展开行。
 *
 * <p>父表关联 resume_project_experience，按 sortOrder 保持亮点顺序。
 */
@TableName("resume_project_highlight")
public class ProjectHighlightEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long projectId;

    private String content;

    private Integer sortOrder;

    private Instant createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
