package com.rice.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("product")
public class Product {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long shopId;
    private String name;
    private String category;
    private BigDecimal price;
    private Integer stock;
    private String images;
    private String description;
    private String specs;
    private Integer status;
    private Integer sales;
    private String origin;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
