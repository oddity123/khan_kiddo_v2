package com.khankiddo.learning.conversation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.khankiddo.learning.ai.conversation.model.GrammarAnalysisResult;
import com.khankiddo.learning.config.ConversationAnalysisProperties;
import com.khankiddo.learning.exception.BadRequestException;
import com.khankiddo.learning.llm.LlmChatModelFactory;
import com.khankiddo.learning.llm.ResolvedLlmModel;
import com.khankiddo.learning.log.ConversationAnalysisCallLog;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConversationAnalysisStreamingHelperIoRetryTest {

    @Mock
    private LlmChatModelFactory chatModelFactory;

    @Mock
    private StreamingChatModel streamingChatModel;

    @Mock
    private ChatModel chatModel;

    private ConversationAnalysisStreamingHelper helper;

    @BeforeEach
    void setUp() {
        ConversationAnalysisProperties properties = new ConversationAnalysisProperties();
        properties.setStreamWallClockTimeout(Duration.ofSeconds(2));
        helper = new ConversationAnalysisStreamingHelper(chatModelFactory, new ObjectMapper(), properties);
        when(chatModelFactory.streamingForGrammarAnalysis(any())).thenReturn(streamingChatModel);
    }

    @Test
    void fallsBackToChatWhenStreamClosesPrematurely() {
        when(chatModelFactory.chatForGrammarAnalysis(any())).thenReturn(chatModel);
        doAnswer(invocation -> {
            StreamingChatResponseHandler handler = invocation.getArgument(1);
            handler.onError(new RuntimeException("Connection prematurely closed DURING response"));
            return null;
        }).when(streamingChatModel).chat(any(ChatRequest.class), any(StreamingChatResponseHandler.class));
        when(chatModel.chat(any(ChatRequest.class))).thenReturn(
                ChatResponse.builder().aiMessage(AiMessage.from("{\"items\":[]}")).build());

        GrammarAnalysisResult result = helper.streamGrammarAnalysis("sys", "user", model(), progress -> {});

        assertThat(result.getItems()).isEmpty();
        verify(chatModel).chat(any(ChatRequest.class));
    }

    @Test
    void doesNotFallBackToChatOnAuthFailure() {
        doAnswer(invocation -> {
            StreamingChatResponseHandler handler = invocation.getArgument(1);
            handler.onError(new RuntimeException("401 Unauthorized invalid_api_key"));
            return null;
        }).when(streamingChatModel).chat(any(ChatRequest.class), any(StreamingChatResponseHandler.class));

        assertThatThrownBy(() -> helper.streamGrammarAnalysis("sys", "user", model(), progress -> {}))
                .isInstanceOf(BadRequestException.class);

        verify(chatModelFactory, never()).chatForGrammarAnalysis(any());
    }

    @Test
    void logsLlmCallForStreamFailureAndChatFallback() {
        when(chatModelFactory.chatForGrammarAnalysis(any())).thenReturn(chatModel);
        doAnswer(invocation -> {
            StreamingChatResponseHandler handler = invocation.getArgument(1);
            handler.onError(new RuntimeException("Connection prematurely closed DURING response"));
            return null;
        }).when(streamingChatModel).chat(any(ChatRequest.class), any(StreamingChatResponseHandler.class));
        when(chatModel.chat(any(ChatRequest.class))).thenReturn(
                ChatResponse.builder().aiMessage(AiMessage.from("{\"items\":[]}")).build());

        Logger logger = (Logger) LoggerFactory.getLogger(ConversationAnalysisCallLog.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        ConversationAnalysisCallLog.putAnalysisId("a-stream-1");
        try {
            helper.streamGrammarAnalysis("sys", "user", model(), progress -> {});
        } finally {
            logger.detachAppender(appender);
            ConversationAnalysisCallLog.clear();
        }

        List<String> lines = appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
        assertThat(lines).anyMatch(line ->
                line.contains("llm_call")
                        && line.contains("analysisId=a-stream-1")
                        && line.contains("stage=grammar")
                        && line.contains("model=doubao-seed")
                        && line.contains("mode=stream")
                        && line.contains("attempt=1")
                        && line.contains("result=RuntimeException"));
        assertThat(lines).anyMatch(line ->
                line.contains("llm_call")
                        && line.contains("analysisId=a-stream-1")
                        && line.contains("mode=chat")
                        && line.contains("attempt=2")
                        && line.contains("result=ok"));
        assertThat(lines).noneMatch(line -> line.contains("batch="));
    }

    @AfterEach
    void tearDown() {
        ConversationAnalysisCallLog.clear();
    }

    private static ResolvedLlmModel model() {
        return ResolvedLlmModel.builder().id("doubao-seed").build();
    }
}
