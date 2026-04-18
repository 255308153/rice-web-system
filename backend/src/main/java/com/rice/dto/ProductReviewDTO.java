package com.rice.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ProductReviewDTO {
    private Long id;
    private Long userId;
    private String username;
    private String userAvatar;
    private Integer rating;
    private String content;
    private LocalDateTime createTime;
}
