package com.match.dashboard.model;

public class DashboardAlertSummary {
    private int offlineAgents;
    private int degradedEnvironments;
    private int failedEnvironments;
    private int pendingOperations;
    private int failedOperations;
    private int pendingCommands;
    private int failedCommands;
    private int licenseUnusable;
    private int licenseExpiring;

    public int getOfflineAgents() { return offlineAgents; }
    public void setOfflineAgents(int offlineAgents) { this.offlineAgents = offlineAgents; }
    public int getDegradedEnvironments() { return degradedEnvironments; }
    public void setDegradedEnvironments(int degradedEnvironments) { this.degradedEnvironments = degradedEnvironments; }
    public int getFailedEnvironments() { return failedEnvironments; }
    public void setFailedEnvironments(int failedEnvironments) { this.failedEnvironments = failedEnvironments; }
    public int getPendingOperations() { return pendingOperations; }
    public void setPendingOperations(int pendingOperations) { this.pendingOperations = pendingOperations; }
    public int getFailedOperations() { return failedOperations; }
    public void setFailedOperations(int failedOperations) { this.failedOperations = failedOperations; }
    public int getPendingCommands() { return pendingCommands; }
    public void setPendingCommands(int pendingCommands) { this.pendingCommands = pendingCommands; }
    public int getFailedCommands() { return failedCommands; }
    public void setFailedCommands(int failedCommands) { this.failedCommands = failedCommands; }
    public int getLicenseUnusable() { return licenseUnusable; }
    public void setLicenseUnusable(int licenseUnusable) { this.licenseUnusable = licenseUnusable; }
    public int getLicenseExpiring() { return licenseExpiring; }
    public void setLicenseExpiring(int licenseExpiring) { this.licenseExpiring = licenseExpiring; }

    public int getTotal() {
        return offlineAgents + degradedEnvironments + failedEnvironments + failedOperations
                + failedCommands + licenseUnusable + licenseExpiring;
    }

    public int getPendingWorkTotal() { return pendingOperations + pendingCommands; }
}
