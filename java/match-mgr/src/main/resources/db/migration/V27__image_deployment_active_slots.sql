ALTER TABLE image_deployment
    ADD COLUMN active_agent_component_key VARCHAR(128) NULL AFTER active_deployment_key,
    ADD COLUMN command_id VARCHAR(36) NULL AFTER active_agent_component_key,
    ADD UNIQUE KEY uk_image_deployment_agent_component_active (active_agent_component_key),
    ADD KEY idx_image_deployment_command (command_id);
