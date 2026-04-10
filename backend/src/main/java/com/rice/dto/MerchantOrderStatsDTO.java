package com.rice.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class MerchantOrderStatsDTO {
    private Integer daySalesVolume;
    private BigDecimal daySalesAmount;
    private Integer monthSalesVolume;
    private BigDecimal monthSalesAmount;
}
