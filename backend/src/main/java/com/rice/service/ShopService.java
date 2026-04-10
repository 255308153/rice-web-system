package com.rice.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.rice.entity.Shop;
import com.rice.mapper.ShopMapper;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ShopService {

    @Autowired
    private ShopMapper shopMapper;

    @Cacheable(cacheNames = "shop:list", key = "T(String).valueOf(#page).concat(':').concat(T(String).valueOf(#size)).concat(':').concat(#keyword == null ? '' : #keyword.trim())")
    public Page<Shop> list(int page, int size, String keyword) {
        LambdaQueryWrapper<Shop> wrapper = new LambdaQueryWrapper<Shop>()
                .eq(Shop::getStatus, 1);
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like(Shop::getName, kw)
                    .or().like(Shop::getDescription, kw)
                    .or().like(Shop::getContact, kw));
        }
        wrapper.orderByDesc(Shop::getRating).orderByDesc(Shop::getId);
        return shopMapper.selectPage(new Page<>(Math.max(page, 1), Math.max(size, 1)), wrapper);
    }

    @Cacheable(cacheNames = "shop:detail", key = "#id")
    public Shop getById(Long id) {
        return shopMapper.selectById(id);
    }

    @CacheEvict(cacheNames = {"shop:list", "shop:detail"}, allEntries = true)
    public void updateById(Shop shop) {
        shopMapper.updateById(shop);
    }

    public Shop getByUserId(Long userId) {
        return shopMapper.selectOne(new LambdaQueryWrapper<Shop>()
                .eq(Shop::getUserId, userId)
                .last("LIMIT 1"));
    }
}
