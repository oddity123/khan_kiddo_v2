package com.khankiddo.learning.ai.conversation;

import com.khankiddo.learning.ai.conversation.model.GrammarAnalysisResult;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.langchain4j.service.spring.AiService;
import dev.langchain4j.service.spring.AiServiceWiringMode;

/**
 * Stage 2：用户英文句语法/表达分析 — LangChain4j 声明式 AI 服务。
 * <p>
 * {@code @AiService} 本身不会开启 API 级 JSON Schema；需在绑定的 {@code ChatModel} Bean 上配置
 * {@code responseFormat} + {@code strictJsonSchema}。当前 pipeline 使用
 * {@link com.khankiddo.learning.conversation.ConversationAnalysisStreamingHelper} +
 * {@link com.khankiddo.learning.llm.LlmChatModelFactory#streamingForGrammarAnalysis}。
 */
@AiService(wiringMode = AiServiceWiringMode.EXPLICIT, chatModel = "openAiChatModel")
public interface ConversationGrammarAi {

    @SystemMessage("{{systemPrompt}}")
    @UserMessage("{{userPrompt}}")
    GrammarAnalysisResult analyze(@V("systemPrompt") String systemPrompt, @V("userPrompt") String userPrompt);
}
