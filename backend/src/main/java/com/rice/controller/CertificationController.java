package com.rice.controller;

import com.rice.common.Result;
import com.rice.entity.Certification;
import com.rice.service.CertificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/certifications")
public class CertificationController {

    @Autowired
    private CertificationService certificationService;

    @PostMapping("/apply")
    public Result<Void> apply(@RequestAttribute("userId") Long userId,
                              @RequestBody Map<String, String> params) {
        certificationService.apply(userId, params.get("role"), params.get("credentials"));
        return Result.success();
    }

    @GetMapping("/my")
    public Result<List<Certification>> myApplications(@RequestAttribute("userId") Long userId,
                                                      @RequestParam(required = false) String role,
                                                      @RequestParam(defaultValue = "20") int limit) {
        return Result.success(certificationService.getMyApplications(userId, role, limit));
    }
}
