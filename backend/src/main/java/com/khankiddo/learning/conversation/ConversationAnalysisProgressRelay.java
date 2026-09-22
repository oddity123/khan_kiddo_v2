package com.khankiddo.learning.conversation;

import com.khankiddo.learning.dto.conversation.ConversationAnalysisProgress;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

/**
 * 分批分析 progress 中继：串行化推送批级文案，避免多批并发时前端事件交错。
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

    /**
     * 只转发批级文案（status + message）；无 message 直接丢弃；
     * 同一 status+message 全局只推一次（并发批交错时，仅靠「与上一条比较」去重不够）。
     */
    static Consumer<ConversationAnalysisProgress> batchLevelOnly(
            Consumer<ConversationAnalysisProgress> delegate) {
        Set<String> seen = new HashSet<>();
        return progress -> {
            if (!StringUtils.hasText(progress.getMessage())) {
                return;
            }
            String key = progress.getStatus() + '\0' + progress.getMessage();
            if (!seen.add(key)) {
                return;
            }
            delegate.accept(ConversationAnalysisProgress.builder()
                    .status(progress.getStatus())
                    .message(progress.getMessage())
                    .build());
        };
    }
}
