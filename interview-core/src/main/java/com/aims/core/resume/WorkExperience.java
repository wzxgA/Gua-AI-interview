package com.aims.core.resume;

/** 工作或实习经历。 */
public record WorkExperience(
        String type, String company, String title, String period, String description) {}
