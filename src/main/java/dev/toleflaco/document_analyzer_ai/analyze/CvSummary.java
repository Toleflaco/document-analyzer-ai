package dev.toleflaco.document_analyzer_ai.analyze;

import java.util.List;

public record CvSummary(
        String fullName,
        Integer yearsOfExperience,
        List<String> topSkills,
        String seniorityLevel
) {}
