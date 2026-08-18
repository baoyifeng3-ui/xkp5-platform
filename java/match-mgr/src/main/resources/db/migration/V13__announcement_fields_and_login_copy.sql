CREATE TABLE announcement_field (
    field_id INT NOT NULL AUTO_INCREMENT,
    field_key VARCHAR(64) NOT NULL,
    field_name VARCHAR(30) NOT NULL,
    field_type VARCHAR(16) NOT NULL,
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    sort_order INT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    updated_by INT NULL,
    PRIMARY KEY (field_id),
    UNIQUE KEY uk_announcement_field_key (field_key),
    UNIQUE KEY uk_announcement_field_name (field_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE announcement_field_value (
    value_id INT NOT NULL AUTO_INCREMENT,
    field_id INT NOT NULL,
    user_id INT NOT NULL,
    field_value VARCHAR(255) NOT NULL DEFAULT '',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (value_id),
    UNIQUE KEY uk_announcement_field_user (field_id, user_id),
    KEY idx_announcement_value_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO announcement_field (field_key, field_name, field_type, enabled, sort_order)
VALUES ('sequence', '序号', 'BUILT_IN', 1, 10),
       ('schoolName', '学校名称', 'BUILT_IN', 1, 20),
       ('contestantName', '参赛选手', 'BUILT_IN', 1, 30),
       ('teacherName', '带队老师', 'BUILT_IN', 1, 40);

INSERT INTO system_setting (setting_key, setting_value)
VALUES ('login_brand_name', '数智杯竞赛平台-登陆页'),
       ('login_title', '进入比赛工作台'),
       ('login_description', '参赛账号可在开放登录后进入平台，比赛开始前将显示赛前倒计时。'),
       ('login_copyright', '2026 数智杯人工智能比赛平台')
ON DUPLICATE KEY UPDATE setting_key = VALUES(setting_key);
