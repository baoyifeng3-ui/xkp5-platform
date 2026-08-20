package com.match.dashboard.model;

public class DashboardResourceSummary {
    private MetricSummary cpu;
    private MetricSummary gpu;
    private MetricSummary gpuMemory;
    private MetricSummary memory;
    private MetricSummary disk;

    public DashboardResourceSummary() { }

    public DashboardResourceSummary(MetricSummary cpu, MetricSummary gpu, MetricSummary gpuMemory,
                                    MetricSummary memory, MetricSummary disk) {
        this.cpu = cpu;
        this.gpu = gpu;
        this.gpuMemory = gpuMemory;
        this.memory = memory;
        this.disk = disk;
    }

    public MetricSummary getCpu() { return cpu; }
    public void setCpu(MetricSummary cpu) { this.cpu = cpu; }
    public MetricSummary getGpu() { return gpu; }
    public void setGpu(MetricSummary gpu) { this.gpu = gpu; }
    public MetricSummary getGpuMemory() { return gpuMemory; }
    public void setGpuMemory(MetricSummary gpuMemory) { this.gpuMemory = gpuMemory; }
    public MetricSummary getMemory() { return memory; }
    public void setMemory(MetricSummary memory) { this.memory = memory; }
    public MetricSummary getDisk() { return disk; }
    public void setDisk(MetricSummary disk) { this.disk = disk; }

    public static class MetricSummary {
        private Double averagePercent;
        private int sampleCount;

        public MetricSummary() { }

        public MetricSummary(Double averagePercent, int sampleCount) {
            this.averagePercent = averagePercent;
            this.sampleCount = sampleCount;
        }

        public Double getAveragePercent() { return averagePercent; }
        public void setAveragePercent(Double averagePercent) { this.averagePercent = averagePercent; }
        public int getSampleCount() { return sampleCount; }
        public void setSampleCount(int sampleCount) { this.sampleCount = sampleCount; }
    }
}
