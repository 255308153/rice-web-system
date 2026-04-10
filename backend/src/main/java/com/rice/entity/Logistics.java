package com.rice.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("logistics")
public class Logistics {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long orderId;
    private String company;
    private String trackingNumber;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
