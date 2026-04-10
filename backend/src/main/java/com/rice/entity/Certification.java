package com.rice.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("certification")
public class Certification {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String role;
    private String credentials;
    private Integer status;
    private LocalDateTime auditTime;
    private String auditRemark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(exist = false)
    private String username;

    @TableField(exist = false)
    private String phone;

    @TableField(exist = false)
    private String currentRole;
}
