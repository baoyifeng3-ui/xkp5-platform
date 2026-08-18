package com.match.dto;

import lombok.Data;

import java.util.Map;

@Data
public class CompetitionContentRequest {
    private Map<String, CompetitionContentSection> sections;

    @Data
    public static class CompetitionContentSection {
        private String title;
        private String body;
    }
}
