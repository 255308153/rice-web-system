package com.rice.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.rice.entity.User;
import com.rice.mapper.UserMapper;
import com.rice.utils.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class UserService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtil jwtUtil;

    public String register(String username, String password, String phone) {
        String normalizedUsername = username == null ? null : username.trim();
        String normalizedPhone = normalizePhone(phone);

        if (!StringUtils.hasText(normalizedUsername)) {
            throw new RuntimeException("用户名不能为空");
        }
        if (!StringUtils.hasText(password)) {
            throw new RuntimeException("密码不能为空");
        }

        // 检查用户名是否存在
        if (userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, normalizedUsername)) != null) {
            throw new RuntimeException("用户名已存在");
        }
        if (normalizedPhone != null
                && userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getPhone, normalizedPhone)) != null) {
            throw new RuntimeException("手机号已存在");
        }

        User user = new User();
        user.setUsername(normalizedUsername);
        user.setPassword(passwordEncoder.encode(password));
        user.setPhone(normalizedPhone);
        user.setRole("USER");
        user.setStatus(1);

        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException e) {
            throw new RuntimeException("用户名或手机号已存在");
        }

        return jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole());
    }

    public String login(String username, String password) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        if (user == null || !passwordEncoder.matches(password, user.getPassword())) {
            throw new RuntimeException("用户名或密码错误");
        }
        if (user.getStatus() == 0) {
            throw new RuntimeException("账号已被禁用");
        }

        return jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole());
    }

    public User getUserById(Long id) {
        return userMapper.selectById(id);
    }

    public void updateUser(Long userId, User payload) {
        User existing = userMapper.selectById(userId);
        if (existing == null) {
            throw new RuntimeException("用户不存在");
        }

        String normalizedPhone = normalizePhone(payload == null ? null : payload.getPhone());
        if (normalizedPhone != null) {
            User samePhoneUser = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getPhone, normalizedPhone));
            if (samePhoneUser != null && !samePhoneUser.getId().equals(userId)) {
                throw new RuntimeException("手机号已存在");
            }
        }

        User update = new User();
        update.setId(userId);
        update.setPhone(normalizedPhone);
        update.setAvatar(payload == null ? null : payload.getAvatar());
        userMapper.updateById(update);
    }

    private String normalizePhone(String phone) {
        if (!StringUtils.hasText(phone)) {
            return null;
        }
        return phone.trim();
    }
}
