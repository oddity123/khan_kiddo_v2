package com.khankiddo.learning.conversation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class UtteranceRouterTest {

    private UtteranceRouter router;

    @BeforeEach
    void setUp() {
        router = new UtteranceRouter();
    }

    @Test
    void containsCjk_detectsHanCharacters() {
        assertThat(router.containsCjk("stair 这个词怎么说")).isTrue();
        assertThat(router.containsCjk("I sit on a 楼梯.")).isTrue();
        assertThat(router.containsCjk("Hello world")).isFalse();
        assertThat(router.containsCjk("")).isFalse();
        assertThat(router.containsCjk(null)).isFalse();
    }

    @Test
    void route_splitsEnglishAndChineseSentences() {
        UtteranceRouter.RoutedUtterances routed = router.route(List.of(
                "I think the model has issues.",
                "这个词怎么说",
                "Maybe we can try again.",
                "我觉得不太对"));

        assertThat(routed.englishSentences()).containsExactly(
                "I think the model has issues.",
                "Maybe we can try again.");
        assertThat(routed.chineseSentences()).hasSize(2);
        assertThat(routed.chineseSentences().get(0).originalIndex()).isEqualTo(1);
        assertThat(routed.chineseSentences().get(0).sentence()).isEqualTo("这个词怎么说");
        assertThat(routed.chineseSentences().get(1).originalIndex()).isEqualTo(3);
        assertThat(routed.chineseCount()).isEqualTo(2);
        assertThat(routed.englishCount()).isEqualTo(2);
    }

    @Test
    void route_emptyInput_returnsEmptyLists() {
        UtteranceRouter.RoutedUtterances routed = router.route(List.of());
        assertThat(routed.englishSentences()).isEmpty();
        assertThat(routed.chineseSentences()).isEmpty();
    }

    @Test
    void route_allEnglish_goesToGrammarChannel() {
        UtteranceRouter.RoutedUtterances routed = router.route(List.of(
                "First sentence.",
                "Second sentence."));
        assertThat(routed.chineseSentences()).isEmpty();
        assertThat(routed.englishSentences()).hasSize(2);
    }

    @Test
    void route_allChinese_skipsGrammarChannel() {
        UtteranceRouter.RoutedUtterances routed = router.route(List.of(
                "你好",
                "什么意思"));
        assertThat(routed.englishSentences()).isEmpty();
        assertThat(routed.chineseSentences()).hasSize(2);
    }

    @Test
    void route_englishWithFewChinese_goesToChineseChannel() {
        UtteranceRouter.RoutedUtterances routed = router.route(List.of(
                "I want to discuss 这个问题 with my manager.",
                "I sit on a 楼梯."));

        assertThat(routed.englishSentences()).isEmpty();
        assertThat(routed.chineseSentences()).hasSize(2);
        assertThat(routed.chineseSentences().get(0).sentence())
                .isEqualTo("I want to discuss 这个问题 with my manager.");
        assertThat(routed.chineseSentences().get(1).sentence()).isEqualTo("I sit on a 楼梯.");
    }

    @Test
    void route_pureEnglish_staysInGrammarChannel() {
        UtteranceRouter.RoutedUtterances routed = router.route(List.of(
                "I want to discuss this with my manager.",
                "I sit on a staircase."));

        assertThat(routed.chineseSentences()).isEmpty();
        assertThat(routed.englishSentences()).containsExactly(
                "I want to discuss this with my manager.",
                "I sit on a staircase.");
    }

    @Test
    void route_vocabHelp_evenWithEnglishWrapper_goesToChineseChannel() {
        UtteranceRouter.RoutedUtterances routed = router.route(List.of(
                "stair 这个词怎么说",
                "how to say 直接主管",
                "what's 纸巾 in English"));

        assertThat(routed.englishSentences()).isEmpty();
        assertThat(routed.chineseSentences()).hasSize(3);
    }

    @Test
    void route_chineseContentGap_goesToChineseChannel() {
        UtteranceRouter.RoutedUtterances routed = router.route(List.of(
                "我顶着烈日来到了公司",
                "这个 API 有问题"));

        assertThat(routed.englishSentences()).isEmpty();
        assertThat(routed.chineseSentences()).hasSize(2);
    }
}
