package com.rice.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.rice.common.Result;
import com.rice.entity.Shop;
import com.rice.service.ShopService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/shops")
public class ShopController {

    @Autowired
    private ShopService shopService;

    @GetMapping
    public Result<Page<Shop>> listShops(@RequestParam(defaultValue = "1") int page,
                                        @RequestParam(defaultValue = "10") int size,
                                        @RequestParam(required = false) String keyword) {
        return Result.success(shopService.list(page, size, keyword));
    }

    @GetMapping("/{id}")
    public Result<Shop> getShop(@PathVariable Long id) {
        return Result.success(shopService.getById(id));
    }

    @PutMapping("/{id}")
    public Result<Void> updateShop(@PathVariable Long id, @RequestBody Shop shop) {
        shop.setId(id);
        shopService.updateById(shop);
        return Result.success();
    }

    @GetMapping("/user/{userId}")
    public Result<Shop> getShopByUser(@PathVariable Long userId) {
        return Result.success(shopService.getByUserId(userId));
    }
}
