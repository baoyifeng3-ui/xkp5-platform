INSERT INTO system_setting (setting_key, setting_value)
VALUES ('theme_color', '#162d45')
ON DUPLICATE KEY UPDATE
    setting_value = CASE
        WHEN setting_value IS NULL OR setting_value = '' OR LOWER(setting_value) = '#e0b43c'
            THEN VALUES(setting_value)
        ELSE setting_value
    END;
