ALTER TABLE processing_agent_audit
  ADD COLUMN command_id CHAR(36) NULL AFTER token_id,
  ADD INDEX idx_agent_audit_command (command_id, created_at);
