ALTER TABLE active_class_session
  ADD COLUMN environment_name VARCHAR(64) NULL AFTER environment_id;
