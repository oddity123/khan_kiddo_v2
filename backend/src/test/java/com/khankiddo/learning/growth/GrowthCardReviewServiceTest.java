package com.khankiddo.learning.growth;

import com.khankiddo.learning.dto.growth.GrowthCardGradeRequest;
import com.khankiddo.learning.model.GrowthCard;
import com.khankiddo.learning.security.AuthenticatedUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GrowthCardReviewServiceTest {

    @Mock
    private GrowthCardStore store;

    @Mock
    private GrowthCardMintGateway gateway;

    @Mock
    private GrowthCardEvidenceHydrator evidenceHydrator;

    private GrowthCardReviewService reviewService;

    @BeforeEach
    void setUp() {
        reviewService = new GrowthCardReviewService(store, gateway, evidenceHydrator);
        var auth = new UsernamePasswordAuthenticationToken(
                new AuthenticatedUser(1L, "test", "USER"), null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void resolveHabitMintStatus_shouldReturnReadyWhenCardPresent() {
        assertEquals("ready", reviewService.resolveHabitMintStatus(true));
    }

    @Test
    void resolveHabitMintStatus_shouldReturnNoneWhenNoHabitCard() {
        assertEquals("none", reviewService.resolveHabitMintStatus(false));
    }

    @Test
    void grade_shouldApplySchedulerAndUpdateStore() {
        GrowthCardGradeRequest request = new GrowthCardGradeRequest();
        request.setGrade("easy");

        GrowthCard updated = GrowthCard.builder()
                .cardId("card-1")
                .type("habit")
                .status("mastered")
                .nextDueAt(null)
                .front("front")
                .back("back")
                .sourceAnalysisId("analysis-1")
                .build();
        when(store.updateReview(eq("card-1"), eq(1L), eq("mastered"), eq(null)))
                .thenReturn(updated);

        var dto = reviewService.grade("card-1", request);

        verify(store).updateReview("card-1", 1L, "mastered", null);
        assertEquals("card-1", dto.getCardId());
        assertEquals("mastered", dto.getStatus());
        assertNull(dto.getNextDueAt());
    }

    @Test
    void toDto_shouldMapEntityFields() {
        GrowthCard card = GrowthCard.builder()
                .cardId("card-2")
                .type("vocab")
                .status("fuzzy")
                .nextDueAt(LocalDate.of(2026, 8, 5))
                .front("hello")
                .back("world")
                .sourceAnalysisId("analysis-2")
                .sourceRef("habit:tense")
                .build();

        when(store.listEvidence("card-2")).thenReturn(List.of());
        when(evidenceHydrator.hydrateMissing(anyList(), anyMap())).thenReturn(Map.of());

        var dto = reviewService.toDto(card);

        assertEquals("card-2", dto.getCardId());
        assertEquals("vocab", dto.getType());
        assertEquals("fuzzy", dto.getStatus());
        assertEquals(LocalDate.of(2026, 8, 5), dto.getNextDueAt());
        assertEquals("hello", dto.getFront());
        assertEquals("world", dto.getBack());
        assertEquals("analysis-2", dto.getSourceAnalysisId());
        assertEquals("habit:tense", dto.getSourceRef());
        assertEquals(0, dto.getEvidence() == null ? 0 : dto.getEvidence().size());
    }
}
