package com.match.security;

import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.session.SaSession;
import org.springframework.stereotype.Component;

@Component
public class LoginSession {
    public int loginId() {
        StpUtil.checkLogin();
        return StpUtil.getLoginIdAsInt();
    }

    public SaSession tokenSession() {
        return StpUtil.getTokenSession();
    }

    public void logout() {
        StpUtil.logout();
    }
}
