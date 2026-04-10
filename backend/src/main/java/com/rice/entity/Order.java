package com.rice.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("`order`")
public class Order {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String orderNo;
    private Long userId;
    private Long shopId;
    private BigDecimal totalPrice;
    private Integer status;
    private Long addressId;

    @TableField(exist = false)
    private Boolean reviewed;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
