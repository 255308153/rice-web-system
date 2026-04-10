package com.rice.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class OrderItemDetailDTO {
    private Long id;
    private Long orderId;
    private Long productId;
    private String productName;
    private String productImage;
    private Integer quantity;
    private BigDecimal price;
}
