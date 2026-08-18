CREATE TABLE processing_agent (
  agent_id CHAR(36) PRIMARY KEY,
  display_name VARCHAR(80) NOT NULL,
  machine_digest CHAR(64) NOT NULL,
  hostname VARCHAR(255) NOT NULL,
  primary_ip VARCHAR(45) NOT NULL,
  mac_address VARCHAR(17) NOT NULL,
  agent_version VARCHAR(40) NOT NULL,
  credential_digest CHAR(64) NOT NULL,
  enabled TINYINT(1) NOT NULL DEFAULT 1,
  last_seen_at DATETIME(3) NULL,
  last_boot_id CHAR(36) NULL,
  last_sequence BIGINT NULL,
  latest_metrics JSON NULL,
  removed_at DATETIME(3) NULL,
  registered_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  UNIQUE KEY uk_processing_agent_machine (machine_digest),
  UNIQUE KEY uk_processing_agent_credential (credential_digest),
  INDEX idx_processing_agent_seen (enabled, last_seen_at)
);

CREATE TABLE processing_agent_registration_token (
  token_id CHAR(36) PRIMARY KEY,
  token_digest CHAR(64) NOT NULL,
  label VARCHAR(80) NULL,
  created_by INT NOT NULL,
  created_at DATETIME(3) NOT NULL,
  expires_at DATETIME(3) NOT NULL,
  consumed_at DATETIME(3) NULL,
  registered_agent_id CHAR(36) NULL,
  UNIQUE KEY uk_agent_registration_digest (token_digest),
  INDEX idx_agent_registration_expiry (expires_at, consumed_at)
);

CREATE TABLE processing_agent_metric_minute (
  metric_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  agent_id CHAR(36) NOT NULL,
  bucket_start DATETIME NOT NULL,
  sample_count INT NOT NULL,
  cpu_sum DECIMAL(12,2) NULL,
  cpu_max DECIMAL(6,2) NULL,
  ram_sum DECIMAL(12,2) NULL,
  ram_max DECIMAL(6,2) NULL,
  gpu_sum DECIMAL(12,2) NULL,
  gpu_max DECIMAL(6,2) NULL,
  gpu_memory_sum DECIMAL(12,2) NULL,
  gpu_memory_max DECIMAL(6,2) NULL,
  system_disk_sum DECIMAL(12,2) NULL,
  system_disk_max DECIMAL(6,2) NULL,
  workspace_disk_sum DECIMAL(12,2) NULL,
  workspace_disk_max DECIMAL(6,2) NULL,
  running_environment_sum BIGINT NOT NULL DEFAULT 0,
  running_environment_max INT NOT NULL DEFAULT 0,
  running_container_sum BIGINT NOT NULL DEFAULT 0,
  running_container_max INT NOT NULL DEFAULT 0,
  docker_available_samples INT NOT NULL DEFAULT 0,
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  UNIQUE KEY uk_agent_metric_minute (agent_id, bucket_start),
  INDEX idx_agent_metric_retention (bucket_start)
);

CREATE TABLE processing_agent_audit (
  audit_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  actor_user_id INT NULL,
  agent_id CHAR(36) NULL,
  token_id CHAR(36) NULL,
  action VARCHAR(64) NOT NULL,
  result VARCHAR(32) NOT NULL,
  reason_code VARCHAR(64) NULL,
  correlation_id CHAR(36) NOT NULL,
  created_at DATETIME(3) NOT NULL,
  INDEX idx_agent_audit_agent (agent_id, created_at),
  INDEX idx_agent_audit_created (created_at)
);
