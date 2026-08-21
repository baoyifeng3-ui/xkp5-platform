ALTER TABLE image_deployment
    ADD COLUMN active_agent_component_key VARCHAR(128) NULL AFTER active_deployment_key,
    ADD COLUMN command_id VARCHAR(36) NULL AFTER active_agent_component_key,
    ADD COLUMN idempotency_key VARCHAR(128) NULL AFTER command_id,
    ADD UNIQUE KEY uk_image_deployment_agent_component_active (active_agent_component_key),
    ADD UNIQUE KEY uk_image_deployment_agent_component_idempotency (agent_id, component_type, idempotency_key),
    ADD KEY idx_image_deployment_command (command_id);

UPDATE image_deployment SET idempotency_key = SUBSTRING_INDEX(active_deployment_key, ':', -1)
    WHERE idempotency_key IS NULL AND active_deployment_key IS NOT NULL;
UPDATE image_deployment SET idempotency_key = deployment_id WHERE idempotency_key IS NULL;
ALTER TABLE image_deployment MODIFY COLUMN idempotency_key VARCHAR(128) NOT NULL;
