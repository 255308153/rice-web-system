package com.rice.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("conversation")
public class Conversation {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long user1Id;
    private Long user2Id;
    private String lastMessage;
    private LocalDateTime lastTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(exist = false)
    private Long peerId;

    @TableField(exist = false)
    private String peerName;

    @TableField(exist = false)
    private String peerRole;

    @TableField(exist = false)
    private String peerAvatar;

    @TableField(exist = false)
    private Integer unreadCount;
}
