package com.match.dto;

import lombok.Data;

import java.util.List;

@Data
public class AdminSubjectRequest {
    private String testPaperType;
    private String subjectType;
    private String subjectName;
    private Integer score;
    private String answering;
    private String screenshotRequirement;
    private String modular;
    private String modularName;
    private String point;
    private String subjectIdentification;
    private Integer sortOrder;
    private List<String> options;
    private String correctAnswer;
}
