package com.match.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.match.dto.AdministratorRequest;
import com.match.dto.AdministratorView;
import com.match.entity.User;
import com.match.mapper.UserMapper;
import com.match.security.UserRole;
import com.match.security.PasswordCodec;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class AdministratorManagementService {
    private final UserMapper userMapper;
    private final PasswordCodec passwordCodec;

    public AdministratorManagementService(UserMapper userMapper, PasswordCodec passwordCodec) {
        this.userMapper = userMapper;
        this.passwordCodec = passwordCodec;
    }

    public List<AdministratorView> list() {
        return userMapper.selectList(Wrappers.<User>lambdaQuery().orderByAsc(User::getUserId))
                .stream()
                .filter(user -> roleOf(user) == UserRole.ADMIN)
                .map(this::toView)
                .collect(Collectors.toList());
    }

    @Transactional
    public AdministratorView create(AdministratorRequest request) {
        validate(request, true);
        User user = new User();
        user.setUserName(request.getUserName().trim());
        user.setPassword(passwordCodec.encode(request.getPassword()));
        user.setEnabled(true);
        user.setMustChangePassword(true);
        user.setIsAdmin(true);
        user.setRole(UserRole.ADMIN.name());
        insert(user);
        return toView(user);
    }

    @Transactional
    public AdministratorView update(Integer userId, AdministratorRequest request) {
        User user = requireAdministrator(userId);
        validate(request, false);
        if (request.getUserName() != null) {
            user.setUserName(request.getUserName().trim());
        }
        if (request.getEnabled() != null) {
            user.setEnabled(request.getEnabled());
        }
        try {
            userMapper.updateById(user);
        } catch (DuplicateKeyException exception) {
            throw new IllegalArgumentException("用户名已存在");
        }
        return toView(user);
    }

    @Transactional
    public AdministratorView resetPassword(Integer userId, AdministratorRequest request) {
        User user = requireAdministrator(userId);
        if (request == null || request.getPassword() == null || request.getPassword().length() < 6) {
            throw new IllegalArgumentException("新密码至少需要 6 个字符");
        }
        user.setPassword(passwordCodec.encode(request.getPassword()));
        user.setMustChangePassword(true);
        userMapper.updateById(user);
        return toView(user);
    }

    private User requireAdministrator(Integer userId) {
        User user = userMapper.selectById(userId);
        if (user == null || roleOf(user) != UserRole.ADMIN) {
            throw new IllegalArgumentException("普通管理员账号不存在");
        }
        return user;
    }

    private void validate(AdministratorRequest request, boolean passwordRequired) {
        if (request == null) {
            throw new IllegalArgumentException("请求内容不能为空");
        }
        if (request.getUserName() != null) {
            String userName = request.getUserName().trim();
            if (userName.isEmpty() || userName.length() > 50) {
                throw new IllegalArgumentException("用户名长度必须为 1 到 50 个字符");
            }
            if ("admin".equalsIgnoreCase(userName)) {
                throw new IllegalArgumentException("不能创建或修改超级管理员账号");
            }
        } else if (passwordRequired) {
            throw new IllegalArgumentException("用户名不能为空");
        }
        if (passwordRequired && (request.getPassword() == null || request.getPassword().length() < 6)) {
            throw new IllegalArgumentException("初始密码至少需要 6 个字符");
        }
    }

    private void insert(User user) {
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException exception) {
            throw new IllegalArgumentException("用户名已存在");
        }
    }

    private UserRole roleOf(User user) {
        return UserRole.resolve(user.getRole(), user.getIsAdmin(), user.getUserName());
    }

    private AdministratorView toView(User user) {
        AdministratorView view = new AdministratorView();
        view.setUserId(user.getUserId());
        view.setUserName(user.getUserName());
        view.setEnabled(user.getEnabled());
        view.setMustChangePassword(user.getMustChangePassword());
        view.setRole(roleOf(user).name());
        return view;
    }
}
