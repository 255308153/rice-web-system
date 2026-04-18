package com.rice.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Autowired
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    /**
     * Spring Security 统一使用 PasswordEncoder 进行密码处理。
     * 这里采用 BCrypt（含随机盐），用于登录比对和密码加密存储。
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 核心安全链路配置：
     * 1) 关闭 CSRF（前后端分离 + JWT 场景通常关闭）
     * 2) 开启 CORS
     * 3) 使用无状态会话（STATELESS），不依赖 HttpSession
     * 4) 配置 URL 级别权限
     * 5) 在用户名密码过滤器前注入 JWT 过滤器，提前完成 token 鉴权
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // 登录/注册接口放行
                .requestMatchers("/api/auth/**").permitAll()
                // AI 健康检查放行（用于联调探活）
                .requestMatchers("/api/ai/chat/health").permitAll()
                .requestMatchers("/api/ai/recognition/health").permitAll()
                // 允许错误分发路径返回真实错误信息，避免被安全链再次拦截成 403 空响应
                .requestMatchers("/error").permitAll()
                // 静态上传资源放行
                .requestMatchers("/uploads/images/**").permitAll()
                // WebSocket 握手放行（连接鉴权由握手拦截器处理）
                .requestMatchers("/ws/**").permitAll()
                // 管理员接口必须 ADMIN 角色
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                // 商户接口必须 MERCHANT 角色
                .requestMatchers("/api/merchant/**").hasRole("MERCHANT")
                // 其余接口默认需要登录
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /**
     * CORS 配置：当前为全放开，适合本地联调与多前端端口开发。
     * 生产环境建议按域名白名单、方法、请求头进行收敛。
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.asList("*"));
        configuration.setAllowedMethods(Arrays.asList("*"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
