-- 对话分析记录：用户选择的 LLM 模型元数据
ALTER TABLE conversation_analysis
    ADD COLUMN llm_model_id VARCHAR(100) NULL COMMENT '用户选择的模型配置ID' AFTER educational_summary,
    ADD COLUMN llm_model_name VARCHAR(160) NULL COMMENT '模型展示名称' AFTER llm_model_id,
    ADD COLUMN llm_provider VARCHAR(60) NULL COMMENT '模型供应商' AFTER llm_model_name;
