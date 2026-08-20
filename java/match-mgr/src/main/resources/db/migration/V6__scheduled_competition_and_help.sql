ALTER TABLE count_down
    ADD COLUMN scheduled_start_time DATETIME NULL AFTER start_time,
    ADD COLUMN pre_login_minutes INT NOT NULL DEFAULT 30 AFTER scheduled_start_time,
    ADD COLUMN duration_minutes INT NOT NULL DEFAULT 240 AFTER pre_login_minutes;

INSERT INTO system_setting (setting_key, setting_value)
VALUES ('competition_help_content', '')
ON DUPLICATE KEY UPDATE setting_key = VALUES(setting_key);
