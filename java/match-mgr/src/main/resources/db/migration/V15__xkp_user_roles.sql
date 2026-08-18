ALTER TABLE user ADD COLUMN role VARCHAR(32) NULL AFTER is_admin;

UPDATE user
SET role = CASE
    WHEN LOWER(user_name) = 'admin' THEN 'SUPER_ADMIN'
    WHEN is_admin = 1 THEN 'ADMIN'
    ELSE 'USER'
END
WHERE role IS NULL;

ALTER TABLE user MODIFY COLUMN role VARCHAR(32) NOT NULL DEFAULT 'USER';
CREATE INDEX idx_user_role_enabled ON user (role, enabled);
