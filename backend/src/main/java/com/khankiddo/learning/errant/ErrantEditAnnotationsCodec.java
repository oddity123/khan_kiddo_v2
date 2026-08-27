package com.khankiddo.learning.errant;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.khankiddo.learning.dto.conversation.AnalysisItemDto;
import com.khankiddo.learning.dto.conversation.SentenceEditDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 句级 ERRANT 批注与 {@code conversation_analysis.edit_annotations} JSON 互转。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ErrantEditAnnotationsCodec {

    private static final TypeReference<List<StoredSentenceAnnotation>> LIST_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;

    public String serializeFromItems(List<AnalysisItemDto> items) {
        if (CollectionUtils.isEmpty(items)) {
            return null;
        }
        List<StoredSentenceAnnotation> rows = new ArrayList<>();
        for (AnalysisItemDto item : items) {
            if (item == null || item.getSentenceId() == null) {
                continue;
            }
            if (CollectionUtils.isEmpty(item.getEdits())
                    || CollectionUtils.isEmpty(item.getOriginalTokens())
                    || CollectionUtils.isEmpty(item.getCorrectedTokens())) {
                continue;
            }
            rows.add(new StoredSentenceAnnotation(
                    item.getSentenceId(),
                    item.getOriginalTokens(),
                    item.getCorrectedTokens(),
                    item.getEdits()));
        }
        if (rows.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(rows);
        } catch (JsonProcessingException ex) {
            log.warn("serialize edit_annotations failed: {}", ex.toString());
            return null;
        }
    }

    public String serializeFromAnalysisResults(Map<String, Object> analysisResults) {
        if (analysisResults == null) {
            return null;
        }
        Object rawItems = analysisResults.get("items");
        if (rawItems == null) {
            return null;
        }
        try {
            List<AnalysisItemDto> items = objectMapper.convertValue(
                    rawItems,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, AnalysisItemDto.class));
            return serializeFromItems(items);
        } catch (IllegalArgumentException ex) {
            log.warn("extract edit_annotations from analysisResults failed: {}", ex.toString());
            return null;
        }
    }

    public void mergeIntoItems(List<AnalysisItemDto> items, String editAnnotationsJson) {
        if (CollectionUtils.isEmpty(items) || !StringUtils.hasText(editAnnotationsJson)) {
            return;
        }
        List<StoredSentenceAnnotation> rows;
        try {
            rows = objectMapper.readValue(editAnnotationsJson, LIST_TYPE);
        } catch (JsonProcessingException ex) {
            log.warn("parse edit_annotations failed: {}", ex.toString());
            return;
        }
        if (CollectionUtils.isEmpty(rows)) {
            return;
        }
        Map<Long, StoredSentenceAnnotation> bySentenceId = new LinkedHashMap<>();
        for (StoredSentenceAnnotation row : rows) {
            if (row != null && row.sentenceId() != null) {
                bySentenceId.put(row.sentenceId(), row);
            }
        }
        for (AnalysisItemDto item : items) {
            if (item == null || item.getSentenceId() == null) {
                continue;
            }
            StoredSentenceAnnotation row = bySentenceId.get(item.getSentenceId());
            if (row == null) {
                continue;
            }
            item.setOriginalTokens(row.originalTokens());
            item.setCorrectedTokens(row.correctedTokens());
            item.setEdits(row.edits());
        }
    }

    public record StoredSentenceAnnotation(
            Long sentenceId,
            List<String> originalTokens,
            List<String> correctedTokens,
            List<SentenceEditDto> edits) {
    }
}
