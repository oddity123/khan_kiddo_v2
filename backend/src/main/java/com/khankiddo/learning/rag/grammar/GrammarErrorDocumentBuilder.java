package com.khankiddo.learning.rag.grammar;

import com.khankiddo.learning.knowledge.PointDefinition;
import com.khankiddo.learning.knowledge.PointDictionary;
import com.khankiddo.learning.model.ConversationAnalysisItem;
import com.khankiddo.learning.model.enums.ProblemType;
import com.khankiddo.learning.rag.core.RagDocumentBuilder;
import com.khankiddo.learning.rag.core.RagMetadataKeys;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class GrammarErrorDocumentBuilder implements RagDocumentBuilder<GrammarErrorSentenceDocument> {

    private static final DateTimeFormatter ISO_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final PointDictionary pointDictionary;

    @Override
    public TextSegment build(GrammarErrorSentenceDocument source) {
        List<ConversationAnalysisItem> items = source.getItems();
        ConversationAnalysisItem first = items.get(0);
        Set<String> pointIds = new LinkedHashSet<>();
        Set<String> familyIds = new LinkedHashSet<>();
        Set<String> knowledgeLabels = new LinkedHashSet<>();
        Set<String> errorPoints = new LinkedHashSet<>();
        for (ConversationAnalysisItem item : items) {
            if (StringUtils.hasText(item.getPointId())) {
                PointDefinition definition = pointDictionary.resolveOrFallback(item.getPointId());
                pointIds.add(definition.pointId());
                familyIds.add(definition.familyId());
                knowledgeLabels.add(definition.titleZh());
            } else if (StringUtils.hasText(item.getProblemTypes())) {
                knowledgeLabels.add(ProblemType.translate(item.getProblemTypes()));
            }
            if (StringUtils.hasText(item.getErrorPoint())) {
                errorPoints.add(item.getErrorPoint().trim());
            }
        }
        String knowledgeLine = String.join(", ", knowledgeLabels);
        String errorPointLine = String.join("; ", errorPoints);
        String suggestion = StringUtils.hasText(first.getSuggestion()) ? first.getSuggestion().trim() : "（无建议）";
        String text = """
                知识点: %s
                原句: %s
                错误点: %s
                建议: %s
                """.formatted(
                knowledgeLine,
                first.getOriginalSentence().trim(),
                errorPointLine,
                suggestion).trim();

        String createdAt = source.getCreatedAt() != null
                ? source.getCreatedAt().format(ISO_FORMAT)
                : ISO_FORMAT.format(first.getCreatedAt() != null ? first.getCreatedAt() : java.time.LocalDateTime.now());

        Map<String, Object> metadataMap = new HashMap<>();
        metadataMap.put(RagMetadataKeys.USER_ID, String.valueOf(source.getUserId()));
        metadataMap.put(RagMetadataKeys.ANALYSIS_ID, source.getAnalysisId());
        metadataMap.put(RagMetadataKeys.SENTENCE_ID, String.valueOf(source.getSentenceId()));
        metadataMap.put(RagMetadataKeys.POINT_IDS, pointIds.stream().collect(Collectors.joining(",")));
        metadataMap.put(RagMetadataKeys.FAMILY_IDS, familyIds.stream().collect(Collectors.joining(",")));
        metadataMap.put(RagMetadataKeys.CREATED_AT, createdAt);
        Metadata metadata = Metadata.from(metadataMap);
        return TextSegment.from(text, metadata);
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
