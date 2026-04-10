package com.rice.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("post")
public class Post {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String title;
    private String content;
    private String category;
    private String images;
    private String tags;
    private Integer views;
    private Integer likes;
    private Integer status;
    private String auditRemark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(exist = false)
    private String username;

    @TableField(exist = false)
    private String userRole;

    @TableField(exist = false)
    private Integer commentCount;

    @TableField(exist = false)
    private Boolean liked;

    @TableField(exist = false)
    private Boolean favorited;
}
