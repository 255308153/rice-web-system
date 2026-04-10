package com.rice.dto;

import lombok.Data;

@Data
public class ChatContactDTO {
    private Long id;
    private String username;
    private String role;
    private String avatar;
    private String phone;
    private Long conversationId;
}
