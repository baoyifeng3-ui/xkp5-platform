param([string]$Container = 'xkp5test-mysql-1')
$ErrorActionPreference = 'Stop'
# Temporary tables inherit the deployed schema and disappear with this connection.
$sql = @'
CREATE TEMPORARY TABLE training_test LIKE training_environment;
CREATE TEMPORARY TABLE ports_test LIKE environment_port_allocation;
CREATE TEMPORARY TABLE operations_test LIKE environment_operation;
START TRANSACTION;
INSERT INTO training_test (environment_id,user_id,course_id,agent_id,slot_id,slot_number,workspace_relative_path,desired_state,actual_state,annotation_container_state,editor_container_state,created_by,created_at,updated_by,updated_at)
VALUES ('test-env',2147483000,'test-course','test-agent','test-slot',1,'training/test','STOPPED','STOPPED','STOPPED','STOPPED',1,NOW(),1,NOW());
SELECT 'create', COUNT(*)=1 FROM training_test WHERE environment_id='test-env';
UPDATE training_test SET actual_state='STARTING',lock_version=lock_version+1 WHERE environment_id='test-env' AND lock_version=0;
SELECT 'optimistic_update', ROW_COUNT()=1;
UPDATE training_test SET actual_state='ERROR' WHERE environment_id='test-env' AND lock_version=0;
SELECT 'stale_update_rejected', ROW_COUNT()=0;
INSERT IGNORE INTO training_test (environment_id,user_id,course_id,agent_id,slot_id,slot_number,workspace_relative_path,desired_state,actual_state,annotation_container_state,editor_container_state,created_by,created_at,updated_by,updated_at)
VALUES ('duplicate',2147483000,'test-course','test-agent','test-slot',1,'training/test','STOPPED','STOPPED','STOPPED','STOPPED',1,NOW(),1,NOW());
SELECT 'assignment_unique', ROW_COUNT()=0;
INSERT INTO ports_test VALUES ('port-1','test-env','test-slot','test-agent','EDITOR',9090,9091,'tcp',NOW());
INSERT IGNORE INTO ports_test VALUES ('port-2','other-env','other-slot','test-agent','EDITOR',9090,9091,'tcp',NOW());
SELECT 'host_port_unique', ROW_COUNT()=0;
INSERT INTO operations_test (operation_id,environment_id,operation_type,actor_user_id,actor_role,state,active_operation_key,correlation_id,requested_at,updated_at)
VALUES ('op-1','test-env','START',1,'ADMIN','PENDING','test-env:START','test-correlation',NOW(),NOW());
INSERT IGNORE INTO operations_test (operation_id,environment_id,operation_type,actor_user_id,actor_role,state,active_operation_key,correlation_id,requested_at,updated_at)
VALUES ('op-2','test-env','START',1,'ADMIN','PENDING','test-env:START','test-correlation',NOW(),NOW());
SELECT 'active_operation_unique', ROW_COUNT()=0;
DELETE FROM ports_test WHERE environment_id='test-env';
SELECT 'port_release', COUNT(*)=0 FROM ports_test;
DELETE FROM training_test WHERE environment_id='test-env';
SELECT 'delete', COUNT(*)=0 FROM training_test;
ROLLBACK;
SELECT 'rollback', COUNT(*)=0 FROM operations_test;
'@
$result = $sql | docker exec -i $Container sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot -N -B match'
if ($LASTEXITCODE -ne 0) { throw 'Database checks failed to execute' }
if (@($result).Count -ne 9 -or @($result | Where-Object { $_ -notmatch '\s1$' }).Count) { throw ($result -join "`n") }
$result
Write-Output 'Training database checks: 9/9 passed; no persistent rows created.'
