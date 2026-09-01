CREATE TABLE temporary_training_transfer (
 transfer_id VARCHAR(36) PRIMARY KEY,user_id INT NOT NULL,environment_id VARCHAR(36) NOT NULL,
 file_name VARCHAR(255) NOT NULL,storage_key VARCHAR(512) NOT NULL,sha256 CHAR(64) NOT NULL,
 state VARCHAR(24) NOT NULL,command_id VARCHAR(36) NULL,expires_at DATETIME(3) NOT NULL,
 created_at DATETIME(3) NOT NULL,updated_at DATETIME(3) NOT NULL,INDEX idx_transfer_command(command_id),INDEX idx_transfer_expiry(state,expires_at)
);
