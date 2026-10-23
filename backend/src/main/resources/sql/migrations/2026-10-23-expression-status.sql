-- 在部署新版后端前执行一次。历史行默认 ERROR，保留既有评分与统计。
ALTER TABLE conversation_analysis_item
    ADD COLUMN expression_status VARCHAR(16) NOT NULL DEFAULT 'ERROR'
    COMMENT 'ERROR/RISK/UNNATURAL；仅存问题' AFTER point_id;
