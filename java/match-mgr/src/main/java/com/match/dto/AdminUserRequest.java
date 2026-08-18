package com.match.dto;

import lombok.Data;

import java.util.Map;

@Data
public class AdminUserRequest {
    private String userName;
    private String password;
    private Boolean enabled;
    private Boolean admin;
    private String schoolName;
    private String contestantName;
    private String teacherName;
    private Map<String, String> customFields;
}
