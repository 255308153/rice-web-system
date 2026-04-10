package com.rice.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rice.entity.*;
import com.rice.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PostService {
    private static final int POST_STATUS_ABNORMAL = 0;
    private static final int POST_STATUS_NORMAL = 1;
    private static final int POST_STATUS_DOWN = 2;

    private final PostMapper postMapper;
    private final CommentMapper commentMapper;
    private final LikeRecordMapper likeRecordMapper;
    private final FavoriteMapper favoriteMapper;
    private final UserMapper userMapper;
    private final SystemConfigMapper systemConfigMapper;
    private final ChatService chatService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public Post createPost(Post post) {
        if (!StringUtils.hasText(post.getTitle())) {
            throw new RuntimeException("标题不能为空");
        }
        if (!StringUtils.hasText(post.getContent())) {
            throw new RuntimeException("内容不能为空");
        }
        post.setCreateTime(LocalDateTime.now());
        post.setViews(0);
        post.setLikes(0);
        post.setCategory(StringUtils.hasText(post.getCategory()) ? post.getCategory().trim() : "综合交流");

        Map<String, Object> auditResult = chatService.reviewPostContent(post.getUserId(), Map.of(
                "title", trimText(post.getTitle(), 200),
                "content", trimText(post.getContent(), 4000),
                "category", post.getCategory(),
                "images", trimText(post.getImages(), 1000)
        ));
        boolean violation = Boolean.TRUE.equals(auditResult.get("violation"));
        String reason = String.valueOf(auditResult.getOrDefault("reason", violation ? "AI预审核判定存在风险" : ""));
        post.setStatus(violation ? POST_STATUS_ABNORMAL : POST_STATUS_NORMAL);
        post.setAuditRemark(violation && StringUtils.hasText(reason) ? trimText(reason, 255) : null);
        postMapper.insert(post);
        return post;
    }

    public Page<Post> listPosts(Long userId, int page, int size, String sortBy, String keyword, String category) {
        LambdaQueryWrapper<Post> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Post::getStatus, POST_STATUS_NORMAL);
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like(Post::getTitle, kw).or().like(Post::getContent, kw));
        }
        if (StringUtils.hasText(category)) {
            wrapper.eq(Post::getCategory, category.trim());
        }

        if ("hot".equalsIgnoreCase(sortBy)) {
            wrapper.orderByDesc(Post::getLikes).orderByDesc(Post::getViews).orderByDesc(Post::getCreateTime);
        } else {
            wrapper.orderByDesc(Post::getCreateTime);
        }

        Page<Post> postPage = postMapper.selectPage(new Page<>(Math.max(page, 1), Math.max(size, 1)), wrapper);
        fillPostExtra(postPage.getRecords(), userId);
        return postPage;
    }

    @Transactional
    public Post getPostDetail(Long id, Long userId) {
        Post post = postMapper.selectById(id);
        if (post == null) {
            throw new RuntimeException("帖子不存在");
        }
        boolean isOwner = userId != null && Objects.equals(post.getUserId(), userId);
        if (!Objects.equals(post.getStatus(), POST_STATUS_NORMAL) && !isOwner) {
            if (Objects.equals(post.getStatus(), POST_STATUS_ABNORMAL)) {
                throw new RuntimeException("帖子正在等待审核");
            }
            throw new RuntimeException("帖子已下架");
        }

        if (Objects.equals(post.getStatus(), POST_STATUS_NORMAL)) {
            int views = post.getViews() == null ? 0 : post.getViews();
            post.setViews(views + 1);
            postMapper.updateById(post);
        }
        fillPostExtra(Collections.singletonList(post), userId);
        return post;
    }

    public void addComment(Comment comment) {
        if (!StringUtils.hasText(comment.getContent())) {
            throw new RuntimeException("评论内容不能为空");
        }
        Post post = postMapper.selectById(comment.getPostId());
        if (post == null) {
            throw new RuntimeException("帖子不存在");
        }
        if (!Objects.equals(post.getStatus(), POST_STATUS_NORMAL)) {
            throw new RuntimeException("帖子已下架，无法评论");
        }
        comment.setCreateTime(LocalDateTime.now());
        comment.setStatus(1);
        comment.setAuditRemark(null);
        commentMapper.insert(comment);
    }

    public Page<Comment> listComments(Long postId, int page, int size) {
        Page<Comment> commentPage = commentMapper.selectPage(new Page<>(Math.max(page, 1), Math.max(size, 1)),
            new LambdaQueryWrapper<Comment>()
                .eq(Comment::getPostId, postId)
                .eq(Comment::getStatus, 1)
                .orderByAsc(Comment::getCreateTime));
        fillCommentExtra(commentPage.getRecords(), false);
        return commentPage;
    }

    @Transactional
    public boolean toggleLike(Long userId, Long targetId, String targetType) {
        if (!"POST".equals(targetType)) {
            throw new RuntimeException("仅支持帖子点赞");
        }
        Post post = postMapper.selectById(targetId);
        if (post == null) {
            throw new RuntimeException("帖子不存在");
        }
        if (!Objects.equals(post.getStatus(), POST_STATUS_NORMAL)) {
            throw new RuntimeException("帖子已下架");
        }

        LikeRecord record = likeRecordMapper.selectOne(
            new LambdaQueryWrapper<LikeRecord>()
                .eq(LikeRecord::getUserId, userId)
                .eq(LikeRecord::getTargetId, targetId)
                .eq(LikeRecord::getTargetType, targetType));

        if (record != null) {
            likeRecordMapper.deleteById(record.getId());
            int likes = post.getLikes() == null ? 0 : post.getLikes();
            post.setLikes(Math.max(0, likes - 1));
            postMapper.updateById(post);
            return false;
        } else {
            record = new LikeRecord();
            record.setUserId(userId);
            record.setTargetId(targetId);
            record.setTargetType(targetType);
            likeRecordMapper.insert(record);
            int likes = post.getLikes() == null ? 0 : post.getLikes();
            post.setLikes(likes + 1);
            postMapper.updateById(post);
            return true;
        }
    }

    @Transactional
    public boolean toggleFavorite(Long userId, Long postId) {
        Post post = postMapper.selectById(postId);
        if (post == null) {
            throw new RuntimeException("帖子不存在");
        }
        if (!Objects.equals(post.getStatus(), POST_STATUS_NORMAL)) {
            throw new RuntimeException("帖子已下架");
        }

        Favorite favorite = favoriteMapper.selectOne(
            new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getUserId, userId)
                .eq(Favorite::getPostId, postId));

        if (favorite != null) {
            favoriteMapper.deleteById(favorite.getId());
            return false;
        } else {
            favorite = new Favorite();
            favorite.setUserId(userId);
            favorite.setPostId(postId);
            favoriteMapper.insert(favorite);
            return true;
        }
    }

    public Page<Post> listMyPosts(Long userId, int page, int size) {
        Page<Post> postPage = postMapper.selectPage(new Page<>(Math.max(page, 1), Math.max(size, 1)),
                new LambdaQueryWrapper<Post>()
                        .eq(Post::getUserId, userId)
                        .orderByDesc(Post::getCreateTime));
        fillPostExtra(postPage.getRecords(), userId);
        return postPage;
    }

    public Page<Comment> listMyComments(Long userId, int page, int size) {
        Page<Comment> commentPage = commentMapper.selectPage(new Page<>(Math.max(page, 1), Math.max(size, 1)),
                new LambdaQueryWrapper<Comment>()
                        .eq(Comment::getUserId, userId)
                        .orderByDesc(Comment::getCreateTime));
        fillCommentExtra(commentPage.getRecords(), true);
        return commentPage;
    }

    public Page<Post> listFavoritePosts(Long userId, int page, int size) {
        Page<Favorite> favoritePage = favoriteMapper.selectPage(new Page<>(Math.max(page, 1), Math.max(size, 1)),
                new LambdaQueryWrapper<Favorite>()
                        .eq(Favorite::getUserId, userId)
                        .orderByDesc(Favorite::getCreateTime));

        List<Long> postIds = favoritePage.getRecords().stream()
                .map(Favorite::getPostId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        List<Post> posts = new ArrayList<>();
        if (!postIds.isEmpty()) {
            Map<Long, Post> postMap = postMapper.selectBatchIds(postIds).stream()
                    .collect(Collectors.toMap(Post::getId, Function.identity(), (a, b) -> a));
            for (Long postId : postIds) {
                Post post = postMap.get(postId);
                if (post != null && Objects.equals(post.getStatus(), POST_STATUS_NORMAL)) {
                    posts.add(post);
                }
            }
            fillPostExtra(posts, userId);
        }

        Page<Post> result = new Page<>(favoritePage.getCurrent(), favoritePage.getSize(), favoritePage.getTotal());
        result.setRecords(posts);
        return result;
    }

    private void fillPostExtra(List<Post> posts, Long userId) {
        if (posts == null || posts.isEmpty()) {
            return;
        }

        Set<Long> postIds = posts.stream().map(Post::getId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> authorIds = posts.stream().map(Post::getUserId).filter(Objects::nonNull).collect(Collectors.toSet());

        Map<Long, User> authorMap = Collections.emptyMap();
        if (!authorIds.isEmpty()) {
            authorMap = userMapper.selectBatchIds(authorIds).stream()
                    .collect(Collectors.toMap(User::getId, Function.identity(), (a, b) -> a));
        }

        Map<Long, Integer> commentCountMap = new HashMap<>();
        if (!postIds.isEmpty()) {
            List<Comment> comments = commentMapper.selectList(new LambdaQueryWrapper<Comment>()
                    .in(Comment::getPostId, postIds)
                    .eq(Comment::getStatus, 1)
                    .select(Comment::getPostId));
            for (Comment comment : comments) {
                Long pid = comment.getPostId();
                commentCountMap.put(pid, commentCountMap.getOrDefault(pid, 0) + 1);
            }
        }

        Set<Long> likedPostIds = new HashSet<>();
        Set<Long> favoritePostIds = new HashSet<>();
        if (userId != null && !postIds.isEmpty()) {
            List<LikeRecord> likeRecords = likeRecordMapper.selectList(new LambdaQueryWrapper<LikeRecord>()
                    .eq(LikeRecord::getUserId, userId)
                    .eq(LikeRecord::getTargetType, "POST")
                    .in(LikeRecord::getTargetId, postIds));
            likedPostIds = likeRecords.stream().map(LikeRecord::getTargetId).collect(Collectors.toSet());

            List<Favorite> favorites = favoriteMapper.selectList(new LambdaQueryWrapper<Favorite>()
                    .eq(Favorite::getUserId, userId)
                    .in(Favorite::getPostId, postIds));
            favoritePostIds = favorites.stream().map(Favorite::getPostId).collect(Collectors.toSet());
        }

        for (Post post : posts) {
            if (post.getViews() == null) {
                post.setViews(0);
            }
            if (post.getLikes() == null) {
                post.setLikes(0);
            }
            if (post.getStatus() == null) {
                post.setStatus(POST_STATUS_NORMAL);
            }

            User author = authorMap.get(post.getUserId());
            if (author != null) {
                post.setUsername(author.getUsername());
                post.setUserRole(author.getRole());
            }

            post.setCommentCount(commentCountMap.getOrDefault(post.getId(), 0));
            post.setLiked(likedPostIds.contains(post.getId()));
            post.setFavorited(favoritePostIds.contains(post.getId()));
        }
    }

    private void fillCommentExtra(List<Comment> comments, boolean includePostTitle) {
        if (comments == null || comments.isEmpty()) {
            return;
        }

        Set<Long> userIds = comments.stream().map(Comment::getUserId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, User> userMap = Collections.emptyMap();
        if (!userIds.isEmpty()) {
            userMap = userMapper.selectBatchIds(userIds).stream()
                    .collect(Collectors.toMap(User::getId, Function.identity(), (a, b) -> a));
        }

        Map<Long, Post> postMap = Collections.emptyMap();
        if (includePostTitle) {
            Set<Long> postIds = comments.stream().map(Comment::getPostId).filter(Objects::nonNull).collect(Collectors.toSet());
            if (!postIds.isEmpty()) {
                postMap = postMapper.selectBatchIds(postIds).stream()
                        .collect(Collectors.toMap(Post::getId, Function.identity(), (a, b) -> a));
            }
        }

        for (Comment comment : comments) {
            User user = userMap.get(comment.getUserId());
            if (user != null) {
                comment.setUsername(user.getUsername());
                comment.setUserRole(user.getRole());
            }

            if (includePostTitle) {
                Post post = postMap.get(comment.getPostId());
                if (post != null) {
                    comment.setPostTitle(post.getTitle());
                }
            }
        }
    }

    public List<String> listForumCategories() {
        SystemConfig config = systemConfigMapper.selectOne(new LambdaQueryWrapper<SystemConfig>()
                .eq(SystemConfig::getConfigKey, "forum_categories")
                .last("LIMIT 1"));
        if (config == null || !StringUtils.hasText(config.getConfigValue())) {
            return defaultForumCategories();
        }
        try {
            JsonNode node = objectMapper.readTree(config.getConfigValue());
            if (node.isArray()) {
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
            }
        } catch (Exception ignored) {
        }
        return defaultForumCategories();
    }

    private List<String> defaultForumCategories() {
        return Arrays.asList("综合交流", "种植经验", "病虫害防治", "市场行情", "政策资讯");
    }

    private String trimText(String text, int maxLen) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String normalized = text.trim();
        if (normalized.length() <= maxLen) {
            return normalized;
        }
        return normalized.substring(0, maxLen);
    }
}
