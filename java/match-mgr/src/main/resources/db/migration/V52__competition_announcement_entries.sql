CREATE TABLE competition_announcement_entry (
  entry_id INT NOT NULL AUTO_INCREMENT,
  values_json JSON NOT NULL,
  sort_order INT NOT NULL DEFAULT 0,
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (entry_id),
  KEY idx_competition_announcement_sort (sort_order, entry_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
