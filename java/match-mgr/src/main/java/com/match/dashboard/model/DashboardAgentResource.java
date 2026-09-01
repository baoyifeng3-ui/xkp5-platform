package com.match.dashboard.model;

import java.time.LocalDateTime;

public class DashboardAgentResource {
    private String agentId;
    private String displayName;
    private String primaryIp;
    private boolean online;
    private LocalDateTime lastSeenAt;
    private Double cpuPercent;
    private Double gpuPercent;
    private Double memoryPercent;
    private Double diskPercent;
    private Long networkReceiveBytesPerSecond;
    private Long networkSendBytesPerSecond;
    private Integer runningContainerCount;

    public String getAgentId() { return agentId; }
    public void setAgentId(String agentId) { this.agentId = agentId; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getPrimaryIp() { return primaryIp; }
    public void setPrimaryIp(String primaryIp) { this.primaryIp = primaryIp; }
    public boolean isOnline() { return online; }
    public void setOnline(boolean online) { this.online = online; }
    public LocalDateTime getLastSeenAt() { return lastSeenAt; }
    public void setLastSeenAt(LocalDateTime lastSeenAt) { this.lastSeenAt = lastSeenAt; }
    public Double getCpuPercent() { return cpuPercent; }
    public void setCpuPercent(Double cpuPercent) { this.cpuPercent = cpuPercent; }
    public Double getGpuPercent() { return gpuPercent; }
    public void setGpuPercent(Double gpuPercent) { this.gpuPercent = gpuPercent; }
    public Double getMemoryPercent() { return memoryPercent; }
    public void setMemoryPercent(Double memoryPercent) { this.memoryPercent = memoryPercent; }
    public Double getDiskPercent() { return diskPercent; }
    public void setDiskPercent(Double diskPercent) { this.diskPercent = diskPercent; }
    public Long getNetworkReceiveBytesPerSecond() { return networkReceiveBytesPerSecond; }
    public void setNetworkReceiveBytesPerSecond(Long value) { this.networkReceiveBytesPerSecond = value; }
    public Long getNetworkSendBytesPerSecond() { return networkSendBytesPerSecond; }
    public void setNetworkSendBytesPerSecond(Long value) { this.networkSendBytesPerSecond = value; }
    public Integer getRunningContainerCount() { return runningContainerCount; }
    public void setRunningContainerCount(Integer runningContainerCount) { this.runningContainerCount = runningContainerCount; }
}
