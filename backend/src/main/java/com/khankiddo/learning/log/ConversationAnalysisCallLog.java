package com.khankiddo.learning.log;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.util.StringUtils;

import java.util.Map;

/**
 * 对话分析请求级关联日志。一条 {@code llm_call} 对应一次 LLM 调用或落库；前端不展示这些字段。
 */
@Slf4j
public final class ConversationAnalysisCallLog {

    public static final String MDC_ANALYSIS_ID = "analysisId";

    public static final String STAGE_SEPARATION = "separation";
    public static final String STAGE_GRAMMAR = "grammar";
    public static final String STAGE_CHINESE_REVIEW = "chinese-review";
    public static final String STAGE_SUMMARY = "summary";
    public static final String STAGE_PERSIST = "persist";

    public static final String RESULT_OK = "ok";
    public static final String RESULT_INCOMPLETE = "incomplete";

    public static final String MODE_STREAM = "stream";
    public static final String MODE_CHAT = "chat";

    private ConversationAnalysisCallLog() {
    }

    public static void putAnalysisId(String analysisId) {
        if (StringUtils.hasText(analysisId)) {
            MDC.put(MDC_ANALYSIS_ID, analysisId);
        } else {
            MDC.remove(MDC_ANALYSIS_ID);
        }
    }

    /**
     * @return true 表示本次写入了 MDC，调用方应在 finally 中 {@link #clear()}
     */
    public static boolean putIfAbsent(String analysisId) {
        if (StringUtils.hasText(MDC.get(MDC_ANALYSIS_ID))) {
            return false;
        }
        putAnalysisId(analysisId);
        return true;
    }

    public static void clear() {
        MDC.remove(MDC_ANALYSIS_ID);
    }

    public static Map<String, String> copyContext() {
        return MDC.getCopyOfContextMap();
    }

    public static void runWithCopiedContext(Map<String, String> parent, Runnable task) {
        Map<String, String> previous = MDC.getCopyOfContextMap();
        try {
            if (parent == null) {
                MDC.clear();
            } else {
                MDC.setContextMap(parent);
            }
            task.run();
        } finally {
            if (previous == null) {
                MDC.clear();
            } else {
                MDC.setContextMap(previous);
            }
        }
    }

    public static String resultOf(Throwable error) {
        if (error == null) {
            return RESULT_OK;
        }
        Throwable current = error;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        String name = current.getClass().getSimpleName();
        return StringUtils.hasText(name) ? name : current.getClass().getName();
    }

    public static void record(String stage, String modelId, int attempt, long durationMs, String result) {
        record(stage, modelId, attempt, durationMs, result, 0, 0, null);
    }

    public static void record(
            String stage,
            String modelId,
            int attempt,
            long durationMs,
            String result,
            int batchNum,
            int totalBatches,
            String mode) {
        log.info(format(
                MDC.get(MDC_ANALYSIS_ID),
                stage,
                modelId,
                batchNum,
                totalBatches,
                mode,
                attempt,
                durationMs,
                result));
    }

    static String format(
            String analysisId,
            String stage,
            String modelId,
            int batchNum,
            int totalBatches,
            String mode,
            int attempt,
            long durationMs,
            String result) {
        StringBuilder line = new StringBuilder("llm_call");
        appendField(line, "analysisId", dashIfBlank(analysisId));
        appendField(line, "stage", dashIfBlank(stage));
        appendField(line, "model", dashIfBlank(modelId));
        if (totalBatches > 1 && batchNum > 0) {
            line.append(" batch=").append(batchNum).append('/').append(totalBatches);
        }
        if (StringUtils.hasText(mode)) {
            appendField(line, "mode", mode);
        }
        appendField(line, "attempt", String.valueOf(attempt));
        appendField(line, "durationMs", String.valueOf(durationMs));
        appendField(line, "result", dashIfBlank(result));
        return line.toString();
    }

    private static void appendField(StringBuilder line, String name, String value) {
        line.append(' ').append(name).append('=').append(value);
    }

    private static String dashIfBlank(String value) {
        return StringUtils.hasText(value) ? value : "-";
    }
}
