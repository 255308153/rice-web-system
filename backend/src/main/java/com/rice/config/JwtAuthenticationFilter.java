package com.rice.config;

import com.rice.utils.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Autowired
    private JwtUtil jwtUtil;

    /**
     * 每个请求只执行一次的 JWT 鉴权过滤器。
     * 执行目标：
     * 1) 从 Authorization 头提取 Bearer Token
     * 2) 校验 token 合法性
     * 3) 将 userId/role 封装进 Spring Security 上下文
     * 4) 供后续 @RequestAttribute / 权限判断使用
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // 标准 JWT 头格式：Authorization: Bearer xxx.yyy.zzz
        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);

            try {
                if (jwtUtil.validateToken(token)) {
                    // 从 token 中解析业务身份信息
                    Long userId = jwtUtil.getUserId(token);
                    String role = jwtUtil.getRole(token);

                    // Spring Security 的角色前缀规范是 ROLE_*
                    List<SimpleGrantedAuthority> authorities = StringUtils.hasText(role)
                            ? List.of(new SimpleGrantedAuthority("ROLE_" + role.trim().toUpperCase()))
                            : List.of();

                    // 将认证信息写入上下文：后续接口可视为“已登录”
                    UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(userId, null, authorities);
                    SecurityContextHolder.getContext().setAuthentication(authentication);

                    // 额外写入 request attribute，便于 Controller 直接取 userId/role
                    request.setAttribute("userId", userId);
                    request.setAttribute("role", role);
                }
            } catch (Exception e) {
                // Token 无效时不抛出，交给后续安全链路处理（通常会被判定为未认证）
            }
        }

        // 必须继续放行到后续过滤器与 Controller
        filterChain.doFilter(request, response);
    }

    /**
     * AI 识别等接口使用 CompletableFuture 返回时会触发 ASYNC 二次分发，
     * 这里保持过滤器在异步分发阶段也生效，避免出现“首段请求已认证、异步分发未认证”的 403。
     */
    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false;
    }

    /**
     * 错误分发阶段同样执行过滤器，保证 /error 分支下也能拿到认证上下文。
     */
    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return false;
    }
}
