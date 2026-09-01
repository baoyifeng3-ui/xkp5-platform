CREATE TABLE learning_evaluation (
  evaluation_id VARCHAR(36) NOT NULL PRIMARY KEY,
  user_id INT NOT NULL,
  course_id VARCHAR(36) NULL,
  rating TINYINT UNSIGNED NOT NULL,
  content VARCHAR(2000) NOT NULL,
  created_by INT NOT NULL,
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  KEY idx_learning_evaluation_user (user_id, created_at),
  CONSTRAINT chk_learning_evaluation_rating CHECK (rating BETWEEN 1 AND 5)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
