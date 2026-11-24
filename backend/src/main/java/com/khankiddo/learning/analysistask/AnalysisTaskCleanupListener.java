package com.khankiddo.learning.analysistask;

import com.khankiddo.learning.memory.event.MemoryAnalysisDeletedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 分析报告删除时，同事务清理关联的异步分析任务记录，避免扩展端继续显示「查看报告」或复用已删报告。
 */
@Component
@RequiredArgsConstructor
public class AnalysisTaskCleanupListener {

    private final ConversationAnalysisTaskMapper taskMapper;

    @EventListener
    public void onAnalysisDeleted(MemoryAnalysisDeletedEvent event) {
        if (event == null || event.userId() == null || !StringUtils.hasText(event.analysisId())) {
            return;
        }
        taskMapper.deleteByAnalysisIdAndUserId(event.analysisId(), event.userId());
    }
}
