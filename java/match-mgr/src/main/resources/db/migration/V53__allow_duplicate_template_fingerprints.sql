SET @template_fingerprint_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.statistics
           WHERE table_schema = DATABASE() AND table_name = 'container_template'
             AND index_name = 'uk_container_template_fingerprint'),
    'ALTER TABLE container_template DROP INDEX uk_container_template_fingerprint, ADD KEY idx_container_template_fingerprint (config_fingerprint)',
    IF(EXISTS(SELECT 1 FROM information_schema.statistics
              WHERE table_schema = DATABASE() AND table_name = 'container_template'
                AND index_name = 'idx_container_template_fingerprint'),
       'SELECT 1',
       'ALTER TABLE container_template ADD KEY idx_container_template_fingerprint (config_fingerprint)')
);
PREPARE template_fingerprint_stmt FROM @template_fingerprint_sql;
EXECUTE template_fingerprint_stmt;
DEALLOCATE PREPARE template_fingerprint_stmt;
