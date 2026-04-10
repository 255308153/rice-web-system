package com.rice.controller;

import com.rice.common.Result;
import com.rice.dto.CartItemDTO;
import com.rice.entity.Cart;
import com.rice.service.CartService;
import com.rice.utils.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/cart")
public class CartController {

    @Autowired
    private CartService cartService;

    @Autowired
    private JwtUtil jwtUtil;

    @PostMapping("/add")
    public Result<Void> addToCart(@RequestHeader("Authorization") String authHeader,
                                   @RequestBody Map<String, Object> params) {
        Long userId = jwtUtil.getUserId(authHeader.replace("Bearer ", ""));
        Long productId = Long.valueOf(params.get("productId").toString());
        Integer quantity = Integer.valueOf(params.getOrDefault("quantity", 1).toString());
        cartService.addToCart(userId, productId, quantity);
        return Result.success();
    }

    @GetMapping("/list")
    public Result<List<CartItemDTO>> getCartList(@RequestHeader("Authorization") String authHeader) {
        Long userId = jwtUtil.getUserId(authHeader.replace("Bearer ", ""));
        return Result.success(cartService.getCartList(userId));
    }

    @PutMapping("/{id}")
    public Result<Void> updateQuantity(@RequestHeader("Authorization") String authHeader,
                                       @PathVariable Long id,
                                       @RequestBody Map<String, Integer> params) {
        Long userId = jwtUtil.getUserId(authHeader.replace("Bearer ", ""));
        cartService.updateQuantity(userId, id, params.get("quantity"));
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> deleteCart(@RequestHeader("Authorization") String authHeader,
                                   @PathVariable Long id) {
        Long userId = jwtUtil.getUserId(authHeader.replace("Bearer ", ""));
        cartService.deleteCart(userId, id);
        return Result.success();
    }

    @DeleteMapping("/clear")
    public Result<Void> clearCart(@RequestHeader("Authorization") String authHeader) {
        Long userId = jwtUtil.getUserId(authHeader.replace("Bearer ", ""));
        cartService.clearCart(userId);
        return Result.success();
    }
}
