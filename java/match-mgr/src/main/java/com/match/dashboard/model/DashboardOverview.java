package com.match.dashboard.model;

import java.time.Instant;

public class DashboardOverview {
    private Instant snapshotAt;
    private boolean fresh;
    private AgentSummary agents;
    private AgentModeSummary agentModes;
    private EnvironmentSummary environments;
    private int onlineUsers;
    private DashboardAlertSummary alerts;
    private DashboardResourceSummary resources;

    public Instant getSnapshotAt() { return snapshotAt; }
    public void setSnapshotAt(Instant snapshotAt) { this.snapshotAt = snapshotAt; }
    public boolean isFresh() { return fresh; }
    public void setFresh(boolean fresh) { this.fresh = fresh; }
    public AgentSummary getAgents() { return agents; }
    public void setAgents(AgentSummary agents) { this.agents = agents; }
    public AgentModeSummary getAgentModes() { return agentModes; }
    public void setAgentModes(AgentModeSummary agentModes) { this.agentModes = agentModes; }
    public EnvironmentSummary getEnvironments() { return environments; }
    public void setEnvironments(EnvironmentSummary environments) { this.environments = environments; }
    public int getOnlineUsers() { return onlineUsers; }
    public void setOnlineUsers(int onlineUsers) { this.onlineUsers = onlineUsers; }
    public DashboardAlertSummary getAlerts() { return alerts; }
    public void setAlerts(DashboardAlertSummary alerts) { this.alerts = alerts; }
    public DashboardResourceSummary getResources() { return resources; }
    public void setResources(DashboardResourceSummary resources) { this.resources = resources; }

    public static class AgentSummary {
        private int enabled;
        private int online;
        private int offline;

        public AgentSummary() { }
        public AgentSummary(int enabled, int online) {
            this.enabled = enabled;
            this.online = online;
            this.offline = Math.max(0, enabled - online);
        }
        public int getEnabled() { return enabled; }
        public void setEnabled(int enabled) { this.enabled = enabled; }
        public int getOnline() { return online; }
        public void setOnline(int online) { this.online = online; }
        public int getOffline() { return offline; }
        public void setOffline(int offline) { this.offline = offline; }
    }

    public static class EnvironmentSummary {
        private int running;
        private int transitional;
        private int degraded;
        private int failed;

        public int getRunning() { return running; }
        public void setRunning(int running) { this.running = running; }
        public int getTransitional() { return transitional; }
        public void setTransitional(int transitional) { this.transitional = transitional; }
        public int getDegraded() { return degraded; }
        public void setDegraded(int degraded) { this.degraded = degraded; }
        public int getFailed() { return failed; }
        public void setFailed(int failed) { this.failed = failed; }
    }

    public static class AgentModeSummary {
        private int normal;
        private int entering;
        private int competition;
        private int exiting;
        private int degraded;

        public int getNormal() { return normal; }
        public void setNormal(int normal) { this.normal = normal; }
        public int getEntering() { return entering; }
        public void setEntering(int entering) { this.entering = entering; }
        public int getCompetition() { return competition; }
        public void setCompetition(int competition) { this.competition = competition; }
        public int getExiting() { return exiting; }
        public void setExiting(int exiting) { this.exiting = exiting; }
        public int getDegraded() { return degraded; }
        public void setDegraded(int degraded) { this.degraded = degraded; }
    }
}
