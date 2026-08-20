CREATE TABLE user_session_activity (
    activity_id BIGINT NOT NULL AUTO_INCREMENT,
    session_digest CHAR(64) NOT NULL,
    user_id INT NOT NULL,
    login_at DATETIME(3) NOT NULL,
    last_activity_at DATETIME(3) NOT NULL,
    expires_at DATETIME(3) NOT NULL,
    PRIMARY KEY (activity_id),
    UNIQUE KEY uk_user_session_activity_digest (session_digest),
    KEY idx_user_session_activity_expiry (expires_at),
    KEY idx_user_session_activity_user_expiry (user_id, expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
