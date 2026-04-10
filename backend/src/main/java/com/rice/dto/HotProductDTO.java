package com.rice.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class HotProductDTO {
    private Long productId;
    private String productName;
    private Long salesCount;
    private BigDecimal salesAmount;
}
