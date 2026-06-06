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
    `created_at` DATETIME   DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME   DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX `idx_username` (`username`)
) ENGINE = InnoDB COMMENT ='用户表';

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
    `error_point`       VARCHAR(200) NOT NULL COMMENT '错误点描述',
    `suggestion`        TEXT         NOT NULL COMMENT '修改建议或正确英文表达',
    `created_at`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX `idx_analysis_id` (`analysis_id`),
    INDEX `idx_sentence_id` (`sentence_id`),
    INDEX `idx_analysis_sentence` (`analysis_id`, `sentence_id`),
    INDEX `idx_problem_types` (`problem_types`) COMMENT '问题类型索引，用于查询特定问题类型'
) ENGINE = InnoDB COMMENT = '对话分析明细表';

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
