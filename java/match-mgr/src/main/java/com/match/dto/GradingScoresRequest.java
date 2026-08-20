package com.match.dto;

import lombok.Data;

import java.util.List;

@Data
public class GradingScoresRequest {
    private List<Item> scores;

    @Data
    public static class Item {
        private Integer submissionAnswerId;
        private Integer score;
    }
}
