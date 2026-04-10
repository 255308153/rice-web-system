package com.rice.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AnomalyUserDTO {
    private Long userId;
    private String username;
    private Integer postViolationCount;
    private Integer commentViolationCount;
    private Integer totalViolationCount;
    private LocalDateTime lastViolationTime;
    private String riskLevel;
}
