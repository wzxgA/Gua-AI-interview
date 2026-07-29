package com.aims.core.resume;

import java.util.List;

/** 项目经历。 */
public record ProjectExperience(
        String name, String role, String period, String description, List<String> highlights) {}
