package com.khankiddo.learning.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.khankiddo.learning.conversation.UtteranceRouter;
import com.khankiddo.learning.dto.conversation.ChineseExpressionDto;
import com.khankiddo.learning.dto.conversation.ExpressionPhraseDto;
import com.khankiddo.learning.prompt.PromptLoader;
import com.khankiddo.learning.util.SchemaLoader;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PhraseCardReviewClientTest {

    @Mock
    private LlmChatModelFactory chatModelFactory;

    @Mock
    private PromptLoader promptLoader;

    @Mock
    private ChatModel chatModel;

    private PhraseCardReviewClient client;

    @BeforeEach
    void setUp() {
        client = new PhraseCardReviewClient(
                chatModelFactory, promptLoader, new ObjectMapper(),
                new PhraseCardReviewOutputPolicy(new SchemaLoader()));
    }

    @Test
    void review_emptyInput_returnsEmpty_withoutLlm() {
        PhraseReviewOutcome outcome = client.review(List.of(), List.of(), null);

        assertThat(outcome.chineseExpressions()).isEmpty();
        assertThat(outcome.expressionPhrases()).isEmpty();
        verify(chatModelFactory, never()).chatForPhraseCardReview(any());
    }

    @Test
    void review_mergesChineseAndExpressionByIndex_withReason() throws Exception {
        stubPrompt();
        when(chatModelFactory.chatForPhraseCardReview(any())).thenReturn(chatModel);

        String json = """
                {
                  "items": [
                    {
                      "kind": "chinese",
                      "index": 1,
                      "front": "楼梯",
                      "back": "stair / staircase",
                      "reason": "词汇缺口：想说楼梯"
                    },
                    {
                      "kind": "expression",
                      "index": 2,
                      "front": "exciting",
                      "back": "excited",
                      "reason": "感到…用 -ed"
                    }
                  ]
                }
                """;
        when(chatModel.chat(any(ChatRequest.class))).thenReturn(
                ChatResponse.builder().aiMessage(AiMessage.from(json)).build());

        List<UtteranceRouter.RoutedChineseSentence> chinese = List.of(
                new UtteranceRouter.RoutedChineseSentence(0, "楼梯怎么说"));
        List<ExpressionReviewCandidate> expressions = List.of(
                new ExpressionReviewCandidate(
                        11L,
                        "I'm so exciting about the trip.",
                        "I'm so excited about the trip.",
                        "exciting → excited（感到…用 -ed）",
                        "FEEL_ED_ADJ"));

        PhraseReviewOutcome outcome = client.review(chinese, expressions, null);

        assertThat(outcome.chineseExpressions()).hasSize(1);
        ChineseExpressionDto zh = outcome.chineseExpressions().get(0);
        assertThat(zh.getFront()).isEqualTo("楼梯");
        assertThat(zh.getBack()).isEqualTo("stair / staircase");
        assertThat(zh.getReason()).isEqualTo("词汇缺口：想说楼梯");

        assertThat(outcome.expressionPhrases()).hasSize(1);
        ExpressionPhraseDto expr = outcome.expressionPhrases().get(0);
        assertThat(expr.getSentenceId()).isEqualTo(11L);
        assertThat(expr.getPointId()).isEqualTo("FEEL_ED_ADJ");
        assertThat(expr.getFront()).isEqualTo("exciting");
        assertThat(expr.getBack()).isEqualTo("excited");
        assertThat(expr.getReason()).isEqualTo("感到…用 -ed");

        ArgumentCaptor<ChatRequest> captor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(chatModel).chat(captor.capture());
        String userText = ((dev.langchain4j.data.message.UserMessage) captor.getValue().messages().get(1))
                .singleText();
        assertThat(userText).contains("kind=chinese");
        assertThat(userText).contains("kind=expression");
        assertThat(userText).contains("FEEL_ED_ADJ");
    }

    @Test
    void review_expressionOnly_stillCallsLlm() throws Exception {
        stubPrompt();
        when(chatModelFactory.chatForPhraseCardReview(any())).thenReturn(chatModel);
        String json = """
                {
                  "items": [
                    {
                      "kind": "expression",
                      "index": 1,
                      "front": "do a role-play",
                      "back": "do role-plays",
                      "reason": "固定搭配用复数"
                    }
                  ]
                }
                """;
        when(chatModel.chat(any(ChatRequest.class))).thenReturn(
                ChatResponse.builder().aiMessage(AiMessage.from(json)).build());

        PhraseReviewOutcome outcome = client.review(
                List.of(),
                List.of(new ExpressionReviewCandidate(
                        2L,
                        "I do a role-play every week.",
                        "I do role-plays every week.",
                        "do a role-play → do role-plays（固定搭配）",
                        "COLLOCATION")),
                null);

        assertThat(outcome.chineseExpressions()).isEmpty();
        assertThat(outcome.expressionPhrases()).hasSize(1);
        assertThat(outcome.expressionPhrases().get(0).getFront()).isEqualTo("do a role-play");
        verify(chatModel).chat(any(ChatRequest.class));
    }

    @Test
    void review_dedupesChineseByFront_keepsFirst_andDropsEmptyFocus() throws Exception {
        stubPrompt();
        when(chatModelFactory.chatForPhraseCardReview(any())).thenReturn(chatModel);

        String json = """
                {
                  "items": [
                    { "kind": "chinese", "index": 1, "front": "直接主管", "back": "direct supervisor", "reason": "职位词" },
                    { "kind": "chinese", "index": 2, "front": "直 接 主 管", "back": "immediate manager", "reason": "重复" },
                    { "kind": "chinese", "index": 3, "front": "", "back": "ignored", "reason": "空" },
                    { "kind": "chinese", "index": 4, "front": "纸巾", "back": "tissue", "reason": "日常词" }
                  ]
                }
                """;
        when(chatModel.chat(any(ChatRequest.class))).thenReturn(
                ChatResponse.builder().aiMessage(AiMessage.from(json)).build());

        List<UtteranceRouter.RoutedChineseSentence> input = List.of(
                new UtteranceRouter.RoutedChineseSentence(0, "直接主管怎么说"),
                new UtteranceRouter.RoutedChineseSentence(1, "how to say 直接主管"),
                new UtteranceRouter.RoutedChineseSentence(2, "我顶着烈日来到了公司"),
                new UtteranceRouter.RoutedChineseSentence(3, "纸巾怎么说"));

        PhraseReviewOutcome outcome = client.review(input, List.of(), null);

        assertThat(outcome.chineseExpressions()).hasSize(2);
        assertThat(outcome.chineseExpressions().get(0).getFront()).isEqualTo("直接主管");
        assertThat(outcome.chineseExpressions().get(1).getFront()).isEqualTo("纸巾");
    }

    @Test
    void review_onFailure_chineseEmptySuggestions_expressionEmptyForHeuristic() {
        stubPrompt();
        when(chatModelFactory.chatForPhraseCardReview(any())).thenReturn(chatModel);
        when(chatModel.chat(any(ChatRequest.class))).thenThrow(new RuntimeException("LLM down"));

        PhraseReviewOutcome outcome = client.review(
                List.of(new UtteranceRouter.RoutedChineseSentence(1, "什么意思")),
                List.of(new ExpressionReviewCandidate(
                        9L, "I'm exciting.", "I'm excited.", "exciting → excited", "FEEL_ED_ADJ")),
                null);

        assertThat(outcome.chineseExpressions()).hasSize(1);
        assertThat(outcome.chineseExpressions().get(0).getOriginalSentence()).isEqualTo("什么意思");
        assertThat(outcome.chineseExpressions().get(0).getFront()).isEmpty();
        assertThat(outcome.chineseExpressions().get(0).getBack()).isEmpty();
        assertThat(outcome.expressionPhrases()).isEmpty();
    }

    @Test
    void review_skipsExpressionItemMissingFrontOrBack() throws Exception {
        stubPrompt();
        when(chatModelFactory.chatForPhraseCardReview(any())).thenReturn(chatModel);
        String json = """
                {
                  "items": [
                    {
                      "kind": "expression",
                      "index": 1,
                      "front": "",
                      "back": "excited",
                      "reason": "缺 front"
                    }
                  ]
                }
                """;
        when(chatModel.chat(any(ChatRequest.class))).thenReturn(
                ChatResponse.builder().aiMessage(AiMessage.from(json)).build());

        PhraseReviewOutcome outcome = client.review(
                List.of(),
                List.of(new ExpressionReviewCandidate(
                        1L, "I'm exciting.", "I'm excited.", "exciting → excited", "FEEL_ED_ADJ")),
                null);

        assertThat(outcome.expressionPhrases()).isEmpty();
    }

    private void stubPrompt() {
        when(promptLoader.getSystemPromptPhraseCardReview()).thenReturn("system");
        when(promptLoader.getPhraseCardReviewTemplate()).thenReturn("items:\n{items}");
        when(promptLoader.fillTemplate(any(), any(), any())).thenAnswer(inv -> {
            String template = inv.getArgument(0);
            String placeholder = inv.getArgument(1);
            String value = inv.getArgument(2);
            return template.replace("{" + placeholder + "}", value);
        });
    }
}
