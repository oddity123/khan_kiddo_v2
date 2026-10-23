package com.khankiddo.learning.conversation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.khankiddo.learning.ai.conversation.model.GrammarAnalysisResult;
import com.khankiddo.learning.config.ConversationAnalysisProperties;
import com.khankiddo.learning.config.PerformanceScoringProperties;
import com.khankiddo.learning.conversation.scoring.PerformanceScoringInput;
import com.khankiddo.learning.conversation.scoring.WeightedNaturalnessPerformanceScorer;
import com.khankiddo.learning.knowledge.HabitScoreSupport;
import com.khankiddo.learning.knowledge.PointDictionary;
import com.khankiddo.learning.model.enums.ExpressionStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 使用真实 JSON、清洗、总结与评分，覆盖语义过滤整条链路。 */
class ExpressionStatusFlowTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final PointDictionary dictionary = PointDictionary.loadFromClasspath("knowledge/point-dictionary-v1.json");

    @Test
    void optionalPolishNeverCountsEvenWithHeuristicSanitizerDisabled() throws Exception {
        var properties = new ConversationAnalysisProperties();
        properties.setSanitizerEnabled(false);
        var grammar = mapper.readValue("""
                {"items":[{"originalSentence":"I like this movie a lot.",
                "suggestion":"I really enjoy this film.","errors":[
                {"pointId":"LEXICAL_GAP","point":"like → enjoy（另一种说法）","status":"OPTIONAL"}]}]}
                """, GrammarAnalysisResult.class);
        // 即使调用方尚未清洗，打分入口也拒绝 OPTIONAL。
        assertThat(HabitScoreSupport.errorHitsFromGrammar(grammar, dictionary)).isEmpty();
        assertThat(PerformanceScoringInput.fromGrammar(grammar, 1, dictionary)
                .sentencesWithErrors()).isEmpty();
        new GrammarAnalysisSanitizer(properties, dictionary).sanitize(grammar);
        assertThat(grammar.getItems()).isEmpty();
        var parser = new EducationalSummaryParser(mapper,
                new WeightedNaturalnessPerformanceScorer(new PerformanceScoringProperties(), dictionary), dictionary);
        var stats = parser.defaultReport(grammar, 1, 1, 0).getReport().getOverallStats();
        assertThat(stats.getTotalIssues()).isZero();
        assertThat(stats.getPerformanceScore()).isEqualTo(new PerformanceScoringProperties().getMaxScore());
    }

    @Test
    void mixedSentenceKeepsOnlyRealIssueAndHistoricalMissingStatus() throws Exception {
        var grammar = mapper.readValue("""
                {"items":[{"originalSentence":"Yesterday I go to the store.",
                "suggestion":"Yesterday I went to the store.","errors":[
                {"pointId":"PAST_SIMPLE_DONE","point":"go → went（过去时）"},
                {"pointId":"LEXICAL_GAP","point":"store → shop（另一种说法）","status":"OPTIONAL"}]}]}
                """, GrammarAnalysisResult.class);
        new GrammarAnalysisSanitizer(new ConversationAnalysisProperties(), dictionary).sanitize(grammar);
        assertThat(grammar.getItems()).hasSize(1);
        assertThat(grammar.getItems().getFirst().getErrors()).singleElement().satisfies(error -> {
            assertThat(error.getStatus()).isEqualTo(ExpressionStatus.ERROR);
            assertThat(error.getPointId()).isEqualTo("PAST_SIMPLE_DONE");
        });
        assertThat(HabitScoreSupport.errorHitsFromGrammar(grammar, dictionary)).hasSize(1);
    }

    @Test
    void riskAndUnnaturalRemainDistinctThroughJsonAndSanitization() throws Exception {
        var grammar = mapper.readValue("""
                {"items":[
                {"originalSentence":"I want to make propaganda for my website.",
                "suggestion":"I want to promote my website.","errors":[
                {"pointId":"LEXICAL_GAP","point":"make propaganda for → promote（推广语境有歧义）","status":"RISK"}]},
                {"originalSentence":"My English has room for development.",
                "suggestion":"My English has room for improvement.","errors":[
                {"pointId":"COLLOCATION","point":"room for development → room for improvement（常见搭配）","status":"UNNATURAL"}]}]}
                """, GrammarAnalysisResult.class);
        new GrammarAnalysisSanitizer(new ConversationAnalysisProperties(), dictionary).sanitize(grammar);
        assertThat(grammar.getItems()).hasSize(2);
        assertThat(grammar.getItems().get(0).getErrors().getFirst().getStatus()).isEqualTo(ExpressionStatus.RISK);
        assertThat(grammar.getItems().get(1).getErrors().getFirst().getStatus()).isEqualTo(ExpressionStatus.UNNATURAL);
        assertThat(HabitScoreSupport.errorHitsFromGrammar(grammar, dictionary)).hasSize(2);
    }

    @Test
    void validSpokenEnglishWithNoIssuesStaysEmpty() throws Exception {
        var grammar = mapper.readValue("{\"items\":[]}", GrammarAnalysisResult.class);
        new GrammarAnalysisSanitizer(new ConversationAnalysisProperties(), dictionary).sanitize(grammar);
        assertThat(grammar.getItems()).isEmpty();
    }
}
