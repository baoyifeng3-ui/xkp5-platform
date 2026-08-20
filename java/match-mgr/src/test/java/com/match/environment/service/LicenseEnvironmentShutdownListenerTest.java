package com.match.environment.service;

import com.match.environment.persistence.TrainingEnvironmentMapper;
import com.match.environment.persistence.TrainingEnvironmentRecord;
import com.match.licensing.model.LicenseState;
import com.match.licensing.service.LicenseStateChangedEvent;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class LicenseEnvironmentShutdownListenerTest {
    private TrainingEnvironmentMapper environments;
    private EnvironmentOperationService operations;
    private LicenseEnvironmentShutdownListener listener;

    @Before
    public void setUp() {
        environments = mock(TrainingEnvironmentMapper.class);
        operations = mock(EnvironmentOperationService.class);
        listener = new LicenseEnvironmentShutdownListener(environments, operations);
    }

    @Test
    public void expiredLicenseQueuesStopForEveryNonStoppedEnvironment() {
        TrainingEnvironmentRecord first = environment("environment-1");
        TrainingEnvironmentRecord second = environment("environment-2");
        when(environments.selectRequiringLicenseStop()).thenReturn(Arrays.asList(first, second));

        listener.onLicenseStateChanged(new LicenseStateChangedEvent(
                LicenseState.EXPIRING, LicenseState.EXPIRED, "license-1"));

        verify(operations).stop("environment-1", 0, "SYSTEM");
        verify(operations).stop("environment-2", 0, "SYSTEM");
    }

    @Test
    public void activeLicenseNeverStartsStoppedEnvironments() {
        listener.onLicenseStateChanged(new LicenseStateChangedEvent(
                LicenseState.EXPIRED, LicenseState.ACTIVE, "license-2"));

        verify(environments, never()).selectRequiringLicenseStop();
        verify(operations, never()).start(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyString());
    }

    private TrainingEnvironmentRecord environment(String id) {
        TrainingEnvironmentRecord record = new TrainingEnvironmentRecord();
        record.setEnvironmentId(id);
        return record;
    }
}
