package com.rice.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("refund_request")
public class RefundRequest {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long orderId;
    private Long shopId;
    private Long userId;
    private String reason;
    private BigDecimal amount;
    private Integer status;
    private String merchantRemark;
    private LocalDateTime auditTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    @TableField(exist = false)
    private String username;

    @TableField(exist = false)
    private String orderNo;
}
