package com.khankiddo.learning.conversation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.khankiddo.learning.ai.conversation.model.GrammarAnalysisResult;
import com.khankiddo.learning.conversation.scoring.PerformanceScoreResult;
import com.khankiddo.learning.dto.conversation.ActionCardDiagnosisDto;
import com.khankiddo.learning.dto.conversation.ActionCardDiagnosisResultDto;
import com.khankiddo.learning.dto.conversation.ActionCardDto;
import com.khankiddo.learning.dto.conversation.EducationalSummaryDto;
import com.khankiddo.learning.knowledge.CardKind;
import com.khankiddo.learning.knowledge.CardPolicy;
import com.khankiddo.learning.knowledge.PointChannel;
import com.khankiddo.learning.knowledge.PointDictionary;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EducationalSummaryParserTest {

    @Test
    void normalizesActionCardDiagnosisResult() {
        PointDictionary dictionary = PointDictionary.loadFromClasspath("knowledge/point-dictionary-v1.json");
        EducationalSummaryParser parser = new EducationalSummaryParser(
                new ObjectMapper(),
                input -> new PerformanceScoreResult(90, 90, 90, 90, 90),
                dictionary);
        ActionCardDto card = ActionCardDto.builder()
                .rank(1)
                .habitKey("FAM_ARTICLE")
                .pointId("ARTICLE_A_AN")
                .channel(PointChannel.RULE)
                .cardKind(CardKind.GRAMMAR)
                .cardPolicy(CardPolicy.NORMAL)
                .titleZh("冠词")
                .headlineZh("本次最该改：冠词容易用错")
                .errorCount(2)
                .build();

        EducationalSummaryDto summary = parser.parseActionCardDiagnosisSummary(
                ActionCardDiagnosisResultDto.builder()
                        .cards(List.of(ActionCardDiagnosisDto.builder()
                                .rank(1)
                                .diagnosisZh("  这次冠词问题集中在 a/an 的读音判断，说明单数名词前的冠词选择还不够稳定。  ")
                                .build()))
                        .build(),
                GrammarAnalysisResult.builder().build(),
                2,
                2,
                0,
                List.of(card));

        assertEquals(1, summary.getActionCardDiagnoses().size());
        assertEquals("FAM_ARTICLE", summary.getActionCardDiagnoses().get(0).getHabitKey());
        assertEquals("ARTICLE_A_AN", summary.getActionCardDiagnoses().get(0).getPointId());
        assertEquals("这次冠词问题集中在 a/an 的读音判断，说明单数名词前的冠词选择还不够稳定。",
                summary.getActionCardDiagnoses().get(0).getDiagnosisZh());
    }
}
