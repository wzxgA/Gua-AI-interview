package com.aims.core.resume;

import java.util.List;

/**
 * 解析后的简历结构。
 *
 * @param candidateName 候选人姓名
 * @param phone 联系电话
 * @param email 邮箱
 * @param yearsOfExperience 工作年限
 * @param education 学历
 * @param currentTitle 当前职位
 * @param skills 技能列表
 * @param workExperiences 工作经历
 * @param projectHighlights 项目亮点
 */
public record ParsedResume(
        String candidateName,
        String phone,
        String email,
        Integer yearsOfExperience,
        String education,
        String currentTitle,
        List<String> skills,
        List<WorkExperience> workExperiences,
        List<String> projectHighlights) {}
