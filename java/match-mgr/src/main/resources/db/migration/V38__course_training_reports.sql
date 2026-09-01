CREATE TABLE course_training_report (
 report_id VARCHAR(36) NOT NULL PRIMARY KEY, user_id INT NOT NULL, course_id VARCHAR(36) NOT NULL,
 content MEDIUMTEXT NOT NULL, updated_at DATETIME(3) NOT NULL,
 UNIQUE KEY uk_course_report_user_course(user_id,course_id), KEY idx_course_report_course(course_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
