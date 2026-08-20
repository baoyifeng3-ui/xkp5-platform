CREATE TABLE IF NOT EXISTS user (
    user_id INT NOT NULL AUTO_INCREMENT,
    user_name VARCHAR(255),
    password VARCHAR(255),
    PRIMARY KEY (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE IF NOT EXISTS count_down (
    count_down_id INT NOT NULL,
    count_down_time DATETIME,
    PRIMARY KEY (count_down_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS answer_sheet (
    answer_id INT NOT NULL AUTO_INCREMENT,
    user_id INT,
    subject_id INT,
    subject_type VARCHAR(255),
    answer_text VARCHAR(255),
    answer_img VARCHAR(1638),
    creation_time DATETIME,
    PRIMARY KEY (answer_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS notice (
    notice_id INT NOT NULL AUTO_INCREMENT,
    notice_content VARCHAR(255),
    creation_time DATETIME,
    PRIMARY KEY (notice_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS score (
    score_id INT NOT NULL AUTO_INCREMENT,
    user_id INT,
    score FLOAT,
    create_time DATETIME,
    PRIMARY KEY (score_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS subject (
    subject_id INT NOT NULL AUTO_INCREMENT,
    subject_creation_time DATETIME,
    modular VARCHAR(255),
    modular_name VARCHAR(255),
    test_paper_type VARCHAR(255),
    subject_type VARCHAR(255),
    subject_name VARCHAR(1000),
    answering VARCHAR(255),
    point VARCHAR(255),
    subject_identification VARCHAR(255),
    PRIMARY KEY (subject_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS teams (
    teams_id INT NOT NULL AUTO_INCREMENT,
    teams_name VARCHAR(255),
    teams_teacher VARCHAR(255),
    speed_progress INT,
    PRIMARY KEY (teams_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS teams_user (
    teams_id INT NOT NULL,
    user_id INT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS train_url (
    train_url_id INT NOT NULL AUTO_INCREMENT,
    user_id INT,
    vscode_url VARCHAR(255),
    cvat_url VARCHAR(255),
    t100_url VARCHAR(255),
    PRIMARY KEY (train_url_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
