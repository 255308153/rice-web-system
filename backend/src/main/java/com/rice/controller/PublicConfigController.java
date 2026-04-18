package com.rice.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rice.common.Result;
import com.rice.service.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/config")
@RequiredArgsConstructor
public class PublicConfigController {

    private final AdminService adminService;
    private final ObjectMapper objectMapper;

    @GetMapping("/home-carousel")
    public Result<List<Map<String, Object>>> getHomeCarousel() {
        String raw = adminService.getConfigValue("home_carousel");
        if (!StringUtils.hasText(raw)) {
            return Result.success(Collections.emptyList());
        }
        try {
            List<Map<String, Object>> list = objectMapper.readValue(raw, new TypeReference<List<Map<String, Object>>>() {});
            List<Map<String, Object>> normalized = new ArrayList<>();
            for (Map<String, Object> item : list) {
                if (item == null || item.isEmpty()) {
                    continue;
                }
                String imageUrl = text(item.get("imageUrl"));
                if (!StringUtils.hasText(imageUrl)) {
                    imageUrl = text(item.get("url"));
                }
                if (!StringUtils.hasText(imageUrl)) {
                    continue;
                }
                boolean enabled = true;
                Object enabledRaw = item.get("enabled");
                if (enabledRaw instanceof Boolean) {
                    enabled = (Boolean) enabledRaw;
                } else if (enabledRaw != null) {
                    enabled = "1".equals(String.valueOf(enabledRaw)) || "true".equalsIgnoreCase(String.valueOf(enabledRaw));
                }
                if (!enabled) {
                    continue;
                }
                Map<String, Object> one = new LinkedHashMap<>();
                one.put("imageUrl", imageUrl);
                one.put("title", text(item.get("title")));
                one.put("subtitle", text(item.get("subtitle")));
                one.put("link", text(item.get("link")));
                one.put("linkText", text(item.get("linkText")));
                normalized.add(one);
            }
            return Result.success(normalized);
        } catch (Exception e) {
            return Result.success(Collections.emptyList());
        }
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}

