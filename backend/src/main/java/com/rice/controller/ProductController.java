package com.rice.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.rice.common.Result;
import com.rice.entity.Product;
import com.rice.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {
    private final ProductService productService;

    @GetMapping
    public Result<Page<Product>> list(
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "10") int size,
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) Long shopId
    ) {
        return Result.success(productService.list(page, size, keyword, shopId));
    }

    @GetMapping("/recommendations")
    public Result<Page<Product>> recommend(@RequestAttribute("userId") Long userId,
                                           @RequestParam(defaultValue = "1") int page,
                                           @RequestParam(defaultValue = "10") int size) {
        return Result.success(productService.recommend(userId, page, size));
    }

    @GetMapping("/{id}")
    public Result<Product> getById(@PathVariable Long id) {
        return Result.success(productService.getById(id));
    }
}
