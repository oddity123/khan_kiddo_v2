package com.khankiddo.learning.conversation;

import com.khankiddo.learning.exception.BadRequestException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;

class ConversationAnalysisErrorMessagesTest {

    @Test
    void mapsDataAccessFailureToPersistMessage() {
        String jdbc = "### Error updating database. Cause: MysqlDataTruncation: "
                + "Data too long for column 'error_point' at row 45 ### The error may exist in file "
                + "[/Users/oddity/workspace/khan_kiddo_v2/backend/target/classes/mapper/"
                + "ConversationAnalysisItemMapper.xml] ### SQL: INSERT INTO conversation_analysis_item";
        Throwable error = new DataIntegrityViolationException(jdbc);

        String message = ConversationAnalysisErrorMessages.toUserMessage(error);

        assertThat(message).isEqualTo("保存分析结果失败，请重试");
        assertThat(message).doesNotContain("INSERT", "error_point", "/Users/", "mapper");
    }

    @Test
    void keepsSafeBadRequestMessage() {
        String message = ConversationAnalysisErrorMessages.toUserMessage(
                new BadRequestException("AI 分析超时，请缩短对话内容或稍后重试"));

        assertThat(message).isEqualTo("AI 分析超时，请缩短对话内容或稍后重试");
    }

    @Test
    void hidesTechnicalAiFailureDetails() {
        String message = ConversationAnalysisErrorMessages.toUserMessage(
                new BadRequestException(
                        "AI 分析失败: reactor.netty.http.client.PrematureCloseException: "
                                + "Connection prematurely closed DURING response"));

        assertThat(message).isEqualTo("分析失败，请稍后重试");
        assertThat(message).doesNotContain("PrematureCloseException", "reactor.netty");
    }

    @Test
    void mapsUnknownFailureToGenericMessage() {
        String message = ConversationAnalysisErrorMessages.toUserMessage(
                new IllegalStateException("JDBC rollback; Communications link failure during rollback()"));

        assertThat(message).isEqualTo("分析失败，请稍后重试");
        assertThat(message).doesNotContain("JDBC", "rollback");
    }

    @Test
    void sanitizeStoredMessage_rejectsSqlPayload() {
        String stored = ConversationAnalysisErrorMessages.sanitizeStoredMessage(
                "### Error updating database. Cause: Data too long for column 'error_point'");

        assertThat(stored).isEqualTo("分析失败，请稍后重试");
        assertThat(stored).doesNotContain("error_point", "Error updating");
    }

    @Test
    void sanitizeStoredMessage_keepsMappedPersistMessage() {
        assertThat(ConversationAnalysisErrorMessages.sanitizeStoredMessage("保存分析结果失败，请重试"))
                .isEqualTo("保存分析结果失败，请重试");
    }
}
