package com.khankiddo.learning.llm;

import com.khankiddo.learning.dto.conversation.ActionCardDiagnosisResultDto;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * Stage 3：根据已确定的 Top 行动卡生成本场诊断。
 * <p>
 * 运行时由 {@link EducationalSummaryClient} 按用户选择的模型动态创建 AiService 实例。
 */
public interface ActionCardDiagnosisAssistant {

    @SystemMessage("{{systemPrompt}}")
    @UserMessage("{{userPrompt}}")
    ActionCardDiagnosisResultDto diagnose(@V("systemPrompt") String systemPrompt,
                                          @V("userPrompt") String userPrompt);
}
