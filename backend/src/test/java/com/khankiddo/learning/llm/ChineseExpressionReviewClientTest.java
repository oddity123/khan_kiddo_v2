package com.khankiddo.learning.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.khankiddo.learning.conversation.UtteranceRouter;
import com.khankiddo.learning.dto.conversation.ChineseExpressionDto;
import com.khankiddo.learning.prompt.PromptLoader;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChineseExpressionReviewClientTest {

    @Mock
    private LlmChatModelFactory chatModelFactory;

    @Mock
    private PromptLoader promptLoader;

    @Mock
    private ChatModel chatModel;

    private ChineseExpressionReviewClient client;

    @BeforeEach
    void setUp() {
        client = new ChineseExpressionReviewClient(chatModelFactory, promptLoader, new ObjectMapper());
    }

    @Test
    void review_emptyInput_returnsEmpty() {
        assertThat(client.review(List.of(), null)).isEmpty();
    }

    @Test
    void review_mapsSuggestionsByIndex() throws Exception {
        when(promptLoader.getSystemPromptChineseExpressionReview()).thenReturn("system");
        when(promptLoader.getChineseExpressionReviewTemplate()).thenReturn("sentences:\n{sentences}");
        when(promptLoader.fillTemplate(any(), any(), any())).thenReturn("user prompt");
        when(chatModelFactory.chatForChineseExpressionReview(any())).thenReturn(chatModel);

        String json = """
                {
                  "items": [
                    { "index": 1, "suggestion": "How do you say 'stair' in English?" },
                    { "index": 2, "suggestion": "I think this feature is not very useful." }
                  ]
                }
                """;
        when(chatModel.chat(any(ChatRequest.class))).thenReturn(
                ChatResponse.builder().aiMessage(AiMessage.from(json)).build());

        List<UtteranceRouter.RoutedChineseSentence> input = List.of(
                new UtteranceRouter.RoutedChineseSentence(0, "stair 怎么说"),
                new UtteranceRouter.RoutedChineseSentence(2, "我觉得不太好用"));

        List<ChineseExpressionDto> result = client.review(input, null);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getOriginalIndex()).isZero();
        assertThat(result.get(0).getOriginalSentence()).isEqualTo("stair 怎么说");
        assertThat(result.get(0).getSuggestion()).isEqualTo("How do you say 'stair' in English?");
        assertThat(result.get(1).getOriginalIndex()).isEqualTo(2);
        assertThat(result.get(1).getSuggestion()).isEqualTo("I think this feature is not very useful.");

        ArgumentCaptor<ChatRequest> captor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(chatModel).chat(captor.capture());
        assertThat(captor.getValue().messages()).hasSize(2);
    }

    @Test
    void review_onFailure_returnsOriginalWithoutSuggestions() {
        when(promptLoader.getSystemPromptChineseExpressionReview()).thenReturn("system");
        when(promptLoader.getChineseExpressionReviewTemplate()).thenReturn("{sentences}");
        when(promptLoader.fillTemplate(any(), any(), any())).thenReturn("user");
        when(chatModelFactory.chatForChineseExpressionReview(any())).thenReturn(chatModel);
        when(chatModel.chat(any(ChatRequest.class))).thenThrow(new RuntimeException("LLM down"));

        List<UtteranceRouter.RoutedChineseSentence> input = List.of(
                new UtteranceRouter.RoutedChineseSentence(1, "什么意思"));

        List<ChineseExpressionDto> result = client.review(input, null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getOriginalSentence()).isEqualTo("什么意思");
        assertThat(result.get(0).getSuggestion()).isEmpty();
    }
}
