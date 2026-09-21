package com.khankiddo.learning.conversation;

import com.khankiddo.learning.dto.conversation.AnalysisErrorDto;
import com.khankiddo.learning.dto.conversation.AnalysisItemDto;
import com.khankiddo.learning.growth.NaturalExpressionCandidateFilter;
import com.khankiddo.learning.knowledge.PointDictionary;
import com.khankiddo.learning.llm.ExpressionReviewCandidate;
import com.khankiddo.learning.model.ConversationAnalysisItem;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.apache.commons.lang3.ObjectUtils.isEmpty;

/**
 * 覆盖 Pipeline 侧 NATURAL 过滤入模候选收集逻辑（与 {@link ConversationAnalysisPipeline} 同构）。
 */
class ConversationAnalysisPipelinePhraseCandidatesTest {

    private static NaturalExpressionCandidateFilter filter;

    @BeforeAll
    static void setUp() {
        PointDictionary dictionary = PointDictionary.loadFromClasspath("knowledge/point-dictionary-v1.json");
        filter = new NaturalExpressionCandidateFilter(dictionary);
    }

    @Test
    void collectNaturalExpressionCandidates_keepsNatural_rejectsStyle() {
        AnalysisItemDto natural = AnalysisItemDto.builder()
                .sentenceId(1L)
                .originalSentence("I'm so exciting.")
                .suggestion("I'm so excited.")
                .errors(List.of(AnalysisErrorDto.builder()
                        .pointId("FEEL_ED_ADJ")
                        .point("exciting → excited（感到…用 -ed）")
                        .errorLevel("NATURAL")
                        .build()))
                .build();
        AnalysisItemDto style = AnalysisItemDto.builder()
                .sentenceId(2L)
                .originalSentence("I went and I ate.")
                .suggestion("I went, then I ate.")
                .errors(List.of(AnalysisErrorDto.builder()
                        .pointId("SENTENCE_LOOSE_AND")
                        .point("and I → , then I")
                        .errorLevel("STYLE")
                        .build()))
                .build();

        List<ExpressionReviewCandidate> candidates =
                collectNaturalExpressionCandidates(List.of(natural, style));

        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).sentenceId()).isEqualTo(1L);
        assertThat(candidates.get(0).pointId()).isEqualTo("FEEL_ED_ADJ");
    }

    /** 与 Pipeline#collectNaturalExpressionCandidates 同构，避免反射构造胖依赖。 */
    private static List<ExpressionReviewCandidate> collectNaturalExpressionCandidates(
            List<AnalysisItemDto> items) {
        if (CollectionUtils.isEmpty(items)) {
            return List.of();
        }
        List<ExpressionReviewCandidate> candidates = new ArrayList<>();
        for (AnalysisItemDto item : items) {
            if (isEmpty(item) || CollectionUtils.isEmpty(item.getErrors())) {
                continue;
            }
            for (AnalysisErrorDto error : item.getErrors()) {
                ConversationAnalysisItem probe = ConversationAnalysisItem.builder()
                        .sentenceId(item.getSentenceId())
                        .pointId(error.getPointId())
                        .originalSentence(item.getOriginalSentence())
                        .errorPoint(error.getPoint())
                        .suggestion(item.getSuggestion())
                        .build();
                if (!filter.test(probe)) {
                    continue;
                }
                candidates.add(new ExpressionReviewCandidate(
                        item.getSentenceId(),
                        item.getOriginalSentence(),
                        item.getSuggestion(),
                        error.getPoint(),
                        error.getPointId()));
            }
        }
        return candidates;
    }
}
