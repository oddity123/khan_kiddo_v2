package com.khankiddo.learning.conversation;

import com.khankiddo.learning.dto.conversation.ConversationAnalysisProgress;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

class ConversationAnalysisProgressRelayTest {

    @Test
    void batchLevelOnly_skipsBlankMessageAndDedupesAcrossInterleavedBatches() {
        List<ConversationAnalysisProgress> forwarded = new ArrayList<>();
        Consumer<ConversationAnalysisProgress> sink =
                ConversationAnalysisProgressRelay.batchLevelOnly(forwarded::add);

        sink.accept(ConversationAnalysisProgress.builder()
                .status(ConversationAnalysisProgress.STATUS_ANALYZING)
                .message("[6/8] 正在接收 AI 分析结果...")
                .build());
        sink.accept(ConversationAnalysisProgress.builder()
                .status(ConversationAnalysisProgress.STATUS_ANALYZING)
                .message("[3/8] 正在接收 AI 分析结果...")
                .build());
        // 无文案应丢弃
        sink.accept(ConversationAnalysisProgress.builder()
                .status(ConversationAnalysisProgress.STATUS_ANALYZING)
                .build());
        // 与已见文案相同（中间插入了其它批）——不得再次转发
        sink.accept(ConversationAnalysisProgress.builder()
                .status(ConversationAnalysisProgress.STATUS_ANALYZING)
                .message("[6/8] 正在接收 AI 分析结果...")
                .build());
        sink.accept(ConversationAnalysisProgress.builder()
                .status(ConversationAnalysisProgress.STATUS_ANALYZING)
                .message("已完成 1/8 批")
                .build());

        assertThat(forwarded).extracting(ConversationAnalysisProgress::getMessage)
                .containsExactly(
                        "[6/8] 正在接收 AI 分析结果...",
                        "[3/8] 正在接收 AI 分析结果...",
                        "已完成 1/8 批");
        assertThat(forwarded).allSatisfy(p ->
                assertThat(p.getStatus()).isEqualTo(ConversationAnalysisProgress.STATUS_ANALYZING));
    }
}
