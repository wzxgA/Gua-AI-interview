package com.aims.core.resume;

/**
 * 工作经历。
 *
 * @param company 公司
 * @param title 职位
 * @param period 在职时间段
 * @param description 工作描述
 */
public record WorkExperience(String company, String title, String period, String description) {}
