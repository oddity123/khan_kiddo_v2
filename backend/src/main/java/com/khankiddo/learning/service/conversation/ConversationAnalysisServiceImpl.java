package com.khankiddo.learning.service.conversation;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.khankiddo.learning.conversation.ConversationAnalysisPipeline;
import com.khankiddo.learning.conversation.EducationalSummaryParser;
import com.khankiddo.learning.dto.conversation.AnalysisErrorDto;
import com.khankiddo.learning.dto.conversation.AnalysisItemDto;
import com.khankiddo.learning.dto.conversation.ConversationAnalysisDetailDto;
import com.khankiddo.learning.dto.conversation.ConversationAnalysisListResponse;
import com.khankiddo.learning.dto.conversation.ConversationAnalysisProgress;
import com.khankiddo.learning.dto.conversation.ConversationAnalysisRequest;
import com.khankiddo.learning.dto.conversation.ConversationAnalysisResultDto;
import com.khankiddo.learning.dto.conversation.ConversationAnalysisSaveRequest;
import com.khankiddo.learning.dto.conversation.ErrorTypeDistributionDto;
import com.khankiddo.learning.exception.BadRequestException;
import com.khankiddo.learning.exception.UnauthorizedException;
import com.khankiddo.learning.mapper.ConversationAnalysisItemMapper;
import com.khankiddo.learning.mapper.ConversationAnalysisMapper;
import com.khankiddo.learning.model.ConversationAnalysis;
import com.khankiddo.learning.model.ConversationAnalysisItem;
import com.khankiddo.learning.model.enums.ProblemType;
import com.khankiddo.learning.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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

    @Override
    public ConversationAnalysisResultDto analyze(ConversationAnalysisRequest request,
                                                   Consumer<ConversationAnalysisProgress> onProgress) {
        return pipeline.run(request, onProgress);
    }

    @Override
    @Transactional
    public ConversationAnalysisResultDto save(ConversationAnalysisSaveRequest request) {
        Long userId = requireUserId();
        String analysisId = StringUtils.hasText(request.getAnalysisId())
                ? request.getAnalysisId().trim()
                : UUID.randomUUID().toString();
        LocalDateTime analyzedAt = ObjectUtils.defaultIfNull(request.getAnalyzedAt(), LocalDateTime.now());
        long processingTimeMs = ObjectUtils.defaultIfNull(request.getProcessingTimeMs(), 0L);

        ConversationAnalysis analysis = ConversationAnalysis.builder()
                .userId(userId)
                .analysisId(analysisId)
                .conversationContent(request.getConversationContent().trim())
                .status("success")
                .processingTimeMs(processingTimeMs)
                .educationalSummary(request.getEducationalSummary())
                .createdAt(analyzedAt)
                .updatedAt(LocalDateTime.now())
                .build();
        analysisMapper.insert(analysis);

        List<ConversationAnalysisItem> dbItems = new ArrayList<>();
        Map<String, Long> sentenceIdMap = new HashMap<>();
        AtomicLong sentenceCounter = new AtomicLong(1L);
        if (!CollectionUtils.isEmpty(request.getItems())) {
            for (ConversationAnalysisSaveRequest.SaveAnalysisItem item : request.getItems()) {
                Long sentenceId = sentenceIdMap.computeIfAbsent(
                        item.getOriginalSentence(), key -> sentenceCounter.getAndIncrement());
                if (CollectionUtils.isEmpty(item.getErrors())) {
                    continue;
                }
                for (ConversationAnalysisSaveRequest.SaveError error : item.getErrors()) {
                    String englishType = toEnglishProblemType(error.getType());
                    String point = StringUtils.hasText(error.getPoint()) ? error.getPoint() : "（未返回具体错误措辞）";
                    dbItems.add(ConversationAnalysisItem.builder()
                            .analysisId(analysisId)
                            .sentenceId(sentenceId)
                            .originalSentence(item.getOriginalSentence())
                            .problemTypes(englishType)
                            .errorPoint(point)
                            .suggestion(StringUtils.hasText(item.getSuggestion()) ? item.getSuggestion() : "")
                            .build());
                }
            }
        }
        if (!CollectionUtils.isEmpty(dbItems)) {
            itemMapper.batchInsert(dbItems);
        }

        return ConversationAnalysisResultDto.builder()
                .analysisId(analysisId)
                .analyzedAt(analyzedAt)
                .processingTimeMs(processingTimeMs)
                .status("success")
                .build();
    }

    @Override
    public ConversationAnalysisDetailDto getDetail(String analysisId) {
        Long userId = requireUserId();
        ConversationAnalysis analysis = analysisMapper.findByAnalysisIdAndUserId(analysisId, userId)
                .orElseThrow(() -> new BadRequestException("分析记录不存在"));

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
            ProblemType problemType = ProblemType.fromEnglishName(row.getProblemTypes());
            item.getErrors().add(AnalysisErrorDto.builder()
                    .type(problemType != null ? problemType.getChineseName() : row.getProblemTypes())
                    .point(row.getErrorPoint())
                    .errorLevel(problemType != null ? problemType.getErrorLevel().name() : "STYLE")
                    .build());
        }

        Map<String, Integer> distCounts = new HashMap<>();
        for (ConversationAnalysisItem row : rows) {
            ProblemType problemType = ProblemType.fromEnglishName(row.getProblemTypes());
            String label = problemType != null ? problemType.getChineseName() : row.getProblemTypes();
            distCounts.merge(label, 1, Integer::sum);
        }
        List<ErrorTypeDistributionDto> distribution = distCounts.entrySet().stream()
                .map(e -> ErrorTypeDistributionDto.builder().type(e.getKey()).count(e.getValue()).build())
                .toList();

        Object summary = summaryParser.fromJson(analysis.getEducationalSummary()).get("report");

        return ConversationAnalysisDetailDto.builder()
                .analysisId(analysis.getAnalysisId())
                .conversationContent(analysis.getConversationContent())
                .status(analysis.getStatus())
                .processingTimeMs(analysis.getProcessingTimeMs())
                .createdAt(analysis.getCreatedAt())
                .educationalSummary(new ConversationAnalysisDetailDto.MapHolder(summary))
                .items(new ArrayList<>(grouped.values()))
                .errorTypeDistribution(distribution)
                .build();
    }

    @Override
    public ConversationAnalysisListResponse list(int page, int size, String keyword) {
        Long userId = requireUserId();
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
                .map(record -> ConversationAnalysisListResponse.SummaryRow.builder()
                        .analysisId(record.getAnalysisId())
                        .status(record.getStatus())
                        .processingTimeMs(record.getProcessingTimeMs())
                        .createdAt(record.getCreatedAt())
                        .preview(buildPreview(record.getConversationContent()))
                        .build())
                .collect(Collectors.toList());

        return ConversationAnalysisListResponse.builder().total(total).records(rows).build();
    }

    @Override
    @Transactional
    public void delete(String analysisId) {
        Long userId = requireUserId();
        int deleted = analysisMapper.deleteByAnalysisIdAndUserId(analysisId, userId);
        if (deleted == 0) {
            throw new BadRequestException("分析记录不存在");
        }
        itemMapper.deleteByAnalysisId(analysisId);
    }

    private Long requireUserId() {
        Long userId = SecurityUtils.getCurrentUserId();
        if (ObjectUtils.isEmpty(userId)) {
            throw new UnauthorizedException("未登录");
        }
        return userId;
    }

    private String toEnglishProblemType(String type) {
        if (!StringUtils.hasText(type)) {
            return type;
        }
        String trimmed = type.trim();
        for (ProblemType problemType : ProblemType.values()) {
            if (problemType.getChineseName().equals(trimmed) || problemType.getEnglishName().equals(trimmed)) {
                return problemType.getEnglishName();
            }
        }
        return trimmed;
    }

    private String buildPreview(String content) {
        if (!StringUtils.hasText(content)) {
            return "";
        }
        String normalized = content.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 80 ? normalized : normalized.substring(0, 80) + "...";
    }
}
