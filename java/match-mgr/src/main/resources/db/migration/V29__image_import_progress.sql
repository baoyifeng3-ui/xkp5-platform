ALTER TABLE image_artifact
    ADD COLUMN import_stage VARCHAR(32) NULL AFTER import_state,
    ADD COLUMN import_progress TINYINT UNSIGNED NOT NULL DEFAULT 0 AFTER import_stage,
    ADD COLUMN import_completed_layers INT UNSIGNED NOT NULL DEFAULT 0 AFTER import_progress,
    ADD COLUMN import_total_layers INT UNSIGNED NOT NULL DEFAULT 0 AFTER import_completed_layers,
    ADD COLUMN import_updated_at DATETIME(3) NULL AFTER import_total_layers;

UPDATE image_artifact
SET import_stage = CASE
        WHEN import_state = 'READY' THEN 'COMPLETED'
        WHEN import_state = 'FAILED' THEN 'FAILED'
        WHEN import_state = 'IMPORTING' THEN 'COPYING_LAYERS'
        ELSE NULL
    END,
    import_progress = CASE WHEN import_state = 'READY' THEN 100 ELSE 0 END,
    import_updated_at = CASE
        WHEN import_state IN ('READY', 'FAILED', 'IMPORTING') THEN updated_at
        ELSE NULL
    END;
