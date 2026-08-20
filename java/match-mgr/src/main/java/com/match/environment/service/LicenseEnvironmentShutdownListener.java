package com.match.environment.service;

import com.match.environment.persistence.TrainingEnvironmentMapper;
import com.match.environment.persistence.TrainingEnvironmentRecord;
import com.match.licensing.model.LicenseState;
import com.match.licensing.service.LicenseStateChangedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class LicenseEnvironmentShutdownListener {
    private final TrainingEnvironmentMapper environmentMapper;
    private final EnvironmentOperationService operationService;

    public LicenseEnvironmentShutdownListener(TrainingEnvironmentMapper environmentMapper,
                                              EnvironmentOperationService operationService) {
        this.environmentMapper = environmentMapper;
        this.operationService = operationService;
    }

    @EventListener
    public void onLicenseStateChanged(LicenseStateChangedEvent event) {
        if (event == null || usable(event.getCurrentState())) {
            return;
        }
        List<TrainingEnvironmentRecord> environments = environmentMapper.selectRequiringLicenseStop();
        if (environments == null) {
            return;
        }
        for (TrainingEnvironmentRecord environment : environments) {
            operationService.stop(environment.getEnvironmentId(), 0, "SYSTEM");
        }
    }

    private boolean usable(LicenseState state) {
        return state == LicenseState.ACTIVE || state == LicenseState.EXPIRING;
    }
}
