-- 已有生产库升级：扩展账号连接（配对会话 + 可撤销专用凭证）
-- 新建库请直接跑 DDL.sql（已含同结构 CREATE TABLE IF NOT EXISTS）

CREATE TABLE IF NOT EXISTS `extension_connect_session`
(
    `id`             VARCHAR(36)  NOT NULL PRIMARY KEY COMMENT '会话 UUID',
    `code_challenge` VARCHAR(128) NOT NULL COMMENT 'PKCE S256 code_challenge',
    `status`         VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/AUTHORIZED/EXCHANGED/DENIED/EXPIRED',
    `user_id`        BIGINT                DEFAULT NULL COMMENT '授权后绑定的用户',
    `expires_at`     DATETIME     NOT NULL COMMENT '会话过期时间',
    `authorized_at`  DATETIME              DEFAULT NULL COMMENT '用户点允许时间',
    `exchanged_at`   DATETIME              DEFAULT NULL COMMENT '扩展兑换完成时间',
    `created_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX `idx_ext_conn_sess_status_exp` (`status`, `expires_at`),
    INDEX `idx_ext_conn_sess_user` (`user_id`)
) ENGINE = InnoDB COMMENT ='扩展连接配对会话';

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
