package com.khankiddo.learning;

import com.khankiddo.learning.dto.conversation.ConversationAnalysisSaveRequest;
import com.khankiddo.learning.mapper.ConversationAnalysisItemMapper;
import com.khankiddo.learning.model.enums.ExpressionStatus;
import com.khankiddo.learning.security.AuthenticatedUser;
import com.khankiddo.learning.service.conversation.ConversationAnalysisService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ExpressionStatusPersistenceTest {
    @Autowired private ConversationAnalysisService service;
    @Autowired private ConversationAnalysisItemMapper items;

    @BeforeEach
    void authenticate() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new AuthenticatedUser(98765L, "status-test", "USER"), null, List.of()));
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void savingAndReopeningPreservesStatusAndExcludesOptionalFromHistoricalIssues() {
        var request = ConversationAnalysisSaveRequest.builder()
                .conversationContent("A spoken English practice conversation.")
                .items(List.of(ConversationAnalysisSaveRequest.SaveAnalysisItem.builder()
                        .originalSentence("A practice sentence.")
                        .suggestion("A corrected practice sentence.")
                        .errors(List.of(error(ExpressionStatus.RISK), error(ExpressionStatus.UNNATURAL),
                                error(null), error(ExpressionStatus.OPTIONAL)))
                        .build()))
                .build();
        String id = service.save(request).getAnalysisId();
        assertThat(items.findByAnalysisId(id)).extracting(row -> row.getStatus())
                .containsExactly(ExpressionStatus.RISK, ExpressionStatus.UNNATURAL, ExpressionStatus.ERROR);
        assertThat(items.countByUserIdAndPointIds(98765L, List.of("LEXICAL_GAP"))).isEqualTo(3);
        assertThat(service.getDetail(id).getItems().getFirst().getErrors()).extracting(error -> error.getStatus())
                .containsExactly(ExpressionStatus.RISK, ExpressionStatus.UNNATURAL, ExpressionStatus.ERROR);
    }

    @Test
    void optionalOnlySaveCreatesNoHistoricalIssuesOrHabitCards() {
        var request = ConversationAnalysisSaveRequest.builder()
                .conversationContent("I like this movie a lot.")
                .items(List.of(ConversationAnalysisSaveRequest.SaveAnalysisItem.builder()
                        .originalSentence("I like this movie a lot.")
                        .suggestion("I really enjoy this film.")
                        .errors(List.of(error(ExpressionStatus.OPTIONAL))).build()))
                .build();
        String id = service.save(request).getAnalysisId();
        assertThat(items.findByAnalysisId(id)).isEmpty();
        assertThat(service.getDetail(id).getItems()).isEmpty();
        assertThat(service.getDetail(id).getActionCards()).isEmpty();
    }

    private static ConversationAnalysisSaveRequest.SaveError error(ExpressionStatus status) {
        return ConversationAnalysisSaveRequest.SaveError.builder().type("词汇缺口")
                .pointId("LEXICAL_GAP").point("practice → exercise").status(status).build();
    }
}
