package com.khankiddo.learning.ai.conversation;

import com.khankiddo.learning.ai.conversation.model.GrammarAnalysisResult;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.langchain4j.service.spring.AiService;
import dev.langchain4j.service.spring.AiServiceWiringMode;

/**
 * Stage 2：用户英文句语法/表达分析 — LangChain4j 声明式 AI 服务。
 */
@AiService(wiringMode = AiServiceWiringMode.EXPLICIT, chatModel = "openAiChatModel")
public interface ConversationGrammarAi {

    @SystemMessage("{{systemPrompt}}")
    @UserMessage("{{userPrompt}}")
    GrammarAnalysisResult analyze(@V("systemPrompt") String systemPrompt, @V("userPrompt") String userPrompt);
}
