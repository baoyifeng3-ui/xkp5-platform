package com.match.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class AdminUserBatchRequest {
    private Integer count;
    private List<String> agentIds;
    private String remark;
    private Map<String, String> customFields;
}
