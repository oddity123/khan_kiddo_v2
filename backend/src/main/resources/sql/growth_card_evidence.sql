-- 若线上库尚未有成长卡证据表，执行本段（与 DDL.sql 一致，幂等）
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
