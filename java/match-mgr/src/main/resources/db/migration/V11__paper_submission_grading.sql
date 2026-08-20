ALTER TABLE subject
    ADD COLUMN correct_answer TEXT NULL AFTER subject_options;

CREATE TABLE paper_submission (
    submission_id INT NOT NULL AUTO_INCREMENT,
    user_id INT NOT NULL,
    paper_type VARCHAR(16) NOT NULL,
    revision INT NOT NULL,
    status VARCHAR(32) NOT NULL,
    current_flag TINYINT NULL,
    objective_score INT NOT NULL DEFAULT 0,
    practical_score INT NULL,
    total_score INT NULL,
    submitted_at DATETIME NOT NULL,
    graded_at DATETIME NULL,
    returned_at DATETIME NULL,
    return_reason VARCHAR(1000) NULL,
    PRIMARY KEY (submission_id),
    UNIQUE KEY uk_submission_revision (user_id, paper_type, revision),
    UNIQUE KEY uk_submission_current (user_id, paper_type, current_flag),
    KEY idx_submission_paper_status (paper_type, status),
    KEY idx_submission_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE paper_submission_answer (
    submission_answer_id INT NOT NULL AUTO_INCREMENT,
    submission_id INT NOT NULL,
    subject_id INT NOT NULL,
    subject_type VARCHAR(32) NOT NULL,
    modular VARCHAR(255) NULL,
    modular_name VARCHAR(255) NULL,
    sort_order INT NOT NULL DEFAULT 0,
    subject_name VARCHAR(1000) NOT NULL,
    subject_options TEXT NULL,
    answering TEXT NULL,
    screenshot_requirement TEXT NULL,
    max_score INT NOT NULL DEFAULT 0,
    correct_answer TEXT NULL,
    answer_text TEXT NULL,
    answer_img TEXT NULL,
    awarded_score INT NULL,
    grading_method VARCHAR(16) NOT NULL,
    PRIMARY KEY (submission_answer_id),
    UNIQUE KEY uk_submission_subject (submission_id, subject_id),
    KEY idx_submission_answer_submission (submission_id),
    CONSTRAINT fk_submission_answer_submission
        FOREIGN KEY (submission_id) REFERENCES paper_submission (submission_id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE paper_export_batch (
    export_batch_id INT NOT NULL AUTO_INCREMENT,
    paper_type VARCHAR(16) NOT NULL,
    created_by INT NOT NULL,
    created_by_name VARCHAR(255) NOT NULL,
    created_at DATETIME NOT NULL,
    export_key VARCHAR(64) NOT NULL,
    submission_ids TEXT NOT NULL,
    pdf_count INT NOT NULL,
    status VARCHAR(16) NOT NULL,
    PRIMARY KEY (export_batch_id),
    KEY idx_export_batch_paper_created (paper_type, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
