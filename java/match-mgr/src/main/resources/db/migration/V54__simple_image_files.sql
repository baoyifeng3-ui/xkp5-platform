CREATE TABLE image_file (
    file_id CHAR(36) NOT NULL,
    image_repository VARCHAR(255) NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    image_tag VARCHAR(128) NOT NULL,
    archive_path VARCHAR(768) NOT NULL,
    size_bytes BIGINT UNSIGNED NOT NULL,
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    created_by INT NOT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (file_id),
    UNIQUE KEY uk_image_file_identity (image_repository, original_filename)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE image_deployment
    ADD COLUMN file_id CHAR(36) NULL AFTER release_id,
    ADD COLUMN target_image VARCHAR(384) NULL AFTER component_type,
    MODIFY release_id VARCHAR(36) NULL,
    MODIFY target_digest CHAR(71) NULL,
    ADD KEY idx_image_deployment_file (file_id);

ALTER TABLE image_upload_chunk MODIFY chunk_sha256 CHAR(64) NULL;
