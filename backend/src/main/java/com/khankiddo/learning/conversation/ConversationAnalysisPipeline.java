package com.khankiddo.learning.conversation;

import com.khankiddo.learning.ai.conversation.ConversationSeparationAi;
import com.khankiddo.learning.ai.conversation.EducationalSummaryAi;
import com.khankiddo.learning.ai.conversation.model.ConversationMessageDto;
import com.khankiddo.learning.ai.conversation.model.GrammarAnalysisResult;
import com.khankiddo.learning.ai.conversation.model.GrammarErrorDto;
import com.khankiddo.learning.ai.conversation.model.GrammarSentenceItemDto;
import com.khankiddo.learning.ai.conversation.model.SeparationResult;
import com.khankiddo.learning.config.ConversationAnalysisProperties;
import com.khankiddo.learning.dto.conversation.AnalysisErrorDto;
import com.khankiddo.learning.dto.conversation.AnalysisItemDto;
import com.khankiddo.learning.dto.conversation.ConversationAnalysisProgress;
import com.khankiddo.learning.dto.conversation.ConversationAnalysisRequest;
import com.khankiddo.learning.dto.conversation.ConversationAnalysisResultDto;
import com.khankiddo.learning.dto.conversation.ErrorTypeDistributionDto;
import com.khankiddo.learning.exception.BadRequestException;
import com.khankiddo.learning.model.enums.ProblemType;
import com.khankiddo.learning.prompt.PromptLoader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * 三阶段对话分析编排器：分离 → 语法分析 → 教育总结。
 * 各阶段通过 LangChain4j {@code @AiService} 调用，便于单独扩展或替换模型。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConversationAnalysisPipeline {

    private final ConversationSeparationAi separationAi;
    private final ConversationAnalysisStreamingHelper streamingHelper;
    private final EducationalSummaryAi summaryAi;
    private final PromptLoader promptLoader;
    private final ConversationAnalysisProperties properties;
    private final EducationalSummaryParser summaryParser;

    public ConversationAnalysisResultDto run(ConversationAnalysisRequest request,
                                               Consumer<ConversationAnalysisProgress> onProgress) {
        long start = System.currentTimeMillis();
        String analysisId = UUID.randomUUID().toString();

        onProgress.accept(ConversationAnalysisProgress.of(ConversationAnalysisProgress.STATUS_START, "开始对话分析..."));
        onProgress.accept(ConversationAnalysisProgress.of(ConversationAnalysisProgress.STATUS_VALIDATING, "正在验证请求内容..."));
        validate(request);

        onProgress.accept(ConversationAnalysisProgress.of(ConversationAnalysisProgress.STATUS_SEPARATING, "正在分离对话消息..."));
        SeparationResult separation = separationAi.separate(
                promptLoader.getConversationSeparationTemplate(),
                request.getConversationContent().trim());
        List<ConversationMessageDto> messages = ObjectUtils.defaultIfNull(separation.getMessages(), List.of());
        if (CollectionUtils.isEmpty(messages)) {
            throw new BadRequestException("未能从字幕中分离出有效对话");
        }

        int userCount = (int) messages.stream().filter(m -> "user".equalsIgnoreCase(m.getRole())).count();
        int aiCount = messages.size() - userCount;
        onProgress.accept(ConversationAnalysisProgress.builder()
                .status(ConversationAnalysisProgress.STATUS_SEPARATING)
                .message("对话分离完成")
                .messageStats(ConversationAnalysisProgress.MessageStats.builder()
                        .totalMessages(messages.size())
                        .userMessages(userCount)
                        .aiMessages(aiCount)
                        .build())
                .build());

        if (userCount > properties.getBatchThreshold()) {
            log.info("用户句数 {} 超过阈值 {}，Phase 1 仍使用单次分析（后续可扩展分批）",
                    userCount, properties.getBatchThreshold());
        }

        String formattedConversation = formatMessages(messages);
        String systemPrompt = promptLoader.getSystemPromptConversationAnalysis();
        String userPrompt = promptLoader.fillTemplate(
                promptLoader.getConversationAnalysisTemplate(),
                "conversationContent",
                formattedConversation);

        GrammarAnalysisResult grammar = streamingHelper.streamGrammarAnalysis(
                systemPrompt, userPrompt, onProgress);
        if (grammar == null) {
            grammar = GrammarAnalysisResult.builder().build();
        }

        onProgress.accept(ConversationAnalysisProgress.of(
                ConversationAnalysisProgress.STATUS_PARSING, "AI 分析成功，正在解析结果..."));
        List<AnalysisItemDto> items = toDisplayItems(grammar);
        List<ErrorTypeDistributionDto> distribution = buildDistribution(grammar);

        onProgress.accept(ConversationAnalysisProgress.of(
                ConversationAnalysisProgress.STATUS_SUMMARIZING, "正在生成学习诊断概要..."));
        Map<String, Object> summaryReport;
        try {
            String summaryTemplate = promptLoader.getEducationalSummaryTemplate();
            String summaryPrompt = promptLoader.fillTemplate(summaryTemplate, "itemsSummary",
                    summaryParser.formatItemsForSummary(grammar));
            String markdown = summaryAi.summarize(summaryPrompt);
            summaryReport = summaryParser.parseMarkdownSummary(markdown, grammar, userCount);
        } catch (Exception ex) {
            log.warn("教育总结生成失败，使用默认总结: {}", ex.getMessage());
            summaryReport = summaryParser.defaultReport(grammar, userCount);
        }

        String summaryJson = summaryParser.toJson(summaryReport);
        Map<String, Object> analysisResults = new LinkedHashMap<>();
        analysisResults.put("items", items);
        analysisResults.put("totalSentences", userCount);
        analysisResults.put("totalErrors", countErrors(grammar));
        analysisResults.put("educationalSummary", summaryReport);
        analysisResults.put("errorTypeDistribution", distribution);

        long elapsed = System.currentTimeMillis() - start;
        return ConversationAnalysisResultDto.builder()
                .analysisId(analysisId)
                .analyzedAt(LocalDateTime.now())
                .processingTimeMs(elapsed)
                .status("success")
                .analysisResults(analysisResults)
                .educationalSummaryJson(summaryJson)
                .build();
    }

    private void validate(ConversationAnalysisRequest request) {
        if (ObjectUtils.isEmpty(request) || !StringUtils.hasText(request.getConversationContent())) {
            throw new BadRequestException("对话内容不能为空");
        }
        int len = request.getConversationContent().trim().length();
        if (len < properties.getMinContentLength()) {
            throw new BadRequestException("对话内容长度需大于 " + properties.getMinContentLength() + " 个字符");
        }
        if (len > properties.getMaxContentLength()) {
            throw new BadRequestException("对话内容长度不能超过 " + properties.getMaxContentLength() + " 个字符");
        }
    }

    private String formatMessages(List<ConversationMessageDto> messages) {
        StringBuilder sb = new StringBuilder();
        for (ConversationMessageDto message : messages) {
            if (!StringUtils.hasText(message.getContent())) {
                continue;
            }
            String roleLabel = "user".equalsIgnoreCase(message.getRole()) ? "用户" : "AI";
            sb.append(roleLabel).append(": ").append(message.getContent().trim()).append("\n\n");
        }
        return sb.toString().trim();
    }

    private List<AnalysisItemDto> toDisplayItems(GrammarAnalysisResult grammar) {
        List<AnalysisItemDto> items = new ArrayList<>();
        if (grammar == null || CollectionUtils.isEmpty(grammar.getItems())) {
            return items;
        }
        long sentenceId = 1;
        for (GrammarSentenceItemDto raw : grammar.getItems()) {
            List<AnalysisErrorDto> errors = new ArrayList<>();
            if (!CollectionUtils.isEmpty(raw.getErrors())) {
                for (GrammarErrorDto error : raw.getErrors()) {
                    String englishType = StringUtils.hasText(error.getType()) ? error.getType().trim() : "Other";
                    ProblemType problemType = ProblemType.fromEnglishName(englishType);
                    String displayType = problemType != null ? problemType.getChineseName() : englishType;
                    String level = problemType != null ? problemType.getErrorLevel().name() : "STYLE";
                    errors.add(AnalysisErrorDto.builder()
                            .type(displayType)
                            .point(error.getPoint())
                            .errorLevel(level)
                            .build());
                }
            }
            items.add(AnalysisItemDto.builder()
                    .sentenceId(sentenceId++)
                    .originalSentence(raw.getOriginalSentence())
                    .suggestion(raw.getSuggestion())
                    .errors(errors)
                    .build());
        }
        return items;
    }

    private List<ErrorTypeDistributionDto> buildDistribution(GrammarAnalysisResult grammar) {
        Map<String, Integer> counts = new HashMap<>();
        if (grammar != null && !CollectionUtils.isEmpty(grammar.getItems())) {
            for (GrammarSentenceItemDto item : grammar.getItems()) {
                if (CollectionUtils.isEmpty(item.getErrors())) {
                    continue;
                }
                for (GrammarErrorDto error : item.getErrors()) {
                    if (!StringUtils.hasText(error.getType())) {
                        continue;
                    }
                    String label = ProblemType.translate(error.getType());
                    counts.merge(label, 1, Integer::sum);
                }
            }
        }
        return counts.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .map(entry -> ErrorTypeDistributionDto.builder().type(entry.getKey()).count(entry.getValue()).build())
                .toList();
    }

    private int countErrors(GrammarAnalysisResult grammar) {
        if (grammar == null || CollectionUtils.isEmpty(grammar.getItems())) {
            return 0;
        }
        return grammar.getItems().stream()
                .mapToInt(item -> CollectionUtils.isEmpty(item.getErrors()) ? 0 : item.getErrors().size())
                .sum();
    }
}
