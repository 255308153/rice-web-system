package com.rice.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.rice.common.Result;
import com.rice.entity.Comment;
import com.rice.entity.Post;
import com.rice.service.FileUploadService;
import com.rice.service.PostService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
public class PostController {
    private final PostService postService;
    private final FileUploadService fileUploadService;

    @PostMapping
    public Result<Post> createPost(@RequestBody Post post, @RequestAttribute Long userId) {
        post.setUserId(userId);
        return Result.success(postService.createPost(post));
    }

    @PostMapping("/upload-image")
    public Result<Map<String, String>> uploadImage(@RequestParam("image") MultipartFile image) {
        try {
            if (image == null || image.isEmpty()) {
                return Result.error("请选择图片");
            }
            if (image.getSize() > 5 * 1024 * 1024) {
                return Result.error("图片大小不能超过5MB");
            }
            String contentType = image.getContentType();
            if (contentType == null || (!contentType.startsWith("image/"))) {
                return Result.error("仅支持图片文件");
            }
            String imageUrl = fileUploadService.upload(image);
            Map<String, String> data = new HashMap<>();
            data.put("url", imageUrl);
            return Result.success(data);
        } catch (Exception e) {
            return Result.error("上传失败：" + e.getMessage());
        }
    }

    @GetMapping
    public Result<Page<Post>> listPosts(@RequestParam(defaultValue = "1") int page,
                                        @RequestParam(defaultValue = "10") int size,
                                        @RequestParam(defaultValue = "time") String sortBy,
                                        @RequestParam(required = false) String keyword,
                                        @RequestParam(required = false) String category,
                                        @RequestAttribute Long userId) {
        return Result.success(postService.listPosts(userId, page, size, sortBy, keyword, category));
    }

    @GetMapping("/categories")
    public Result<java.util.List<String>> listForumCategories() {
        return Result.success(postService.listForumCategories());
    }

    @GetMapping("/my")
    public Result<Page<Post>> listMyPosts(@RequestAttribute Long userId,
                                          @RequestParam(defaultValue = "1") int page,
                                          @RequestParam(defaultValue = "10") int size) {
        return Result.success(postService.listMyPosts(userId, page, size));
    }

    @GetMapping("/favorites")
    public Result<Page<Post>> listMyFavorites(@RequestAttribute Long userId,
                                              @RequestParam(defaultValue = "1") int page,
                                              @RequestParam(defaultValue = "10") int size) {
        return Result.success(postService.listFavoritePosts(userId, page, size));
    }

    @GetMapping("/my/comments")
    public Result<Page<Comment>> listMyComments(@RequestAttribute Long userId,
                                                @RequestParam(defaultValue = "1") int page,
                                                @RequestParam(defaultValue = "10") int size) {
        return Result.success(postService.listMyComments(userId, page, size));
    }

    @GetMapping("/{id}")
    public Result<Post> getPost(@PathVariable Long id, @RequestAttribute Long userId) {
        return Result.success(postService.getPostDetail(id, userId));
    }

    @PostMapping("/{id}/comment")
    public Result<Void> addComment(@PathVariable Long id, @RequestBody Comment comment,
                                    @RequestAttribute Long userId) {
        comment.setPostId(id);
        comment.setUserId(userId);
        postService.addComment(comment);
        return Result.success();
    }

    @GetMapping("/{id}/comments")
    public Result<Page<Comment>> listComments(@PathVariable Long id,
                                               @RequestParam(defaultValue = "1") int page,
                                               @RequestParam(defaultValue = "20") int size) {
        return Result.success(postService.listComments(id, page, size));
    }

    @PostMapping("/{id}/like")
    public Result<Boolean> toggleLike(@PathVariable Long id, @RequestAttribute Long userId) {
        return Result.success(postService.toggleLike(userId, id, "POST"));
    }

    @PostMapping("/{id}/favorite")
    public Result<Boolean> toggleFavorite(@PathVariable Long id, @RequestAttribute Long userId) {
        return Result.success(postService.toggleFavorite(userId, id));
    }
}
