package com.khankiddo.learning.weaknessmemory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.khankiddo.learning.config.WeaknessMemoryProperties;
import com.khankiddo.learning.dto.conversation.ActionCardDto;
import com.khankiddo.learning.llm.LlmChatModelFactory;
import com.khankiddo.learning.llm.LlmModelCatalog;
import com.khankiddo.learning.llm.ResolvedLlmModel;
import com.khankiddo.learning.log.ConversationAnalysisCallLog;
import com.khankiddo.learning.model.UserWeaknessMemory;
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

import java.util.ArrayList;
import java.util.List;

/**
 * 单次结构化 LLM 调用：薄弱点记忆归并。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WeaknessMemoryLlmClient {

    public static final String STAGE_WEAKNESS_CONSOLIDATION = "weakness_consolidation";

    private final LlmChatModelFactory chatModelFactory;
    private final LlmModelCatalog modelCatalog;
    private final PromptLoader promptLoader;
    private final ObjectMapper objectMapper;
    private final WeaknessMemoryOutputPolicy outputPolicy;
    private final WeaknessMemoryProperties properties;

    public List<WeaknessMemoryOperation> consolidate(WeaknessMemoryPrefetchContext context) {
        if (context == null || CollectionUtils.isEmpty(context.actionCards())) {
            return List.of();
        }
        ResolvedLlmModel model = modelCatalog.resolveOrDefault(null);
        long startedAt = System.currentTimeMillis();
        try {
            String userPrompt = promptLoader.fillTemplate(
                    promptLoader.getWeaknessMemoryTemplate(),
                    "context",
                    buildContextBlock(context));
            ChatModel chatModel = chatModelFactory.chatForWeaknessMemoryConsolidation(model);
            String systemPrompt = outputPolicy.composeSystemPrompt(
                    promptLoader.getSystemPromptWeaknessMemory(), model);
            ChatRequest request = ChatRequest.builder()
                    .messages(
                            SystemMessage.from(systemPrompt),
                            UserMessage.from(userPrompt))
                    .build();
            ChatResponse response = chatModel.chat(request);
            String json = response.aiMessage().text();
            WeaknessMemoryConsolidationResult parsed =
                    objectMapper.readValue(json, WeaknessMemoryConsolidationResult.class);
            ConversationAnalysisCallLog.record(
                    STAGE_WEAKNESS_CONSOLIDATION,
                    model.getId(),
                    1,
                    System.currentTimeMillis() - startedAt,
                    ConversationAnalysisCallLog.RESULT_OK);
            return toOperations(parsed);
        } catch (Exception ex) {
            ConversationAnalysisCallLog.record(
                    STAGE_WEAKNESS_CONSOLIDATION,
                    model != null ? model.getId() : null,
                    1,
                    System.currentTimeMillis() - startedAt,
                    ConversationAnalysisCallLog.resultOf(ex));
            log.warn("weakness consolidation LLM failed analysisId={}: {}",
                    context.analysisId(), ex.getMessage(), ex);
            return List.of();
        }
    }

    private String buildContextBlock(WeaknessMemoryPrefetchContext context) {
        StringBuilder sb = new StringBuilder();
        sb.append("## ACTIVE 薄弱点记忆\n");
        if (CollectionUtils.isEmpty(context.activeMemories())) {
            sb.append("(无)\n");
        } else {
            for (UserWeaknessMemory memory : context.activeMemories()) {
                sb.append("- id=").append(memory.getId())
                        .append(" | title=").append(nullToEmpty(memory.getTitle()))
                        .append(" | habitKey=").append(nullToEmpty(memory.getAnchorHabitKey()))
                        .append(" | hitCount=").append(memory.getHitCount())
                        .append(" | badExample=").append(nullToEmpty(memory.getBadExample()))
                        .append('\n');
            }
        }

        sb.append("\n## 本场行动卡 Top\n");
        int exampleLimit = Math.max(1, properties.getActionCardExampleLimit());
        for (ActionCardDto card : context.actionCards()) {
            sb.append("- habitKey=").append(nullToEmpty(card.getHabitKey()))
                    .append(" | pointId=").append(nullToEmpty(card.getPointId()))
                    .append(" | title=").append(nullToEmpty(card.getTitleZh()))
                    .append(" | diagnosis=").append(nullToEmpty(card.getDiagnosisZh()))
                    .append('\n');
            if (!CollectionUtils.isEmpty(card.getExamples())) {
                int n = 0;
                for (ActionCardDto.ExampleDto ex : card.getExamples()) {
                    if (n >= exampleLimit) {
                        break;
                    }
                    sb.append("  * ").append(nullToEmpty(ex.getOriginalSentence()))
                            .append(" -> ").append(nullToEmpty(ex.getSuggestion()))
                            .append('\n');
                    n++;
                }
            }
        }

        sb.append("\n## 跨场证据（已排除本场）\n");
        if (!CollectionUtils.isEmpty(context.cardEvidences())) {
            for (WeaknessMemoryPrefetchContext.CardEvidence evidence : context.cardEvidences()) {
                sb.append("### habitKey=").append(nullToEmpty(evidence.habitKey()))
                        .append(" pointId=").append(nullToEmpty(evidence.pointId()))
                        .append('\n');
                sb.append("统计：\n").append(nullToEmpty(evidence.statsSummary())).append('\n');
                sb.append("历史同点错句：\n");
                if (CollectionUtils.isEmpty(evidence.historicalExamples())) {
                    sb.append("(无)\n");
                } else {
                    for (WeaknessMemoryPrefetchContext.ExampleLine line : evidence.historicalExamples()) {
                        sb.append("- [").append(nullToEmpty(line.analysisId())).append("] ")
                                .append(nullToEmpty(line.originalSentence()))
                                .append(" -> ").append(nullToEmpty(line.suggestion()))
                                .append('\n');
                    }
                }
                sb.append("向量相似错句：\n");
                if (CollectionUtils.isEmpty(evidence.similarSnippets())) {
                    sb.append("(未启用或无命中)\n");
                } else {
                    for (String snippet : evidence.similarSnippets()) {
                        sb.append("- ").append(snippet).append('\n');
                    }
                }
            }
        }
        return sb.toString().trim();
    }

    private static List<WeaknessMemoryOperation> toOperations(WeaknessMemoryConsolidationResult parsed) {
        if (parsed == null || CollectionUtils.isEmpty(parsed.getOperations())) {
            return List.of();
        }
        List<WeaknessMemoryOperation> ops = new ArrayList<>();
        for (WeaknessMemoryConsolidationResult.OperationDto dto : parsed.getOperations()) {
            if (dto == null || !StringUtils.hasText(dto.getOp())) {
                continue;
            }
            ops.add(new WeaknessMemoryOperation(
                    dto.getOp().trim(),
                    dto.getMemoryId(),
                    dto.getTitle(),
                    dto.getDiagnosis(),
                    dto.getCoaching(),
                    dto.getAnchorHabitKey(),
                    dto.getAnchorPointId(),
                    dto.getBadExample(),
                    dto.getTargetRewrite()));
        }
        return ops;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
