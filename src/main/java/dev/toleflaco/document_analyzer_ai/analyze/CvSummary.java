package dev.toleflaco.document_analyzer_ai.analyze;

import java.util.List;

public record CvSummary(
        String fullName,
        Integer yearsOfExperience,
        List<String> topSkills,
        String seniorityLevel,
        List<Language> languages,
        List<Education> education,
        List<WorkExperience> workExperience
) {
    public record Language (String name, String level){}
    public record Education (String degree, String institution, Integer year) {}
    public record WorkExperience(
            String role,
            String company,
            Integer startYear,
            Integer endYear,
            List<String> responsibilities)
    {}
}
