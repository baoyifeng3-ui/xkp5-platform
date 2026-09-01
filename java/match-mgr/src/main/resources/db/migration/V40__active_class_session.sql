CREATE TABLE active_class_session (
    session_key VARCHAR(32) PRIMARY KEY,
    environment_id VARCHAR(36) NULL,
    course_id VARCHAR(36) NULL,
    active TINYINT(1) NOT NULL DEFAULT 0,
    started_by INT NULL,
    started_at DATETIME(3) NULL,
    updated_at DATETIME(3) NOT NULL
);

INSERT INTO active_class_session(session_key, active, updated_at)
VALUES ('CURRENT', 0, CURRENT_TIMESTAMP(3));
