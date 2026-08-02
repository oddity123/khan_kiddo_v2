package com.khankiddo.learning.knowledge;

import com.khankiddo.learning.dto.conversation.ChineseExpressionDto;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HabitCardScorerTest {

    @Test
    void chineseCanBeatSparseStyleGrammar() {
        PointDictionary dict = PointDictionary.loadFromClasspath("knowledge/point-dictionary-v1.json");
        HabitCardScorer scorer = new HabitCardScorer(dict);

        List<HabitScoreInput.ErrorHit> hits = List.of(
                hit("ARTICLE_A_AN", "STYLE", "a apple", "an apple", "I eat a apple."),
                hit("ARTICLE_A_AN", "BASIC", "a hour", "an hour", "Wait a hour.")
        );
        // 3 条中文夹杂
        List<ChineseExpressionDto> chinese = List.of(
                chinese("立法", "legislation"),
                chinese("客商", "client"),
                chinese("敲碗", "tap the bowl")
        );

        HabitCardScorer.HabitScoreResult result = scorer.score(new HabitScoreInput(hits, chinese));

        assertEquals("CHINESE_CODE_SWITCH", result.topHabit().getPointId());
        assertTrue(result.topHabit().getHeadlineZh().contains("中文") || result.topHabit().getHeadlineZh().contains("切"));
    }

    @Test
    void chineseInjectUsesBasicSeverityNotDictionaryStyle() {
        PointDictionary dict = PointDictionary.loadFromClasspath("knowledge/point-dictionary-v1.json");
        HabitCardScorer scorer = new HabitCardScorer(dict);

        // FAM_ARTICLE score = 2 * (BASIC=2 * impactWeight=1.0 * fixability=0.9) = 3.6
        List<HabitScoreInput.ErrorHit> hits = List.of(
                hit("ARTICLE_A_AN", "BASIC", "a apple", "an apple", "I eat a apple."),
                hit("ARTICLE_A_AN", "BASIC", "a hour", "an hour", "Wait a hour.")
        );
        // 2 条中文夹杂：BASIC 应为 2*(2*1.25*1.0)=5.0 > 3.6；若误用字典 STYLE=1 则为 2*(1*1.25*1.0)=2.5 < 3.6
        List<ChineseExpressionDto> chinese = List.of(
                chinese("立法", "legislation"),
                chinese("客商", "client")
        );

        HabitCardScorer.HabitScoreResult result = scorer.score(new HabitScoreInput(hits, chinese));

        assertEquals("CHINESE_CODE_SWITCH", result.topHabit().getPointId());
        assertEquals(5.0, result.topHabit().getScore(), 1e-9);
        assertEquals("CHINESE", result.topHabit().getHabitKey());
    }

    @Test
    void rarePrepositionNeverEntersTop() {
        PointDictionary dict = PointDictionary.loadFromClasspath("knowledge/point-dictionary-v1.json");
        HabitCardScorer scorer = new HabitCardScorer(dict);
        assertEquals(CardPolicy.RARE, dict.require("PREP_FIXED").cardPolicy());

        List<HabitScoreInput.ErrorHit> hits = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            hits.add(hit("PREP_FIXED", "BASIC", "interested for", "interested in", "I am interested for it."));
        }
        hits.add(hit("FEEL_ED_ADJ", "NATURAL", "excited feeling", "excited", "I am excited feeling."));
        hits.add(hit("FEEL_ED_ADJ", "NATURAL", "bored feeling", "bored", "I felt bored feeling."));

        HabitCardScorer.HabitScoreResult result = scorer.score(new HabitScoreInput(hits, null));

        assertTrue(result.actionCards().stream().noneMatch(c -> "PREP_FIXED".equals(c.getPointId())));
        assertTrue(result.actionCards().stream().anyMatch(c -> "FAM_WORD_FORM".equals(c.getHabitKey())));
        assertEquals("FEEL_ED_ADJ", result.topHabit().getPointId());
    }

    @Test
    void lexicalIsSingleCandidateEvenIfManyHits() {
        PointDictionary dict = PointDictionary.loadFromClasspath("knowledge/point-dictionary-v1.json");
        HabitCardScorer scorer = new HabitCardScorer(dict);

        List<HabitScoreInput.ErrorHit> hits = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            hits.add(hit("LEXICAL_GAP", "NATURAL", "thing", "specific term", "I need that thing."));
        }

        HabitCardScorer.HabitScoreResult result = scorer.score(new HabitScoreInput(hits, null));

        long lexicalCardCount = result.actionCards().stream()
                .filter(c -> c.getChannel() == PointChannel.LEXICAL)
                .count();
        assertEquals(1, lexicalCardCount);
        assertEquals(10, result.topHabit().getErrorCount());
        assertEquals("LEXICAL", result.topHabit().getHabitKey());
    }

    @Test
    void requiresAtLeastTwoHits() {
        PointDictionary dict = PointDictionary.loadFromClasspath("knowledge/point-dictionary-v1.json");
        HabitCardScorer scorer = new HabitCardScorer(dict);

        List<HabitScoreInput.ErrorHit> hits = List.of(
                hit("FEEL_ED_ADJ", "NATURAL", "excited feeling", "excited", "I am excited feeling.")
        );

        HabitCardScorer.HabitScoreResult result = scorer.score(new HabitScoreInput(hits, null));

        assertTrue(result.actionCards().isEmpty());
        assertNull(result.topHabit());
    }

    @Test
    void familyCatchAllDoesNotStealHeadlineWhenSpecificLeavesExist() {
        PointDictionary dict = PointDictionary.loadFromClasspath("knowledge/point-dictionary-v1.json");
        HabitCardScorer scorer = new HabitCardScorer(dict);

        List<HabitScoreInput.ErrorHit> hits = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            hits.add(hit("WORD_FORM_POS", "BASIC", "accurate → check", "check", "and accurate the accuracy"));
        }
        hits.add(hit("GERUND_AS_SUBJECT", "BASIC", "improve → improving", "improving", "improve quality is urgent"));
        hits.add(hit("GERUND_AS_SUBJECT", "BASIC", "interrupt → interrupting", "interrupting", "interrupt is not fluent"));
        hits.add(hit("FEEL_ED_ADJ", "NATURAL", "exciting → excited", "excited", "I'm so exciting."));

        HabitCardScorer.HabitScoreResult result = scorer.score(new HabitScoreInput(hits, null));

        assertEquals("FAM_WORD_FORM", result.topHabit().getHabitKey());
        assertEquals("GERUND_AS_SUBJECT", result.topHabit().getPointId());
        assertTrue(result.topHabit().getHeadlineZh().contains("词形与词类"));
        assertTrue(result.topHabit().getWhyZh().contains("其中可先练"));
        assertTrue(result.topHabit().getWhyZh().contains("动词做主语"));
        assertTrue(!result.topHabit().getWhyZh().contains("FEEL_ED_ADJ"));
        assertTrue(!result.topHabit().getHeadlineZh().contains("其它词性"));
        String firstPoint = result.topHabit().getExamples().get(0).getErrorPoint();
        assertTrue(firstPoint.contains("improve") || firstPoint.contains("interrupt"));
        String practiceOriginal = result.topHabit().getPracticePrompt().getOriginalSentence();
        assertTrue(practiceOriginal.contains("improve") || practiceOriginal.contains("interrupt"));
    }

    private static int sequence = 0;

    private static HabitScoreInput.ErrorHit hit(
            String pointId, String errorLevel, String errorPoint, String suggestion, String originalSentence) {
        return new HabitScoreInput.ErrorHit(
                pointId, "s" + (sequence++), originalSentence, errorPoint, suggestion, errorLevel);
    }

    private static ChineseExpressionDto chinese(String focusPhrase, String suggestion) {
        return ChineseExpressionDto.builder()
                .originalIndex(sequence++)
                .originalSentence("我觉得" + focusPhrase + "很重要")
                .focusPhrase(focusPhrase)
                .suggestion(suggestion)
                .build();
    }
}
