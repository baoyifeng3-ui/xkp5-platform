ALTER TABLE container_template
    ADD COLUMN mps_enabled TINYINT(1) NOT NULL DEFAULT 0 AFTER gpu_enabled;
