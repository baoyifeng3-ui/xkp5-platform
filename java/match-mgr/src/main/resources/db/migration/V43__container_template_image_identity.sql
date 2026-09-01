ALTER TABLE container_template
    ADD COLUMN image_digest VARCHAR(71) NULL AFTER image_reference;

UPDATE container_template t
JOIN image_artifact a ON BINARY a.registry_digest=BINARY t.image_reference
SET t.image_digest=a.registry_digest,
    t.image_reference=CONCAT(a.image_repository, ':', a.image_tag)
WHERE a.image_repository IS NOT NULL AND a.image_tag IS NOT NULL;
