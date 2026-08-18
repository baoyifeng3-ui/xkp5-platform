package com.match.dto;

import lombok.Data;

@Data
public class AdministratorRequest {
    private String userName;
    private String password;
    private Boolean enabled;
}
