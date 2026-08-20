ALTER TABLE user
    ADD COLUMN enabled TINYINT(1) NOT NULL DEFAULT 1,
    ADD COLUMN must_change_password TINYINT(1) NOT NULL DEFAULT 0,
    ADD COLUMN created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    ADD UNIQUE KEY uk_user_name (user_name);

INSERT INTO user (user_name, password, enabled, must_change_password)
VALUES ('admin', 'admin', 1, 1)
ON DUPLICATE KEY UPDATE
    password = VALUES(password),
    enabled = 1,
    must_change_password = 1;

ALTER TABLE count_down
    ADD COLUMN start_time DATETIME NULL,
    ADD COLUMN status VARCHAR(32) NOT NULL DEFAULT 'NOT_STARTED',
    ADD COLUMN remaining_seconds BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN updated_at DATETIME NULL,
    ADD COLUMN updated_by INT NULL;

INSERT INTO count_down (count_down_id, count_down_time, status, remaining_seconds)
VALUES (1, NULL, 'NOT_STARTED', 0)
ON DUPLICATE KEY UPDATE
    count_down_time = NULL,
    start_time = NULL,
    status = 'NOT_STARTED',
    remaining_seconds = 0;

CREATE TABLE system_setting (
    setting_key VARCHAR(100) NOT NULL,
    setting_value VARCHAR(1000) NULL,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    updated_by INT NULL,
    PRIMARY KEY (setting_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO system_setting (setting_key, setting_value)
VALUES ('active_paper', '');

CREATE TABLE training_server (
    training_server_id INT NOT NULL AUTO_INCREMENT,
    server_name VARCHAR(255) NOT NULL,
    vscode_base_port INT NOT NULL DEFAULT 9090,
    cvat_base_port INT NOT NULL DEFAULT 8080,
    t100_base_port INT NOT NULL DEFAULT 5000,
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (training_server_id),
    UNIQUE KEY uk_training_server_name (server_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE training_node (
    training_node_id INT NOT NULL AUTO_INCREMENT,
    training_server_id INT NOT NULL,
    node_no INT NOT NULL,
    host VARCHAR(255) NOT NULL,
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    PRIMARY KEY (training_node_id),
    UNIQUE KEY uk_training_server_node (training_server_id, node_no),
    CONSTRAINT fk_training_node_server
        FOREIGN KEY (training_server_id) REFERENCES training_server (training_server_id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE user_training_assignment (
    assignment_id INT NOT NULL AUTO_INCREMENT,
    user_id INT NOT NULL,
    training_node_id INT NOT NULL,
    slot_no INT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (assignment_id),
    UNIQUE KEY uk_assignment_user (user_id),
    UNIQUE KEY uk_assignment_node_slot (training_node_id, slot_no),
    CONSTRAINT fk_assignment_user
        FOREIGN KEY (user_id) REFERENCES user (user_id),
    CONSTRAINT fk_assignment_node
        FOREIGN KEY (training_node_id) REFERENCES training_node (training_node_id)
        ON DELETE CASCADE,
    CONSTRAINT chk_assignment_slot CHECK (slot_no BETWEEN 1 AND 4)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
