-- H2 测试库 schema（与 sql/DDL.sql 表结构对齐，省略 MySQL 专有语法）
CREATE TABLE IF NOT EXISTS users
(
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    username   VARCHAR(50)  NOT NULL UNIQUE,
    password   VARCHAR(100) NOT NULL,
    email      VARCHAR(100),
    enabled    TINYINT(1) DEFAULT 1,
    role       VARCHAR(20) NOT NULL DEFAULT 'USER',
    signup_from VARCHAR(64),
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
    point_dictionary_version VARCHAR(64),
    edit_annotations     TEXT,
    created_at           TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMP   DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS conversation_analysis_item
(
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    analysis_id       VARCHAR(64)  NOT NULL,
    sentence_id       BIGINT       NOT NULL,
    original_sentence TEXT         NOT NULL,
    point_id          VARCHAR(48),
    error_point       VARCHAR(500) NOT NULL,
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

CREATE TABLE IF NOT EXISTS growth_card
(
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    card_id            VARCHAR(64)  NOT NULL,
    user_id            BIGINT       NOT NULL,
    type               VARCHAR(16)  NOT NULL,
    status             VARCHAR(16)  NOT NULL,
    next_due_at        DATE,
    front              TEXT         NOT NULL,
    back               TEXT         NOT NULL,
    source_analysis_id VARCHAR(64),
    source_ref         VARCHAR(128) NOT NULL,
    evidence_json      TEXT,
    created_at         TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (card_id),
    UNIQUE (user_id, source_analysis_id, type, source_ref)
);

CREATE TABLE IF NOT EXISTS growth_card_evidence
(
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    card_id            VARCHAR(64)  NOT NULL,
    user_id            BIGINT       NOT NULL,
    source_analysis_id VARCHAR(64)  NOT NULL,
    sentence_id        VARCHAR(64),
    track_key          VARCHAR(190) NOT NULL,
    original_sentence  TEXT         NOT NULL,
    suggestion         TEXT,
    sort_order         INT          NOT NULL DEFAULT 0,
    created_at         TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (card_id, track_key)
);

CREATE TABLE IF NOT EXISTS voice_practice_session
(
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id               BIGINT    NOT NULL,
    started_at            TIMESTAMP NOT NULL,
    ended_at              TIMESTAMP NULL,
    wall_duration_seconds INT       NOT NULL DEFAULT 0,
    reserved_seconds      INT       NOT NULL DEFAULT 0,
    stopped_by_cap        TINYINT(1) NOT NULL DEFAULT 0,
    transcript            TEXT,
    transcript_saved      TINYINT(1) NOT NULL DEFAULT 0,
    created_at            TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS product_funnel_event
(
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id          BIGINT,
    event_name       VARCHAR(40) NOT NULL,
    guest            TINYINT(1),
    duration_seconds INT,
    voice_session_id BIGINT,
    created_at       TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS extension_connect_session
(
    id             VARCHAR(36)  NOT NULL PRIMARY KEY,
    code_challenge VARCHAR(128) NOT NULL,
    status         VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    user_id        BIGINT,
    expires_at     TIMESTAMP    NOT NULL,
    authorized_at  TIMESTAMP,
    exchanged_at   TIMESTAMP,
    created_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS extension_credential
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id      BIGINT       NOT NULL,
    token_hash   CHAR(64)     NOT NULL,
    label        VARCHAR(64),
    expires_at   TIMESTAMP    NOT NULL,
    revoked_at   TIMESTAMP,
    last_used_at TIMESTAMP,
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS conversation_analysis_task
(
    id                     BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id                VARCHAR(36)  NOT NULL,
    user_id                BIGINT       NOT NULL,
    status                 VARCHAR(20)  NOT NULL DEFAULT 'QUEUED',
    conversation_content   CLOB         NOT NULL,
    content_fingerprint    CHAR(64)     NOT NULL,
    source_type            VARCHAR(32)  NOT NULL,
    source_conversation_id VARCHAR(64),
    model_id               VARCHAR(100),
    analysis_id            VARCHAR(64),
    error_message          CLOB,
    created_at             TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at             TIMESTAMP,
    finished_at            TIMESTAMP,
    updated_at             TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
