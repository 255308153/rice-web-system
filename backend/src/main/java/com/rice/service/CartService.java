package com.rice.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.rice.dto.CartItemDTO;
import com.rice.entity.Cart;
import com.rice.entity.Product;
import com.rice.mapper.CartMapper;
import com.rice.mapper.ProductMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class CartService {

    @Autowired
    private CartMapper cartMapper;

    @Autowired
    private ProductMapper productMapper;

    public void addToCart(Long userId, Long productId, Integer quantity) {
        int finalQuantity = quantity == null ? 1 : quantity;
        if (finalQuantity <= 0) {
            throw new RuntimeException("商品数量必须大于0");
        }

        Cart cart = cartMapper.selectOne(new LambdaQueryWrapper<Cart>()
                .eq(Cart::getUserId, userId)
                .eq(Cart::getProductId, productId));

        if (cart != null) {
            cart.setQuantity(cart.getQuantity() + finalQuantity);
            cartMapper.updateById(cart);
        } else {
            cart = new Cart();
            cart.setUserId(userId);
            cart.setProductId(productId);
            cart.setQuantity(finalQuantity);
            cartMapper.insert(cart);
        }
    }

    public List<CartItemDTO> getCartList(Long userId) {
        List<Cart> carts = cartMapper.selectList(new LambdaQueryWrapper<Cart>().eq(Cart::getUserId, userId));
        return carts.stream().map(cart -> {
            CartItemDTO dto = new CartItemDTO();
            dto.setId(cart.getId());
            dto.setProductId(cart.getProductId());
            dto.setQuantity(cart.getQuantity());

            Product product = productMapper.selectById(cart.getProductId());
            if (product != null) {
                dto.setProductName(product.getName());
                dto.setPrice(product.getPrice());
                dto.setProductImage(extractFirstImage(product.getImages()));
            }
            return dto;
        }).collect(Collectors.toList());
    }

    private String extractFirstImage(String rawImages) {
        if (!StringUtils.hasText(rawImages)) {
            return "";
        }
        String raw = rawImages.trim();
        if (!StringUtils.hasText(raw)) {
            return "";
        }
        if (raw.startsWith("[")) {
            String cleaned = raw.replace("[", "").replace("]", "").replace("\"", "").trim();
            if (!StringUtils.hasText(cleaned)) {
                return "";
            }
            String[] parts = cleaned.split(",");
            return parts.length > 0 ? parts[0].trim() : "";
        }
        if (raw.contains(",")) {
            return raw.split(",")[0].trim();
        }
        return raw;
    }

    public void updateQuantity(Long userId, Long id, Integer quantity) {
        if (quantity == null || quantity <= 0) {
            throw new RuntimeException("商品数量必须大于0");
        }
        Cart existed = cartMapper.selectOne(new LambdaQueryWrapper<Cart>()
                .eq(Cart::getId, id)
                .eq(Cart::getUserId, userId)
                .last("LIMIT 1"));
        if (existed == null) {
            throw new RuntimeException("购物车商品不存在");
        }

        Cart cart = new Cart();
        cart.setId(id);
        cart.setQuantity(quantity);
        cartMapper.updateById(cart);
    }

    public void deleteCart(Long userId, Long id) {
        cartMapper.delete(new LambdaQueryWrapper<Cart>()
                .eq(Cart::getId, id)
                .eq(Cart::getUserId, userId));
    }

    public void clearCart(Long userId) {
        cartMapper.delete(new LambdaQueryWrapper<Cart>().eq(Cart::getUserId, userId));
    }
}
