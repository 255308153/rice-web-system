package com.rice.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rice.dto.ChatContactDTO;
import com.rice.entity.Conversation;
import com.rice.entity.Message;
import com.rice.entity.SystemConfig;
import com.rice.entity.User;
import com.rice.mapper.ConversationMapper;
import com.rice.mapper.MessageMapper;
import com.rice.mapper.SystemConfigMapper;
import com.rice.mapper.UserMapper;
import com.rice.ws.ChatWsPushService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class MessageService {
    private static final String CHAT_PREF_KEY_PREFIX = "chat_pref_user_";
    private static final int MAX_PREF_SIZE = 300;

    private final MessageMapper messageMapper;
    private final ConversationMapper conversationMapper;
    private final UserMapper userMapper;
    private final SystemConfigMapper systemConfigMapper;
    private final ChatWsPushService chatWsPushService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional
    public Message sendMessage(Long senderId, Long receiverId, String content, String type) {
        if (receiverId == null) {
            throw new RuntimeException("接收方不能为空");
        }
        if (Objects.equals(senderId, receiverId)) {
            throw new RuntimeException("不能给自己发送消息");
        }
        if (!StringUtils.hasText(content)) {
            throw new RuntimeException("消息内容不能为空");
        }

        User receiver = userMapper.selectById(receiverId);
        if (receiver == null || !Objects.equals(receiver.getStatus(), 1)) {
            throw new RuntimeException("接收方不存在或不可用");
        }

        String normalizedType = normalizeMessageType(type);
        Conversation conversation = findOrCreateConversation(senderId, receiverId);

        conversation.setLastMessage("IMAGE".equals(normalizedType) ? "[图片]" : content);
        conversation.setLastTime(LocalDateTime.now());
        conversationMapper.updateById(conversation);

        Message message = new Message();
        message.setConversationId(conversation.getId());
        message.setSenderId(senderId);
        message.setContent(content);
        message.setType(normalizedType);
        message.setIsRead(0);
        messageMapper.insert(message);
        chatWsPushService.pushChatMessageAfterCommit(message, conversation, receiverId);
        return message;
    }

    @Transactional
    public Conversation startConversation(Long userId, Long receiverId) {
        if (receiverId == null) {
            throw new RuntimeException("接收方不能为空");
        }
        if (Objects.equals(userId, receiverId)) {
            throw new RuntimeException("不能和自己发起会话");
        }
        User receiver = userMapper.selectById(receiverId);
        if (receiver == null || !Objects.equals(receiver.getStatus(), 1)) {
            throw new RuntimeException("接收方不存在或不可用");
        }

        Conversation conversation = findOrCreateConversation(userId, receiverId);
        if (conversation.getLastTime() == null) {
            conversation.setLastTime(LocalDateTime.now());
            conversationMapper.updateById(conversation);
        }

        fillConversationExtra(userId, Collections.singletonList(conversation));
        return conversation;
    }

    public Page<Conversation> listConversations(Long userId, int page, int size) {
        Page<Conversation> conversationPage = conversationMapper.selectPage(new Page<>(Math.max(page, 1), Math.max(size, 1)),
                new LambdaQueryWrapper<Conversation>()
                        .and(w -> w.eq(Conversation::getUser1Id, userId).or().eq(Conversation::getUser2Id, userId))
                        .orderByDesc(Conversation::getLastTime)
                        .orderByDesc(Conversation::getId));
        fillConversationExtra(userId, conversationPage.getRecords());
        return conversationPage;
    }

    @Transactional
    public Page<Message> listMessages(Long userId, Long conversationId, int page, int size) {
        assertConversationMember(userId, conversationId);

        Page<Message> messagePage = messageMapper.selectPage(new Page<>(Math.max(page, 1), Math.max(size, 1)),
                new LambdaQueryWrapper<Message>()
                        .eq(Message::getConversationId, conversationId)
                        .orderByAsc(Message::getCreateTime)
                        .orderByAsc(Message::getId));

        Message markRead = new Message();
        markRead.setIsRead(1);
        messageMapper.update(markRead, new LambdaQueryWrapper<Message>()
                .eq(Message::getConversationId, conversationId)
                .ne(Message::getSenderId, userId)
                .eq(Message::getIsRead, 0));

        fillMessageExtra(messagePage.getRecords());
        return messagePage;
    }

    public List<ChatContactDTO> listContacts(Long userId, String role, String keyword) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<User>()
                .eq(User::getStatus, 1)
                .ne(User::getId, userId);

        if (StringUtils.hasText(role) && !"ALL".equalsIgnoreCase(role)) {
            String normalizedRole = role.trim().toUpperCase();
            List<String> supportedRoles = Arrays.asList("USER", "MERCHANT", "EXPERT", "ADMIN");
            if (!supportedRoles.contains(normalizedRole)) {
                throw new RuntimeException("角色筛选仅支持 USER/MERCHANT/EXPERT/ADMIN/ALL");
            }
            wrapper.eq(User::getRole, normalizedRole);
        }

        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like(User::getUsername, kw).or().like(User::getPhone, kw));
        }

        wrapper.orderByAsc(User::getRole).orderByDesc(User::getId);
        List<User> users = userMapper.selectList(wrapper);
        if (users.isEmpty()) {
            return Collections.emptyList();
        }

        List<Conversation> myConversations = conversationMapper.selectList(new LambdaQueryWrapper<Conversation>()
                .and(w -> w.eq(Conversation::getUser1Id, userId).or().eq(Conversation::getUser2Id, userId)));
        Map<Long, Long> peerConversationMap = new HashMap<>();
        for (Conversation conversation : myConversations) {
            Long peerId = Objects.equals(conversation.getUser1Id(), userId) ? conversation.getUser2Id() : conversation.getUser1Id();
            if (peerId != null) {
                peerConversationMap.put(peerId, conversation.getId());
            }
        }

        List<ChatContactDTO> contacts = new ArrayList<>();
        for (User user : users) {
            ChatContactDTO contact = new ChatContactDTO();
            contact.setId(user.getId());
            contact.setUsername(user.getUsername());
            contact.setRole(user.getRole());
            contact.setAvatar(user.getAvatar());
            contact.setPhone(user.getPhone());
            contact.setConversationId(peerConversationMap.get(user.getId()));
            contacts.add(contact);
        }
        return contacts;
    }

    private Conversation findOrCreateConversation(Long userId, Long peerId) {
        Long user1Id = Math.min(userId, peerId);
        Long user2Id = Math.max(userId, peerId);

        LambdaQueryWrapper<Conversation> query = new LambdaQueryWrapper<Conversation>()
                .eq(Conversation::getUser1Id, user1Id)
                .eq(Conversation::getUser2Id, user2Id);

        Conversation conversation = conversationMapper.selectOne(query);

        if (conversation != null) {
            return conversation;
        }

        conversation = new Conversation();
        conversation.setUser1Id(user1Id);
        conversation.setUser2Id(user2Id);
        conversation.setLastTime(LocalDateTime.now());
        try {
            conversationMapper.insert(conversation);
            return conversation;
        } catch (DuplicateKeyException ex) {
            // 并发下另一个请求已成功创建会话：回查并复用现有会话，保证幂等
            Conversation existing = conversationMapper.selectOne(query);
            if (existing != null) {
                return existing;
            }
            throw new RuntimeException("会话创建失败，请稍后重试");
        }
    }

    private void assertConversationMember(Long userId, Long conversationId) {
        Conversation conversation = conversationMapper.selectById(conversationId);
        if (conversation == null) {
            throw new RuntimeException("会话不存在");
        }
        if (!Objects.equals(conversation.getUser1Id(), userId) && !Objects.equals(conversation.getUser2Id(), userId)) {
            throw new RuntimeException("无权限访问该会话");
        }
    }

    private void fillConversationExtra(Long userId, List<Conversation> conversations) {
        if (conversations == null || conversations.isEmpty()) {
            return;
        }

        Set<Long> peerIds = new HashSet<>();
        Set<Long> conversationIds = new HashSet<>();
        for (Conversation conversation : conversations) {
            conversationIds.add(conversation.getId());
            Long peerId = Objects.equals(conversation.getUser1Id(), userId) ? conversation.getUser2Id() : conversation.getUser1Id();
            if (peerId != null) {
                peerIds.add(peerId);
            }
        }

        Map<Long, User> peerMap = Collections.emptyMap();
        if (!peerIds.isEmpty()) {
            peerMap = userMapper.selectBatchIds(peerIds).stream()
                    .collect(Collectors.toMap(User::getId, Function.identity(), (a, b) -> a));
        }

        Map<Long, Integer> unreadMap = new HashMap<>();
        if (!conversationIds.isEmpty()) {
            List<Message> unreadMessages = messageMapper.selectList(new LambdaQueryWrapper<Message>()
                    .in(Message::getConversationId, conversationIds)
                    .eq(Message::getIsRead, 0)
                    .ne(Message::getSenderId, userId)
                    .select(Message::getConversationId));
            for (Message msg : unreadMessages) {
                unreadMap.put(msg.getConversationId(), unreadMap.getOrDefault(msg.getConversationId(), 0) + 1);
            }
        }

        for (Conversation conversation : conversations) {
            Long peerId = Objects.equals(conversation.getUser1Id(), userId) ? conversation.getUser2Id() : conversation.getUser1Id();
            conversation.setPeerId(peerId);
            User peer = peerMap.get(peerId);
            if (peer != null) {
                conversation.setPeerName(peer.getUsername());
                conversation.setPeerRole(peer.getRole());
                conversation.setPeerAvatar(peer.getAvatar());
            }
            conversation.setUnreadCount(unreadMap.getOrDefault(conversation.getId(), 0));
        }
    }

    private void fillMessageExtra(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return;
        }

        Set<Long> senderIds = messages.stream()
                .map(Message::getSenderId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (senderIds.isEmpty()) {
            return;
        }

        Map<Long, User> senderMap = userMapper.selectBatchIds(senderIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity(), (a, b) -> a));

        for (Message message : messages) {
            User sender = senderMap.get(message.getSenderId());
            if (sender != null) {
                message.setSenderName(sender.getUsername());
                message.setSenderAvatar(sender.getAvatar());
            }
        }
    }

    private String normalizeMessageType(String type) {
        if (!StringUtils.hasText(type)) {
            return "TEXT";
        }
        String normalized = type.trim().toUpperCase();
        if (!"TEXT".equals(normalized) && !"IMAGE".equals(normalized)) {
            throw new RuntimeException("消息类型仅支持 TEXT/IMAGE");
        }
        return normalized;
    }

    public Map<String, List<String>> getConversationPreferences(Long userId) {
        String configKey = buildPrefConfigKey(userId);
        SystemConfig config = systemConfigMapper.selectOne(new LambdaQueryWrapper<SystemConfig>()
                .eq(SystemConfig::getConfigKey, configKey)
                .last("LIMIT 1"));

        List<String> pinnedIds = new ArrayList<>();
        List<String> hiddenIds = new ArrayList<>();
        if (config != null && StringUtils.hasText(config.getConfigValue())) {
            try {
                Map<?, ?> parsed = objectMapper.readValue(config.getConfigValue(), Map.class);
                pinnedIds = normalizePrefIds(parsed.get("pinnedIds"));
                hiddenIds = normalizePrefIds(parsed.get("hiddenIds"));
            } catch (Exception ignored) {
            }
        }

        Map<String, List<String>> result = new LinkedHashMap<>();
        result.put("pinnedIds", pinnedIds);
        result.put("hiddenIds", hiddenIds);
        return result;
    }

    @Transactional
    public void saveConversationPreferences(Long userId, List<String> pinnedIds, List<String> hiddenIds) {
        List<String> normalizedPinned = normalizePrefIds(pinnedIds);
        List<String> normalizedHidden = normalizePrefIds(hiddenIds);

        if (!normalizedHidden.isEmpty()) {
            Set<String> hiddenSet = new HashSet<>(normalizedHidden);
            normalizedPinned = normalizedPinned.stream()
                    .filter(id -> !hiddenSet.contains(id))
                    .collect(Collectors.toList());
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("pinnedIds", normalizedPinned);
        payload.put("hiddenIds", normalizedHidden);
        payload.put("updatedAt", LocalDateTime.now().toString());

        String configValue;
        try {
            configValue = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new RuntimeException("保存会话偏好失败：" + e.getMessage());
        }

        String configKey = buildPrefConfigKey(userId);
        SystemConfig config = systemConfigMapper.selectOne(new LambdaQueryWrapper<SystemConfig>()
                .eq(SystemConfig::getConfigKey, configKey)
                .last("LIMIT 1"));
        if (config == null) {
            config = new SystemConfig();
            config.setConfigKey(configKey);
            config.setDescription("用户会话偏好配置");
            config.setConfigValue(configValue);
            systemConfigMapper.insert(config);
            return;
        }

        config.setConfigValue(configValue);
        if (!StringUtils.hasText(config.getDescription())) {
            config.setDescription("用户会话偏好配置");
        }
        systemConfigMapper.updateById(config);
    }

    private String buildPrefConfigKey(Long userId) {
        return CHAT_PREF_KEY_PREFIX + userId;
    }

    private List<String> normalizePrefIds(Object raw) {
        if (!(raw instanceof List<?> rawList)) {
            return new ArrayList<>();
        }
        LinkedHashSet<String> deduplicated = new LinkedHashSet<>();
        for (Object item : rawList) {
            if (item == null) {
                continue;
            }
            String text = String.valueOf(item).trim();
            if (!StringUtils.hasText(text)) {
                continue;
            }
            deduplicated.add(text);
            if (deduplicated.size() >= MAX_PREF_SIZE) {
                break;
            }
        }
        return new ArrayList<>(deduplicated);
    }
}
