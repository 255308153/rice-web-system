package com.rice.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class MonitorOverviewDTO {
    private Long pendingMerchants;
    private Long pendingExperts;

    private Long totalOrders;
    private Long todayOrders;
    private BigDecimal totalTradeAmount;
    private BigDecimal todayTradeAmount;

    private Long aiChatCalls;
    private Long aiRecognitionCalls;
    private Long aiTotalCalls;
    private Long todayAICalls;

    private Long totalPosts;
    private Long todayPosts;
    private Long totalComments;
    private Long violationPosts;
    private Long violationComments;

    private Long activeUsers1d;
    private Long activeUsers7d;
    private Long activeUsers30d;

    private Long anomalyUserCount;
    private List<AnomalyUserDTO> anomalyUsers;
}
