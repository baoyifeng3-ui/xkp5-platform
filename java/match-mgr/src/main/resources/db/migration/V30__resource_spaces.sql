CREATE TABLE resource_directory (
    directory_id VARCHAR(36) NOT NULL PRIMARY KEY,
    space_type VARCHAR(16) NOT NULL,
    owner_user_id INT NULL,
    parent_id VARCHAR(36) NULL,
    name VARCHAR(160) NOT NULL,
    created_by INT NOT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    KEY idx_resource_directory_parent (space_type, owner_user_id, parent_id, name),
    CONSTRAINT chk_resource_directory_space CHECK (space_type IN ('COURSE','PUBLIC','EXCHANGE','HOMEWORK'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE platform_file (
    file_id VARCHAR(36) NOT NULL PRIMARY KEY,
    space_type VARCHAR(16) NOT NULL,
    owner_user_id INT NULL,
    directory_id VARCHAR(36) NULL,
    file_name VARCHAR(255) NOT NULL,
    storage_key VARCHAR(512) NOT NULL,
    content_length BIGINT UNSIGNED NOT NULL,
    sha256 CHAR(64) NOT NULL,
    mime_type VARCHAR(128) NOT NULL,
    uploaded_by INT NOT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    KEY idx_platform_file_directory (space_type, owner_user_id, directory_id, file_name),
    KEY idx_platform_file_digest (sha256),
    CONSTRAINT chk_platform_file_space CHECK (space_type IN ('COURSE','PUBLIC','EXCHANGE','HOMEWORK')),
    CONSTRAINT chk_platform_file_sha256 CHECK (
        sha256 COLLATE utf8mb4_bin = LOWER(sha256) COLLATE utf8mb4_bin
        AND sha256 COLLATE utf8mb4_bin REGEXP '^[0-9a-f]{64}$'
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE course_resource_link (
    link_id VARCHAR(36) NOT NULL PRIMARY KEY,
    course_id VARCHAR(36) NOT NULL,
    file_id VARCHAR(36) NOT NULL,
    resource_type VARCHAR(16) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    created_by INT NOT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    UNIQUE KEY uk_course_resource_link (course_id, file_id),
    KEY idx_course_resource_link_file (file_id),
    KEY idx_course_resource_link_visible (course_id, enabled, sort_order),
    CONSTRAINT chk_course_resource_link_type CHECK (resource_type IN ('EBOOK','VIDEO','PPT','ARCHIVE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE resource_cleanup_audit (
    audit_id VARCHAR(36) NOT NULL PRIMARY KEY,
    space_type VARCHAR(16) NOT NULL,
    deleted_file_count BIGINT UNSIGNED NOT NULL,
    deleted_total_bytes BIGINT UNSIGNED NOT NULL,
    actor_user_id INT NOT NULL,
    created_at DATETIME(3) NOT NULL,
    KEY idx_resource_cleanup_audit_time (created_at),
    CONSTRAINT chk_resource_cleanup_space CHECK (space_type IN ('EXCHANGE','HOMEWORK'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO platform_file(file_id, space_type, owner_user_id, directory_id, file_name,
                          storage_key, content_length, sha256, mime_type, uploaded_by,
                          created_at, updated_at)
SELECT resource_id, 'COURSE', NULL, NULL, name, storage_key, content_length,
       LOWER(sha256), mime_type, created_by, created_at, updated_at
FROM course_resource;

INSERT INTO course_resource_link(link_id, course_id, file_id, resource_type, sort_order,
                                 enabled, created_by, created_at, updated_at)
SELECT UUID(), course_id, resource_id, resource_type, sort_order, enabled, created_by,
       created_at, updated_at
FROM course_resource;
