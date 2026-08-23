package com.match.security;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.match.entity.User;
import com.match.service.impl.UserServiceImpl;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("local")
public class DevelopmentBusinessAdminSeed implements ApplicationRunner {
    private final UserServiceImpl userService;
    private final String userName;
    private final String password;

    public DevelopmentBusinessAdminSeed(UserServiceImpl userService,
                                        @Value("${MATCH_DEV_BUSINESS_ADMIN_USERNAME:admin}") String userName,
                                        @Value("${MATCH_DEV_BUSINESS_ADMIN_PASSWORD:admin}") String password) {
        this.userService = userService;
        this.userName = userName;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (userName.trim().isEmpty() || password.isEmpty()) return;
        User user = userService.getOne(Wrappers.<User>lambdaQuery().eq(User::getUserName, userName));
        if (user == null) { user = new User(); user.setUserName(userName); }
        user.setPassword(password); user.setEnabled(true); user.setMustChangePassword(false);
        user.setIsAdmin(true); user.setRole("ADMIN"); userService.saveOrUpdate(user);
    }
}
