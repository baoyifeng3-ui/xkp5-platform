INSERT INTO processing_environment_slot
    (slot_id,agent_id,slot_number,user_id,created_by,created_at,updated_at)
SELECT UUID(),a.agent_id,n.slot_number,NULL,0,CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3)
FROM processing_agent a
JOIN (SELECT 1 slot_number UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4) n
LEFT JOIN processing_environment_slot s
    ON BINARY s.agent_id=BINARY a.agent_id AND s.slot_number=n.slot_number
WHERE a.removed_at IS NULL AND s.slot_id IS NULL;
