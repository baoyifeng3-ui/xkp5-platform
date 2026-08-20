ALTER TABLE subject
    ADD COLUMN score INT NOT NULL DEFAULT 0 AFTER subject_name,
    ADD COLUMN screenshot_requirement VARCHAR(2000) NULL AFTER answering;

ALTER TABLE system_setting
    MODIFY COLUMN setting_value LONGTEXT NULL;

INSERT INTO system_setting (setting_key, setting_value)
VALUES ('competition_content', '{}')
ON DUPLICATE KEY UPDATE setting_key = VALUES(setting_key);
