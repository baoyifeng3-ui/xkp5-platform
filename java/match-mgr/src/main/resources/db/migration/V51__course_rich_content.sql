ALTER TABLE course
  ADD COLUMN introduction_html LONGTEXT NULL AFTER description,
  ADD COLUMN outline_html LONGTEXT NULL AFTER introduction_html;

ALTER TABLE course_resource_link
  ADD COLUMN display_name VARCHAR(255) NULL AFTER resource_type;
