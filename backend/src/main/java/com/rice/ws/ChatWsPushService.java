package com.rice.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rice.entity.Conversation;
import com.rice.entity.Message;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ChatWsPushService {

    private final ChatSessionRegistry chatSessionRegistry;
    private final ObjectMapper objectMapper;

    public void pushChatMessageAfterCommit(Message message, Conversation conversation, Long receiverId) {
        if (message == null || conversation == null || receiverId == null) {
            return;
        }
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    doPush(message, conversation, receiverId);
                }
            });
            return;
        }
        doPush(message, conversation, receiverId);
    }

    private void doPush(Message message, Conversation conversation, Long receiverId) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("type", "CHAT_MESSAGE");
            payload.put("conversationId", conversation.getId());
            payload.put("messageId", message.getId());
            payload.put("senderId", message.getSenderId());
            payload.put("receiverId", receiverId);
            payload.put("messageType", message.getType());
            payload.put("content", message.getContent());
            payload.put("createTime", message.getCreateTime() != null ? message.getCreateTime() : LocalDateTime.now());

            String text = objectMapper.writeValueAsString(payload);
            chatSessionRegistry.sendToUser(receiverId, text);
            chatSessionRegistry.sendToUser(message.getSenderId(), text);
        } catch (Exception ignored) {
            // 推送失败不影响主交易流程
        }
    }
}
