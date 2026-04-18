package com.rice.ws;

import com.rice.utils.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class JwtWsHandshakeInterceptor implements HandshakeInterceptor {

    private final JwtUtil jwtUtil;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request,
                                   ServerHttpResponse response,
                                   WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) {
        String token = resolveToken(request);
        if (!StringUtils.hasText(token) || !jwtUtil.validateToken(token)) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

        Long userId = jwtUtil.getUserId(token);
        String role = jwtUtil.getRole(token);
        if (userId == null) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

        attributes.put("userId", userId);
        attributes.put("role", role);
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request,
                               ServerHttpResponse response,
                               WebSocketHandler wsHandler,
                               Exception exception) {
        // no-op
    }

    private String resolveToken(ServerHttpRequest request) {
        MultiValueMap<String, String> queryParams = UriComponentsBuilder.fromUri(request.getURI())
                .build()
                .getQueryParams();
        String queryToken = queryParams.getFirst("token");
        if (StringUtils.hasText(queryToken)) {
            return queryToken.trim();
        }

        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7).trim();
        }
        return null;
    }
}
