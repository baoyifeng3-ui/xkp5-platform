ALTER TABLE training_environment
  MODIFY course_id INT NULL,
  MODIFY annotation_template_id VARCHAR(36) NULL,
  MODIFY annotation_template_version SMALLINT UNSIGNED NULL,
  MODIFY editor_template_id VARCHAR(36) NULL,
  MODIFY editor_template_version SMALLINT UNSIGNED NULL,
  MODIFY annotation_container_name VARCHAR(128) NULL,
  MODIFY editor_container_name VARCHAR(128) NULL,
  ADD COLUMN environment_name VARCHAR(160) NULL AFTER environment_id,
  ADD COLUMN remark VARCHAR(1000) NULL AFTER environment_name;
