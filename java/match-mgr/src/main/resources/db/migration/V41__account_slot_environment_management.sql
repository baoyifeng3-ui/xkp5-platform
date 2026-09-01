ALTER TABLE user
    ADD COLUMN remark VARCHAR(500) NULL AFTER password;

ALTER TABLE environment_port_allocation
    DROP INDEX uk_environment_agent_port,
    DROP INDEX uk_environment_slot_container_port,
    ADD COLUMN environment_id VARCHAR(36) NULL AFTER allocation_id,
    ADD KEY idx_environment_port_environment (environment_id),
    ADD KEY idx_environment_slot_container_port (slot_id, component_type, container_port, protocol),
    ADD UNIQUE KEY uk_environment_port_agent_host (agent_id, host_port, protocol);

CREATE TABLE environment_port_pool (
    pool_id VARCHAR(36) NOT NULL,
    agent_id VARCHAR(36) NOT NULL,
    environment_type VARCHAR(16) NOT NULL,
    service_type VARCHAR(24) NOT NULL,
    range_start INT UNSIGNED NOT NULL,
    range_end INT UNSIGNED NOT NULL,
    updated_by INT NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (pool_id),
    UNIQUE KEY uk_port_pool_agent_kind (agent_id, environment_type, service_type),
    KEY idx_port_pool_agent (agent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO environment_port_pool
    (pool_id, agent_id, environment_type, service_type, range_start, range_end, updated_by, updated_at)
SELECT UUID(), a.agent_id, defaults.environment_type, defaults.service_type,
       defaults.range_start, defaults.range_end, 0, CURRENT_TIMESTAMP(3)
FROM processing_agent a
JOIN (
    SELECT 'COURSE' environment_type, 'ANNOTATION' service_type, 20000 range_start, 20499 range_end
    UNION ALL SELECT 'COURSE', 'VSCODE', 20500, 20999
    UNION ALL SELECT 'COURSE', 'JUPYTER', 21000, 21499
    UNION ALL SELECT 'COURSE', 'T100', 21500, 21999
    UNION ALL SELECT 'COMPETITION', 'ANNOTATION', 22000, 22499
    UNION ALL SELECT 'COMPETITION', 'VSCODE', 22500, 22999
    UNION ALL SELECT 'COMPETITION', 'JUPYTER', 23000, 23499
    UNION ALL SELECT 'COMPETITION', 'T100', 23500, 23999
) defaults
WHERE a.removed_at IS NULL;

CREATE TABLE account_environment_migration (
    migration_id VARCHAR(36) NOT NULL,
    user_id INT NOT NULL,
    source_agent_id VARCHAR(36) NOT NULL,
    source_slot_id VARCHAR(36) NOT NULL,
    target_agent_id VARCHAR(36) NOT NULL,
    target_slot_id VARCHAR(36) NOT NULL,
    migrate_data TINYINT(1) NOT NULL DEFAULT 0,
    state VARCHAR(32) NOT NULL,
    stage VARCHAR(32) NOT NULL,
    failure_code VARCHAR(64) NULL,
    failure_message VARCHAR(512) NULL,
    requested_by INT NOT NULL,
    requested_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    completed_at DATETIME(3) NULL,
    PRIMARY KEY (migration_id),
    KEY idx_account_migration_user_state (user_id, state)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE competition_credential (
    credential_id VARCHAR(36) NOT NULL,
    user_id INT NOT NULL,
    mode_generation BIGINT NOT NULL,
    original_password_value VARCHAR(255) NOT NULL,
    competition_password_value VARCHAR(255) NOT NULL,
    encrypted_plain_password TEXT NOT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (credential_id),
    UNIQUE KEY uk_competition_credential_generation_user (mode_generation, user_id),
    KEY idx_competition_credential_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
