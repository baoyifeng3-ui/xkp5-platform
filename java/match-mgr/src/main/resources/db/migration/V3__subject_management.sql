ALTER TABLE subject
    ADD COLUMN subject_options TEXT NULL AFTER subject_type,
    ADD COLUMN sort_order INT NOT NULL DEFAULT 0 AFTER subject_options;

ALTER TABLE answer_sheet
    MODIFY COLUMN answer_text TEXT NULL;

UPDATE subject SET subject_type = 'fill_blank' WHERE subject_type = '1';
UPDATE subject SET subject_type = 'practical' WHERE subject_type = '2';
UPDATE answer_sheet SET subject_type = 'fill_blank' WHERE subject_type = '1';
UPDATE answer_sheet SET subject_type = 'practical' WHERE subject_type = '2';
