CREATE TABLE IF NOT EXISTS account_template (
  template_id VARCHAR(36) PRIMARY KEY,
  template_name VARCHAR(120) NOT NULL UNIQUE,
  custom_fields JSON NOT NULL,
  require_profile TINYINT(1) NOT NULL DEFAULT 0,
  created_by INT NOT NULL,
  created_at DATETIME(3) NOT NULL
);
ALTER TABLE user ADD COLUMN must_complete_profile TINYINT(1) NOT NULL DEFAULT 0;
