package com.khankiddo.learning.conversation;

import com.khankiddo.learning.dto.conversation.ConversationAnalysisProgress;

import java.util.function.Consumer;

/**
 * 分批分析 progress 中继：串行化推送，并过滤句子级流式预览字段，避免多批并发时前端事件交错。
 */
final class ConversationAnalysisProgressRelay {

    private ConversationAnalysisProgressRelay() {
    }

    static Consumer<ConversationAnalysisProgress> synchronizedBatchLevelSink(
            Consumer<ConversationAnalysisProgress> delegate) {
        Object lock = new Object();
        Consumer<ConversationAnalysisProgress> synchronizedSink = progress -> {
            synchronized (lock) {
                delegate.accept(progress);
            }
        };
        return batchLevelOnly(synchronizedSink);
    }

    static Consumer<ConversationAnalysisProgress> batchLevelOnly(
            Consumer<ConversationAnalysisProgress> delegate) {
        return progress -> delegate.accept(ConversationAnalysisProgress.builder()
                .status(progress.getStatus())
                .message(progress.getMessage())
                .build());
    }
}
