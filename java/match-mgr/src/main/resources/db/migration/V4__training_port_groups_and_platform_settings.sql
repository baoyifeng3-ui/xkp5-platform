ALTER TABLE user_training_assignment
    DROP INDEX uk_assignment_user,
    ADD UNIQUE KEY uk_assignment_user_slot (user_id, slot_no);

INSERT INTO system_setting (setting_key, setting_value)
VALUES
    ('theme_color', '#e0b43c'),
    ('login_background_url', '')
ON DUPLICATE KEY UPDATE setting_key = VALUES(setting_key);
