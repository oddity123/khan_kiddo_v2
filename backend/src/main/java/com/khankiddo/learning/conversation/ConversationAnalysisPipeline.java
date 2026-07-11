package com.khankiddo.learning.conversation;

import com.khankiddo.learning.ai.conversation.ConversationSeparationAi;
import com.khankiddo.learning.ai.conversation.model.*;
import com.khankiddo.learning.config.ConversationAnalysisProperties;
import com.khankiddo.learning.dto.conversation.*;
import com.khankiddo.learning.exception.BadRequestException;
import com.khankiddo.learning.llm.ChineseExpressionReviewClient;
import com.khankiddo.learning.llm.EducationalSummaryClient;
import com.khankiddo.learning.llm.GrammarSystemPromptComposer;
import com.khankiddo.learning.llm.LlmModelCatalog;
import com.khankiddo.learning.llm.ResolvedLlmModel;
import com.khankiddo.learning.model.enums.ProblemType;
import com.khankiddo.learning.prompt.PromptLoader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Consumer;

/**
 * 三阶段对话分析编排器：分离 → 语法分析 → 教育总结。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConversationAnalysisPipeline {

    private final ConversationSeparationAi separationAi;
    private final ConversationAnalysisStreamingHelper streamingHelper;
    private final ConversationBatchGrammarAnalyzer batchAnalyzer;
    private final EducationalSummaryClient summaryClient;
    private final LlmModelCatalog modelCatalog;
    private final PromptLoader promptLoader;
    private final ConversationAnalysisProperties properties;
    private final EducationalSummaryParser summaryParser;
    private final GrammarSystemPromptComposer grammarSystemPromptComposer;
    private final GrammarAnalysisUserPromptBuilder grammarUserPromptBuilder;
    private final GrammarAnalysisSanitizer grammarAnalysisSanitizer;
    private final UtteranceRouter utteranceRouter;
    private final ChineseExpressionReviewClient chineseExpressionReviewClient;

    public ConversationAnalysisResultDto run(ConversationAnalysisRequest request,
                                               String analysisId,
                                               Consumer<ConversationAnalysisProgress> onProgress) {
        long start = System.currentTimeMillis();

        onProgress.accept(ConversationAnalysisProgress.of(ConversationAnalysisProgress.STATUS_START, "开始对话分析..."));
        onProgress.accept(ConversationAnalysisProgress.of(ConversationAnalysisProgress.STATUS_VALIDATING, "正在验证请求内容..."));
        validateRequest(request);

        ResolvedLlmModel selectedModel = modelCatalog.resolveOrDefault(request.getModelId());
        SeparationContext separation = separateConversation(request, onProgress);
        requireUserSentences(separation);

        UtteranceRouter.RoutedUtterances routed = utteranceRouter.route(separation.userSentences());
        if (routed.chineseCount() > 0) {
            onProgress.accept(ConversationAnalysisProgress.of(
                    ConversationAnalysisProgress.STATUS_ANALYZING,
                    "检测到 " + routed.chineseCount() + " 句中文表达，已跳过语法分析"));
        }

        List<ChineseExpressionDto> chineseExpressions = reviewChineseExpressions(routed, selectedModel, onProgress);

        GrammarAnalysisResult grammar = analyzeGrammar(routed.englishSentences(), selectedModel, onProgress);
        grammar = grammarAnalysisSanitizer.sanitize(grammar);
        List<AnalysisItemDto> items = toDisplayItems(grammar);
        List<ErrorTypeDistributionDto> distribution = buildDistribution(grammar);

        int englishPracticeCount = Math.max(1, separation.userCount() - routed.chineseCount());
        SummaryOutcome summaryOutcome = buildEducationalSummary(
                grammar,
                separation.userCount(),
                englishPracticeCount,
                routed.chineseCount(),
                chineseExpressions,
                selectedModel,
                onProgress);

        return assembleResult(
                analysisId,
                start,
                selectedModel,
                separation.userCount(),
                englishPracticeCount,
                items,
                grammar,
                distribution,
                chineseExpressions,
                summaryOutcome);
    }

    private SeparationContext separateConversation(ConversationAnalysisRequest request,
                                                    Consumer<ConversationAnalysisProgress> onProgress) {
        onProgress.accept(ConversationAnalysisProgress.of(
                ConversationAnalysisProgress.STATUS_SEPARATING, "正在分离对话消息..."));
        SeparationResult separation = separationAi.separate(
                promptLoader.getSystemPromptConversationSeparation(),
                promptLoader.getConversationSeparationTemplate(),
                request.getConversationContent().trim());
        List<ConversationMessageDto> messages = ObjectUtils.defaultIfNull(separation.getMessages(), List.of());
        if (CollectionUtils.isEmpty(messages)) {
            throw new BadRequestException("未能从字幕中分离出有效对话");
        }

        List<String> userSentences = extractUserSentences(messages);
        int userRoleCount = (int) messages.stream()
                .filter(m -> "user".equalsIgnoreCase(m.getRole()))
                .count();
        int aiCount = messages.size() - userRoleCount;

        onProgress.accept(ConversationAnalysisProgress.builder()
                .status(ConversationAnalysisProgress.STATUS_SEPARATING)
                .message("对话分离完成")
                .messageStats(ConversationAnalysisProgress.MessageStats.builder()
                        .totalMessages(messages.size())
                        .userMessages(userRoleCount)
                        .aiMessages(aiCount)
                        .build())
                .build());

        return new SeparationContext(messages, userSentences, userSentences.size(), aiCount);
    }

    private static void requireUserSentences(SeparationContext separation) {
        if (CollectionUtils.isEmpty(separation.userSentences())) {
            throw new BadRequestException("未能从字幕中分离出用户发言，无法进行分析");
        }
    }

    private List<ChineseExpressionDto> reviewChineseExpressions(
            UtteranceRouter.RoutedUtterances routed,
            ResolvedLlmModel model,
            Consumer<ConversationAnalysisProgress> onProgress) {
        if (CollectionUtils.isEmpty(routed.chineseSentences())) {
            return List.of();
        }
        onProgress.accept(ConversationAnalysisProgress.of(
                ConversationAnalysisProgress.STATUS_ANALYZING, "正在生成中文表达英文建议..."));
        return chineseExpressionReviewClient.review(routed.chineseSentences(), model);
    }

    private GrammarAnalysisResult analyzeGrammar(List<String> englishSentences,
                                                  ResolvedLlmModel model,
                                                  Consumer<ConversationAnalysisProgress> onProgress) {
        if (CollectionUtils.isEmpty(englishSentences)) {
            onProgress.accept(ConversationAnalysisProgress.of(
                    ConversationAnalysisProgress.STATUS_PARSING, "无纯英文句，跳过语法分析"));
            return GrammarAnalysisResult.builder().build();
        }

        String systemPrompt = grammarSystemPromptComposer.compose(
                promptLoader.getSystemPromptConversationAnalysis(), model);

        GrammarAnalysisResult grammar;
        if (englishSentences.size() > properties.getBatchThreshold()) {
            grammar = batchAnalyzer.analyzeInBatches(englishSentences, systemPrompt, model, onProgress);
        } else {
            String userPrompt = grammarUserPromptBuilder.buildFromUserSentences(englishSentences);
            grammar = streamingHelper.streamGrammarAnalysis(systemPrompt, userPrompt, model, onProgress);
        }
        if (grammar == null) {
            grammar = GrammarAnalysisResult.builder().build();
        }

        onProgress.accept(ConversationAnalysisProgress.of(
                ConversationAnalysisProgress.STATUS_PARSING, "AI 分析成功，正在解析结果..."));
        return grammar;
    }

    private SummaryOutcome buildEducationalSummary(GrammarAnalysisResult grammar,
                                                    int userCount,
                                                    int englishPracticeCount,
                                                    int chineseExpressionCount,
                                                    List<ChineseExpressionDto> chineseExpressions,
                                                    ResolvedLlmModel model,
                                                    Consumer<ConversationAnalysisProgress> onProgress) {
        onProgress.accept(ConversationAnalysisProgress.of(
                ConversationAnalysisProgress.STATUS_SUMMARIZING, "正在生成学习诊断概要..."));
        try {
            String summaryTemplate = promptLoader.getEducationalSummaryTemplate();
            String summaryPrompt = promptLoader.fillTemplate(summaryTemplate, "itemsSummary",
                    summaryParser.formatItemsForSummary(grammar));
            String markdown = summaryClient.summarize(
                    promptLoader.getSystemPromptEducationalSummary(), summaryPrompt, model);
            EducationalSummaryDto report = summaryParser.parseMarkdownSummary(
                    markdown, grammar, userCount, englishPracticeCount, chineseExpressionCount);
            report.setChineseExpressions(chineseExpressions);
            return new SummaryOutcome(report, false, null);
        } catch (RuntimeException ex) {
            log.warn("教育总结生成失败，使用默认总结: {}", ex.getMessage(), ex);
            String reason = StringUtils.hasText(ex.getMessage())
                    ? ex.getMessage()
                    : ex.getClass().getSimpleName();
            onProgress.accept(ConversationAnalysisProgress.builder()
                    .status(ConversationAnalysisProgress.STATUS_SUMMARIZING)
                    .message("学习诊断概要生成失败，已使用默认总结")
                    .build());
            EducationalSummaryDto report = summaryParser.defaultReport(
                    grammar, userCount, englishPracticeCount, chineseExpressionCount);
            report.setChineseExpressions(chineseExpressions);
            return new SummaryOutcome(report, true, reason);
        }
    }

    private ConversationAnalysisResultDto assembleResult(String analysisId,
                                                          long startMs,
                                                          ResolvedLlmModel model,
                                                          int userCount,
                                                          int englishPracticeCount,
                                                          List<AnalysisItemDto> items,
                                                          GrammarAnalysisResult grammar,
                                                          List<ErrorTypeDistributionDto> distribution,
                                                          List<ChineseExpressionDto> chineseExpressions,
                                                          SummaryOutcome summaryOutcome) {
        String summaryJson = summaryParser.toJson(summaryOutcome.report());
        Map<String, Object> analysisResults = new LinkedHashMap<>();
        analysisResults.put("items", items);
        analysisResults.put("totalSentences", userCount);
        analysisResults.put("englishPracticeCount", englishPracticeCount);
        analysisResults.put("totalErrors", countErrors(grammar));
        analysisResults.put("chineseExpressions", chineseExpressions);
        analysisResults.put("chineseExpressionCount", chineseExpressions.size());
        analysisResults.put("educationalSummary", summaryOutcome.report());
        analysisResults.put("errorTypeDistribution", distribution);
        analysisResults.put("summaryDegraded", summaryOutcome.degraded());
        if (summaryOutcome.degraded()) {
            analysisResults.put("summaryDegradedReason", summaryOutcome.degradedReason());
        }

        long elapsed = System.currentTimeMillis() - startMs;
        return ConversationAnalysisResultDto.builder()
                .analysisId(analysisId)
                .analyzedAt(LocalDateTime.now())
                .processingTimeMs(elapsed)
                .status("success")
                .analysisResults(analysisResults)
                .educationalSummaryJson(summaryJson)
                .llmModelId(model.getId())
                .llmModelName(model.getConfig().getModelName())
                .llmProvider(model.getProvider())
                .build();
    }

    private void validateRequest(ConversationAnalysisRequest request) {
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

    private List<String> extractUserSentences(List<ConversationMessageDto> messages) {
        if (CollectionUtils.isEmpty(messages)) {
            return List.of();
        }
        List<String> sentences = new ArrayList<>();
        for (ConversationMessageDto message : messages) {
            if ("user".equalsIgnoreCase(message.getRole()) && StringUtils.hasText(message.getContent())) {
                sentences.add(message.getContent().trim());
            }
        }
        return sentences;
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

    private record SeparationContext(
            List<ConversationMessageDto> messages,
            List<String> userSentences,
            int userCount,
            int aiCount) {
    }

    private record SummaryOutcome(
            EducationalSummaryDto report,
            boolean degraded,
            String degradedReason) {
    }
}
