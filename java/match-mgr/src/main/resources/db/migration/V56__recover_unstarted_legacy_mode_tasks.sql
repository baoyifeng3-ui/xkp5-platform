-- Legacy preflight failures have no container steps. Only cancel an entry whose
-- platform is still in its original TRAINING mode; never reset a dispatched task.
UPDATE mode_transition t
JOIN processing_agent_mode m ON BINARY m.active_transition_id = BINARY t.transition_id
JOIN platform_mode p ON p.singleton_id = 1 AND p.mode = 'TRAINING'
SET t.state = 'CANCELLED', t.active_transition_key = NULL,
    t.failure_summary = 'Cancelled legacy preflight failure; no container steps were dispatched',
    t.completed_at = UTC_TIMESTAMP(3), t.updated_at = UTC_TIMESTAMP(3),
    m.desired_mode = 'TRAINING', m.actual_mode = 'NORMAL',
    m.active_transition_id = NULL, m.lock_version = m.lock_version + 1,
    m.updated_at = UTC_TIMESTAMP(3)
WHERE t.state = 'DEGRADED' AND t.source_mode = 'TRAINING'
  AND t.target_mode = 'COMPETITION' AND t.failure_summary = 'ENVIRONMENT_OPERATION_ACTIVE'
  AND NOT EXISTS (SELECT 1 FROM mode_transition_step s WHERE BINARY s.transition_id = BINARY t.transition_id);

INSERT INTO processing_agent_mode (agent_id, desired_mode, actual_mode, lock_version, updated_at)
SELECT a.agent_id, 'TRAINING', 'NORMAL', 0, UTC_TIMESTAMP(3)
FROM processing_agent a JOIN platform_mode p ON p.singleton_id = 1 AND p.mode = 'TRAINING'
WHERE NOT EXISTS (SELECT 1 FROM processing_agent_mode m WHERE BINARY m.agent_id = BINARY a.agent_id)
  AND NOT EXISTS (SELECT 1 FROM mode_transition t WHERE BINARY t.agent_id = BINARY a.agent_id
                  AND t.state IN ('PENDING','RUNNING','DEGRADED'));
