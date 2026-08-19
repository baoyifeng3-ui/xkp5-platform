package com.match.dashboard.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.match.agent.model.AgentMetricSnapshot;
import com.match.dashboard.model.DashboardAlertSummary;
import com.match.dashboard.model.DashboardOverview;
import com.match.dashboard.model.DashboardResourceSummary;
import com.match.dashboard.persistence.DashboardOverviewMapper;
import com.match.licensing.model.LicenseState;
import com.match.licensing.model.LicenseStatus;
import com.match.licensing.service.LicenseStatusService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

@Service
public class DashboardOverviewService {
    private static final int ONLINE_WINDOW_SECONDS = 15;

    private final DashboardOverviewMapper mapper;
    private final ObjectMapper objectMapper;
    private final UserActivityService activityService;
    private final LicenseStatusService licenseStatusService;
    private final Clock clock;

    public DashboardOverviewService(DashboardOverviewMapper mapper, ObjectMapper objectMapper,
                                    UserActivityService activityService,
                                    LicenseStatusService licenseStatusService, Clock clock) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
        this.activityService = activityService;
        this.licenseStatusService = licenseStatusService;
        this.clock = clock;
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public DashboardOverview snapshot() {
        Instant snapshotAt = clock.instant();
        LocalDateTime onlineCutoff = LocalDateTime.ofInstant(
                snapshotAt.minusSeconds(ONLINE_WINDOW_SECONDS), ZoneOffset.UTC);
        int enabledAgents = mapper.countEnabledAgents();
        int onlineAgents = mapper.countOnlineAgents(onlineCutoff);

        DashboardOverview.EnvironmentSummary environments = new DashboardOverview.EnvironmentSummary();
        environments.setRunning(mapper.countEnvironmentsInState("RUNNING"));
        environments.setTransitional(mapper.countTransitionalEnvironments());
        environments.setDegraded(mapper.countEnvironmentsInState("DEGRADED"));
        environments.setFailed(mapper.countEnvironmentsInState("ERROR"));

        DashboardAlertSummary alerts = new DashboardAlertSummary();
        alerts.setOfflineAgents(Math.max(0, enabledAgents - onlineAgents));
        alerts.setDegradedEnvironments(environments.getDegraded());
        alerts.setFailedEnvironments(environments.getFailed());
        alerts.setPendingOperations(mapper.countActiveOperations());
        alerts.setFailedOperations(mapper.countFailedOperations());
        alerts.setPendingCommands(mapper.countActiveCommands());
        alerts.setFailedCommands(mapper.countFailedCommands());
        applyLicenseAlerts(alerts, licenseStatusService.currentStatus());

        DashboardOverview overview = new DashboardOverview();
        overview.setSnapshotAt(snapshotAt);
        overview.setFresh(true);
        overview.setAgents(new DashboardOverview.AgentSummary(enabledAgents, onlineAgents));
        overview.setEnvironments(environments);
        overview.setOnlineUsers(activityService.onlineUserCount());
        overview.setAlerts(alerts);
        overview.setResources(resources(mapper.selectOnlineMetricJson(onlineCutoff)));
        return overview;
    }

    private void applyLicenseAlerts(DashboardAlertSummary alerts, LicenseStatus status) {
        if (status == null || !status.isUsable()) {
            alerts.setLicenseUnusable(1);
        } else if (status.getState() == LicenseState.EXPIRING) {
            alerts.setLicenseExpiring(1);
        }
    }

    private DashboardResourceSummary resources(List<String> metricJson) {
        List<BigDecimal> cpu = new ArrayList<>();
        List<BigDecimal> gpu = new ArrayList<>();
        List<BigDecimal> gpuMemory = new ArrayList<>();
        List<BigDecimal> memory = new ArrayList<>();
        List<BigDecimal> disk = new ArrayList<>();
        if (metricJson != null) {
            for (String json : metricJson) {
                AgentMetricSnapshot metric = parseMetric(json);
                if (metric == null) continue;
                add(cpu, metric.getCpuPercent());
                add(gpu, metric.getGpuPercent());
                add(gpuMemory, metric.getGpuMemoryPercent());
                add(memory, metric.getRamPercent());
                add(disk, metric.getWorkspaceDiskPercent() == null
                        ? metric.getSystemDiskPercent() : metric.getWorkspaceDiskPercent());
            }
        }
        return new DashboardResourceSummary(summary(cpu), summary(gpu), summary(gpuMemory), summary(memory), summary(disk));
    }

    private AgentMetricSnapshot parseMetric(String json) {
        if (json == null || json.trim().isEmpty()) return null;
        try {
            return objectMapper.readValue(json, AgentMetricSnapshot.class);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void add(List<BigDecimal> samples, BigDecimal value) {
        if (value != null) samples.add(value);
    }

    private DashboardResourceSummary.MetricSummary summary(List<BigDecimal> samples) {
        if (samples.isEmpty()) return new DashboardResourceSummary.MetricSummary(null, 0);
        BigDecimal total = BigDecimal.ZERO;
        for (BigDecimal sample : samples) total = total.add(sample);
        return new DashboardResourceSummary.MetricSummary(
                total.divide(BigDecimal.valueOf(samples.size()), 4, BigDecimal.ROUND_HALF_UP).doubleValue(),
                samples.size());
    }
}
