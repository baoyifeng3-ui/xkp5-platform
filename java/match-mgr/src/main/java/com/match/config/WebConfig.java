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
import com.match.security.ParticipantModeGuard;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    private final ParticipantAccessGuard participantAccessGuard;
    private final ParticipantModeGuard participantModeGuard;
    private final String[] allowedOrigins;

    public WebConfig(ParticipantAccessGuard participantAccessGuard,
                     ParticipantModeGuard participantModeGuard,
                     @Value("${match.allowed-origins:http://localhost:19140,http://127.0.0.1:19140,http://127.0.0.1:19146,http://172.16.33.154:19140,http://172.16.33.158:19140}")
                     String allowedOrigins) {
        this.participantAccessGuard = participantAccessGuard;
        this.participantModeGuard = participantModeGuard;
        this.allowedOrigins = java.util.Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toArray(String[]::new);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SaRouteInterceptor((request, response, handler) -> {
            SaRouter.match("/**")
                    .notMatch("/user/login", "/competition", "/health", "/error", "/files/**", "/agent/v1/**",
                            "/terminal/v1/**",
                            "/v2/api-docs/**", "/swagger-resources/**", "/swagger-ui.html")
                    .check(() -> StpUtil.checkLogin())
                    .check(participantModeGuard::requireCurrentGeneration);
            SaRouter.match("/user/training-environments/**")
                    .check(participantModeGuard::requireTrainingMode);
            SaRouter.match("/user/competition-environment/**")
                    .check(participantModeGuard::requireCompetitionMode);
            SaRouter.match("/testPaper/**", "/score/**")
                    .check(participantModeGuard::requireCompetitionMode)
                    .check(participantAccessGuard::requireCompetitionStarted);
            // /train-url 同时被实训模式的“模型验证”页（TrainingValidation.vue）使用，
            // 不再要求比赛模式或比赛阶段；仅保留登录校验。
            SaRouter.match("/admin/training-environments/**",
                            "/super-admin/training-environments/**")
                    .notMatch("/admin/training-environments/class/status")
                    .check(participantModeGuard::requireAdministrativeTrainingMode);
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
