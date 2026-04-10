package com.rice.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("review")
public class Review {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long orderId;
    private Long userId;
    private Long productId;
    private Integer rating;
    private String content;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
