ALTER TABLE user
    ADD COLUMN is_admin TINYINT(1) NOT NULL DEFAULT 0;

UPDATE user
SET is_admin = 1
WHERE LOWER(user_name) = 'admin';
