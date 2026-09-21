package com.khankiddo.learning.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.khankiddo.learning.ai.conversation.model.PhraseCardReviewItemDto;
import com.khankiddo.learning.ai.conversation.model.PhraseCardReviewResult;
import com.khankiddo.learning.conversation.UtteranceRouter;
import com.khankiddo.learning.dto.conversation.ChineseExpressionDto;
import com.khankiddo.learning.dto.conversation.ExpressionPhraseDto;
import com.khankiddo.learning.log.ConversationAnalysisCallLog;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 统一 Phrase Review：中文词汇缺口 + NATURAL expression 短对立，单次非流式 LLM。
 * NATURAL 过滤在 Java 侧完成后再入模。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PhraseCardReviewClient {

    public static final String KIND_CHINESE = "chinese";
    public static final String KIND_EXPRESSION = "expression";

    private final LlmChatModelFactory chatModelFactory;
    private final PromptLoader promptLoader;
    private final ObjectMapper objectMapper;
    private final PhraseCardReviewOutputPolicy outputPolicy;

    public PhraseReviewOutcome review(
            List<UtteranceRouter.RoutedChineseSentence> chineseSentences,
            List<ExpressionReviewCandidate> expressionCandidates,
            ResolvedLlmModel model) {
        List<UtteranceRouter.RoutedChineseSentence> chinese =
                CollectionUtils.isEmpty(chineseSentences) ? List.of() : chineseSentences;
        List<ExpressionReviewCandidate> expressions =
                CollectionUtils.isEmpty(expressionCandidates) ? List.of() : expressionCandidates;
        if (chinese.isEmpty() && expressions.isEmpty()) {
            return PhraseReviewOutcome.empty();
        }

        List<InputSlot> slots = buildSlots(chinese, expressions);
        long llmStartedAt = System.currentTimeMillis();
        try {
            String userPrompt = buildUserPrompt(slots);
            ChatModel chatModel = chatModelFactory.chatForPhraseCardReview(model);
            String systemPrompt = outputPolicy.composeSystemPrompt(
                    promptLoader.getSystemPromptPhraseCardReview(), model);
            ChatRequest request = ChatRequest.builder()
                    .messages(
                            SystemMessage.from(systemPrompt),
                            UserMessage.from(userPrompt))
                    .build();
            ChatResponse response = chatModel.chat(request);
            String json = response.aiMessage().text();
            PhraseCardReviewResult parsed = objectMapper.readValue(json, PhraseCardReviewResult.class);
            ConversationAnalysisCallLog.record(
                    ConversationAnalysisCallLog.STAGE_PHRASE_REVIEW,
                    model != null ? model.getId() : null,
                    1,
                    System.currentTimeMillis() - llmStartedAt,
                    ConversationAnalysisCallLog.RESULT_OK);
            return mergeOutcome(slots, parsed);
        } catch (Exception ex) {
            ConversationAnalysisCallLog.record(
                    ConversationAnalysisCallLog.STAGE_PHRASE_REVIEW,
                    model != null ? model.getId() : null,
                    1,
                    System.currentTimeMillis() - llmStartedAt,
                    ConversationAnalysisCallLog.resultOf(ex));
            log.warn("Phrase Review 失败，中文保留空建议、expression 交由启发式: {}", ex.getMessage(), ex);
            return fallbackOnFailure(chinese);
        }
    }

    private String buildUserPrompt(List<InputSlot> slots) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < slots.size(); i++) {
            if (i > 0) {
                sb.append("\n\n");
            }
            InputSlot slot = slots.get(i);
            sb.append(slot.index()).append(". kind=").append(slot.kind());
            if (KIND_CHINESE.equals(slot.kind())) {
                sb.append("\nsentence: ").append(slot.chinese().sentence());
            } else {
                ExpressionReviewCandidate c = slot.expression();
                sb.append("\noriginal: ").append(nullToEmpty(c.originalSentence()));
                sb.append("\nsuggestion: ").append(nullToEmpty(c.suggestion()));
                sb.append("\npoint: ").append(nullToEmpty(c.point()));
                sb.append("\npointId: ").append(nullToEmpty(c.pointId()));
            }
        }
        return promptLoader.fillTemplate(
                promptLoader.getPhraseCardReviewTemplate(),
                "items",
                sb.toString());
    }

    private static List<InputSlot> buildSlots(
            List<UtteranceRouter.RoutedChineseSentence> chinese,
            List<ExpressionReviewCandidate> expressions) {
        List<InputSlot> slots = new ArrayList<>();
        int index = 1;
        for (UtteranceRouter.RoutedChineseSentence routed : chinese) {
            slots.add(new InputSlot(index++, KIND_CHINESE, routed, null));
        }
        for (ExpressionReviewCandidate candidate : expressions) {
            slots.add(new InputSlot(index++, KIND_EXPRESSION, null, candidate));
        }
        return slots;
    }

    private static PhraseReviewOutcome mergeOutcome(List<InputSlot> slots, PhraseCardReviewResult parsed) {
        Map<Integer, PhraseCardReviewItemDto> byIndex = indexItems(parsed);
        List<ChineseExpressionDto> chinese = new ArrayList<>();
        List<ExpressionPhraseDto> expressionPhrases = new ArrayList<>();
        for (InputSlot slot : slots) {
            PhraseCardReviewItemDto item = byIndex.get(slot.index());
            if (KIND_CHINESE.equals(slot.kind())) {
                chinese.add(toChineseDto(slot.chinese(), item));
            } else if (item != null
                    && StringUtils.hasText(item.getFocusPhrase())
                    && StringUtils.hasText(item.getSuggestion())) {
                ExpressionReviewCandidate c = slot.expression();
                expressionPhrases.add(ExpressionPhraseDto.builder()
                        .sentenceId(c.sentenceId())
                        .pointId(c.pointId())
                        .originalSentence(c.originalSentence())
                        .focusPhrase(item.getFocusPhrase().trim())
                        .suggestion(item.getSuggestion().trim())
                        .reason(trimToEmpty(item.getReason()))
                        .build());
            }
        }
        return new PhraseReviewOutcome(dedupeChineseByFocusPhrase(chinese), expressionPhrases);
    }

    private static ChineseExpressionDto toChineseDto(
            UtteranceRouter.RoutedChineseSentence routed,
            PhraseCardReviewItemDto item) {
        String suggestion = "";
        String focusPhrase = "";
        String reason = "";
        if (item != null) {
            if (StringUtils.hasText(item.getSuggestion())) {
                suggestion = item.getSuggestion().trim();
            }
            if (StringUtils.hasText(item.getFocusPhrase())) {
                focusPhrase = item.getFocusPhrase().trim();
            }
            reason = trimToEmpty(item.getReason());
        }
        return ChineseExpressionDto.builder()
                .originalIndex(routed.originalIndex())
                .originalSentence(routed.sentence())
                .focusPhrase(focusPhrase)
                .suggestion(suggestion)
                .reason(reason)
                .build();
    }

    private static Map<Integer, PhraseCardReviewItemDto> indexItems(PhraseCardReviewResult parsed) {
        Map<Integer, PhraseCardReviewItemDto> itemByIndex = new HashMap<>();
        if (parsed != null && !CollectionUtils.isEmpty(parsed.getItems())) {
            for (PhraseCardReviewItemDto item : parsed.getItems()) {
                if (item.getIndex() >= 1) {
                    itemByIndex.put(item.getIndex(), item);
                }
            }
        }
        return itemByIndex;
    }

    /**
     * 同一 {@code focusPhrase}（去空白后）只保留首次出现；无 focusPhrase 的项跳过。
     */
    static List<ChineseExpressionDto> dedupeChineseByFocusPhrase(List<ChineseExpressionDto> items) {
        if (CollectionUtils.isEmpty(items)) {
            return items;
        }
        Set<String> seenFocus = new HashSet<>();
        List<ChineseExpressionDto> result = new ArrayList<>();
        for (ChineseExpressionDto item : items) {
            if (!StringUtils.hasText(item.getFocusPhrase())) {
                continue;
            }
            String key = normalizeFocusPhrase(item.getFocusPhrase());
            if (!seenFocus.add(key)) {
                continue;
            }
            result.add(item);
        }
        return result;
    }

    private static String normalizeFocusPhrase(String focusPhrase) {
        return focusPhrase.replaceAll("\\s+", "");
    }

    private static PhraseReviewOutcome fallbackOnFailure(
            List<UtteranceRouter.RoutedChineseSentence> chineseSentences) {
        List<ChineseExpressionDto> result = new ArrayList<>();
        for (UtteranceRouter.RoutedChineseSentence routed : chineseSentences) {
            result.add(ChineseExpressionDto.builder()
                    .originalIndex(routed.originalIndex())
                    .originalSentence(routed.sentence())
                    .focusPhrase("")
                    .suggestion("")
                    .reason("")
                    .build());
        }
        // expression 空列表 → mint 侧启发式降级
        return new PhraseReviewOutcome(result, List.of());
    }

    private static String trimToEmpty(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private record InputSlot(
            int index,
            String kind,
            UtteranceRouter.RoutedChineseSentence chinese,
            ExpressionReviewCandidate expression) {
    }
}
