package com.rice.controller;

import com.rice.common.Result;
import com.rice.entity.SystemNotification;
import com.rice.service.AdminService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/notices")
public class NoticeController {

    @Autowired
    private AdminService adminService;

    @GetMapping
    public Result<List<SystemNotification>> getNoticeList(@RequestAttribute("userId") Long userId,
                                                          @RequestParam(defaultValue = "100") int limit) {
        return Result.success(adminService.getNoticeList(userId, limit));
    }
}

