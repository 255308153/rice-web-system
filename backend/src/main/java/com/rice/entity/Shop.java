package com.rice.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("shop")
public class Shop {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String name;
    private String description;
    private String license;
    private String contact;
    private String avatar;
    private String cover;
    private BigDecimal rating;
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
