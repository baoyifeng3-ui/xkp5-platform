CREATE TABLE help_document (
  document_id VARCHAR(36) PRIMARY KEY,
  title VARCHAR(200) NOT NULL,
  audience VARCHAR(16) NOT NULL,
  content_type VARCHAR(16) NOT NULL,
  html_content LONGTEXT NULL,
  storage_key VARCHAR(512) NULL,
  file_name VARCHAR(255) NULL,
  published TINYINT(1) NOT NULL DEFAULT 0,
  created_by INT NOT NULL,
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  INDEX idx_help_audience_state (audience,published,updated_at)
);
