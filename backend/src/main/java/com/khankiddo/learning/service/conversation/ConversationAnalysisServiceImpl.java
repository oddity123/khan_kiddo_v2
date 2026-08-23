package com.khankiddo.learning.service.conversation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.khankiddo.learning.conversation.ConversationAnalysisErrorMessages;
import com.khankiddo.learning.conversation.ConversationAnalysisPersistSupport;
import com.khankiddo.learning.conversation.ConversationAnalysisPipeline;
import com.khankiddo.learning.conversation.EducationalSummaryParser;
import com.khankiddo.learning.dto.admin.AdminAnalysisListResponse;
import com.khankiddo.learning.dto.conversation.*;
import com.khankiddo.learning.exception.BadRequestException;
import com.khankiddo.learning.knowledge.HabitCardScorer;
import com.khankiddo.learning.knowledge.HabitScoreInput;
import com.khankiddo.learning.knowledge.KnowledgePointStatsSupport;
import com.khankiddo.learning.knowledge.PointDefinition;
import com.khankiddo.learning.knowledge.PointDictionary;
import com.khankiddo.learning.knowledge.PointScoringSupport;
import com.khankiddo.learning.log.ConversationAnalysisCallLog;
import com.khankiddo.learning.mapper.ConversationAnalysisItemMapper;
import com.khankiddo.learning.mapper.ConversationAnalysisMapper;
import com.khankiddo.learning.model.ConversationAnalysis;
import com.khankiddo.learning.model.ConversationAnalysisItem;
import com.khankiddo.learning.model.ConversationAnalysisWithUsername;
import com.khankiddo.learning.dto.growth.GrowthCardDto;
import com.khankiddo.learning.growth.GrowthCardMintRequestedEvent;
import com.khankiddo.learning.growth.GrowthCardReviewService;
import com.khankiddo.learning.rag.grammar.GrammarErrorDeletedEvent;
import com.khankiddo.learning.rag.grammar.GrammarErrorIndexedEvent;
import com.khankiddo.learning.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationAnalysisServiceImpl implements ConversationAnalysisService {

    private final ConversationAnalysisPipeline pipeline;
    private final ConversationAnalysisMapper analysisMapper;
    private final ConversationAnalysisItemMapper itemMapper;
    private final EducationalSummaryParser summaryParser;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final PointDictionary pointDictionary;
    private final HabitCardScorer habitCardScorer;
    private final GrowthCardReviewService growthCardReviewService;
    private final PlatformTransactionManager transactionManager;

    @Override
    public ConversationAnalysisResultDto analyze(ConversationAnalysisRequest request,
                                                   Consumer<ConversationAnalysisProgress> onProgress) {
        String analysisId = UUID.randomUUID().toString();
        boolean ownedMdc = ConversationAnalysisCallLog.putIfAbsent(analysisId);
        try {
            return pipeline.run(request, analysisId, onProgress);
        } finally {
            if (ownedMdc) {
                ConversationAnalysisCallLog.clear();
            }
        }
    }

    @Override
    public ConversationAnalysisResultDto analyzeEphemeral(ConversationAnalysisRequest request,
                                                         String analysisId,
                                                         Consumer<ConversationAnalysisProgress> onProgress) {
        boolean ownedMdc = ConversationAnalysisCallLog.putIfAbsent(analysisId);
        try {
            return pipeline.run(request, analysisId, onProgress);
        } finally {
            if (ownedMdc) {
                ConversationAnalysisCallLog.clear();
            }
        }
    }

    @Override
    public ConversationAnalysisResultDto analyzeAndPersist(ConversationAnalysisRequest request,
                                                           String analysisId,
                                                           Consumer<ConversationAnalysisProgress> onProgress) {
        boolean ownedMdc = ConversationAnalysisCallLog.putIfAbsent(analysisId);
        try {
            ConversationAnalysisResultDto result = pipeline.run(request, analysisId, onProgress);
            return new TransactionTemplate(transactionManager).execute(status ->
                    persistAnalysis(request.getConversationContent().trim(), result));
        } finally {
            if (ownedMdc) {
                ConversationAnalysisCallLog.clear();
            }
        }
    }

    @Override
    @Transactional
    public void saveFailed(String analysisId, String conversationContent, String errorMessage, long processingTimeMs) {
        Long userId = SecurityUtils.requireUserId();
        String trimmedContent = StringUtils.hasText(conversationContent) ? conversationContent.trim() : "";
        String trimmedError = ConversationAnalysisErrorMessages.sanitizeStoredMessage(errorMessage);
        LocalDateTime now = LocalDateTime.now();
        ConversationAnalysis analysis = ConversationAnalysis.builder()
                .userId(userId)
                .analysisId(ConversationAnalysisPersistSupport.truncate(
                        analysisId, ConversationAnalysisPersistSupport.ANALYSIS_ID_MAX))
                .conversationContent(trimmedContent)
                .status("failed")
                .errorMessage(trimmedError)
                .processingTimeMs(processingTimeMs)
                .createdAt(now)
                .updatedAt(now)
                .build();
        long persistStartedAt = System.currentTimeMillis();
        try {
            analysisMapper.insert(analysis);
            ConversationAnalysisCallLog.record(
                    ConversationAnalysisCallLog.STAGE_PERSIST,
                    null,
                    1,
                    System.currentTimeMillis() - persistStartedAt,
                    ConversationAnalysisCallLog.RESULT_OK);
        } catch (RuntimeException ex) {
            ConversationAnalysisCallLog.record(
                    ConversationAnalysisCallLog.STAGE_PERSIST,
                    null,
                    1,
                    System.currentTimeMillis() - persistStartedAt,
                    ConversationAnalysisCallLog.resultOf(ex));
            throw ex;
        }
    }

    @Override
    @Transactional
    public ConversationAnalysisResultDto save(ConversationAnalysisSaveRequest request) {
        return persistAnalysis(request.getConversationContent().trim(),
                ConversationAnalysisResultDto.builder()
                        .analysisId(StringUtils.hasText(request.getAnalysisId())
                                ? request.getAnalysisId().trim()
                                : UUID.randomUUID().toString())
                        .analyzedAt(ObjectUtils.defaultIfNull(request.getAnalyzedAt(), LocalDateTime.now()))
                        .processingTimeMs(ObjectUtils.defaultIfNull(request.getProcessingTimeMs(), 0L))
                        .status("success")
                        .educationalSummaryJson(request.getEducationalSummary())
                        .analysisResults(buildAnalysisResultsFromSaveRequest(request))
                        .build());
    }

    private ConversationAnalysisResultDto persistAnalysis(String conversationContent,
                                                          ConversationAnalysisResultDto result) {
        long persistStartedAt = System.currentTimeMillis();
        try {
            ConversationAnalysisResultDto persisted = doPersistAnalysis(conversationContent, result);
            ConversationAnalysisCallLog.record(
                    ConversationAnalysisCallLog.STAGE_PERSIST,
                    result.getLlmModelId(),
                    1,
                    System.currentTimeMillis() - persistStartedAt,
                    ConversationAnalysisCallLog.RESULT_OK);
            return persisted;
        } catch (RuntimeException ex) {
            ConversationAnalysisCallLog.record(
                    ConversationAnalysisCallLog.STAGE_PERSIST,
                    result.getLlmModelId(),
                    1,
                    System.currentTimeMillis() - persistStartedAt,
                    ConversationAnalysisCallLog.resultOf(ex));
            throw ex;
        }
    }

    private ConversationAnalysisResultDto doPersistAnalysis(String conversationContent,
                                                            ConversationAnalysisResultDto result) {
        Long userId = SecurityUtils.requireUserId();
        String analysisId = ConversationAnalysisPersistSupport.truncate(
                StringUtils.hasText(result.getAnalysisId())
                        ? result.getAnalysisId().trim()
                        : UUID.randomUUID().toString(),
                ConversationAnalysisPersistSupport.ANALYSIS_ID_MAX);
        LocalDateTime analyzedAt = ObjectUtils.defaultIfNull(result.getAnalyzedAt(), LocalDateTime.now());
        long processingTimeMs = ObjectUtils.defaultIfNull(result.getProcessingTimeMs(), 0L);

        ConversationAnalysis analysis = ConversationAnalysis.builder()
                .userId(userId)
                .analysisId(analysisId)
                .conversationContent(conversationContent)
                .status("success")
                .processingTimeMs(processingTimeMs)
                .educationalSummary(result.getEducationalSummaryJson())
                .llmModelId(ConversationAnalysisPersistSupport.truncate(
                        result.getLlmModelId(), ConversationAnalysisPersistSupport.LLM_MODEL_ID_MAX))
                .llmModelName(ConversationAnalysisPersistSupport.truncate(
                        result.getLlmModelName(), ConversationAnalysisPersistSupport.LLM_MODEL_NAME_MAX))
                .llmProvider(ConversationAnalysisPersistSupport.truncate(
                        result.getLlmProvider(), ConversationAnalysisPersistSupport.LLM_PROVIDER_MAX))
                .pointDictionaryVersion(pointDictionary.version())
                .createdAt(analyzedAt)
                .updatedAt(LocalDateTime.now())
                .build();
        analysisMapper.insert(analysis);

        List<ConversationAnalysisItem> dbItems = buildDbItems(analysisId, result);
        if (!CollectionUtils.isEmpty(dbItems)) {
            itemMapper.batchInsert(dbItems);
            eventPublisher.publishEvent(new GrammarErrorIndexedEvent(userId, analysisId, dbItems));
        }

        eventPublisher.publishEvent(new GrowthCardMintRequestedEvent(userId, analysisId));

        return ConversationAnalysisResultDto.builder()
                .analysisId(analysisId)
                .analyzedAt(analyzedAt)
                .processingTimeMs(processingTimeMs)
                .status("success")
                .analysisResults(result.getAnalysisResults())
                .educationalSummaryJson(result.getEducationalSummaryJson())
                .llmModelId(result.getLlmModelId())
                .llmModelName(result.getLlmModelName())
                .llmProvider(result.getLlmProvider())
                .build();
    }

    private List<ConversationAnalysisItem> buildDbItems(String analysisId, ConversationAnalysisResultDto result) {
        List<ConversationAnalysisSaveRequest.SaveAnalysisItem> items = extractSaveItems(result);
        List<ConversationAnalysisItem> dbItems = new ArrayList<>();
        Map<String, Long> sentenceIdMap = new HashMap<>();
        AtomicLong sentenceCounter = new AtomicLong(1L);
        if (CollectionUtils.isEmpty(items)) {
            return dbItems;
        }
        for (ConversationAnalysisSaveRequest.SaveAnalysisItem item : items) {
            Long sentenceId = sentenceIdMap.computeIfAbsent(
                    item.getOriginalSentence(), key -> sentenceCounter.getAndIncrement());
            if (CollectionUtils.isEmpty(item.getErrors())) {
                continue;
            }
            for (ConversationAnalysisSaveRequest.SaveError error : item.getErrors()) {
                String point = StringUtils.hasText(error.getPoint()) ? error.getPoint() : "（未返回具体错误措辞）";
                String resolvedPointId;
                if (StringUtils.hasText(error.getPointId())) {
                    resolvedPointId = pointDictionary.resolveOrFallback(error.getPointId()).pointId();
                } else {
                    resolvedPointId = pointDictionary.resolveOrFallback(null).pointId();
                }
                pointDictionary.require(resolvedPointId);
                dbItems.add(ConversationAnalysisPersistSupport.truncateItem(ConversationAnalysisItem.builder()
                        .analysisId(analysisId)
                        .sentenceId(sentenceId)
                        .originalSentence(item.getOriginalSentence())
                        .pointId(resolvedPointId)
                        .errorPoint(point)
                        .suggestion(StringUtils.hasText(item.getSuggestion()) ? item.getSuggestion() : "")
                        .build()));
            }
        }
        return dbItems;
    }

    private List<ConversationAnalysisSaveRequest.SaveAnalysisItem> extractSaveItems(
            ConversationAnalysisResultDto result) {
        if (ObjectUtils.isEmpty(result) || CollectionUtils.isEmpty(result.getAnalysisResults())) {
            return List.of();
        }
        Object rawItems = result.getAnalysisResults().get("items");
        if (ObjectUtils.isEmpty(rawItems)) {
            return List.of();
        }
        List<AnalysisItemDto> items = objectMapper.convertValue(
                rawItems,
                objectMapper.getTypeFactory().constructCollectionType(List.class, AnalysisItemDto.class));
        if (CollectionUtils.isEmpty(items)) {
            return List.of();
        }
        List<ConversationAnalysisSaveRequest.SaveAnalysisItem> saveItems = new ArrayList<>();
        for (AnalysisItemDto item : items) {
            if (CollectionUtils.isEmpty(item.getErrors())) {
                continue;
            }
            saveItems.add(ConversationAnalysisSaveRequest.SaveAnalysisItem.builder()
                    .originalSentence(item.getOriginalSentence())
                    .suggestion(item.getSuggestion())
                    .errors(item.getErrors().stream()
                            .map(err -> ConversationAnalysisSaveRequest.SaveError.builder()
                                    .type(err.getType())
                                    .point(err.getPoint())
                                    .pointId(err.getPointId())
                                    .build())
                            .toList())
                    .build());
        }
        return saveItems;
    }

    private Map<String, Object> buildAnalysisResultsFromSaveRequest(ConversationAnalysisSaveRequest request) {
        Map<String, Object> analysisResults = new LinkedHashMap<>();
        if (!CollectionUtils.isEmpty(request.getItems())) {
            List<AnalysisItemDto> items = request.getItems().stream()
                    .map(item -> AnalysisItemDto.builder()
                            .originalSentence(item.getOriginalSentence())
                            .suggestion(item.getSuggestion())
                            .errors(CollectionUtils.isEmpty(item.getErrors())
                                    ? List.of()
                                    : item.getErrors().stream()
                                    .map(err -> AnalysisErrorDto.builder()
                                            .type(err.getType())
                                            .point(err.getPoint())
                                            .pointId(err.getPointId())
                                            .build())
                                    .toList())
                            .build())
                    .toList();
            analysisResults.put("items", items);
        }
        return analysisResults;
    }

    @Override
    public ConversationAnalysisDetailDto getDetail(String analysisId) {
        Long userId = SecurityUtils.requireUserId();
        ConversationAnalysis analysis = analysisMapper.findByAnalysisIdAndUserId(analysisId, userId)
                .orElseThrow(() -> new BadRequestException("分析记录不存在"));
        return buildDetailFromAnalysis(analysis, analysisId);
    }

    @Override
    public ConversationAnalysisDetailDto getDetailAsAdmin(String analysisId) {
        SecurityUtils.requireAdmin();
        ConversationAnalysis analysis = analysisMapper.findByAnalysisId(analysisId)
                .orElseThrow(() -> new BadRequestException("分析记录不存在"));
        return buildDetailFromAnalysis(analysis, analysisId);
    }

    private ConversationAnalysisDetailDto buildDetailFromAnalysis(ConversationAnalysis analysis, String analysisId) {
        List<ConversationAnalysisItem> rows = itemMapper.findByAnalysisId(analysisId);
        Map<Long, AnalysisItemDto> grouped = new LinkedHashMap<>();
        for (ConversationAnalysisItem row : rows) {
            AnalysisItemDto item = grouped.computeIfAbsent(row.getSentenceId(), id -> AnalysisItemDto.builder()
                    .sentenceId(id)
                    .originalSentence(row.getOriginalSentence())
                    .suggestion(row.getSuggestion())
                    .errors(new ArrayList<>())
                    .build());
            if (CollectionUtils.isEmpty(item.getErrors())) {
                item.setErrors(new ArrayList<>());
            }
            PointDefinition definition = StringUtils.hasText(row.getPointId())
                    ? pointDictionary.resolveOrFallback(row.getPointId())
                    : null;
            String displayType = definition != null ? definition.titleZh() : null;
            String errorLevel = definition != null
                    ? PointScoringSupport.errorLevel(definition)
                    : "STYLE";
            item.getErrors().add(AnalysisErrorDto.builder()
                    .pointId(definition != null ? definition.pointId() : row.getPointId())
                    .type(displayType)
                    .point(row.getErrorPoint())
                    .errorLevel(errorLevel)
                    .familyId(definition != null ? definition.familyId() : null)
                    .familyTitleZh(definition != null
                            ? KnowledgePointStatsSupport.familyTitle(pointDictionary, definition.familyId())
                            : null)
                    .channel(definition != null ? definition.channel().getJsonValue() : null)
                    .build());
        }

        List<ErrorTypeDistributionDto> distribution = List.of();

        List<AnalysisItemDto> items = new ArrayList<>(grouped.values());
        EducationalSummaryDto summaryRoot = summaryParser.fromJson(analysis.getEducationalSummary());
        EducationalSummaryDto enrichedSummary = summaryParser.hasPersistedScores(summaryRoot)
                ? summaryRoot
                : summaryParser.enrichReportWithScores(
                        summaryRoot, items, resolveTotalSentences(summaryRoot, items.size()));

        HabitCardScorer.HabitScoreResult habitScoreResult =
                buildHabitScoreResult(
                        rows,
                        enrichedSummary.getChineseExpressions(),
                        enrichedSummary.getActionCardDiagnoses());

        Optional<GrowthCardDto> habitCard = growthCardReviewService.findHabitCardForAnalysis(analysisId);
        String habitGrowthMintStatus = growthCardReviewService.resolveHabitMintStatus(habitCard.isPresent());
        List<GrowthCardDto> growthCards = growthCardReviewService.listByAnalysis(analysisId);

        return ConversationAnalysisDetailDto.builder()
                .analysisId(analysis.getAnalysisId())
                .conversationContent(analysis.getConversationContent())
                .status(analysis.getStatus())
                .errorMessage(analysis.getErrorMessage())
                .processingTimeMs(analysis.getProcessingTimeMs())
                .createdAt(analysis.getCreatedAt())
                .llmModelId(analysis.getLlmModelId())
                .llmModelName(analysis.getLlmModelName())
                .llmProvider(analysis.getLlmProvider())
                .educationalSummary(enrichedSummary)
                .items(items)
                .errorTypeDistribution(distribution)
                .actionCards(habitScoreResult.actionCards())
                .familyDistribution(habitScoreResult.familyDistribution())
                .habitGrowthMintStatus(habitGrowthMintStatus)
                .growthCards(growthCards)
                .build();
    }

    /**
     * 由持久化的错误行（含 {@code pointId}）+ 中文表达组装打分器输入。
     */
    HabitCardScorer.HabitScoreResult buildHabitScoreResult(
            List<ConversationAnalysisItem> rows, List<ChineseExpressionDto> chineseExpressions) {
        return buildHabitScoreResult(rows, chineseExpressions, List.of());
    }

    HabitCardScorer.HabitScoreResult buildHabitScoreResult(
            List<ConversationAnalysisItem> rows,
            List<ChineseExpressionDto> chineseExpressions,
            List<ActionCardDiagnosisDto> diagnoses) {
        boolean hasPointId = rows.stream().anyMatch(row -> StringUtils.hasText(row.getPointId()));
        if (!hasPointId) {
            return new HabitCardScorer.HabitScoreResult(null, List.of(), List.of());
        }

        List<HabitScoreInput.ErrorHit> errorHits = rows.stream()
                .map(row -> new HabitScoreInput.ErrorHit(
                        row.getPointId(),
                        row.getSentenceId() != null ? String.valueOf(row.getSentenceId()) : null,
                        row.getOriginalSentence(),
                        row.getErrorPoint(),
                        row.getSuggestion(),
                        resolveErrorLevel(row.getPointId())))
                .toList();

        HabitCardScorer.HabitScoreResult result =
                habitCardScorer.score(new HabitScoreInput(errorHits, chineseExpressions));
        return mergeActionCardDiagnoses(result, diagnoses);
    }

    private String resolveErrorLevel(String pointId) {
        if (!StringUtils.hasText(pointId)) {
            return null;
        }
        return PointScoringSupport.errorLevel(
                pointDictionary.resolveOrFallback(pointId));
    }

    private HabitCardScorer.HabitScoreResult mergeActionCardDiagnoses(
            HabitCardScorer.HabitScoreResult result,
            List<ActionCardDiagnosisDto> diagnoses) {
        if (ObjectUtils.isEmpty(result)
                || CollectionUtils.isEmpty(result.actionCards())
                || CollectionUtils.isEmpty(diagnoses)) {
            return result;
        }
        Map<Integer, ActionCardDiagnosisDto> byRank = new HashMap<>();
        Map<String, ActionCardDiagnosisDto> byHabitKey = new HashMap<>();
        Map<String, ActionCardDiagnosisDto> byPointId = new HashMap<>();
        for (ActionCardDiagnosisDto diagnosis : diagnoses) {
            if (!StringUtils.hasText(diagnosis.getDiagnosisZh())) {
                continue;
            }
            if (diagnosis.getRank() != null) {
                byRank.put(diagnosis.getRank(), diagnosis);
            }
            if (StringUtils.hasText(diagnosis.getHabitKey())) {
                byHabitKey.put(diagnosis.getHabitKey(), diagnosis);
            }
            if (StringUtils.hasText(diagnosis.getPointId())) {
                byPointId.put(diagnosis.getPointId(), diagnosis);
            }
        }
        for (ActionCardDto card : result.actionCards()) {
            ActionCardDiagnosisDto diagnosis = Optional.ofNullable(byHabitKey.get(card.getHabitKey()))
                    .orElseGet(() -> Optional.ofNullable(byPointId.get(card.getPointId()))
                            .orElse(byRank.get(card.getRank())));
            if (diagnosis != null && StringUtils.hasText(diagnosis.getDiagnosisZh())) {
                card.setDiagnosisZh(diagnosis.getDiagnosisZh().trim());
            }
        }
        return result;
    }

    private int resolveTotalSentences(EducationalSummaryDto summaryRoot, int fallbackFromItems) {
        if (ObjectUtils.isEmpty(summaryRoot) || ObjectUtils.isEmpty(summaryRoot.getReport())) {
            return Math.max(1, fallbackFromItems);
        }
        EducationalSummaryReportDto report = summaryRoot.getReport();
        EducationalSummaryStatsDto stats = report.getOverallStats();
        if (ObjectUtils.isEmpty(stats) || stats.getTotalSentences() == null) {
            return Math.max(1, fallbackFromItems);
        }
        return Math.max(1, stats.getTotalSentences());
    }

    @Override
    public ConversationAnalysisListResponse list(int page, int size, String keyword) {
        return listForUser(SecurityUtils.requireUserId(), page, size, keyword);
    }

    @Override
    public ConversationAnalysisListResponse listForUser(Long userId, int page, int size, String keyword) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 50);
        int offset = (safePage - 1) * safeSize;

        List<ConversationAnalysis> records = StringUtils.hasText(keyword)
                ? analysisMapper.findByConditions(userId, "success", null, null, keyword.trim())
                : analysisMapper.findByUserId(userId, safeSize, offset);

        long total = StringUtils.hasText(keyword)
                ? records.size()
                : analysisMapper.countByUserId(userId);

        List<ConversationAnalysisListResponse.SummaryRow> rows = records.stream()
                .map(record -> {
                    EducationalSummaryStatsDto stats = summaryParser.readPersistedStats(record.getEducationalSummary());
                    ConversationAnalysisListResponse.SummaryRow.SummaryRowBuilder rowBuilder =
                            ConversationAnalysisListResponse.SummaryRow.builder()
                                    .analysisId(record.getAnalysisId())
                                    .status(record.getStatus())
                                    .processingTimeMs(record.getProcessingTimeMs())
                                    .createdAt(record.getCreatedAt())
                                    .preview(buildPreview(record.getConversationContent()))
                                    .contentCharCount(contentCharCount(record.getConversationContent()))
                                    .llmModelId(record.getLlmModelId())
                                    .llmModelName(record.getLlmModelName())
                                    .llmProvider(record.getLlmProvider());
                    if (ObjectUtils.isNotEmpty(stats)) {
                        rowBuilder.performanceScore(stats.getPerformanceScore())
                                .dimensionScores(stats.getDimensionScores());
                    }
                    return rowBuilder.build();
                })
                .collect(Collectors.toList());

        return ConversationAnalysisListResponse.builder().total(total).records(rows).build();
    }

    @Override
    public AdminAnalysisListResponse listAllAsAdmin(int page, int size, String keyword, String username) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 50);
        int offset = (safePage - 1) * safeSize;
        String trimmedKeyword = StringUtils.hasText(keyword) ? keyword.trim() : null;
        String trimmedUsername = StringUtils.hasText(username) ? username.trim() : null;

        List<ConversationAnalysisWithUsername> records =
                analysisMapper.findAllWithUsername(trimmedKeyword, trimmedUsername, safeSize, offset);
        long total = analysisMapper.countAllWithKeyword(trimmedKeyword, trimmedUsername);

        List<AdminAnalysisListResponse.SummaryRow> rows = records.stream()
                .map(record -> {
                    EducationalSummaryStatsDto stats = summaryParser.readPersistedStats(record.getEducationalSummary());
                    AdminAnalysisListResponse.SummaryRow.SummaryRowBuilder rowBuilder =
                            AdminAnalysisListResponse.SummaryRow.builder()
                                    .analysisId(record.getAnalysisId())
                                    .userId(record.getUserId())
                                    .username(record.getUsername())
                                    .status(record.getStatus())
                                    .processingTimeMs(record.getProcessingTimeMs())
                                    .createdAt(record.getCreatedAt())
                                    .preview(buildPreview(record.getConversationContent()))
                                    .contentCharCount(contentCharCount(record.getConversationContent()))
                                    .llmModelId(record.getLlmModelId())
                                    .llmModelName(record.getLlmModelName())
                                    .llmProvider(record.getLlmProvider());
                    if (ObjectUtils.isNotEmpty(stats)) {
                        rowBuilder.performanceScore(stats.getPerformanceScore())
                                .dimensionScores(stats.getDimensionScores());
                    }
                    return rowBuilder.build();
                })
                .collect(Collectors.toList());

        return AdminAnalysisListResponse.builder().total(total).records(rows).build();
    }

    @Override
    @Transactional
    public void delete(String analysisId) {
        Long userId = SecurityUtils.requireUserId();
        List<ConversationAnalysisItem> items = itemMapper.findByAnalysisId(analysisId);
        List<Long> sentenceIds = items.stream()
                .map(ConversationAnalysisItem::getSentenceId)
                .distinct()
                .toList();
        int deleted = analysisMapper.deleteByAnalysisIdAndUserId(analysisId, userId);
        if (deleted == 0) {
            throw new BadRequestException("分析记录不存在");
        }
        itemMapper.deleteByAnalysisId(analysisId);
        if (!CollectionUtils.isEmpty(sentenceIds)) {
            eventPublisher.publishEvent(new GrammarErrorDeletedEvent(userId, analysisId, sentenceIds));
        }
    }

    private String buildPreview(String content) {
        if (!StringUtils.hasText(content)) {
            return "";
        }
        String normalized = content.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 80 ? normalized : normalized.substring(0, 80) + "...";
    }

    private int contentCharCount(String content) {
        return StringUtils.hasText(content) ? content.length() : 0;
    }
}
