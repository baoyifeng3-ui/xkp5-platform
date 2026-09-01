ALTER TABLE training_environment
  ADD COLUMN environment_type VARCHAR(16) NOT NULL DEFAULT 'COURSE' AFTER remark,
  ADD KEY idx_training_environment_type (environment_type, user_id, actual_state);
