package com.rice.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rice.dto.AnomalyUserDTO;
import com.rice.dto.HotProductDTO;
import com.rice.dto.MonitorOverviewDTO;
import com.rice.entity.*;
import com.rice.mapper.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class AdminService {
    private static final int POST_STATUS_ABNORMAL = 0;
    private static final int POST_STATUS_NORMAL = 1;
    private static final int POST_STATUS_DOWN = 2;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private CertificationMapper certificationMapper;

    @Autowired
    private PostMapper postMapper;

    @Autowired
    private CommentMapper commentMapper;

    @Autowired
    private SystemConfigMapper systemConfigMapper;

    @Autowired
    private SystemNotificationMapper systemNotificationMapper;

    @Autowired
    private AdminLogMapper adminLogMapper;

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private ShopMapper shopMapper;

    @Autowired
    private OrderItemMapper orderItemMapper;

    @Autowired
    private AIChatMapper aiChatMapper;

    @Autowired
    private AIRecognitionMapper aiRecognitionMapper;

    @Autowired
    private MessageMapper messageMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Value("${spring.datasource.url:}")
    private String datasourceUrl;

    @Value("${spring.datasource.username:}")
    private String datasourceUsername;

    @Value("${spring.datasource.password:}")
    private String datasourcePassword;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public Page<User> getUserList(Long adminId, int page, int size, String role, String keyword, Integer status) {
        assertAdmin(adminId);
        LambdaQueryWrapper<User> wrapper = buildUserFilter(role, keyword, status)
                .orderByDesc(User::getId);
        Page<User> result = userMapper.selectPage(new Page<>(page, size), wrapper);
        maskUserPasswords(result.getRecords());
        return result;
    }

    public List<User> exportUserList(Long adminId, String role, String keyword, Integer status) {
        assertAdmin(adminId);
        List<User> users = userMapper.selectList(buildUserFilter(role, keyword, status).orderByDesc(User::getId));
        maskUserPasswords(users);
        return users;
    }

    public Page<Order> getOrderList(Long adminId, int page, int size) {
        assertAdmin(adminId);
        return orderMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<Order>().orderByDesc(Order::getId));
    }

    public List<HotProductDTO> getHotProducts(Long adminId, int limit) {
        assertAdmin(adminId);
        int safeLimit = Math.max(1, Math.min(limit, 20));
        List<HotProductDTO> hotProducts = orderItemMapper.selectHotProducts(safeLimit);
        if (hotProducts == null) {
            return Collections.emptyList();
        }
        return hotProducts;
    }

    public MonitorOverviewDTO getMonitorOverview(Long adminId, Integer violationWindowDays, Integer violationThreshold) {
        assertAdmin(adminId);
        int safeWindowDays = Math.max(1, Math.min(violationWindowDays == null ? 30 : violationWindowDays, 365));
        int safeThreshold = Math.max(1, Math.min(violationThreshold == null ? 3 : violationThreshold, 100));

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime dayStart = now.toLocalDate().atStartOfDay();
        LocalDateTime sevenDaysAgo = now.minusDays(7);
        LocalDateTime thirtyDaysAgo = now.minusDays(30);
        LocalDateTime violationSince = now.minusDays(safeWindowDays);

        MonitorOverviewDTO dto = new MonitorOverviewDTO();
        dto.setPendingMerchants(zeroIfNull(certificationMapper.selectCount(new LambdaQueryWrapper<Certification>()
                .eq(Certification::getStatus, 0)
                .eq(Certification::getRole, "MERCHANT"))));
        dto.setPendingExperts(zeroIfNull(certificationMapper.selectCount(new LambdaQueryWrapper<Certification>()
                .eq(Certification::getStatus, 0)
                .eq(Certification::getRole, "EXPERT"))));

        dto.setTotalOrders(zeroIfNull(orderMapper.selectCount(new LambdaQueryWrapper<Order>())));
        dto.setTodayOrders(zeroIfNull(orderMapper.selectCount(new LambdaQueryWrapper<Order>()
                .ge(Order::getCreateTime, dayStart))));
        dto.setTotalTradeAmount(sumOrderAmount(null));
        dto.setTodayTradeAmount(sumOrderAmount(dayStart));

        Long aiChatCalls = zeroIfNull(aiChatMapper.countAll());
        Long aiRecognitionCalls = zeroIfNull(aiRecognitionMapper.countAll());
        dto.setAiChatCalls(aiChatCalls);
        dto.setAiRecognitionCalls(aiRecognitionCalls);
        dto.setAiTotalCalls(aiChatCalls + aiRecognitionCalls);
        dto.setTodayAICalls(zeroIfNull(aiChatMapper.countSince(dayStart)) + zeroIfNull(aiRecognitionMapper.countSince(dayStart)));

        dto.setTotalPosts(zeroIfNull(postMapper.selectCount(new LambdaQueryWrapper<Post>())));
        dto.setTodayPosts(zeroIfNull(postMapper.selectCount(new LambdaQueryWrapper<Post>()
                .ge(Post::getCreateTime, dayStart))));
        dto.setTotalComments(zeroIfNull(commentMapper.selectCount(new LambdaQueryWrapper<Comment>())));
        dto.setViolationPosts(zeroIfNull(postMapper.selectCount(new LambdaQueryWrapper<Post>()
                .ne(Post::getStatus, POST_STATUS_NORMAL))));
        dto.setViolationComments(zeroIfNull(commentMapper.selectCount(new LambdaQueryWrapper<Comment>()
                .eq(Comment::getStatus, 0))));

        dto.setActiveUsers1d((long) collectActiveUsersSince(dayStart).size());
        dto.setActiveUsers7d((long) collectActiveUsersSince(sevenDaysAgo).size());
        dto.setActiveUsers30d((long) collectActiveUsersSince(thirtyDaysAgo).size());

        List<AnomalyUserDTO> anomalyUsers = buildAnomalyUsers(violationSince, safeThreshold);
        dto.setAnomalyUsers(anomalyUsers);
        dto.setAnomalyUserCount((long) anomalyUsers.size());
        return dto;
    }

    @Transactional
    public void updateUserStatus(Long adminId, Long userId, Integer status) {
        assertAdmin(adminId);
        if (status == null || (status != 0 && status != 1)) {
            throw new RuntimeException("用户状态不合法");
        }
        if (Objects.equals(adminId, userId)) {
            throw new RuntimeException("不能修改当前登录管理员状态");
        }

        User target = userMapper.selectById(userId);
        if (target == null) {
            throw new RuntimeException("用户不存在");
        }
        if ("ADMIN".equalsIgnoreCase(target.getRole())) {
            throw new RuntimeException("不支持修改管理员账号状态");
        }

        User user = new User();
        user.setId(userId);
        user.setStatus(status);
        userMapper.updateById(user);
        logAction(adminId, "UPDATE_USER_STATUS", "user:" + userId, "status:" + status);
    }

    @Transactional
    public void resetUserPassword(Long adminId, Long userId, String newPassword) {
        assertAdmin(adminId);
        if (!StringUtils.hasText(newPassword)) {
            throw new RuntimeException("新密码不能为空");
        }
        User target = userMapper.selectById(userId);
        if (target == null) {
            throw new RuntimeException("用户不存在");
        }
        User user = new User();
        user.setId(userId);
        user.setPassword(passwordEncoder.encode(newPassword.trim()));
        userMapper.updateById(user);
        logAction(adminId, "RESET_USER_PASSWORD", "user:" + userId, "reset");
    }

    public Page<Certification> getAuditList(Long adminId, int page, int size, Integer status, String role) {
        assertAdmin(adminId);
        LambdaQueryWrapper<Certification> wrapper = new LambdaQueryWrapper<>();
        if (status != null && status >= 0) {
            wrapper.eq(Certification::getStatus, status);
        }
        if (StringUtils.hasText(role)) {
            wrapper.eq(Certification::getRole, role.trim());
        }
        wrapper.orderByDesc(Certification::getId);

        Page<Certification> certPage = certificationMapper.selectPage(new Page<>(page, size), wrapper);
        fillAuditUserInfo(certPage.getRecords());
        return certPage;
    }

    @Transactional
    public void auditCertification(Long id, Integer status, String remark, Long adminId) {
        assertAdmin(adminId);
        if (status == null || (status != 1 && status != 2)) {
            throw new RuntimeException("审核状态不合法");
        }
        if (status == 2 && !StringUtils.hasText(remark)) {
            throw new RuntimeException("拒绝时请填写审核备注");
        }

        Certification current = certificationMapper.selectById(id);
        if (current == null) {
            throw new RuntimeException("审核记录不存在");
        }
        if (!Objects.equals(current.getStatus(), 0)) {
            throw new RuntimeException("该申请已审核，请勿重复操作");
        }

        Certification cert = new Certification();
        cert.setId(id);
        cert.setStatus(status);
        cert.setAuditRemark(StringUtils.hasText(remark) ? remark.trim() : null);
        cert.setAuditTime(LocalDateTime.now());
        certificationMapper.updateById(cert);

        if (status == 1) {
            User user = new User();
            user.setId(current.getUserId());
            user.setRole(current.getRole());
            user.setStatus(1);
            userMapper.updateById(user);

            if ("MERCHANT".equalsIgnoreCase(current.getRole())) {
                ensureMerchantShop(current.getUserId());
            }
        }

        logAction(adminId, "AUDIT_CERTIFICATION", "cert:" + id,
                "status:" + status + ", remark:" + (StringUtils.hasText(remark) ? remark.trim() : "-"));
    }

    @Transactional
    public void deletePost(Long postId, Long adminId) {
        assertAdmin(adminId);
        postMapper.deleteById(postId);
        logAction(adminId, "DELETE_POST", "post:" + postId, "deleted");
    }

    public Page<Post> getPostAuditList(Long adminId, int page, int size, Integer status, String keyword) {
        assertAdmin(adminId);
        LambdaQueryWrapper<Post> wrapper = new LambdaQueryWrapper<>();
        if (status != null && status >= 0) {
            wrapper.eq(Post::getStatus, status);
        }
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like(Post::getTitle, kw)
                    .or().like(Post::getContent, kw)
                    .or().like(Post::getCategory, kw));
        }
        wrapper.orderByDesc(Post::getId);

        Page<Post> postPage = postMapper.selectPage(new Page<>(Math.max(page, 1), Math.max(size, 1)), wrapper);
        fillPostAuditUserInfo(postPage.getRecords());
        return postPage;
    }

    @Transactional
    public void updatePostStatus(Long adminId, Long postId, Integer status, String remark) {
        assertAdmin(adminId);
        if (status == null || (status != POST_STATUS_ABNORMAL && status != POST_STATUS_NORMAL && status != POST_STATUS_DOWN)) {
            throw new RuntimeException("帖子状态不合法");
        }
        Post post = postMapper.selectById(postId);
        if (post == null) {
            throw new RuntimeException("帖子不存在");
        }
        Post update = new Post();
        update.setId(postId);
        update.setStatus(status);
        update.setAuditRemark(StringUtils.hasText(remark) ? remark.trim() : null);
        postMapper.updateById(update);

        logAction(adminId, "AUDIT_POST", "post:" + postId,
                "status:" + status + ", remark:" + (StringUtils.hasText(remark) ? remark.trim() : "-"));
    }

    public Page<Comment> getCommentAuditList(Long adminId, int page, int size, Integer status, String keyword) {
        assertAdmin(adminId);
        LambdaQueryWrapper<Comment> wrapper = new LambdaQueryWrapper<>();
        if (status != null && status >= 0) {
            wrapper.eq(Comment::getStatus, status);
        }
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            wrapper.like(Comment::getContent, kw);
        }
        wrapper.orderByDesc(Comment::getId);

        Page<Comment> commentPage = commentMapper.selectPage(new Page<>(Math.max(page, 1), Math.max(size, 1)), wrapper);
        fillCommentAuditExtra(commentPage.getRecords());
        return commentPage;
    }

    @Transactional
    public void updateCommentStatus(Long adminId, Long commentId, Integer status, String remark) {
        assertAdmin(adminId);
        if (status == null || (status != 0 && status != 1)) {
            throw new RuntimeException("评论状态不合法");
        }
        Comment comment = commentMapper.selectById(commentId);
        if (comment == null) {
            throw new RuntimeException("评论不存在");
        }
        Comment update = new Comment();
        update.setId(commentId);
        update.setStatus(status);
        update.setAuditRemark(StringUtils.hasText(remark) ? remark.trim() : null);
        commentMapper.updateById(update);

        logAction(adminId, "AUDIT_COMMENT", "comment:" + commentId,
                "status:" + status + ", remark:" + (StringUtils.hasText(remark) ? remark.trim() : "-"));
    }

    public List<String> getForumCategories(Long adminId) {
        assertAdmin(adminId);
        return parseForumCategoriesFromConfig();
    }

    public void setForumCategories(Long adminId, List<String> categories) {
        assertAdmin(adminId);
        List<String> normalized = Optional.ofNullable(categories).orElse(Collections.emptyList())
                .stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .limit(30)
                .collect(Collectors.toList());
        if (normalized.isEmpty()) {
            throw new RuntimeException("至少保留一个论坛分类");
        }
        try {
            setConfig(adminId, "forum_categories", objectMapper.writeValueAsString(normalized), "论坛话题分类配置");
        } catch (Exception e) {
            throw new RuntimeException("保存论坛分类失败: " + e.getMessage());
        }
        logAction(adminId, "SET_FORUM_CATEGORIES", "forum_categories", String.join(",", normalized));
    }

    public List<SystemNotification> getNoticeList(Long userId, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 200));
        User user = userMapper.selectById(userId);
        String role = user == null ? "" : user.getRole();

        LambdaQueryWrapper<SystemNotification> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SystemNotification::getType, "NOTICE")
                .and(w -> w.isNull(SystemNotification::getRole)
                        .or().eq(SystemNotification::getRole, "ALL")
                        .or(StringUtils.hasText(role), x -> x.eq(SystemNotification::getRole, role)))
                .orderByDesc(SystemNotification::getCreateTime)
                .orderByDesc(SystemNotification::getId)
                .last("LIMIT " + safeLimit);
        return systemNotificationMapper.selectList(wrapper);
    }

    @Transactional
    public void createNotice(Long adminId, String title, String content, String role) {
        assertAdmin(adminId);
        if (!StringUtils.hasText(title) || !StringUtils.hasText(content)) {
            throw new RuntimeException("公告标题和内容不能为空");
        }
        SystemNotification notice = new SystemNotification();
        notice.setUserId(adminId);
        notice.setRole(StringUtils.hasText(role) ? role.trim().toUpperCase() : "ALL");
        notice.setTitle(title.trim());
        notice.setContent(content.trim());
        notice.setType("NOTICE");
        notice.setIsRead(0);
        notice.setCreateTime(LocalDateTime.now());
        systemNotificationMapper.insert(notice);

        logAction(adminId, "CREATE_NOTICE", "notice:" + notice.getId(), notice.getTitle());
    }

    @Transactional
    public void deleteNotice(Long adminId, Long noticeId) {
        assertAdmin(adminId);
        if (noticeId == null) {
            throw new RuntimeException("公告ID不能为空");
        }
        systemNotificationMapper.deleteById(noticeId);
        logAction(adminId, "DELETE_NOTICE", "notice:" + noticeId, "deleted");
    }

    public Map<String, Object> backupDatabase(Long adminId) {
        assertAdmin(adminId);
        DbConnectionInfo db = parseDbConnection();

        Path backupDir = Paths.get("backups");
        try {
            Files.createDirectories(backupDir);
        } catch (IOException e) {
            throw new RuntimeException("创建备份目录失败: " + e.getMessage());
        }

        String fileName = "rice_backup_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".sql";
        Path backupFile = backupDir.resolve(fileName);

        ProcessBuilder pb = new ProcessBuilder(
                "mysqldump",
                "-h", db.host(),
                "-P", String.valueOf(db.port()),
                "-u" + datasourceUsername,
                "-p" + datasourcePassword,
                db.database()
        );
        pb.redirectErrorStream(true);
        pb.redirectOutput(backupFile.toFile());

        try {
            int exitCode = pb.start().waitFor();
            long fileSize = Files.exists(backupFile) ? Files.size(backupFile) : 0;
            if (exitCode != 0 || fileSize <= 0) {
                throw new RuntimeException("mysqldump 执行失败，退出码: " + exitCode);
            }
            logAction(adminId, "BACKUP_DATABASE", backupFile.toString(), "size:" + fileSize);

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("fileName", fileName);
            data.put("filePath", backupFile.toAbsolutePath().toString());
            data.put("size", fileSize);
            data.put("createdAt", LocalDateTime.now());
            return data;
        } catch (IOException e) {
            throw new RuntimeException("数据库备份失败: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("数据库备份被中断");
        }
    }

    public List<Map<String, Object>> listBackupFiles(Long adminId) {
        assertAdmin(adminId);
        Path backupDir = Paths.get("backups");
        if (!Files.exists(backupDir) || !Files.isDirectory(backupDir)) {
            return Collections.emptyList();
        }
        try (Stream<Path> stream = Files.list(backupDir)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".sql"))
                    .sorted((a, b) -> {
                        try {
                            return Files.getLastModifiedTime(b).compareTo(Files.getLastModifiedTime(a));
                        } catch (IOException e) {
                            return 0;
                        }
                    })
                    .limit(50)
                    .map(path -> {
                        Map<String, Object> item = new LinkedHashMap<>();
                        item.put("fileName", path.getFileName().toString());
                        item.put("filePath", path.toAbsolutePath().toString());
                        try {
                            item.put("size", Files.size(path));
                            item.put("modifiedTime", Files.getLastModifiedTime(path).toString());
                        } catch (IOException e) {
                            item.put("size", 0L);
                            item.put("modifiedTime", "-");
                        }
                        return item;
                    })
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new RuntimeException("读取备份列表失败: " + e.getMessage());
        }
    }

    public String getConfig(Long adminId, String key) {
        assertAdmin(adminId);
        SystemConfig config = systemConfigMapper.selectOne(
                new LambdaQueryWrapper<SystemConfig>().eq(SystemConfig::getConfigKey, key));
        return config != null ? config.getConfigValue() : null;
    }

    public void setConfig(Long adminId, String key, String value, String description) {
        assertAdmin(adminId);
        if (!StringUtils.hasText(key)) {
            throw new RuntimeException("配置键不能为空");
        }
        SystemConfig config = systemConfigMapper.selectOne(
                new LambdaQueryWrapper<SystemConfig>().eq(SystemConfig::getConfigKey, key));

        if (config != null) {
            config.setConfigValue(value);
            if (StringUtils.hasText(description)) {
                config.setDescription(description.trim());
            }
            systemConfigMapper.updateById(config);
        } else {
            config = new SystemConfig();
            config.setConfigKey(key.trim());
            config.setConfigValue(value);
            config.setDescription(description);
            systemConfigMapper.insert(config);
        }
    }

    private void ensureMerchantShop(Long userId) {
        Shop existingShop = shopMapper.selectOne(new LambdaQueryWrapper<Shop>()
                .eq(Shop::getUserId, userId)
                .last("LIMIT 1"));
        if (existingShop != null) {
            return;
        }

        User user = userMapper.selectById(userId);
        String shopName = (user != null && StringUtils.hasText(user.getUsername()))
                ? user.getUsername().trim() + "的店铺"
                : "默认商户店铺";

        Shop shop = new Shop();
        shop.setUserId(userId);
        shop.setName(shopName);
        shop.setDescription("该店铺已通过平台资质审核");
        shop.setStatus(1);
        shop.setRating(new BigDecimal("5.0"));
        shopMapper.insert(shop);
    }

    private void fillAuditUserInfo(List<Certification> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        Set<Long> userIds = records.stream()
                .map(Certification::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (userIds.isEmpty()) {
            return;
        }

        List<User> users = userMapper.selectBatchIds(userIds);
        Map<Long, User> userMap = users.stream()
                .collect(Collectors.toMap(User::getId, Function.identity(), (a, b) -> a));

        for (Certification record : records) {
            User user = userMap.get(record.getUserId());
            if (user == null) {
                continue;
            }
            record.setUsername(user.getUsername());
            record.setPhone(user.getPhone());
            record.setCurrentRole(user.getRole());
        }
    }

    private void fillPostAuditUserInfo(List<Post> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        Set<Long> userIds = records.stream()
                .map(Post::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (userIds.isEmpty()) {
            return;
        }
        Map<Long, User> userMap = userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity(), (a, b) -> a));
        for (Post post : records) {
            User user = userMap.get(post.getUserId());
            if (user != null) {
                post.setUsername(user.getUsername());
                post.setUserRole(user.getRole());
            }
            if (post.getStatus() == null) {
                post.setStatus(POST_STATUS_NORMAL);
            }
        }
    }

    private void fillCommentAuditExtra(List<Comment> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        Set<Long> userIds = records.stream()
                .map(Comment::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<Long> postIds = records.stream()
                .map(Comment::getPostId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<Long, User> userMap = userIds.isEmpty() ? Collections.emptyMap() : userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity(), (a, b) -> a));
        Map<Long, Post> postMap = postIds.isEmpty() ? Collections.emptyMap() : postMapper.selectBatchIds(postIds).stream()
                .collect(Collectors.toMap(Post::getId, Function.identity(), (a, b) -> a));

        for (Comment comment : records) {
            User user = userMap.get(comment.getUserId());
            if (user != null) {
                comment.setUsername(user.getUsername());
                comment.setUserRole(user.getRole());
            }
            Post post = postMap.get(comment.getPostId());
            if (post != null) {
                comment.setPostTitle(post.getTitle());
            }
            if (comment.getStatus() == null) {
                comment.setStatus(1);
            }
        }
    }

    private LambdaQueryWrapper<User> buildUserFilter(String role, String keyword, Integer status) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(role) && !"ALL".equalsIgnoreCase(role.trim())) {
            wrapper.eq(User::getRole, role.trim().toUpperCase());
        }
        if (status != null && status >= 0) {
            wrapper.eq(User::getStatus, status);
        }
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like(User::getUsername, kw).or().like(User::getPhone, kw));
        }
        return wrapper;
    }

    private void maskUserPasswords(List<User> users) {
        if (users == null || users.isEmpty()) {
            return;
        }
        for (User user : users) {
            user.setPassword(null);
        }
    }

    private BigDecimal sumOrderAmount(LocalDateTime since) {
        QueryWrapper<Order> wrapper = new QueryWrapper<>();
        wrapper.select("IFNULL(SUM(total_price), 0) AS total_amount")
                .in("status", Arrays.asList(1, 2, 3));
        if (since != null) {
            wrapper.ge("create_time", since);
        }
        List<Map<String, Object>> rows = orderMapper.selectMaps(wrapper);
        if (rows == null || rows.isEmpty()) {
            return BigDecimal.ZERO;
        }
        Map<String, Object> row = rows.get(0);
        Object value = row.get("total_amount");
        if (value == null && row.values().stream().findFirst().isPresent()) {
            value = row.values().stream().findFirst().orElse(null);
        }
        return parseBigDecimal(value);
    }

    private BigDecimal parseBigDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        try {
            if (value instanceof BigDecimal bigDecimal) {
                return bigDecimal;
            }
            return new BigDecimal(String.valueOf(value));
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    private Set<Long> collectActiveUsersSince(LocalDateTime since) {
        Set<Long> userIds = new HashSet<>();
        addUserIds(userIds, orderMapper.selectList(new LambdaQueryWrapper<Order>()
                .ge(Order::getCreateTime, since)
                .select(Order::getUserId)), Order::getUserId);
        addUserIds(userIds, postMapper.selectList(new LambdaQueryWrapper<Post>()
                .ge(Post::getCreateTime, since)
                .select(Post::getUserId)), Post::getUserId);
        addUserIds(userIds, commentMapper.selectList(new LambdaQueryWrapper<Comment>()
                .ge(Comment::getCreateTime, since)
                .select(Comment::getUserId)), Comment::getUserId);
        addUserIds(userIds, messageMapper.selectList(new LambdaQueryWrapper<Message>()
                .ge(Message::getCreateTime, since)
                .select(Message::getSenderId)), Message::getSenderId);

        List<Long> chatUserIds = aiChatMapper.findUserIdsSince(since);
        if (chatUserIds != null) {
            userIds.addAll(chatUserIds.stream().filter(Objects::nonNull).collect(Collectors.toSet()));
        }
        List<Long> recognitionUserIds = aiRecognitionMapper.findUserIdsSince(since);
        if (recognitionUserIds != null) {
            userIds.addAll(recognitionUserIds.stream().filter(Objects::nonNull).collect(Collectors.toSet()));
        }
        return userIds;
    }

    private <T> void addUserIds(Set<Long> target, List<T> rows, Function<T, Long> extractor) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        for (T row : rows) {
            Long userId = extractor.apply(row);
            if (userId != null) {
                target.add(userId);
            }
        }
    }

    private List<AnomalyUserDTO> buildAnomalyUsers(LocalDateTime since, int threshold) {
        Map<Long, ViolationAggregate> aggregateMap = new HashMap<>();

        List<Post> violationPosts = postMapper.selectList(new LambdaQueryWrapper<Post>()
                .ne(Post::getStatus, POST_STATUS_NORMAL)
                .ge(Post::getCreateTime, since)
                .select(Post::getUserId, Post::getCreateTime));
        if (violationPosts != null) {
            for (Post post : violationPosts) {
                if (post.getUserId() == null) {
                    continue;
                }
                aggregateMap.computeIfAbsent(post.getUserId(), k -> new ViolationAggregate())
                        .addPost(post.getCreateTime());
            }
        }

        List<Comment> violationComments = commentMapper.selectList(new LambdaQueryWrapper<Comment>()
                .eq(Comment::getStatus, 0)
                .ge(Comment::getCreateTime, since)
                .select(Comment::getUserId, Comment::getCreateTime));
        if (violationComments != null) {
            for (Comment comment : violationComments) {
                if (comment.getUserId() == null) {
                    continue;
                }
                aggregateMap.computeIfAbsent(comment.getUserId(), k -> new ViolationAggregate())
                        .addComment(comment.getCreateTime());
            }
        }

        if (aggregateMap.isEmpty()) {
            return Collections.emptyList();
        }

        Set<Long> userIds = aggregateMap.keySet();
        Map<Long, User> userMap = userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity(), (a, b) -> a));

        return aggregateMap.entrySet().stream()
                .filter(entry -> entry.getValue().totalCount() >= threshold)
                .map(entry -> {
                    Long userId = entry.getKey();
                    ViolationAggregate aggregate = entry.getValue();
                    AnomalyUserDTO dto = new AnomalyUserDTO();
                    dto.setUserId(userId);
                    dto.setUsername(Optional.ofNullable(userMap.get(userId)).map(User::getUsername).orElse(null));
                    dto.setPostViolationCount(aggregate.postViolationCount);
                    dto.setCommentViolationCount(aggregate.commentViolationCount);
                    dto.setTotalViolationCount(aggregate.totalCount());
                    dto.setLastViolationTime(aggregate.lastViolationTime);
                    dto.setRiskLevel(resolveRiskLevel(aggregate.totalCount(), threshold));
                    return dto;
                })
                .sorted((a, b) -> {
                    int byCount = Integer.compare(
                            Optional.ofNullable(b.getTotalViolationCount()).orElse(0),
                            Optional.ofNullable(a.getTotalViolationCount()).orElse(0));
                    if (byCount != 0) {
                        return byCount;
                    }
                    LocalDateTime aTime = a.getLastViolationTime();
                    LocalDateTime bTime = b.getLastViolationTime();
                    if (aTime == null && bTime == null) {
                        return 0;
                    }
                    if (aTime == null) {
                        return 1;
                    }
                    if (bTime == null) {
                        return -1;
                    }
                    return bTime.compareTo(aTime);
                })
                .limit(30)
                .collect(Collectors.toList());
    }

    private String resolveRiskLevel(int totalCount, int threshold) {
        if (totalCount >= threshold * 2) {
            return "HIGH";
        }
        if (totalCount >= threshold) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private Long zeroIfNull(Long value) {
        return value == null ? 0L : value;
    }

    private List<String> parseForumCategoriesFromConfig() {
        SystemConfig config = systemConfigMapper.selectOne(new LambdaQueryWrapper<SystemConfig>()
                .eq(SystemConfig::getConfigKey, "forum_categories")
                .last("LIMIT 1"));
        if (config == null || !StringUtils.hasText(config.getConfigValue())) {
            return Arrays.asList("综合交流", "种植经验", "病虫害防治", "市场行情", "政策资讯");
        }
        try {
            JsonNode node = objectMapper.readTree(config.getConfigValue());
            if (!node.isArray()) {
                return Arrays.asList("综合交流", "种植经验", "病虫害防治", "市场行情", "政策资讯");
            }
            List<String> categories = new ArrayList<>();
            node.forEach(item -> {
                String text = item.asText("");
                if (StringUtils.hasText(text)) {
                    categories.add(text.trim());
                }
            });
            if (!categories.isEmpty()) {
                return categories;
            }
        } catch (Exception ignored) {
        }
        return Arrays.asList("综合交流", "种植经验", "病虫害防治", "市场行情", "政策资讯");
    }

    private DbConnectionInfo parseDbConnection() {
        if (!StringUtils.hasText(datasourceUrl) || !datasourceUrl.startsWith("jdbc:mysql://")) {
            throw new RuntimeException("不支持的数据库连接串: " + datasourceUrl);
        }
        String uri = datasourceUrl.substring("jdbc:mysql://".length());
        int queryIndex = uri.indexOf('?');
        if (queryIndex >= 0) {
            uri = uri.substring(0, queryIndex);
        }
        String[] hostAndDb = uri.split("/", 2);
        if (hostAndDb.length != 2 || !StringUtils.hasText(hostAndDb[1])) {
            throw new RuntimeException("无法解析数据库名: " + datasourceUrl);
        }
        String hostPort = hostAndDb[0];
        String database = hostAndDb[1];

        String host = hostPort;
        int port = 3306;
        int colonIndex = hostPort.indexOf(':');
        if (colonIndex > 0) {
            host = hostPort.substring(0, colonIndex);
            try {
                port = Integer.parseInt(hostPort.substring(colonIndex + 1));
            } catch (Exception e) {
                port = 3306;
            }
        }
        return new DbConnectionInfo(host, port, database);
    }

    private void assertAdmin(Long adminId) {
        if (adminId == null) {
            throw new RuntimeException("未登录");
        }
        User admin = userMapper.selectById(adminId);
        if (admin == null || !"ADMIN".equals(admin.getRole()) || !Objects.equals(admin.getStatus(), 1)) {
            throw new RuntimeException("无管理员权限");
        }
    }

    private void logAction(Long adminId, String action, String target, String detail) {
        AdminLog log = new AdminLog();
        log.setAdminId(adminId);
        log.setAction(action);
        log.setTarget(target);
        log.setDetail(detail);
        adminLogMapper.insert(log);
    }

    private record DbConnectionInfo(String host, int port, String database) {
    }

    private static class ViolationAggregate {
        private int postViolationCount;
        private int commentViolationCount;
        private LocalDateTime lastViolationTime;

        private void addPost(LocalDateTime time) {
            postViolationCount++;
            updateLastTime(time);
        }

        private void addComment(LocalDateTime time) {
            commentViolationCount++;
            updateLastTime(time);
        }

        private int totalCount() {
            return postViolationCount + commentViolationCount;
        }

        private void updateLastTime(LocalDateTime time) {
            if (time == null) {
                return;
            }
            if (lastViolationTime == null || time.isAfter(lastViolationTime)) {
                lastViolationTime = time;
            }
        }
    }
}
