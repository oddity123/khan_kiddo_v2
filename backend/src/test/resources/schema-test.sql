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
    conversation_content   CLOB,
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

CREATE INDEX IF NOT EXISTS idx_analysis_task_user_source
    ON conversation_analysis_task (user_id, source_type, source_conversation_id, created_at);

CREATE TABLE IF NOT EXISTS weakness_memory
(
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id             BIGINT        NOT NULL,
    title               VARCHAR(255)  NOT NULL,
    memory_type         VARCHAR(32)   NOT NULL,
    family_id           VARCHAR(100)  NOT NULL,
    point_id            VARCHAR(100)  NOT NULL,
    status              VARCHAR(32)   NOT NULL DEFAULT 'NEW',
    hit_count           INT           NOT NULL DEFAULT 0,
    recent_hit_count    INT           NOT NULL DEFAULT 0,
    practiced_count     INT           NOT NULL DEFAULT 0,
    correct_count       INT           NOT NULL DEFAULT 0,
    mastery_level       DECIMAL(5, 2) NOT NULL DEFAULT 0.00,
    consecutive_correct INT           NOT NULL DEFAULT 0,
    first_seen_at       TIMESTAMP     NOT NULL,
    last_seen_at        TIMESTAMP     NOT NULL,
    last_practiced_at   TIMESTAMP,
    next_review_at      TIMESTAMP,
    created_at          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (user_id, point_id)
);

CREATE INDEX IF NOT EXISTS idx_weakness_memory_user_status
    ON weakness_memory (user_id, status, last_seen_at);

CREATE INDEX IF NOT EXISTS idx_weakness_memory_user_review
    ON weakness_memory (user_id, status, next_review_at);

CREATE TABLE IF NOT EXISTS memory_evidence
(
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    memory_id      BIGINT       NOT NULL,
    user_id        BIGINT       NOT NULL,
    analysis_id    VARCHAR(64),
    sentence_id    VARCHAR(64),
    evidence_type  VARCHAR(32)  NOT NULL,
    dedup_key      VARCHAR(160) NOT NULL,
    original_text  TEXT,
    corrected_text TEXT,
    confidence     DECIMAL(5, 4),
    created_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (memory_id, dedup_key)
);

CREATE INDEX IF NOT EXISTS idx_memory_evidence_memory_time
    ON memory_evidence (memory_id, created_at);

CREATE INDEX IF NOT EXISTS idx_memory_evidence_user_analysis
    ON memory_evidence (user_id, analysis_id);

CREATE TABLE IF NOT EXISTS practice_session
(
    id                     BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id                BIGINT       NOT NULL,
    memory_id              BIGINT       NOT NULL,
    practice_type          VARCHAR(32)  NOT NULL,
    target_point_id        VARCHAR(100) NOT NULL,
    status                 VARCHAR(32)  NOT NULL DEFAULT 'IN_PROGRESS',
    source_conversation_id VARCHAR(64),
    analysis_id            VARCHAR(64),
    total_attempts         INT          NOT NULL DEFAULT 0,
    correct_attempts       INT          NOT NULL DEFAULT 0,
    started_at             TIMESTAMP    NOT NULL,
    completed_at           TIMESTAMP,
    created_at             TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_practice_session_user_memory
    ON practice_session (user_id, memory_id);

CREATE INDEX IF NOT EXISTS idx_practice_session_user_status
    ON practice_session (user_id, status);

CREATE INDEX IF NOT EXISTS idx_practice_session_user_conv
    ON practice_session (user_id, source_conversation_id);

CREATE INDEX IF NOT EXISTS idx_practice_session_analysis
    ON practice_session (user_id, analysis_id);

CREATE TABLE IF NOT EXISTS practice_attempt
(
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id        BIGINT        NOT NULL,
    attempt_no        INT           NOT NULL,
    user_response     TEXT,
    expected_response TEXT,
    is_correct        TINYINT(1)    NOT NULL DEFAULT 0,
    independent       TINYINT(1)    NOT NULL DEFAULT 0,
    hint_used         TINYINT(1)    NOT NULL DEFAULT 0,
    score             DECIMAL(5, 2) NOT NULL DEFAULT 0.00,
    feedback          TEXT,
    evaluation_json   TEXT,
    created_at        TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (session_id, attempt_no)
);

CREATE INDEX IF NOT EXISTS idx_practice_attempt_session
    ON practice_attempt (session_id);
