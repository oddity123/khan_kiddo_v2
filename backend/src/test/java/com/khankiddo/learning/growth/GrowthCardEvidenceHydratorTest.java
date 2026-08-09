package com.khankiddo.learning.growth;

import com.khankiddo.learning.conversation.EducationalSummaryParser;
import com.khankiddo.learning.dto.conversation.ChineseExpressionDto;
import com.khankiddo.learning.dto.conversation.EducationalSummaryDto;
import com.khankiddo.learning.mapper.ConversationAnalysisItemMapper;
import com.khankiddo.learning.mapper.ConversationAnalysisMapper;
import com.khankiddo.learning.model.ConversationAnalysis;
import com.khankiddo.learning.model.GrowthCard;
import com.khankiddo.learning.model.GrowthCardEvidence;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GrowthCardEvidenceHydratorTest {

    @Mock
    private ConversationAnalysisMapper analysisMapper;
    @Mock
    private ConversationAnalysisItemMapper itemMapper;
    @Mock
    private EducationalSummaryParser summaryParser;
    @Mock
    private GrowthCardAnalysisSupport analysisSupport;
    @Mock
    private GrowthCardStore store;

    private GrowthCardEvidenceHydrator hydrator;

    @BeforeEach
    void setUp() {
        hydrator = new GrowthCardEvidenceHydrator(
                analysisMapper, itemMapper, summaryParser, analysisSupport, store);
    }

    @Test
    void hydrateMissing_shouldRebuildVocabEvidenceFromSummary() {
        GrowthCard card = GrowthCard.builder()
                .cardId("c1")
                .userId(9L)
                .type("vocab")
                .sourceAnalysisId("a1")
                .sourceRef("vocab:3")
                .front("准确")
                .back("accuracy")
                .build();
        when(analysisMapper.findByAnalysisId("a1")).thenReturn(Optional.of(
                ConversationAnalysis.builder().analysisId("a1").educationalSummary("{}").build()));
        when(summaryParser.fromJson("{}")).thenReturn(EducationalSummaryDto.builder()
                .chineseExpressions(List.of(ChineseExpressionDto.builder()
                        .originalIndex(3)
                        .originalSentence("我想说准确度")
                        .suggestion("accuracy")
                        .build()))
                .build());

        Map<String, List<GrowthCardEvidence>> result =
                hydrator.hydrateMissing(List.of(card), Map.of());

        assertEquals(1, result.get("c1").size());
        assertEquals("我想说准确度", result.get("c1").get(0).getOriginalSentence());
        assertEquals("s:3", result.get("c1").get(0).getTrackKey());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<GrowthCardEvidence>> captor = ArgumentCaptor.forClass(List.class);
        verify(store).saveEvidence(captor.capture());
        assertEquals(1, captor.getValue().size());
    }

    @Test
    void hydrateMissing_shouldSkipWhenAlreadyHasEvidence() {
        GrowthCard card = GrowthCard.builder()
                .cardId("c1")
                .sourceAnalysisId("a1")
                .sourceRef("vocab:1")
                .build();
        GrowthCardEvidence existing = GrowthCardEvidence.builder()
                .cardId("c1")
                .originalSentence("already")
                .trackKey("s:1")
                .build();

        Map<String, List<GrowthCardEvidence>> result =
                hydrator.hydrateMissing(List.of(card), Map.of("c1", List.of(existing)));

        assertEquals(1, result.get("c1").size());
        assertEquals("already", result.get("c1").get(0).getOriginalSentence());
        verify(store, org.mockito.Mockito.never()).saveEvidence(anyList());
        assertTrue(true);
    }
}
