ALTER TABLE container_template ADD COLUMN image_id VARCHAR(71) NULL;
ALTER TABLE container_template ADD COLUMN image_file_id CHAR(36) NULL;
CREATE INDEX idx_template_image_id ON container_template(image_id);
