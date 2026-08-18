package com.match.config;

import cn.dev33.satoken.interceptor.SaRouteInterceptor;
import cn.dev33.satoken.router.SaRouter;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import com.match.security.ParticipantAccessGuard;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    private final ParticipantAccessGuard participantAccessGuard;
    private final String[] allowedOrigins;

    public WebConfig(ParticipantAccessGuard participantAccessGuard,
                     @Value("${match.allowed-origins:http://localhost:19140,http://127.0.0.1:19140,http://127.0.0.1:19146,http://172.16.33.154:19140,http://172.16.33.158:19140}")
                     String allowedOrigins) {
        this.participantAccessGuard = participantAccessGuard;
        this.allowedOrigins = java.util.Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toArray(String[]::new);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SaRouteInterceptor((request, response, handler) -> {
            SaRouter.match("/**")
                    .notMatch("/user/login", "/competition", "/health", "/error", "/agent/v1/**",
                            "/v2/api-docs/**", "/swagger-resources/**", "/swagger-ui.html")
                    .check(() -> StpUtil.checkLogin());
            SaRouter.match("/testPaper/**").check(participantAccessGuard::requireCompetitionStarted);
            SaRouter.match("/score/**").check(participantAccessGuard::requireCompetitionStarted);
            SaRouter.match("/train-url/**").check(participantAccessGuard::requireCompetitionStarted);
        }))
                .addPathPatterns("/**");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "OPTIONS", "DELETE", "PATCH")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
