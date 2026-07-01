package com.khankiddo.learning.rag.grammar;

import com.khankiddo.learning.model.ConversationAnalysisItem;
import com.khankiddo.learning.model.enums.ProblemType;
import com.khankiddo.learning.rag.core.RagDocumentBuilder;
import com.khankiddo.learning.rag.core.RagMetadataKeys;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class GrammarErrorDocumentBuilder implements RagDocumentBuilder<GrammarErrorSentenceDocument> {

    private static final DateTimeFormatter ISO_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    @Override
    public TextSegment build(GrammarErrorSentenceDocument source) {
        List<ConversationAnalysisItem> items = source.getItems();
        ConversationAnalysisItem first = items.get(0);
        Set<String> problemTypes = new LinkedHashSet<>();
        Set<String> errorPoints = new LinkedHashSet<>();
        for (ConversationAnalysisItem item : items) {
            if (StringUtils.hasText(item.getProblemTypes())) {
                problemTypes.add(item.getProblemTypes().trim());
            }
            if (StringUtils.hasText(item.getErrorPoint())) {
                errorPoints.add(item.getErrorPoint().trim());
            }
        }
        String problemTypeLine = problemTypes.stream()
                .map(this::translateProblemType)
                .collect(Collectors.joining(", "));
        String errorPointLine = String.join("; ", errorPoints);
        String suggestion = StringUtils.hasText(first.getSuggestion()) ? first.getSuggestion().trim() : "（无建议）";
        String text = """
                问题类型: %s
                原句: %s
                错误点: %s
                建议: %s
                """.formatted(
                problemTypeLine,
                first.getOriginalSentence().trim(),
                errorPointLine,
                suggestion).trim();

        String problemTypesMeta = problemTypes.stream().collect(Collectors.joining(","));
        String createdAt = source.getCreatedAt() != null
                ? source.getCreatedAt().format(ISO_FORMAT)
                : ISO_FORMAT.format(first.getCreatedAt() != null ? first.getCreatedAt() : java.time.LocalDateTime.now());

        Map<String, Object> metadataMap = new HashMap<>();
        metadataMap.put(RagMetadataKeys.USER_ID, String.valueOf(source.getUserId()));
        metadataMap.put(RagMetadataKeys.ANALYSIS_ID, source.getAnalysisId());
        metadataMap.put(RagMetadataKeys.SENTENCE_ID, String.valueOf(source.getSentenceId()));
        metadataMap.put(RagMetadataKeys.PROBLEM_TYPES, problemTypesMeta);
        metadataMap.put(RagMetadataKeys.CREATED_AT, createdAt);
        Metadata metadata = Metadata.from(metadataMap);
        return TextSegment.from(text, metadata);
    }

    private String translateProblemType(String englishName) {
        ProblemType type = ProblemType.fromEnglishName(englishName);
        return type != null ? type.getChineseName() + " (" + englishName + ")" : englishName;
    }

    public List<GrammarErrorSentenceDocument> groupBySentence(
            Long userId,
            String analysisId,
            List<ConversationAnalysisItem> items) {
        if (CollectionUtils.isEmpty(items)) {
            return List.of();
        }
        return items.stream()
                .collect(Collectors.groupingBy(ConversationAnalysisItem::getSentenceId))
                .entrySet()
                .stream()
                .map(entry -> GrammarErrorSentenceDocument.builder()
                        .userId(userId)
                        .analysisId(analysisId)
                        .sentenceId(entry.getKey())
                        .items(entry.getValue())
                        .createdAt(resolveCreatedAt(entry.getValue()))
                        .build())
                .toList();
    }

    private java.time.LocalDateTime resolveCreatedAt(List<ConversationAnalysisItem> items) {
        return items.stream()
                .map(ConversationAnalysisItem::getCreatedAt)
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(java.time.LocalDateTime.now());
    }
}
