CREATE TABLE course (
    course_id VARCHAR(36) NOT NULL PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    course_type VARCHAR(48) NOT NULL,
    description VARCHAR(2000) NULL,
    cover_resource_id VARCHAR(36) NULL,
    enabled TINYINT(1) NOT NULL DEFAULT 0,
    created_by INT NOT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    UNIQUE KEY uk_course_name (name, course_type),
    KEY idx_course_enabled_type (enabled, course_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE course_resource (
    resource_id VARCHAR(36) NOT NULL PRIMARY KEY,
    course_id VARCHAR(36) NOT NULL,
    resource_type VARCHAR(16) NOT NULL,
    name VARCHAR(160) NOT NULL,
    storage_key VARCHAR(512) NOT NULL,
    content_length BIGINT UNSIGNED NOT NULL,
    sha256 CHAR(64) NOT NULL,
    mime_type VARCHAR(128) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    created_by INT NOT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    UNIQUE KEY uk_course_resource_digest (course_id, sha256),
    KEY idx_course_resource_visible (course_id, enabled, sort_order),
    CONSTRAINT chk_course_resource_type CHECK (resource_type IN ('EBOOK','VIDEO','PPT','ARCHIVE')),
    CONSTRAINT chk_course_resource_sha256 CHECK (sha256 COLLATE utf8mb4_bin = LOWER(sha256) COLLATE utf8mb4_bin AND sha256 COLLATE utf8mb4_bin REGEXP '^[0-9a-f]{64}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE course_resource_progress (
    progress_id VARCHAR(36) NOT NULL PRIMARY KEY,
    user_id INT NOT NULL,
    resource_id VARCHAR(36) NOT NULL,
    progress_kind VARCHAR(16) NOT NULL,
    current_value BIGINT UNSIGNED NOT NULL DEFAULT 0,
    total_value BIGINT UNSIGNED NOT NULL,
    percent DECIMAL(5,2) NOT NULL DEFAULT 0,
    last_position VARCHAR(512) NULL,
    state VARCHAR(16) NOT NULL DEFAULT 'NOT_STARTED',
    completed_at DATETIME(3) NULL,
    updated_at DATETIME(3) NOT NULL,
    UNIQUE KEY uk_course_progress_user_resource (user_id, resource_id),
    CONSTRAINT chk_course_progress_kind CHECK (progress_kind IN ('PAGE','TIME','SLIDE','DELIVERY')),
    CONSTRAINT chk_course_progress_state CHECK (state IN ('NOT_STARTED','IN_PROGRESS','COMPLETED')),
    CONSTRAINT chk_course_progress_percent CHECK (percent >= 0 AND percent <= 100),
    CONSTRAINT chk_course_progress_values CHECK (current_value <= total_value)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE course_resource_delivery (
    delivery_id VARCHAR(36) NOT NULL PRIMARY KEY,
    resource_id VARCHAR(36) NOT NULL,
    user_id INT NOT NULL,
    environment_id VARCHAR(36) NOT NULL,
    digest CHAR(64) NOT NULL,
    state VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    operation_id VARCHAR(36) NULL,
    command_id VARCHAR(36) NULL,
    failure_code VARCHAR(64) NULL,
    failure_message VARCHAR(512) NULL,
    requested_at DATETIME(3) NOT NULL,
    completed_at DATETIME(3) NULL,
    updated_at DATETIME(3) NOT NULL,
    UNIQUE KEY uk_course_delivery_idempotency (resource_id, user_id, environment_id, digest),
    KEY idx_course_delivery_state (state, updated_at),
    CONSTRAINT chk_course_delivery_state CHECK (state IN ('PENDING','DISPATCHED','RUNNING','SUCCEEDED','FAILED')),
    CONSTRAINT chk_course_delivery_digest CHECK (digest COLLATE utf8mb4_bin = LOWER(digest) COLLATE utf8mb4_bin AND digest COLLATE utf8mb4_bin REGEXP '^[0-9a-f]{64}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
