package com.khankiddo.learning.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.khankiddo.learning.ai.conversation.model.ChineseExpressionReviewItemDto;
import com.khankiddo.learning.ai.conversation.model.ChineseExpressionReviewResult;
import com.khankiddo.learning.conversation.UtteranceRouter;
import com.khankiddo.learning.dto.conversation.ChineseExpressionDto;
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

import java.util.*;

/**
 * 对含中文的用户句批量生成知识卡片内容（focusPhrase + suggestion，单次非流式 LLM 调用）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChineseExpressionReviewClient {

    private final LlmChatModelFactory chatModelFactory;
    private final PromptLoader promptLoader;
    private final ObjectMapper objectMapper;

    public List<ChineseExpressionDto> review(
            List<UtteranceRouter.RoutedChineseSentence> chineseSentences,
            ResolvedLlmModel model) {
        if (CollectionUtils.isEmpty(chineseSentences)) {
            return List.of();
        }
        try {
            String userPrompt = buildUserPrompt(chineseSentences);
            ChatModel chatModel = chatModelFactory.chatForChineseExpressionReview(model);
            ChatRequest request = ChatRequest.builder()
                    .messages(
                            SystemMessage.from(promptLoader.getSystemPromptChineseExpressionReview()),
                            UserMessage.from(userPrompt))
                    .build();
            ChatResponse response = chatModel.chat(request);
            String json = response.aiMessage().text();
            ChineseExpressionReviewResult parsed = objectMapper.readValue(json, ChineseExpressionReviewResult.class);
            return dedupeByFocusPhrase(mergeSuggestions(chineseSentences, parsed));
        } catch (Exception ex) {
            log.warn("中文表达建议生成失败，保留原句: {}", ex.getMessage(), ex);
            return fallbackWithoutSuggestions(chineseSentences);
        }
    }

    private String buildUserPrompt(List<UtteranceRouter.RoutedChineseSentence> chineseSentences) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < chineseSentences.size(); i++) {
            if (i > 0) {
                sb.append("\n\n");
            }
            sb.append(i + 1).append(". ").append(chineseSentences.get(i).sentence());
        }
        return promptLoader.fillTemplate(
                promptLoader.getChineseExpressionReviewTemplate(),
                "sentences",
                sb.toString());
    }

    private static List<ChineseExpressionDto> mergeSuggestions(
            List<UtteranceRouter.RoutedChineseSentence> chineseSentences,
            ChineseExpressionReviewResult parsed) {
        Map<Integer, ChineseExpressionReviewItemDto> itemByIndex = new HashMap<>();
        if (parsed != null && !CollectionUtils.isEmpty(parsed.getItems())) {
            for (ChineseExpressionReviewItemDto item : parsed.getItems()) {
                if (item.getIndex() >= 1) {
                    itemByIndex.put(item.getIndex(), item);
                }
            }
        }
        List<ChineseExpressionDto> result = new ArrayList<>();
        for (int i = 0; i < chineseSentences.size(); i++) {
            UtteranceRouter.RoutedChineseSentence routed = chineseSentences.get(i);
            int promptIndex = i + 1;
            ChineseExpressionReviewItemDto item = itemByIndex.get(promptIndex);
            String suggestion = "";
            String focusPhrase = "";
            if (item != null) {
                if (StringUtils.hasText(item.getSuggestion())) {
                    suggestion = item.getSuggestion().trim();
                }
                if (StringUtils.hasText(item.getFocusPhrase())) {
                    focusPhrase = item.getFocusPhrase().trim();
                }
            }
            result.add(ChineseExpressionDto.builder()
                    .originalIndex(routed.originalIndex())
                    .originalSentence(routed.sentence())
                    .focusPhrase(focusPhrase)
                    .suggestion(suggestion)
                    .build());
        }
        return result;
    }

    /**
     * 同一 {@code focusPhrase}（去空白后）只保留首次出现；无 focusPhrase 的内容表达项不去重。
     */
    static List<ChineseExpressionDto> dedupeByFocusPhrase(List<ChineseExpressionDto> items) {
        if (CollectionUtils.isEmpty(items)) {
            return items;
        }
        Set<String> seenFocus = new HashSet<>();
        List<ChineseExpressionDto> result = new ArrayList<>();
        for (ChineseExpressionDto item : items) {
            if (StringUtils.hasText(item.getFocusPhrase())) {
                String key = normalizeFocusPhrase(item.getFocusPhrase());
                if (!seenFocus.add(key)) {
                    continue;
                }
            }
            result.add(item);
        }
        return result;
    }

    private static String normalizeFocusPhrase(String focusPhrase) {
        return focusPhrase.replaceAll("\\s+", "");
    }

    private static List<ChineseExpressionDto> fallbackWithoutSuggestions(
            List<UtteranceRouter.RoutedChineseSentence> chineseSentences) {
        List<ChineseExpressionDto> result = new ArrayList<>();
        for (UtteranceRouter.RoutedChineseSentence routed : chineseSentences) {
            result.add(ChineseExpressionDto.builder()
                    .originalIndex(routed.originalIndex())
                    .originalSentence(routed.sentence())
                    .focusPhrase("")
                    .suggestion("")
                    .build());
        }
        return result;
    }
}
