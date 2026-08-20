CREATE TABLE paper_definition (
    paper_type VARCHAR(1) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by INT NULL,
    PRIMARY KEY (paper_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO paper_definition (paper_type)
VALUES ('A'), ('B')
ON DUPLICATE KEY UPDATE paper_type = VALUES(paper_type);
