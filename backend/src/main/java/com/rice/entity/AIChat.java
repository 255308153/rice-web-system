package com.rice.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class AIChat {
    private Long id;
    private Long userId;
    private String question;
    private String answer;
    private LocalDateTime createTime;
}
