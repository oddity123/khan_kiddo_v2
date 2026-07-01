package com.khankiddo.learning.llm;

import dev.langchain4j.model.chat.request.ResponseFormat;
import lombok.Builder;
import lombok.Value;

/**
 * Stage2 语法分析流式模型构建参数。
 */
@Value
@Builder
public class GrammarStreamingModelSpec {

    String cacheSuffix;
    ResponseFormat responseFormat;
    boolean strictJsonSchema;
    boolean omitMaxTokens;
}
