package com.match.dashboard.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.match.dashboard.model.DashboardOverview;
import com.match.dashboard.persistence.DashboardOverviewMapper;
import com.match.licensing.model.LicenseState;
import com.match.licensing.model.LicenseStatus;
import com.match.licensing.service.LicenseStatusService;
import org.junit.Before;
import org.junit.Test;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.apache.ibatis.annotations.Select;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class DashboardOverviewServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-19T04:30:00Z");
    private DashboardOverviewMapper mapper;
    private UserActivityService activity;
    private LicenseStatusService licenses;
    private DashboardOverviewService service;

    @Before
    public void setUp() {
        mapper = mock(DashboardOverviewMapper.class);
        activity = mock(UserActivityService.class);
        licenses = mock(LicenseStatusService.class);
        service = new DashboardOverviewService(mapper, new ObjectMapper(), activity, licenses,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    public void assemblesPlatformCountsAndAlerts() {
        LocalDateTime onlineCutoff = LocalDateTime.ofInstant(NOW.minusSeconds(15), ZoneOffset.UTC);
        when(mapper.countEnabledAgents()).thenReturn(5);
        when(mapper.countOnlineAgents(onlineCutoff)).thenReturn(3);
        when(mapper.countEnvironmentsInState("RUNNING")).thenReturn(8);
        when(mapper.countTransitionalEnvironments()).thenReturn(2);
        when(mapper.countEnvironmentsInState("DEGRADED")).thenReturn(1);
        when(mapper.countEnvironmentsInState("ERROR")).thenReturn(4);
        when(mapper.countActiveOperations()).thenReturn(6);
        when(mapper.countFailedOperations()).thenReturn(2);
        when(mapper.countActiveCommands()).thenReturn(7);
        when(mapper.countFailedCommands()).thenReturn(3);
        when(mapper.countAgentModesInState("NORMAL")).thenReturn(1);
        when(mapper.countAgentModesInState("ENTERING_COMPETITION")).thenReturn(2);
        when(mapper.countAgentModesInState("COMPETITION")).thenReturn(3);
        when(mapper.countAgentModesInState("EXITING_COMPETITION")).thenReturn(4);
        when(mapper.countAgentModesInState("DEGRADED")).thenReturn(5);
        when(mapper.selectOnlineMetricJson(onlineCutoff)).thenReturn(Arrays.asList());
        when(activity.onlineUserCount()).thenReturn(11);
        when(licenses.currentStatus()).thenReturn(new LicenseStatus(
                LicenseState.EXPIRING, "license", "org", NOW.plusSeconds(86400), 10));

        DashboardOverview result = service.snapshot();

        assertEquals(NOW, result.getSnapshotAt());
        assertTrue(result.isFresh());
        assertEquals(5, result.getAgents().getEnabled());
        assertEquals(3, result.getAgents().getOnline());
        assertEquals(2, result.getAgents().getOffline());
        assertEquals(8, result.getEnvironments().getRunning());
        assertEquals(2, result.getEnvironments().getTransitional());
        assertEquals(1, result.getEnvironments().getDegraded());
        assertEquals(4, result.getEnvironments().getFailed());
        assertEquals(11, result.getOnlineUsers());
        assertEquals(6, result.getAlerts().getPendingOperations());
        assertEquals(2, result.getAlerts().getFailedOperations());
        assertEquals(7, result.getAlerts().getPendingCommands());
        assertEquals(3, result.getAlerts().getFailedCommands());
        assertEquals(1, result.getAgentModes().getNormal());
        assertEquals(2, result.getAgentModes().getEntering());
        assertEquals(3, result.getAgentModes().getCompetition());
        assertEquals(4, result.getAgentModes().getExiting());
        assertEquals(5, result.getAgentModes().getDegraded());
        assertEquals(2, result.getAlerts().getOfflineAgents());
        assertEquals(1, result.getAlerts().getDegradedEnvironments());
        assertEquals(4, result.getAlerts().getFailedEnvironments());
        assertEquals(0, result.getAlerts().getLicenseUnusable());
        assertEquals(1, result.getAlerts().getLicenseExpiring());
        verify(mapper).countOnlineAgents(onlineCutoff);
    }

    @Test
    public void snapshotUsesARepeatableReadTransaction() throws Exception {
        Transactional transaction = DashboardOverviewService.class
                .getMethod("snapshot").getAnnotation(Transactional.class);

        assertNotNull(transaction);
        assertEquals(Isolation.REPEATABLE_READ, transaction.isolation());
    }

    @Test
    public void agentModeCountsIncludeUninitializedEnabledAgentsAsNormal() throws Exception {
        String sql = DashboardOverviewMapper.class.getMethod(
                "countAgentModesInState", String.class).getAnnotation(Select.class).value()[0];

        assertTrue(sql.contains("FROM processing_agent a LEFT JOIN processing_agent_mode m"));
        assertTrue(sql.contains("a.enabled = 1"));
        assertTrue(sql.contains("a.removed_at IS NULL"));
        assertTrue(sql.contains("COALESCE(m.actual_mode, 'NORMAL') = #{state}"));
    }

    @Test
    public void averagesOnlyPresentOnlineAgentMetricValues() {
        LocalDateTime cutoff = LocalDateTime.ofInstant(NOW.minusSeconds(15), ZoneOffset.UTC);
        when(mapper.selectOnlineMetricJson(cutoff)).thenReturn(Arrays.asList(
                "{\"cpuPercent\":20.0,\"ramPercent\":40.0,\"systemDiskPercent\":60.0}",
                "{\"cpuPercent\":40.0,\"gpuPercent\":70.0,\"gpuMemoryPercent\":25.0,\"ramPercent\":60.0,\"workspaceDiskPercent\":80.0}",
                "{\"gpuPercent\":null,\"ramPercent\":null}"));
        when(licenses.currentStatus()).thenReturn(new LicenseStatus(LicenseState.ACTIVE,
                "license", "org", NOW.plusSeconds(86400), 10));

        DashboardOverview result = service.snapshot();

        assertEquals(Double.valueOf(30.0), result.getResources().getCpu().getAveragePercent());
        assertEquals(2, result.getResources().getCpu().getSampleCount());
        assertEquals(Double.valueOf(70.0), result.getResources().getGpu().getAveragePercent());
        assertEquals(1, result.getResources().getGpu().getSampleCount());
        assertEquals(Double.valueOf(25.0), result.getResources().getGpuMemory().getAveragePercent());
        assertEquals(1, result.getResources().getGpuMemory().getSampleCount());
        assertEquals(Double.valueOf(50.0), result.getResources().getMemory().getAveragePercent());
        assertEquals(2, result.getResources().getMemory().getSampleCount());
        assertEquals(Double.valueOf(70.0), result.getResources().getDisk().getAveragePercent());
        assertEquals(2, result.getResources().getDisk().getSampleCount());
    }

    @Test
    public void returnsNullWhenNoResourceSampleExistsAndFlagsUnusableLicense() {
        when(mapper.selectOnlineMetricJson(
                LocalDateTime.ofInstant(NOW.minusSeconds(15), ZoneOffset.UTC)))
                .thenReturn(Arrays.asList("{}", null, "not-json"));
        when(licenses.currentStatus()).thenReturn(new LicenseStatus(
                LicenseState.EXPIRED, "license", "org", NOW.minusSeconds(1), 10));

        DashboardOverview result = service.snapshot();

        assertNull(result.getResources().getGpu().getAveragePercent());
        assertEquals(0, result.getResources().getGpu().getSampleCount());
        assertEquals(1, result.getAlerts().getLicenseUnusable());
        assertEquals(0, result.getAlerts().getLicenseExpiring());
    }
}
