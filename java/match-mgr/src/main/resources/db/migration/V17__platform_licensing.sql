CREATE TABLE platform_installation (
  installation_key VARCHAR(32) PRIMARY KEY,
  installation_id CHAR(36) NOT NULL UNIQUE,
  max_trusted_time DATETIME(3) NULL,
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL
);

CREATE TABLE license_request (
  request_id CHAR(36) PRIMARY KEY,
  installation_id CHAR(36) NOT NULL,
  challenge_hash CHAR(64) NOT NULL,
  fingerprint_digest VARCHAR(80) NOT NULL,
  environment VARCHAR(16) NOT NULL,
  organization VARCHAR(200) NULL,
  created_by INT NOT NULL,
  created_at DATETIME(3) NOT NULL,
  consumed_at DATETIME(3) NULL,
  consumed_license_id CHAR(36) NULL,
  INDEX idx_license_request_installation (installation_id, created_at)
);

CREATE TABLE platform_license (
  license_id CHAR(36) PRIMARY KEY,
  active TINYINT(1) NOT NULL DEFAULT 0,
  active_slot TINYINT GENERATED ALWAYS AS (CASE WHEN active = 1 THEN 1 ELSE NULL END) STORED,
  request_id CHAR(36) NOT NULL,
  organization VARCHAR(200) NOT NULL,
  environment VARCHAR(16) NOT NULL,
  fingerprint_digest VARCHAR(80) NOT NULL,
  key_id VARCHAR(80) NOT NULL,
  not_before DATETIME(3) NOT NULL,
  expires_at DATETIME(3) NOT NULL,
  max_processing_servers INT NULL,
  signed_envelope LONGTEXT NOT NULL,
  imported_by INT NOT NULL,
  imported_at DATETIME(3) NOT NULL,
  UNIQUE KEY uk_platform_license_active (active_slot),
  INDEX idx_platform_license_imported (imported_at)
);

CREATE TABLE license_audit (
  audit_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  actor_user_id INT NULL,
  action VARCHAR(64) NOT NULL,
  result VARCHAR(32) NOT NULL,
  reason_code VARCHAR(64) NULL,
  license_id CHAR(36) NULL,
  request_id CHAR(36) NULL,
  correlation_id CHAR(36) NOT NULL,
  created_at DATETIME(3) NOT NULL,
  INDEX idx_license_audit_created (created_at)
);
