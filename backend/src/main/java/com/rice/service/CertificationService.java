package com.rice.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.rice.entity.Certification;
import com.rice.entity.User;
import com.rice.mapper.CertificationMapper;
import com.rice.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class CertificationService {

    @Autowired
    private CertificationMapper certificationMapper;

    @Autowired
    private UserMapper userMapper;

    public void apply(Long userId, String role, String credentials) {
        String normalizedRole = normalizeRole(role);
        if (!StringUtils.hasText(credentials)) {
            throw new RuntimeException("请填写资质说明");
        }

        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        if ("ADMIN".equals(user.getRole())) {
            throw new RuntimeException("管理员无需申请角色认证");
        }
        if (normalizedRole.equals(user.getRole())) {
            throw new RuntimeException("当前账号已是该角色，无需重复申请");
        }

        Certification pending = certificationMapper.selectOne(new LambdaQueryWrapper<Certification>()
                .eq(Certification::getUserId, userId)
                .eq(Certification::getRole, normalizedRole)
                .eq(Certification::getStatus, 0)
                .last("LIMIT 1"));
        if (pending != null) {
            throw new RuntimeException("已存在待审核申请，请勿重复提交");
        }

        Certification cert = new Certification();
        cert.setUserId(userId);
        cert.setRole(normalizedRole);
        cert.setCredentials(credentials.trim());
        cert.setStatus(0);
        certificationMapper.insert(cert);
    }

    public List<Certification> getMyApplications(Long userId, String role, int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 100);
        LambdaQueryWrapper<Certification> wrapper = new LambdaQueryWrapper<Certification>()
                .eq(Certification::getUserId, userId)
                .orderByDesc(Certification::getId)
                .last("LIMIT " + safeLimit);
        if (StringUtils.hasText(role)) {
            wrapper.eq(Certification::getRole, normalizeRole(role));
        }
        return certificationMapper.selectList(wrapper);
    }

    private String normalizeRole(String role) {
        if (!StringUtils.hasText(role)) {
            throw new RuntimeException("申请角色不能为空");
        }
        String normalized = role.trim().toUpperCase();
        if (!"MERCHANT".equals(normalized) && !"EXPERT".equals(normalized)) {
            throw new RuntimeException("仅支持申请 MERCHANT 或 EXPERT");
        }
        return normalized;
    }
}
