INSERT INTO system_setting (setting_key, setting_value)
VALUES ('platform_name', 'XKP5.0平台'),
       ('login_brand_name', 'XKP5.0平台'),
       ('login_title', '进入平台工作台'),
       ('login_description', '请使用已分配的平台账号登录。'),
       ('login_copyright', '2026 XKP5.0平台')
ON DUPLICATE KEY UPDATE setting_value = VALUES(setting_value);
