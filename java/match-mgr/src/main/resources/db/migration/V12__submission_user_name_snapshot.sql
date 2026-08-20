ALTER TABLE paper_submission
    ADD COLUMN user_name VARCHAR(255) NULL AFTER user_id;

UPDATE paper_submission ps
LEFT JOIN user u ON u.user_id = ps.user_id
SET ps.user_name = COALESCE(NULLIF(TRIM(u.user_name), ''), CONCAT('user-', ps.user_id))
WHERE ps.user_name IS NULL OR ps.user_name = '';

ALTER TABLE paper_submission
    MODIFY COLUMN user_name VARCHAR(255) NOT NULL;
