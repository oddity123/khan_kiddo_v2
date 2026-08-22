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
    `created_at` DATETIME   DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME   DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX `idx_username` (`username`)
) ENGINE = InnoDB COMMENT ='用户表';

-- 已有库升级（新建库已通过 CREATE TABLE 包含 role 列）：
-- ALTER TABLE users ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'USER' COMMENT 'USER 或 ADMIN' AFTER enabled;
-- UPDATE users SET role = 'ADMIN' WHERE username = 'admin';

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
    `problem_types`     VARCHAR(100) NOT NULL COMMENT '问题类型，如 "Tense"',
    `point_id`          VARCHAR(48)  NULL COMMENT '知识点叶子 pointId',
    `error_point`       VARCHAR(500) NOT NULL COMMENT '错误点描述',
    `suggestion`        TEXT         NOT NULL COMMENT '修改建议或正确英文表达',
    `created_at`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX `idx_analysis_id` (`analysis_id`),
    INDEX `idx_sentence_id` (`sentence_id`),
    INDEX `idx_analysis_sentence` (`analysis_id`, `sentence_id`),
    INDEX `idx_problem_types` (`problem_types`) COMMENT '问题类型索引，用于查询特定问题类型'
) ENGINE = InnoDB COMMENT = '对话分析明细表';

-- 已存在数据库需手动执行（新建库通过上面的 CREATE TABLE IF NOT EXISTS 已包含该列）：
-- ALTER TABLE conversation_analysis_item
--   ADD COLUMN point_id VARCHAR(48) NULL COMMENT '知识点叶子 pointId' AFTER problem_types;

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
    `type`                VARCHAR(16)  NOT NULL COMMENT 'habit|vocab',
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
