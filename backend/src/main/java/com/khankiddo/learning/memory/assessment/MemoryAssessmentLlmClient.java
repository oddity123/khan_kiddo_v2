package com.khankiddo.learning.memory.assessment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.khankiddo.learning.llm.LlmChatModelFactory;
import com.khankiddo.learning.llm.LlmModelCatalog;
import com.khankiddo.learning.llm.ResolvedLlmModel;
import com.khankiddo.learning.log.ConversationAnalysisCallLog;
import com.khankiddo.learning.memory.domain.WeaknessMemory;
import com.khankiddo.learning.model.ConversationAnalysisItem;
import com.khankiddo.learning.prompt.PromptLoader;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 单次结构化 LLM 调用：针对本场对话评估指定目标薄弱点的掌握表现（不直接改状态，由 Java 聚合）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MemoryAssessmentLlmClient {

    public static final String STAGE_MEMORY_ASSESSMENT = "memory_assessment";

    private final LlmChatModelFactory chatModelFactory;
    private final LlmModelCatalog modelCatalog;
    private final PromptLoader promptLoader;
    private final ObjectMapper objectMapper;
    private final MemoryAssessmentOutputPolicy outputPolicy;

    public List<MemoryAssessmentResult.EvaluationDto> evaluate(AssessmentPromptContext context) {
        if (context == null
                || CollectionUtils.isEmpty(context.targets())
                || !StringUtils.hasText(context.cleanedTranscript())) {
            return List.of();
        }
        ResolvedLlmModel model = modelCatalog.resolveOrDefault(null);
        long startedAt = System.currentTimeMillis();
        try {
            String userPrompt = promptLoader.fillTemplate(
                    promptLoader.getMemoryAssessmentTemplate(),
                    "context",
                    buildContextBlock(context));
            ChatModel chatModel = chatModelFactory.chatForMemoryAssessment(model);
            String systemPrompt = outputPolicy.composeSystemPrompt(
                    promptLoader.getSystemPromptMemoryAssessment(), model);
            ChatRequest request = ChatRequest.builder()
                    .messages(
                            SystemMessage.from(systemPrompt),
                            UserMessage.from(userPrompt))
                    .build();
            ChatResponse response = chatModel.chat(request);
            String json = response.aiMessage().text();
            MemoryAssessmentResult parsed = objectMapper.readValue(json, MemoryAssessmentResult.class);
            ConversationAnalysisCallLog.record(
                    STAGE_MEMORY_ASSESSMENT,
                    model.getId(),
                    1,
                    System.currentTimeMillis() - startedAt,
                    ConversationAnalysisCallLog.RESULT_OK);
            return parsed == null || CollectionUtils.isEmpty(parsed.getEvaluations())
                    ? List.of()
                    : parsed.getEvaluations();
        } catch (Exception ex) {
            ConversationAnalysisCallLog.record(
                    STAGE_MEMORY_ASSESSMENT,
                    model != null ? model.getId() : null,
                    1,
                    System.currentTimeMillis() - startedAt,
                    ConversationAnalysisCallLog.resultOf(ex));
            log.warn("memory assessment LLM failed analysisId={}: {}",
                    context.analysisId(), ex.getMessage(), ex);
            return List.of();
        }
    }

    private static String buildContextBlock(AssessmentPromptContext context) {
        StringBuilder sb = new StringBuilder();
        sb.append("## 待评估目标薄弱点列表\n");
        for (AssessmentTarget target : context.targets()) {
            WeaknessMemory memory = target.memory();
            sb.append("- targetPointId=").append(memory.getPointId())
                    .append(" | type=").append(memory.getMemoryType())
                    .append(" | title=").append(memory.getTitle())
                    .append(" | hasActivePracticeSession=").append(target.hasActiveSession())
                    .append('\n');
            if (!CollectionUtils.isEmpty(target.stage2Errors())) {
                sb.append("  Stage2 已检出同点错句：\n");
                for (ConversationAnalysisItem err : target.stage2Errors()) {
                    sb.append("  * ").append(nullToEmpty(err.getOriginalSentence()))
                            .append(" -> ").append(nullToEmpty(err.getSuggestion()))
                            .append('\n');
                }
            }
        }
        sb.append("\n## 本场对话实录（已剔除注入的开场模板）\n");
        sb.append(context.cleanedTranscript());
        return sb.toString().trim();
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    public record AssessmentTarget(
            WeaknessMemory memory,
            boolean hasActiveSession,
            List<ConversationAnalysisItem> stage2Errors
    ) {
    }

    public record AssessmentPromptContext(
            String analysisId,
            List<AssessmentTarget> targets,
            String cleanedTranscript
    ) {
    }
}
