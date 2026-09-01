package com.match.dto;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class AdminUserView {
    private Integer userId;
    private String userName;
    private String password;
    private String initialPassword;
    private String remark;
    private String agentId;
    private String agentName;
    private String primaryIp;
    private String slotId;
    private Integer slotNumber;
    private Integer environmentCount;
    private Boolean placementReady;
    private Boolean enabled;
    private Boolean mustChangePassword;
    private Boolean isAdmin;
    private String schoolName;
    private String contestantName;
    private String teacherName;
    private Map<String, String> customFields = new LinkedHashMap<>();
}
