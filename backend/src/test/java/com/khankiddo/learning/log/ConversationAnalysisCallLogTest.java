package com.khankiddo.learning.log;

import com.khankiddo.learning.exception.BadRequestException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class ConversationAnalysisCallLogTest {

    @AfterEach
    void tearDown() {
        ConversationAnalysisCallLog.clear();
    }

    @Test
    void format_omitsBatchAndModeWhenNotBatched() {
        String line = ConversationAnalysisCallLog.format(
                "a-1", "grammar", "doubao-seed", 0, 0, null, 1, 42, "ok");

        assertThat(line).isEqualTo(
                "llm_call analysisId=a-1 stage=grammar model=doubao-seed attempt=1 durationMs=42 result=ok");
        assertThat(line).doesNotContain("batch=").doesNotContain("mode=");
    }

    @Test
    void format_includesBatchAndModeOnlyWhenUseful() {
        String line = ConversationAnalysisCallLog.format(
                "a-1",
                "grammar",
                "qwen3.6-plus",
                2,
                5,
                "chat",
                2,
                1800,
                "PrematureCloseException");

        assertThat(line).isEqualTo(
                "llm_call analysisId=a-1 stage=grammar model=qwen3.6-plus batch=2/5 mode=chat attempt=2 durationMs=1800 result=PrematureCloseException");
    }

    @Test
    void format_usesDashWhenAnalysisIdMissing() {
        String line = ConversationAnalysisCallLog.format(
                null, "separation", "doubao-seed-1-6-flash-250828", 0, 0, null, 1, 10, "ok");

        assertThat(line).startsWith("llm_call analysisId=- ");
    }

    @Test
    void resultOf_okWhenNoError() {
        assertThat(ConversationAnalysisCallLog.resultOf(null)).isEqualTo("ok");
    }

    @Test
    void resultOf_usesRootCauseSimpleNameNotSql() {
        Throwable nested = new PrematureCloseException("Connection prematurely closed");
        BadRequestException wrapped = new BadRequestException("AI 分析失败，请稍后重试", nested);

        assertThat(ConversationAnalysisCallLog.resultOf(wrapped)).isEqualTo("PrematureCloseException");
        assertThat(ConversationAnalysisCallLog.resultOf(new BadRequestException("AI 分析结果格式无效，请重试")))
                .isEqualTo("BadRequestException");
    }

    @Test
    void runWithCopiedContext_restoresParentMdcOnChildWork() {
        ConversationAnalysisCallLog.putAnalysisId("parent-id");
        Map<String, String> copied = ConversationAnalysisCallLog.copyContext();
        ConversationAnalysisCallLog.clear();
        assertThat(MDC.get(ConversationAnalysisCallLog.MDC_ANALYSIS_ID)).isNull();

        AtomicReference<String> seenOnChild = new AtomicReference<>();
        ConversationAnalysisCallLog.runWithCopiedContext(
                copied, () -> seenOnChild.set(MDC.get(ConversationAnalysisCallLog.MDC_ANALYSIS_ID)));

        assertThat(seenOnChild.get()).isEqualTo("parent-id");
        assertThat(MDC.get(ConversationAnalysisCallLog.MDC_ANALYSIS_ID)).isNull();
    }

    private static final class PrematureCloseException extends RuntimeException {
        private PrematureCloseException(String message) {
            super(message);
        }
    }
}
