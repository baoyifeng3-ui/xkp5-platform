package com.match.security;

import cn.dev33.satoken.stp.StpUtil;
import org.springframework.stereotype.Component;

@Component
public class LoginSession {
    public int loginId() {
        StpUtil.checkLogin();
        return StpUtil.getLoginIdAsInt();
    }
}
