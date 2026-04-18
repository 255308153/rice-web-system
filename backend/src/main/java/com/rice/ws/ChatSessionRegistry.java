package com.rice.ws;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ChatSessionRegistry {

    private final ConcurrentHashMap<Long, Set<WebSocketSession>> userSessions = new ConcurrentHashMap<>();

    public void add(Long userId, WebSocketSession session) {
        if (userId == null || session == null) {
            return;
        }
        userSessions.computeIfAbsent(userId, key -> ConcurrentHashMap.newKeySet()).add(session);
    }

    public void remove(Long userId, WebSocketSession session) {
        if (userId == null || session == null) {
            return;
        }
        Set<WebSocketSession> sessions = userSessions.get(userId);
        if (sessions == null) {
            return;
        }
        sessions.remove(session);
        if (sessions.isEmpty()) {
            userSessions.remove(userId);
        }
    }

    public void sendToUser(Long userId, String payload) {
        if (userId == null || payload == null) {
            return;
        }
        Set<WebSocketSession> sessions = userSessions.get(userId);
        if (sessions == null || sessions.isEmpty()) {
            return;
        }

        TextMessage message = new TextMessage(payload);
        for (WebSocketSession session : sessions.toArray(new WebSocketSession[0])) {
            if (session == null || !session.isOpen()) {
                sessions.remove(session);
                continue;
            }
            try {
                synchronized (session) {
                    session.sendMessage(message);
                }
            } catch (IOException e) {
                sessions.remove(session);
                try {
                    session.close();
                } catch (IOException ignored) {
                    // ignore close exception
                }
            }
        }
        if (sessions.isEmpty()) {
            userSessions.remove(userId);
        }
    }
}
