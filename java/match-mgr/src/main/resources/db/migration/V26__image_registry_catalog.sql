CREATE TABLE image_group (
    group_id VARCHAR(36) NOT NULL,
    name VARCHAR(128) NOT NULL,
    group_type VARCHAR(24) NOT NULL,
    description VARCHAR(512) NULL,
    state VARCHAR(24) NOT NULL,
    created_by INT NOT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_by INT NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (group_id),
    UNIQUE KEY uk_image_group_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE image_artifact (
    artifact_id VARCHAR(36) NOT NULL,
    group_id VARCHAR(36) NOT NULL,
    component_type VARCHAR(24) NOT NULL,
    original_filename VARCHAR(512) NOT NULL,
    size_bytes BIGINT UNSIGNED NOT NULL,
    sha256 CHAR(64) NOT NULL,
    image_repository VARCHAR(255) NULL,
    image_tag VARCHAR(128) NULL,
    version VARCHAR(128) NOT NULL,
    registry_digest CHAR(71) NULL,
    review_state VARCHAR(24) NOT NULL,
    import_state VARCHAR(24) NOT NULL,
    failure_code VARCHAR(64) NULL,
    failure_message VARCHAR(512) NULL,
    reviewed_by INT NULL,
    reviewed_at DATETIME(3) NULL,
    imported_at DATETIME(3) NULL,
    created_by INT NOT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (artifact_id),
    UNIQUE KEY uk_image_artifact_sha256 (sha256),
    UNIQUE KEY uk_image_artifact_digest (registry_digest),
    KEY idx_image_artifact_group_component (group_id, component_type),
    CONSTRAINT chk_image_artifact_size CHECK (size_bytes >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE image_upload (
    upload_id VARCHAR(36) NOT NULL,
    artifact_id VARCHAR(36) NOT NULL,
    original_filename VARCHAR(512) NOT NULL,
    total_size BIGINT UNSIGNED NOT NULL,
    chunk_size INT UNSIGNED NOT NULL,
    total_chunks INT UNSIGNED NOT NULL,
    received_bytes BIGINT UNSIGNED NOT NULL DEFAULT 0,
    expected_sha256 CHAR(64) NULL,
    final_sha256 CHAR(64) NULL,
    state VARCHAR(24) NOT NULL,
    failure_code VARCHAR(64) NULL,
    failure_message VARCHAR(512) NULL,
    expires_at DATETIME(3) NOT NULL,
    completed_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (upload_id),
    UNIQUE KEY uk_image_upload_artifact (artifact_id),
    CONSTRAINT chk_image_upload_total_size CHECK (total_size >= 0),
    CONSTRAINT chk_image_upload_received_bytes CHECK (received_bytes >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE image_upload_chunk (
    upload_id VARCHAR(36) NOT NULL,
    chunk_index INT UNSIGNED NOT NULL,
    chunk_size INT UNSIGNED NOT NULL,
    chunk_sha256 CHAR(64) NOT NULL,
    stored_bytes BIGINT UNSIGNED NOT NULL,
    created_at DATETIME(3) NOT NULL,
    PRIMARY KEY (upload_id, chunk_index),
    UNIQUE KEY uk_image_upload_chunk (upload_id, chunk_index),
    CONSTRAINT chk_image_upload_chunk_index CHECK (chunk_index >= 0),
    CONSTRAINT chk_image_upload_chunk_bytes CHECK (stored_bytes >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE image_release (
    release_id VARCHAR(36) NOT NULL,
    group_id VARCHAR(36) NOT NULL,
    component_type VARCHAR(24) NOT NULL,
    artifact_id VARCHAR(36) NOT NULL,
    registry_digest CHAR(71) NOT NULL,
    version VARCHAR(128) NOT NULL,
    state VARCHAR(24) NOT NULL,
    created_by INT NOT NULL,
    created_at DATETIME(3) NOT NULL,
    published_at DATETIME(3) NULL,
    PRIMARY KEY (release_id),
    UNIQUE KEY uk_image_release_component_version (group_id, component_type, version),
    KEY idx_image_release_digest (registry_digest)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE image_deployment (
    deployment_id VARCHAR(36) NOT NULL,
    release_id VARCHAR(36) NOT NULL,
    agent_id VARCHAR(36) NOT NULL,
    component_type VARCHAR(24) NOT NULL,
    target_digest CHAR(71) NOT NULL,
    previous_digest CHAR(71) NULL,
    update_policy VARCHAR(24) NOT NULL,
    state VARCHAR(24) NOT NULL,
    active_deployment_key VARCHAR(128) NULL,
    failure_code VARCHAR(64) NULL,
    failure_message VARCHAR(512) NULL,
    requested_by INT NOT NULL,
    requested_at DATETIME(3) NOT NULL,
    completed_at DATETIME(3) NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (deployment_id),
    UNIQUE KEY uk_image_deployment_active (active_deployment_key),
    KEY idx_image_deployment_agent_component (agent_id, component_type, requested_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
