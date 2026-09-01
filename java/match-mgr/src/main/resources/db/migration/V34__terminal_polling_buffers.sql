SET @sql = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='processing_agent_terminal_session' AND column_name='polling_output_cursor')=0,
    'ALTER TABLE processing_agent_terminal_session ADD COLUMN polling_output_cursor BIGINT UNSIGNED NOT NULL DEFAULT 0','SELECT 1');
PREPARE terminal_stmt FROM @sql;
EXECUTE terminal_stmt;
DEALLOCATE PREPARE terminal_stmt;
SET @sql = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='processing_agent_terminal_session' AND column_name='polling_last_input_cursor')=0,
    'ALTER TABLE processing_agent_terminal_session ADD COLUMN polling_last_input_cursor BIGINT UNSIGNED NOT NULL DEFAULT 0','SELECT 1');
PREPARE terminal_stmt FROM @sql;
EXECUTE terminal_stmt;
DEALLOCATE PREPARE terminal_stmt;

CREATE TABLE terminal_polling_output (
    session_id VARCHAR(36) COLLATE utf8mb4_unicode_ci NOT NULL,
    output_cursor BIGINT UNSIGNED NOT NULL,
    output_data MEDIUMBLOB NOT NULL,
    created_at DATETIME(3) NOT NULL,
    PRIMARY KEY (session_id, output_cursor),
    CONSTRAINT fk_terminal_polling_output_session
        FOREIGN KEY (session_id) REFERENCES processing_agent_terminal_session(session_id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_terminal_polling_output_created
    ON terminal_polling_output (session_id, created_at);
