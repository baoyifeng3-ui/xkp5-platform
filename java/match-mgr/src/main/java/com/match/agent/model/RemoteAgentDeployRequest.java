package com.match.agent.model;

import lombok.Data;

@Data
public class RemoteAgentDeployRequest {
    private String serverIp;
    private Integer sshPort = 22;
    private String username;
    private String password;
    private String label;
    private String workspace = "/srv/xkp";
}
