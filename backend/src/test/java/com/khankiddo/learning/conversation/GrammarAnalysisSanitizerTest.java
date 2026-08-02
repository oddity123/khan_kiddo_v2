package com.khankiddo.learning.conversation;

import com.khankiddo.learning.ai.conversation.model.GrammarAnalysisResult;
import com.khankiddo.learning.ai.conversation.model.GrammarErrorDto;
import com.khankiddo.learning.ai.conversation.model.GrammarSentenceItemDto;
import com.khankiddo.learning.config.ConversationAnalysisProperties;
import com.khankiddo.learning.knowledge.PointDictionary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GrammarAnalysisSanitizerTest {

    private static final String DICTIONARY_CLASSPATH = "knowledge/point-dictionary-v1.json";

    private ConversationAnalysisProperties properties;
    private GrammarAnalysisSanitizer sanitizer;

    @BeforeEach
    void setUp() {
        properties = new ConversationAnalysisProperties();
        sanitizer = new GrammarAnalysisSanitizer(properties, PointDictionary.loadFromClasspath(DICTIONARY_CLASSPATH));
    }

    private static GrammarErrorDto error(String pointId, String point) {
        return GrammarErrorDto.builder().pointId(pointId).point(point).build();
    }

    private static GrammarSentenceItemDto item(String original, String suggestion, GrammarErrorDto... errors) {
        List<GrammarErrorDto> list = new ArrayList<>(List.of(errors));
        return GrammarSentenceItemDto.builder()
                .originalSentence(original)
                .suggestion(suggestion)
                .errors(list)
                .build();
    }

    private static GrammarAnalysisResult result(GrammarSentenceItemDto... items) {
        return GrammarAnalysisResult.builder().items(new ArrayList<>(List.of(items))).build();
    }

    @Test
    void keepsError_whenWrongFragmentAppearsInOriginal() {
        GrammarAnalysisResult grammar = result(item(
                "And today I want to recap some phrase and vocabulary I learned.",
                "And today I want to recap some phrases and vocabulary I learned.",
                error("PLURAL_COUNTABLE", "phrase → phrases（some 后接可数名词复数）")));

        GrammarAnalysisResult cleaned = sanitizer.sanitize(grammar);

        assertThat(cleaned.getItems()).hasSize(1);
        assertThat(cleaned.getItems().get(0).getErrors()).hasSize(1);
    }

    @Test
    void dropsError_whenWrongFragmentNotInOriginal() {
        GrammarAnalysisResult grammar = result(item(
                "I like this book very much.",
                "I really like this book.",
                error("LEXICAL_GAP", "totally different phrase → something else（凭空捏造）")));

        GrammarAnalysisResult cleaned = sanitizer.sanitize(grammar);

        // 该句唯一错误被剔除后，整条 item 也应移除
        assertThat(cleaned.getItems()).isEmpty();
    }

    @Test
    void dropsSelfCorrection_butKeepsRealError() {
        GrammarAnalysisResult grammar = result(item(
                "It have It has a example.",
                "Here's an example.",
                error("SVA_THIRD_PERSON", "It have → It has（主谓一致）"),
                error("ARTICLE_A_AN", "a example → an example（元音音素开头用 an）")));

        GrammarAnalysisResult cleaned = sanitizer.sanitize(grammar);

        assertThat(cleaned.getItems()).hasSize(1);
        List<GrammarErrorDto> errors = cleaned.getItems().get(0).getErrors();
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).getPointId()).isEqualTo("ARTICLE_A_AN");
    }

    @Test
    void dropsChineseType_evenWhenFragmentInOriginal() {
        GrammarAnalysisResult grammar = result(item(
                "I sit down on a 楼梯.",
                "I sat down on the stairs.",
                error("CHINESE_CODE_SWITCH", "楼梯 → stairs（用中文表达，需替换为英文）")));

        GrammarAnalysisResult cleaned = sanitizer.sanitize(grammar);

        assertThat(cleaned.getItems()).isEmpty();
    }

    @Test
    void keepsError_whenPointHasNoArrow() {
        GrammarAnalysisResult grammar = result(item(
                "Some raw sentence.",
                "Some suggestion.",
                error("STRUCTURE_OTHER", "无法解析的说明文本")));

        GrammarAnalysisResult cleaned = sanitizer.sanitize(grammar);

        assertThat(cleaned.getItems()).hasSize(1);
    }

    @Test
    void replacesUnknownPointIdWithFallback() {
        GrammarAnalysisResult grammar = result(item(
                "Some raw sentence.",
                "Some suggestion.",
                error("NOT_REAL", "无法解析的说明文本")));

        GrammarAnalysisResult cleaned = sanitizer.sanitize(grammar);

        assertThat(cleaned.getItems()).hasSize(1);
        List<GrammarErrorDto> errors = cleaned.getItems().get(0).getErrors();
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).getPointId()).isEqualTo(PointDictionary.FALLBACK_POINT_ID);
    }

    @Test
    void replacesBlankOrNullPointIdWithFallback() {
        GrammarAnalysisResult grammar = result(item(
                "Some raw sentence.",
                "Some suggestion.",
                error("", "无法解析的说明文本"),
                error(null, "无法解析的说明文本")));

        GrammarAnalysisResult cleaned = sanitizer.sanitize(grammar);

        assertThat(cleaned.getItems()).hasSize(1);
        List<GrammarErrorDto> errors = cleaned.getItems().get(0).getErrors();
        assertThat(errors).hasSize(2);
        assertThat(errors).allSatisfy(e -> assertThat(e.getPointId()).isEqualTo(PointDictionary.FALLBACK_POINT_ID));
    }

    @Test
    void keepsValidPointIdUnchanged() {
        GrammarAnalysisResult grammar = result(item(
                "And today I want to recap some phrase and vocabulary I learned.",
                "And today I want to recap some phrases and vocabulary I learned.",
                error("PLURAL_COUNTABLE", "phrase → phrases（some 后接可数名词复数）")));

        GrammarAnalysisResult cleaned = sanitizer.sanitize(grammar);

        assertThat(cleaned.getItems()).hasSize(1);
        assertThat(cleaned.getItems().get(0).getErrors().get(0).getPointId()).isEqualTo("PLURAL_COUNTABLE");
    }

    @Test
    void disabledSanitizer_keepsEverything() {
        properties.setSanitizerEnabled(false);
        GrammarAnalysisResult grammar = result(item(
                "I like this book.",
                "I really like this book.",
                error("LEXICAL_GAP", "not present at all → x（凭空捏造）")));

        GrammarAnalysisResult cleaned = sanitizer.sanitize(grammar);

        assertThat(cleaned.getItems()).hasSize(1);
        assertThat(cleaned.getItems().get(0).getErrors()).hasSize(1);
    }
}
