package com.khankiddo.learning.rag.grammar;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GrammarErrorQueryIntentAnalyzerTest {

    private GrammarErrorQueryIntentAnalyzer analyzer;

    @BeforeEach
    void setUp() {
        analyzer = new GrammarErrorQueryIntentAnalyzer();
    }

    @Test
    void shouldDetectChinglishIntentForChineseExpressionQuestion() {
        GrammarErrorRetrievalIntent intent = analyzer.analyze("对于中文的表达, 我一般会犯什么错误呢?");

        assertEquals(GrammarErrorQueryKind.CHINGLISH, intent.kind());
        assertTrue(intent.primaryTypes().contains("Chinglish"));
        assertTrue(intent.secondaryTypes().contains("Unnatural"));
        assertTrue(intent.includeStats());
    }

    @Test
    void shouldDetectChinglishIntentWithAlternateSummaryPhrasing() {
        GrammarErrorRetrievalIntent intent = analyzer.analyze("中文表达中我一般犯的错误是哪些呢");

        assertEquals(GrammarErrorQueryKind.CHINGLISH, intent.kind());
        assertTrue(intent.primaryTypes().contains("Chinglish"));
        assertTrue(intent.includeStats());
    }

    @Test
    void shouldDetectArticleIntent() {
        GrammarErrorRetrievalIntent intent = analyzer.analyze("我的冠词错误有哪些？");

        assertEquals(GrammarErrorQueryKind.ARTICLE, intent.kind());
        assertTrue(intent.primaryTypes().contains("Article"));
        assertFalse(intent.includeStats());
    }

    @Test
    void shouldDetectTenseIntent() {
        GrammarErrorRetrievalIntent intent = analyzer.analyze("我最近时态错误多吗？");

        assertEquals(GrammarErrorQueryKind.TENSE, intent.kind());
        assertTrue(intent.primaryTypes().contains("Tense"));
    }

    @Test
    void shouldDetectGeneralSummaryIntent() {
        GrammarErrorRetrievalIntent intent = analyzer.analyze("我最常犯哪些语法错误？");

        assertEquals(GrammarErrorQueryKind.GENERAL_SUMMARY, intent.kind());
        assertTrue(intent.includeStats());
    }

    @Test
    void shouldFallbackToSemanticIntent() {
        GrammarErrorRetrievalIntent intent = analyzer.analyze("帮我看看这句话哪里不对");

        assertEquals(GrammarErrorQueryKind.SEMANTIC, intent.kind());
        assertTrue(intent.primaryTypes().isEmpty());
    }
}
