package com.match.account.model;

import lombok.Data;

@Data
public class EligibleAccountView {
    private Integer userId;
    private String userName;
    private String remark;
    private String slotId;
    private Integer slotNumber;
    private String agentId;
    private String agentName;
    private String primaryIp;
}
