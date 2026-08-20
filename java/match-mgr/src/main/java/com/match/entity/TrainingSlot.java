package com.match.entity;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class TrainingSlot {
    private Integer slotNo;
    private Integer userId;
    private String userName;
}
