package com.khankiddo.learning.conversation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.khankiddo.learning.ai.conversation.model.GrammarAnalysisResult;
import com.khankiddo.learning.dto.conversation.ConversationAnalysisProgress;
import com.khankiddo.learning.exception.BadRequestException;
import com.khankiddo.learning.llm.LlmChatModelFactory;
import com.khankiddo.learning.llm.ResolvedLlmModel;
import com.khankiddo.learning.log.ConversationAnalysisCallLog;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConversationGrammarAnalysisHelperTest {

    @Mock
    private LlmChatModelFactory chatModelFactory;

    @Mock
    private ChatModel chatModel;

    private ConversationGrammarAnalysisHelper helper;

    @BeforeEach
    void setUp() {
        helper = new ConversationGrammarAnalysisHelper(chatModelFactory, new ObjectMapper());
        when(chatModelFactory.chatForGrammarAnalysis(any())).thenReturn(chatModel);
    }

    @Test
    void analyzeGrammar_parsesChatResponse() {
        when(chatModel.chat(any(ChatRequest.class))).thenReturn(
                ChatResponse.builder().aiMessage(AiMessage.from("{\"items\":[]}")).build());

        List<ConversationAnalysisProgress> progress = new ArrayList<>();
        GrammarAnalysisResult result = helper.analyzeGrammar("sys", "user", model(), progress::add);

        assertThat(result.getItems()).isEmpty();
        assertThat(progress).extracting(ConversationAnalysisProgress::getMessage)
                .containsExactly("正在分析用户英文表达...");
        verify(chatModel, times(1)).chat(any(ChatRequest.class));
    }

    @Test
    void analyzeGrammar_retriesOnceOnInvalidJson() {
        AtomicInteger calls = new AtomicInteger();
        when(chatModel.chat(any(ChatRequest.class))).thenAnswer(invocation -> {
            if (calls.getAndIncrement() == 0) {
                return ChatResponse.builder().aiMessage(AiMessage.from("not-json")).build();
            }
            return ChatResponse.builder().aiMessage(AiMessage.from("{\"items\":[]}")).build();
        });

        GrammarAnalysisResult result = helper.analyzeGrammar("sys", "user", model(), progress -> {});

        assertThat(result.getItems()).isEmpty();
        verify(chatModel, times(2)).chat(any(ChatRequest.class));
    }

    @Test
    void analyzeGrammar_throwsAfterParseRetriesExhausted() {
        when(chatModel.chat(any(ChatRequest.class))).thenReturn(
                ChatResponse.builder().aiMessage(AiMessage.from("not-json")).build());

        assertThatThrownBy(() -> helper.analyzeGrammar("sys", "user", model(), progress -> {}))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("格式无效");
        verify(chatModel, times(2)).chat(any(ChatRequest.class));
    }

    @Test
    void analyzeGrammar_doesNotRetryWhenFinishReasonLength() {
        when(chatModel.chat(any(ChatRequest.class))).thenReturn(
                ChatResponse.builder()
                        .aiMessage(AiMessage.from("{\"items\":["))
                        .finishReason(FinishReason.LENGTH)
                        .build());

        assertThatThrownBy(() -> helper.analyzeGrammar("sys", "user", model(), progress -> {}))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("截断");
        verify(chatModel, times(1)).chat(any(ChatRequest.class));
    }

    @Test
    void analyzeGrammar_logsChatMode() {
        when(chatModel.chat(any(ChatRequest.class))).thenReturn(
                ChatResponse.builder().aiMessage(AiMessage.from("{\"items\":[]}")).build());

        Logger logger = (Logger) LoggerFactory.getLogger(ConversationAnalysisCallLog.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        ConversationAnalysisCallLog.putAnalysisId("a-chat-1");
        try {
            helper.analyzeGrammar("sys", "user", model(), progress -> {});
        } finally {
            logger.detachAppender(appender);
            ConversationAnalysisCallLog.clear();
        }

        List<String> lines = appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
        assertThat(lines).anyMatch(line ->
                line.contains("llm_call")
                        && line.contains("analysisId=a-chat-1")
                        && line.contains("stage=grammar")
                        && line.contains("model=doubao-seed")
                        && line.contains("mode=chat")
                        && line.contains("result=ok"));
    }

    private static ResolvedLlmModel model() {
        return ResolvedLlmModel.builder().id("doubao-seed").build();
    }
}
