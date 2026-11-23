-- Khan Kiddo v2 数据库初始化（合并原 v1 DDL + llm 相关字段，不含未迁移功能表）
CREATE DATABASE IF NOT EXISTS `khan_kiddo_dev`
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE `khan_kiddo_dev`;

-- 用户表
CREATE TABLE IF NOT EXISTS `users`
(
    `id`         BIGINT PRIMARY KEY AUTO_INCREMENT,
    `username`   VARCHAR(50)  NOT NULL UNIQUE,
    `password`   VARCHAR(100) NOT NULL,
    `email`      VARCHAR(100),
    `enabled`    TINYINT(1) DEFAULT 1,
    `role`       VARCHAR(20)  NOT NULL DEFAULT 'USER' COMMENT 'USER 或 ADMIN',
    `signup_from` VARCHAR(64)          DEFAULT NULL COMMENT '注册来源，来自 query from',
    `created_at` DATETIME   DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME   DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX `idx_username` (`username`)
) ENGINE = InnoDB COMMENT ='用户表';

-- 已有库升级（新建库已通过 CREATE TABLE 包含 role、signup_from 列）：
-- ALTER TABLE users ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'USER' COMMENT 'USER 或 ADMIN' AFTER enabled;
-- UPDATE users SET role = 'ADMIN' WHERE username = 'admin';
-- ALTER TABLE users ADD COLUMN signup_from VARCHAR(64) NULL COMMENT '注册来源，来自 query from' AFTER role;

-- 对话分析主表
CREATE TABLE IF NOT EXISTS `conversation_analysis`
(
    `id`                   BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    `user_id`              BIGINT      NOT NULL DEFAULT 1 COMMENT '用户ID',
    `analysis_id`          VARCHAR(64) NOT NULL UNIQUE COMMENT '分析ID（UUID）',
    `conversation_content` TEXT        NOT NULL COMMENT '原始对话内容',
    `status`               VARCHAR(20) NOT NULL DEFAULT 'success' COMMENT '分析状态: success, error',
    `error_message`        TEXT                 DEFAULT NULL COMMENT '错误消息（如果失败）',
    `processing_time_ms`   BIGINT               DEFAULT 0 COMMENT '处理耗时(毫秒)',
    `educational_summary`  TEXT                 DEFAULT NULL COMMENT '教育性总结（JSON格式）',
    `llm_model_id`         VARCHAR(100)         DEFAULT NULL COMMENT '用户选择的模型配置ID（ModelConfig#id）',
    `llm_model_name`       VARCHAR(160)         DEFAULT NULL COMMENT '厂商侧真实模型 ID（ModelConfig#modelName）',
    `llm_provider`         VARCHAR(60)          DEFAULT NULL COMMENT '模型供应商（ModelConfig#provider）',
    `point_dictionary_version` VARCHAR(64)      DEFAULT NULL COMMENT '分析时知识点字典版本',
    `edit_annotations`         MEDIUMTEXT               DEFAULT NULL COMMENT '句级 ERRANT 操作批注 JSON（tokens + R/M/U edits）',
    `created_at`           DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`           DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_analysis_id` (`analysis_id`),
    INDEX `idx_user_created` (`user_id`, `created_at`),
    INDEX `idx_status` (`status`)
) ENGINE = InnoDB COMMENT = '对话分析主表';

-- 对话分析明细表（存储每个句子的分析结果）
CREATE TABLE IF NOT EXISTS `conversation_analysis_item`
(
    `id`                BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    `analysis_id`       VARCHAR(64)  NOT NULL COMMENT '分析ID（关联conversation_analysis.analysis_id）',
    `sentence_id`       BIGINT       NOT NULL COMMENT '句子ID（同一个句子的不同错误使用相同的sentenceId）',
    `original_sentence` TEXT         NOT NULL COMMENT '用户原句',
    `point_id`          VARCHAR(48)           NULL COMMENT '知识点叶子 pointId',
    `error_point`       VARCHAR(500) NOT NULL COMMENT '错误点描述',
    `suggestion`        TEXT         NOT NULL COMMENT '修改建议或正确英文表达',
    `created_at`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX `idx_analysis_id` (`analysis_id`),
    INDEX `idx_sentence_id` (`sentence_id`),
    INDEX `idx_analysis_sentence` (`analysis_id`, `sentence_id`),
    INDEX `idx_point_id` (`point_id`) COMMENT '知识点索引，用于统计与筛选'
) ENGINE = InnoDB COMMENT = '对话分析明细表';

-- 已存在数据库需手动执行：
-- ALTER TABLE conversation_analysis ADD COLUMN point_dictionary_version VARCHAR(64) NULL COMMENT '分析时知识点字典版本' AFTER llm_provider;
-- ALTER TABLE conversation_analysis ADD COLUMN edit_annotations MEDIUMTEXT NULL COMMENT '句级 ERRANT 操作批注 JSON（tokens + R/M/U edits）' AFTER point_dictionary_version;
-- ALTER TABLE conversation_analysis_item DROP COLUMN problem_types;
-- ALTER TABLE conversation_analysis_item ADD INDEX idx_point_id (point_id);

-- 用户反馈/留言表
CREATE TABLE IF NOT EXISTS `user_feedback`
(
    `id`         BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    `user_id`    BIGINT                DEFAULT NULL COMMENT '用户ID（未登录则为空）',
    `title`      VARCHAR(200) NOT NULL COMMENT '反馈标题',
    `email`      VARCHAR(100)          DEFAULT NULL COMMENT '联系方式邮箱（选填）',
    `content`    TEXT         NOT NULL COMMENT '反馈内容（支持 Markdown）',
    `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_created_at` (`created_at`)
) ENGINE = InnoDB COMMENT = '用户反馈/留言表';

-- 成长卡
CREATE TABLE IF NOT EXISTS `growth_card`
(
    `id`                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    `card_id`             VARCHAR(64)  NOT NULL,
    `user_id`             BIGINT       NOT NULL,
    `type`                VARCHAR(16)  NOT NULL COMMENT 'habit|vocab|expression',
    `status`              VARCHAR(16)  NOT NULL COMMENT 'unfamiliar|fuzzy|mastered',
    `next_due_at`         DATE                  DEFAULT NULL,
    `front`               TEXT         NOT NULL,
    `back`                TEXT         NOT NULL,
    `source_analysis_id`  VARCHAR(64)           DEFAULT NULL,
    `source_ref`          VARCHAR(128) NOT NULL,
    `evidence_json`       TEXT                  DEFAULT NULL,
    `created_at`          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_card_id` (`card_id`),
    UNIQUE KEY `uk_user_source` (`user_id`, `source_analysis_id`, `type`, `source_ref`),
    INDEX `idx_user_due` (`user_id`, `status`, `next_due_at`)
) ENGINE = InnoDB COMMENT ='成长卡';

-- 成长卡证据句（关系表：按句追踪哪些卡引用了它）
CREATE TABLE IF NOT EXISTS `growth_card_evidence`
(
    `id`                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    `card_id`            VARCHAR(64)  NOT NULL,
    `user_id`            BIGINT       NOT NULL,
    `source_analysis_id` VARCHAR(64)  NOT NULL,
    `sentence_id`        VARCHAR(64)           DEFAULT NULL COMMENT '分析句 ID，可空',
    `track_key`          VARCHAR(190) NOT NULL COMMENT '卡内去重键：sentenceId 或 t:规范化原句',
    `original_sentence`  TEXT         NOT NULL,
    `suggestion`         TEXT                  DEFAULT NULL,
    `sort_order`         INT          NOT NULL DEFAULT 0,
    `created_at`         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_card_track` (`card_id`, `track_key`),
    INDEX `idx_card_id` (`card_id`),
    INDEX `idx_user_analysis_sentence` (`user_id`, `source_analysis_id`, `sentence_id`),
    INDEX `idx_user_sentence` (`user_id`, `sentence_id`)
) ENGINE = InnoDB COMMENT ='成长卡关联证据句';

-- 语音练习（薄）：一场一条。不存音频、不存豆包 session.id。
-- 已有库升级：下面两张表都是 IF NOT EXISTS，直接重跑本段即可；users.signup_from 见上方 ALTER 注释。
CREATE TABLE IF NOT EXISTS `voice_practice_session`
(
    `id`                     BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    `user_id`                BIGINT   NOT NULL COMMENT '登录用户',
    `started_at`             DATETIME NOT NULL COMMENT '开始时间',
    `ended_at`               DATETIME          DEFAULT NULL COMMENT '结束时间',
    `wall_duration_seconds`  INT      NOT NULL DEFAULT 0 COMMENT '墙钟时长（秒）',
    `reserved_seconds`       INT      NOT NULL DEFAULT 0 COMMENT '开始时冻结的额度（秒）',
    `stopped_by_cap`         TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否因为额度停下',
    `transcript`             MEDIUMTEXT        DEFAULT NULL COMMENT '已完成字幕，User:/Assistant:，不含 ASR 预览',
    `transcript_saved`       TINYINT(1) NOT NULL DEFAULT 0 COMMENT '字幕是否已写入',
    `created_at`             DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX `idx_voice_user_started` (`user_id`, `started_at`),
    INDEX `idx_voice_started` (`started_at`)
) ENGINE = InnoDB COMMENT ='语音练习会话（薄记录）';

-- 漏斗计数。不建增长后台，用 SQL 查次数即可。
CREATE TABLE IF NOT EXISTS `product_funnel_event`
(
    `id`                BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    `user_id`           BIGINT                DEFAULT NULL COMMENT '登录用户；游客为空',
    `event_name`        VARCHAR(40)  NOT NULL COMMENT '事件名',
    `guest`             TINYINT(1)            DEFAULT NULL COMMENT 'analyze_start 是否游客',
    `duration_seconds`  INT                   DEFAULT NULL COMMENT 'voice_end 墙钟秒',
    `voice_session_id`  BIGINT                DEFAULT NULL COMMENT '交接或保存时的练习记录 id',
    `created_at`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发生时间',
    INDEX `idx_funnel_name_created` (`event_name`, `created_at`)
) ENGINE = InnoDB COMMENT ='产品漏斗事件';

-- 扩展账号连接：配对会话（一次性授权）
CREATE TABLE IF NOT EXISTS `extension_connect_session`
(
    `id`                   VARCHAR(36)  NOT NULL PRIMARY KEY COMMENT '会话 UUID',
    `code_challenge`       VARCHAR(128) NOT NULL COMMENT 'PKCE S256 code_challenge',
    `status`               VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/AUTHORIZED/EXCHANGED/DENIED/EXPIRED',
    `user_id`              BIGINT                DEFAULT NULL COMMENT '授权后绑定的用户',
    `expires_at`           DATETIME     NOT NULL COMMENT '会话过期时间',
    `authorized_at`        DATETIME              DEFAULT NULL COMMENT '用户点允许时间',
    `exchanged_at`         DATETIME              DEFAULT NULL COMMENT '扩展兑换完成时间',
    `created_at`           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX `idx_ext_conn_sess_status_exp` (`status`, `expires_at`),
    INDEX `idx_ext_conn_sess_user` (`user_id`)
) ENGINE = InnoDB COMMENT ='扩展连接配对会话';

-- 扩展专用可撤销凭证（opaque token，仅存 hash）
CREATE TABLE IF NOT EXISTS `extension_credential`
(
    `id`           BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    `user_id`      BIGINT       NOT NULL COMMENT '所属用户',
    `token_hash`   CHAR(64)     NOT NULL COMMENT 'SHA-256 hex of opaque token',
    `label`        VARCHAR(64)           DEFAULT NULL COMMENT '可选设备标签',
    `expires_at`   DATETIME     NOT NULL COMMENT '过期时间',
    `revoked_at`   DATETIME              DEFAULT NULL COMMENT '撤销时间',
    `last_used_at` DATETIME              DEFAULT NULL COMMENT '最近使用',
    `created_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY `uk_ext_cred_token_hash` (`token_hash`),
    INDEX `idx_ext_cred_user` (`user_id`),
    INDEX `idx_ext_cred_user_active` (`user_id`, `revoked_at`, `expires_at`)
) ENGINE = InnoDB COMMENT ='扩展专用凭证';

-- 扩展/异步对话分析任务（先落库再跑流水线）
CREATE TABLE IF NOT EXISTS `conversation_analysis_task`
(
    `id`                       BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    `task_id`                  VARCHAR(36)  NOT NULL COMMENT '对外任务 UUID',
    `user_id`                  BIGINT       NOT NULL COMMENT '发起用户',
    `status`                   VARCHAR(20)  NOT NULL DEFAULT 'QUEUED' COMMENT 'QUEUED/RUNNING/SUCCEEDED/FAILED',
    `conversation_content`     MEDIUMTEXT            DEFAULT NULL COMMENT '排队/执行中的对话原文；SUCCEEDED 后清空，正本在 conversation_analysis',
    `content_fingerprint`      CHAR(64)     NOT NULL COMMENT '内容 SHA-256 hex',
    `source_type`              VARCHAR(32)  NOT NULL COMMENT 'chatgpt_history / chatgpt_share 等',
    `source_conversation_id`   VARCHAR(64)           DEFAULT NULL COMMENT '来源会话标识',
    `model_id`                 VARCHAR(100)          DEFAULT NULL COMMENT '可选分析模型配置 ID',
    `analysis_id`              VARCHAR(64)           DEFAULT NULL COMMENT '成功后的 analysisId',
    `error_message`            TEXT                  DEFAULT NULL COMMENT '失败原因',
    `created_at`               DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `started_at`               DATETIME              DEFAULT NULL COMMENT '开始执行',
    `finished_at`              DATETIME              DEFAULT NULL COMMENT '结束时间',
    `updated_at`               DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY `uk_analysis_task_id` (`task_id`),
    INDEX `idx_analysis_task_user_fp` (`user_id`, `content_fingerprint`, `status`),
    INDEX `idx_analysis_task_user_source` (`user_id`, `source_type`, `source_conversation_id`, `created_at`),
    INDEX `idx_analysis_task_user_created` (`user_id`, `created_at`)
) ENGINE = InnoDB COMMENT ='对话分析异步任务';
-- 已有库：ALTER TABLE conversation_analysis_task ADD INDEX idx_analysis_task_user_source (user_id, source_type, source_conversation_id, created_at);
-- 已有库：ALTER TABLE conversation_analysis_task MODIFY COLUMN conversation_content MEDIUMTEXT NULL COMMENT '排队/执行中的对话原文；SUCCEEDED 后清空，正本在 conversation_analysis';
-- 已有库（可选回收）：UPDATE conversation_analysis_task SET conversation_content = NULL WHERE status = 'SUCCEEDED' AND conversation_content IS NOT NULL;

-- 口语薄弱点长期记忆（Analysis → Memory → Practice → Assessment → Review → Memory Update 闭环）
-- 已有库可清理旧表：DROP TABLE IF EXISTS `user_weakness_memory`;

CREATE TABLE IF NOT EXISTS `weakness_memory`
(
    `id`                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    `user_id`             BIGINT        NOT NULL COMMENT '所属用户 ID',
    `title`               VARCHAR(255)  NOT NULL COMMENT '薄弱点描述（源自知识点字典或人工调整）',
    `memory_type`         VARCHAR(32)   NOT NULL COMMENT 'GRAMMAR | HABIT | LEXICAL',
    `family_id`           VARCHAR(100)  NOT NULL COMMENT '错误家族 ID',
    `point_id`            VARCHAR(100)  NOT NULL COMMENT '具体知识点 ID',
    `status`              VARCHAR(32)   NOT NULL DEFAULT 'NEW' COMMENT 'NEW | ACTIVE | PRACTICING | MASTERED | ARCHIVED',
    `hit_count`           INT           NOT NULL DEFAULT 0 COMMENT '真实分析累计命中次数',
    `recent_hit_count`    INT           NOT NULL DEFAULT 0 COMMENT '近期窗口内命中次数',
    `practiced_count`     INT           NOT NULL DEFAULT 0 COMMENT '累计练习次数',
    `correct_count`       INT           NOT NULL DEFAULT 0 COMMENT '累计正确次数（练习正确 + 自由迁移）',
    `mastery_level`       DECIMAL(5, 2) NOT NULL DEFAULT 0.00 COMMENT '掌握置信度 0~100',
    `consecutive_correct` INT           NOT NULL DEFAULT 0 COMMENT '连续正确次数',
    `first_seen_at`       DATETIME      NOT NULL COMMENT '首次发现时间',
    `last_seen_at`        DATETIME      NOT NULL COMMENT '最近真实命中时间',
    `last_practiced_at`   DATETIME               DEFAULT NULL COMMENT '最近练习时间',
    `next_review_at`      DATETIME               DEFAULT NULL COMMENT '下次建议复习时间',
    `created_at`          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_weakness_memory_user_point` (`user_id`, `point_id`),
    INDEX `idx_weakness_memory_user_status` (`user_id`, `status`, `last_seen_at`),
    INDEX `idx_weakness_memory_user_review` (`user_id`, `status`, `next_review_at`)
) ENGINE = InnoDB COMMENT ='用户口语薄弱点长期记忆主表';

CREATE TABLE IF NOT EXISTS `memory_evidence`
(
    `id`             BIGINT AUTO_INCREMENT PRIMARY KEY,
    `memory_id`      BIGINT       NOT NULL COMMENT '所属薄弱点记忆 ID',
    `user_id`        BIGINT       NOT NULL COMMENT '所属用户 ID',
    `analysis_id`    VARCHAR(64)           DEFAULT NULL COMMENT '来源分析 ID',
    `sentence_id`    VARCHAR(64)           DEFAULT NULL COMMENT '来源句子 ID',
    `evidence_type`  VARCHAR(32)  NOT NULL COMMENT 'HIT | PRACTICE_ERROR | TRANSFER_SUCCESS | PROMPTED_CORRECT',
    `dedup_key`      VARCHAR(160) NOT NULL COMMENT '幂等去重键',
    `original_text`  TEXT                  DEFAULT NULL COMMENT '用户原始表达',
    `corrected_text` TEXT                  DEFAULT NULL COMMENT '建议/目标表达',
    `confidence`     DECIMAL(5, 4)         DEFAULT NULL COMMENT '置信度',
    `created_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_memory_evidence_dedup` (`memory_id`, `dedup_key`),
    INDEX `idx_memory_evidence_memory_time` (`memory_id`, `created_at`),
    INDEX `idx_memory_evidence_user_analysis` (`user_id`, `analysis_id`)
) ENGINE = InnoDB COMMENT ='薄弱点记忆证据追加表';

CREATE TABLE IF NOT EXISTS `practice_session`
(
    `id`                     BIGINT AUTO_INCREMENT PRIMARY KEY,
    `user_id`                BIGINT       NOT NULL COMMENT '所属用户 ID',
    `memory_id`              BIGINT       NOT NULL COMMENT '目标薄弱点记忆 ID',
    `practice_type`          VARCHAR(32)  NOT NULL COMMENT 'RECALL | TRANSFORMATION | SCENARIO | FREE_SPEAKING | REVIEW',
    `target_point_id`        VARCHAR(100) NOT NULL COMMENT '冻结目标 pointId',
    `status`                 VARCHAR(32)  NOT NULL DEFAULT 'IN_PROGRESS' COMMENT 'IN_PROGRESS | COMPLETED | ABANDONED',
    `source_conversation_id` VARCHAR(64)           DEFAULT NULL COMMENT '绑定的外部对话标识（如 ChatGPT 会话 ID）',
    `analysis_id`            VARCHAR(64)           DEFAULT NULL COMMENT '完成评估所对应的分析 ID',
    `total_attempts`         INT          NOT NULL DEFAULT 0 COMMENT '总评估次数',
    `correct_attempts`       INT          NOT NULL DEFAULT 0 COMMENT '正确评估次数',
    `started_at`             DATETIME     NOT NULL COMMENT '开始时间',
    `completed_at`           DATETIME              DEFAULT NULL COMMENT '完成时间',
    `created_at`             DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_practice_session_user_memory` (`user_id`, `memory_id`),
    INDEX `idx_practice_session_user_status` (`user_id`, `status`),
    INDEX `idx_practice_session_user_conv` (`user_id`, `source_conversation_id`),
    INDEX `idx_practice_session_analysis` (`user_id`, `analysis_id`)
) ENGINE = InnoDB COMMENT ='专项练习会话记录';

CREATE TABLE IF NOT EXISTS `practice_attempt`
(
    `id`                BIGINT AUTO_INCREMENT PRIMARY KEY,
    `session_id`        BIGINT        NOT NULL COMMENT '所属练习会话 ID',
    `attempt_no`        INT           NOT NULL COMMENT '会话内序号（从 1 开始）',
    `user_response`     TEXT                   DEFAULT NULL COMMENT '用户实际输出句',
    `expected_response` TEXT                   DEFAULT NULL COMMENT '建议/目标表达',
    `is_correct`        TINYINT(1)    NOT NULL DEFAULT 0 COMMENT '是否正确使用',
    `independent`       TINYINT(1)    NOT NULL DEFAULT 0 COMMENT '是否无提示独立输出',
    `hint_used`         TINYINT(1)    NOT NULL DEFAULT 0 COMMENT '是否借助教练提示/重塑',
    `score`             DECIMAL(5, 2) NOT NULL DEFAULT 0.00 COMMENT '单次表现分 0~100',
    `feedback`          TEXT                   DEFAULT NULL COMMENT '简短评估说明',
    `evaluation_json`   JSON                   DEFAULT NULL COMMENT '结构化评估明细',
    `created_at`        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_practice_attempt_session_no` (`session_id`, `attempt_no`),
    INDEX `idx_practice_attempt_session` (`session_id`)
) ENGINE = InnoDB COMMENT ='专项练习单次评估记录';
