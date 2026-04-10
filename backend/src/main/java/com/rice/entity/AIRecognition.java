package com.rice.entity;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class AIRecognition {
    private Long id;
    private Long userId;
    private String imageUrl;
    private String result;
    private BigDecimal confidence;
    private LocalDateTime createTime;
}
