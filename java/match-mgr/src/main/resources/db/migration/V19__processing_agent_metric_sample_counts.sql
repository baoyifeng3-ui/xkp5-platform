ALTER TABLE processing_agent_metric_minute
  ADD cpu_sample_count INT NOT NULL DEFAULT 0 AFTER sample_count,
  ADD ram_sample_count INT NOT NULL DEFAULT 0 AFTER cpu_max,
  ADD gpu_sample_count INT NOT NULL DEFAULT 0 AFTER ram_max,
  ADD gpu_memory_sample_count INT NOT NULL DEFAULT 0 AFTER gpu_max,
  ADD system_disk_sample_count INT NOT NULL DEFAULT 0 AFTER gpu_memory_max,
  ADD workspace_disk_sample_count INT NOT NULL DEFAULT 0 AFTER system_disk_max;
