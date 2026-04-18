package com.rice.utils;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtUtil {

    // 对称签名密钥（来自 application.yml）
    @Value("${jwt.secret}")
    private String secret;

    // token 过期时长（毫秒）
    @Value("${jwt.expiration}")
    private Long expiration;

    /**
     * 将配置中的字符串密钥转为 JJWT 需要的 SecretKey。
     */
    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 生成 JWT：
     * subject 存 userId，claims 中附加 username/role。
     */
    public String generateToken(Long userId, String username, String role) {
        return Jwts.builder()
                .subject(userId.toString())
                .claim("username", username)
                .claim("role", role)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * 解析并校验 token，成功返回 Claims。
     * 若签名错误、过期、格式非法会抛出异常。
     */
    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    // 读取 subject 作为 userId
    public Long getUserId(String token) {
        return Long.parseLong(parseToken(token).getSubject());
    }

    // 读取自定义 role claim
    public String getRole(String token) {
        Object role = parseToken(token).get("role");
        return role == null ? "" : String.valueOf(role);
    }

    /**
     * 对外提供布尔型校验接口，屏蔽底层异常细节。
     */
    public boolean validateToken(String token) {
        try {
            parseToken(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
