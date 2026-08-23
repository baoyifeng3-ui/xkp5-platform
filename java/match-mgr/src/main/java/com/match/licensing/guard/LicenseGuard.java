package com.match.licensing.guard;

import com.match.licensing.model.LicenseStatus;
import com.match.licensing.service.LicenseStatusService;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import com.match.licensing.model.LicenseState;
import com.match.licensing.model.LicenseStatus;

@Component
public class LicenseGuard {
    private final LicenseStatusService statusService;
    private final Environment environment;
    private final boolean localBypass;

    public LicenseGuard(LicenseStatusService statusService) {
        this(statusService, new org.springframework.core.env.StandardEnvironment(), false);
    }

    @Autowired
    public LicenseGuard(LicenseStatusService statusService, Environment environment,
                        @Value("${MATCH_LOCAL_LICENSE_BYPASS:false}") boolean localBypass) {
        this.statusService = statusService;
        this.environment = environment;
        this.localBypass = localBypass;
    }

    public LicenseStatus requireActive() {
        if (localBypass && java.util.Arrays.asList(environment.getActiveProfiles()).contains("local")) {
            return new LicenseStatus(LicenseState.ACTIVE, "LOCAL-DEVELOPMENT", "本地开发授权", java.time.Instant.now().plusSeconds(86400), 99);
        }
        LicenseStatus status = statusService.currentStatus();
        if (!status.isUsable()) {
            throw new LicenseAccessException(status.getState().name(), "平台授权未生效，无法执行此操作");
        }
        return status;
    }

    public void requireProcessingServerCapacity(int resultingServerCount) {
        if (resultingServerCount < 0) {
            throw new IllegalArgumentException("处理服务器数量不能为负数");
        }
        LicenseStatus status = requireActive();
        Integer limit = status.getMaxProcessingServers();
        if (limit != null && resultingServerCount > limit) {
            throw new LicenseAccessException("SERVER_LIMIT_EXCEEDED", "处理服务器数量超过授权上限");
        }
    }
}
