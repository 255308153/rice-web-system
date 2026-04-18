package com.rice.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private final ChatSessionRegistry chatSessionRegistry;
    private final ObjectMapper objectMapper;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Long userId = resolveUserId(session);
        if (userId == null) {
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("unauthorized"));
            return;
        }
        chatSessionRegistry.add(userId, session);
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(Map.of(
                "type", "CONNECTED",
                "userId", userId
        ))));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        if (payload == null || payload.isBlank()) {
            return;
        }
        if ("ping".equalsIgnoreCase(payload.trim())) {
            session.sendMessage(new TextMessage("{\"type\":\"PONG\"}"));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long userId = resolveUserId(session);
        chatSessionRegistry.remove(userId, session);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        Long userId = resolveUserId(session);
        chatSessionRegistry.remove(userId, session);
        if (session.isOpen()) {
            session.close(CloseStatus.SERVER_ERROR);
        }
    }

    private Long resolveUserId(WebSocketSession session) {
        Object raw = session.getAttributes().get("userId");
        if (raw == null) {
            return null;
        }
        if (raw instanceof Long value) {
            return value;
        }
        try {
            return Long.parseLong(Objects.toString(raw));
        } catch (Exception e) {
            return null;
        }
    }
}
