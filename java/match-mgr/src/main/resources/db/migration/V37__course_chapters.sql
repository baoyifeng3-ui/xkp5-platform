CREATE TABLE course_chapter (
  chapter_id VARCHAR(36) NOT NULL PRIMARY KEY,
  course_id VARCHAR(36) NOT NULL,
  chapter_name VARCHAR(160) NOT NULL,
  practice_tool VARCHAR(16) NULL,
  sort_order INT NOT NULL DEFAULT 0,
  created_by INT NOT NULL,
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  KEY idx_course_chapter_order (course_id, sort_order, created_at),
  CONSTRAINT chk_course_chapter_tool CHECK (practice_tool IS NULL OR practice_tool IN ('ANNOTATION','EDITOR'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE course_resource_link
  ADD COLUMN chapter_id VARCHAR(36) NULL AFTER resource_type,
  ADD COLUMN practice_tool VARCHAR(16) NULL AFTER chapter_id,
  ADD KEY idx_course_resource_chapter (course_id, chapter_id, sort_order),
  ADD CONSTRAINT chk_course_resource_tool CHECK (practice_tool IS NULL OR practice_tool IN ('ANNOTATION','EDITOR'));

ALTER TABLE course_resource
  ADD COLUMN chapter_id VARCHAR(36) NULL AFTER resource_type,
  ADD COLUMN practice_tool VARCHAR(16) NULL AFTER chapter_id;
