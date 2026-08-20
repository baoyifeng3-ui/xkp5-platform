package com.match.dto;

import lombok.Data;

@Data
public class AnnouncementFieldRequest {
    private String fieldName;
    private Boolean enabled;
}
