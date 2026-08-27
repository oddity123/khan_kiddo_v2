package com.khankiddo.learning.errant;

import com.khankiddo.learning.dto.conversation.AnalysisItemDto;
import com.khankiddo.learning.dto.conversation.SentenceEditDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Stage2 sanitize 之后：对有 suggestion 的句子批量调用 ERRANT，写回 tokens + R/M/U edits。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ErrantEditAnnotationService {

    private final ErrantAnnotatorClient annotatorClient;

    public void enrich(List<AnalysisItemDto> items) {
        if (!annotatorClient.isEnabled() || CollectionUtils.isEmpty(items)) {
            return;
        }
        List<ErrantAnnotatorClient.AnnotatePair> pairs = new ArrayList<>();
        for (AnalysisItemDto item : items) {
            if (item == null || item.getSentenceId() == null) {
                continue;
            }
            String original = item.getOriginalSentence();
            String suggestion = item.getSuggestion();
            if (!StringUtils.hasText(original) || !StringUtils.hasText(suggestion)) {
                continue;
            }
            if (Objects.equals(original.trim(), suggestion.trim())) {
                continue;
            }
            List<String> originalTokens = ErrantTokenSupport.tokenize(original);
            List<String> correctedTokens = ErrantTokenSupport.tokenize(suggestion);
            if (originalTokens.isEmpty() || correctedTokens.isEmpty()) {
                continue;
            }
            item.setOriginalTokens(originalTokens);
            item.setCorrectedTokens(correctedTokens);
            pairs.add(new ErrantAnnotatorClient.AnnotatePair(
                    String.valueOf(item.getSentenceId()),
                    ErrantTokenSupport.join(originalTokens),
                    ErrantTokenSupport.join(correctedTokens)));
        }
        if (pairs.isEmpty()) {
            return;
        }
        Map<String, List<SentenceEditDto>> annotated = annotatorClient.annotateBatch(pairs);
        if (annotated.isEmpty()) {
            return;
        }
        int attached = 0;
        for (AnalysisItemDto item : items) {
            if (item == null || item.getSentenceId() == null) {
                continue;
            }
            List<SentenceEditDto> edits = annotated.get(String.valueOf(item.getSentenceId()));
            if (CollectionUtils.isEmpty(edits)) {
                continue;
            }
            if (!offsetsValid(edits, item.getOriginalTokens(), item.getCorrectedTokens())) {
                log.warn("ERRANT edits offsets out of range for sentenceId={}, dropping", item.getSentenceId());
                continue;
            }
            item.setEdits(edits);
            attached++;
        }
        log.info("ERRANT attached edits to {}/{} candidate sentences", attached, pairs.size());
    }

    static boolean offsetsValid(
            List<SentenceEditDto> edits, List<String> originalTokens, List<String> correctedTokens) {
        if (CollectionUtils.isEmpty(edits)) {
            return true;
        }
        int oLen = originalTokens == null ? 0 : originalTokens.size();
        int cLen = correctedTokens == null ? 0 : correctedTokens.size();
        for (SentenceEditDto edit : edits) {
            if (edit.getOStart() < 0 || edit.getOEnd() < edit.getOStart() || edit.getOEnd() > oLen) {
                return false;
            }
            if (edit.getCStart() < 0 || edit.getCEnd() < edit.getCStart() || edit.getCEnd() > cLen) {
                return false;
            }
        }
        return true;
    }
}
