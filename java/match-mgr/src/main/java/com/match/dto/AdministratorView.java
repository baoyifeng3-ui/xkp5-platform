package com.match.dto;

import lombok.Data;

@Data
public class AdministratorView {
    private Integer userId;
    private String userName;
    private Boolean enabled;
    private Boolean mustChangePassword;
    private String role;
}
