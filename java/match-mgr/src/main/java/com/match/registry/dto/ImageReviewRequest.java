package com.match.registry.dto;

import lombok.Data;

@Data
public class ImageReviewRequest {
    private String decision;
    private String reason;
}
