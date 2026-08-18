package com.match.licensing.guard;

import com.match.licensing.model.LicenseStatus;
import com.match.licensing.service.LicenseStatusService;
import org.springframework.stereotype.Component;

@Component
public class LicenseGuard {
    private final LicenseStatusService statusService;

    public LicenseGuard(LicenseStatusService statusService) {
        this.statusService = statusService;
    }

    public LicenseStatus requireActive() {
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
