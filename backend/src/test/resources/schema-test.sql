-- H2 测试库 schema（与 sql/DDL.sql 表结构对齐，省略 MySQL 专有语法）
CREATE TABLE IF NOT EXISTS users
(
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    username   VARCHAR(50)  NOT NULL UNIQUE,
    password   VARCHAR(100) NOT NULL,
    email      VARCHAR(100),
    enabled    TINYINT(1) DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS conversation_analysis
(
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id              BIGINT      NOT NULL DEFAULT 1,
    analysis_id          VARCHAR(64) NOT NULL UNIQUE,
    conversation_content TEXT        NOT NULL,
    status               VARCHAR(20) NOT NULL DEFAULT 'success',
    error_message        TEXT,
    processing_time_ms   BIGINT      DEFAULT 0,
    educational_summary  TEXT,
    llm_model_id         VARCHAR(100),
    llm_model_name       VARCHAR(160),
    llm_provider         VARCHAR(60),
    created_at           TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMP   DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS conversation_analysis_item
(
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    analysis_id       VARCHAR(64)  NOT NULL,
    sentence_id       BIGINT       NOT NULL,
    original_sentence TEXT         NOT NULL,
    problem_types     VARCHAR(100) NOT NULL,
    error_point       VARCHAR(200) NOT NULL,
    suggestion        TEXT         NOT NULL,
    created_at        TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS user_feedback
(
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT,
    title      VARCHAR(200) NOT NULL,
    email      VARCHAR(100),
    content    TEXT         NOT NULL,
    created_at TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);
