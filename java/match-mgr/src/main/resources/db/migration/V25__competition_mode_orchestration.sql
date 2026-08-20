ALTER TABLE processing_environment_slot MODIFY user_id INT NULL;

CREATE TABLE platform_mode (
    singleton_id TINYINT UNSIGNED NOT NULL,
    mode VARCHAR(24) NOT NULL,
    generation BIGINT UNSIGNED NOT NULL,
    changed_by INT NOT NULL,
    changed_at DATETIME(3) NOT NULL,
    PRIMARY KEY (singleton_id),
    CONSTRAINT chk_platform_mode_singleton CHECK (singleton_id = 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO platform_mode(singleton_id, mode, generation, changed_by, changed_at)
VALUES (1, 'TRAINING', 1, 0, UTC_TIMESTAMP(3));

CREATE TABLE competition_environment (
    environment_id VARCHAR(36) NOT NULL,
    agent_id VARCHAR(36) NOT NULL,
    slot_id VARCHAR(36) NOT NULL,
    slot_number TINYINT UNSIGNED NOT NULL,
    annotation_template_id VARCHAR(36) NOT NULL,
    annotation_template_version SMALLINT UNSIGNED NOT NULL,
    editor_template_id VARCHAR(36) NOT NULL,
    editor_template_version SMALLINT UNSIGNED NOT NULL,
    workspace_relative_path VARCHAR(512) NOT NULL,
    desired_state VARCHAR(24) NOT NULL,
    actual_state VARCHAR(24) NOT NULL,
    annotation_container_name VARCHAR(128) NOT NULL,
    annotation_container_state VARCHAR(24) NOT NULL,
    annotation_config_fingerprint CHAR(64) NOT NULL,
    editor_container_name VARCHAR(128) NOT NULL,
    editor_container_state VARCHAR(24) NOT NULL,
    editor_config_fingerprint CHAR(64) NOT NULL,
    last_verified_at DATETIME(3) NULL,
    last_component_results_json JSON NULL,
    current_operation_id VARCHAR(36) NULL,
    lock_version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_by INT NOT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_by INT NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (environment_id),
    UNIQUE KEY uk_competition_environment_slot (slot_id),
    UNIQUE KEY uk_competition_annotation_name (annotation_container_name),
    UNIQUE KEY uk_competition_editor_name (editor_container_name),
    KEY idx_competition_agent_state (agent_id, actual_state)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE processing_agent_mode (
    agent_id VARCHAR(36) NOT NULL,
    desired_mode VARCHAR(24) NOT NULL,
    actual_mode VARCHAR(32) NOT NULL,
    active_transition_id VARCHAR(36) NULL,
    lock_version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (agent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE mode_transition (
    transition_id VARCHAR(36) NOT NULL,
    agent_id VARCHAR(36) NOT NULL,
    source_mode VARCHAR(24) NOT NULL,
    target_mode VARCHAR(24) NOT NULL,
    state VARCHAR(24) NOT NULL,
    active_transition_key VARCHAR(72) NULL,
    actor_user_id INT NOT NULL,
    actor_role VARCHAR(32) NOT NULL,
    requested_at DATETIME(3) NOT NULL,
    completed_at DATETIME(3) NULL,
    failure_summary VARCHAR(512) NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (transition_id),
    UNIQUE KEY uk_mode_active_transition (active_transition_key),
    KEY idx_mode_transition_agent (agent_id, requested_at),
    KEY idx_mode_transition_state (state, requested_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE mode_transition_step (
    step_id VARCHAR(36) NOT NULL,
    transition_id VARCHAR(36) NOT NULL,
    phase_number TINYINT UNSIGNED NOT NULL,
    step_ordinal SMALLINT UNSIGNED NOT NULL,
    environment_kind VARCHAR(16) NOT NULL,
    environment_id VARCHAR(36) NOT NULL,
    action_type VARCHAR(40) NOT NULL,
    state VARCHAR(24) NOT NULL,
    command_id VARCHAR(36) NULL,
    idempotency_key VARCHAR(160) NOT NULL,
    result_code VARCHAR(64) NULL,
    result_message VARCHAR(512) NULL,
    component_results_json JSON NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (step_id),
    UNIQUE KEY uk_mode_transition_step (transition_id, phase_number, step_ordinal),
    UNIQUE KEY uk_mode_step_idempotency (idempotency_key),
    KEY idx_mode_step_command (command_id),
    KEY idx_mode_step_state (transition_id, phase_number, state)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE mode_training_snapshot (
    transition_id VARCHAR(36) NOT NULL,
    environment_id VARCHAR(36) NOT NULL,
    captured_at DATETIME(3) NOT NULL,
    PRIMARY KEY (transition_id, environment_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
