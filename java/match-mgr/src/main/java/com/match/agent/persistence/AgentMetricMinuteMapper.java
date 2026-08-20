package com.match.agent.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

public interface AgentMetricMinuteMapper extends BaseMapper<AgentMetricMinuteRecord> {
    @Insert("INSERT INTO processing_agent_metric_minute ("
            + "agent_id, bucket_start, sample_count, cpu_sample_count, cpu_sum, cpu_max, "
            + "ram_sample_count, ram_sum, ram_max, gpu_sample_count, gpu_sum, gpu_max, "
            + "gpu_memory_sample_count, gpu_memory_sum, gpu_memory_max, "
            + "system_disk_sample_count, system_disk_sum, system_disk_max, "
            + "workspace_disk_sample_count, workspace_disk_sum, workspace_disk_max, "
            + "running_environment_sum, running_environment_max, running_container_sum, "
            + "running_container_max, docker_available_samples, created_at, updated_at) VALUES ("
            + "#{agentId}, #{bucketStart}, #{sampleCount}, #{cpuSampleCount}, #{cpuSum}, #{cpuMax}, "
            + "#{ramSampleCount}, #{ramSum}, #{ramMax}, #{gpuSampleCount}, #{gpuSum}, #{gpuMax}, "
            + "#{gpuMemorySampleCount}, #{gpuMemorySum}, #{gpuMemoryMax}, "
            + "#{systemDiskSampleCount}, #{systemDiskSum}, #{systemDiskMax}, "
            + "#{workspaceDiskSampleCount}, #{workspaceDiskSum}, #{workspaceDiskMax}, "
            + "#{runningEnvironmentSum}, #{runningEnvironmentMax}, #{runningContainerSum}, "
            + "#{runningContainerMax}, #{dockerAvailableSamples}, #{createdAt}, #{updatedAt}) "
            + "ON DUPLICATE KEY UPDATE "
            + "sample_count = sample_count + VALUES(sample_count), "
            + "cpu_sample_count = cpu_sample_count + VALUES(cpu_sample_count), "
            + "cpu_sum = IF(VALUES(cpu_sum) IS NULL, cpu_sum, COALESCE(cpu_sum, 0) + VALUES(cpu_sum)), "
            + "cpu_max = IF(VALUES(cpu_max) IS NULL, cpu_max, GREATEST(COALESCE(cpu_max, VALUES(cpu_max)), VALUES(cpu_max))), "
            + "ram_sample_count = ram_sample_count + VALUES(ram_sample_count), "
            + "ram_sum = IF(VALUES(ram_sum) IS NULL, ram_sum, COALESCE(ram_sum, 0) + VALUES(ram_sum)), "
            + "ram_max = IF(VALUES(ram_max) IS NULL, ram_max, GREATEST(COALESCE(ram_max, VALUES(ram_max)), VALUES(ram_max))), "
            + "gpu_sample_count = gpu_sample_count + VALUES(gpu_sample_count), "
            + "gpu_sum = IF(VALUES(gpu_sum) IS NULL, gpu_sum, COALESCE(gpu_sum, 0) + VALUES(gpu_sum)), "
            + "gpu_max = IF(VALUES(gpu_max) IS NULL, gpu_max, GREATEST(COALESCE(gpu_max, VALUES(gpu_max)), VALUES(gpu_max))), "
            + "gpu_memory_sample_count = gpu_memory_sample_count + VALUES(gpu_memory_sample_count), "
            + "gpu_memory_sum = IF(VALUES(gpu_memory_sum) IS NULL, gpu_memory_sum, COALESCE(gpu_memory_sum, 0) + VALUES(gpu_memory_sum)), "
            + "gpu_memory_max = IF(VALUES(gpu_memory_max) IS NULL, gpu_memory_max, GREATEST(COALESCE(gpu_memory_max, VALUES(gpu_memory_max)), VALUES(gpu_memory_max))), "
            + "system_disk_sample_count = system_disk_sample_count + VALUES(system_disk_sample_count), "
            + "system_disk_sum = IF(VALUES(system_disk_sum) IS NULL, system_disk_sum, COALESCE(system_disk_sum, 0) + VALUES(system_disk_sum)), "
            + "system_disk_max = IF(VALUES(system_disk_max) IS NULL, system_disk_max, GREATEST(COALESCE(system_disk_max, VALUES(system_disk_max)), VALUES(system_disk_max))), "
            + "workspace_disk_sample_count = workspace_disk_sample_count + VALUES(workspace_disk_sample_count), "
            + "workspace_disk_sum = IF(VALUES(workspace_disk_sum) IS NULL, workspace_disk_sum, COALESCE(workspace_disk_sum, 0) + VALUES(workspace_disk_sum)), "
            + "workspace_disk_max = IF(VALUES(workspace_disk_max) IS NULL, workspace_disk_max, GREATEST(COALESCE(workspace_disk_max, VALUES(workspace_disk_max)), VALUES(workspace_disk_max))), "
            + "running_environment_sum = running_environment_sum + VALUES(running_environment_sum), "
            + "running_environment_max = GREATEST(running_environment_max, VALUES(running_environment_max)), "
            + "running_container_sum = running_container_sum + VALUES(running_container_sum), "
            + "running_container_max = GREATEST(running_container_max, VALUES(running_container_max)), "
            + "docker_available_samples = docker_available_samples + VALUES(docker_available_samples), "
            + "updated_at = VALUES(updated_at)")
    int accumulate(AgentMetricMinuteRecord record);

    @Select("SELECT * FROM processing_agent_metric_minute WHERE agent_id = #{agentId} "
            + "AND bucket_start >= #{from} AND bucket_start <= #{to} ORDER BY bucket_start")
    List<AgentMetricMinuteRecord> selectHistory(@Param("agentId") String agentId,
                                                @Param("from") LocalDateTime from,
                                                @Param("to") LocalDateTime to);

    @Delete("DELETE FROM processing_agent_metric_minute WHERE bucket_start < #{cutoff}")
    int deleteBefore(@Param("cutoff") LocalDateTime cutoff);
}
