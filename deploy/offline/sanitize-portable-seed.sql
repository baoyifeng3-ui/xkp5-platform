-- Applied only to the exported release snapshot. Never run against the source platform.
SET FOREIGN_KEY_CHECKS = 0;

DELETE FROM terminal_polling_output;
DELETE FROM processing_agent_terminal_session;
DELETE FROM temporary_training_transfer;
DELETE FROM active_class_session;
DELETE FROM attendance_record;
DELETE FROM attendance_session;

DELETE FROM mode_training_snapshot;
DELETE FROM mode_transition_step;
DELETE FROM mode_transition;
DELETE FROM competition_credential;
DELETE FROM processing_agent_mode;

DELETE FROM account_environment_migration;
DELETE FROM environment_operation;
DELETE FROM environment_port_allocation;
DELETE FROM competition_environment;
DELETE FROM training_environment;
DELETE FROM environment_port_pool;
DELETE FROM processing_environment_slot;
DELETE FROM container_template;

DELETE FROM image_deployment;

DELETE FROM processing_agent_command;
DELETE FROM processing_agent_metric_minute;
DELETE FROM processing_agent_audit;
DELETE FROM processing_agent_registration_token;
DELETE FROM processing_agent;

DELETE FROM user_session_activity;
DELETE FROM user_training_assignment;
DELETE FROM training_node;
DELETE FROM training_server;
DELETE FROM train_url;

DELETE FROM license_audit;
DELETE FROM platform_license;
DELETE FROM license_request;
DELETE FROM platform_installation;

UPDATE platform_mode
SET mode = 'TRAINING', generation = generation + 1, changed_by = 0, changed_at = UTC_TIMESTAMP(3)
WHERE singleton_id = 1;

SET FOREIGN_KEY_CHECKS = 1;
