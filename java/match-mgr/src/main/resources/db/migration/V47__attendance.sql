CREATE TABLE attendance_session (
    session_id VARCHAR(36) PRIMARY KEY,
    status VARCHAR(16) NOT NULL,
    started_at DATETIME(3) NOT NULL,
    ended_at DATETIME(3) NULL
);
CREATE TABLE attendance_record (
    record_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id VARCHAR(36) NOT NULL,
    user_id INT NOT NULL,
    checked_at DATETIME(3) NOT NULL,
    UNIQUE KEY uk_attendance_user (session_id, user_id),
    KEY idx_attendance_session (session_id)
);
