package com.khankiddo.learning.growth;

import com.khankiddo.learning.exception.BadRequestException;
import com.khankiddo.learning.mapper.GrowthCardMapper;
import com.khankiddo.learning.model.GrowthCard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GrowthCardStoreTest {

    @Mock
    private GrowthCardMapper mapper;

    private GrowthCardStore store;

    @BeforeEach
    void setUp() {
        store = new GrowthCardStore(mapper);
    }

    @Test
    void persistNewOrGet_shouldInsertWhenNotExists() {
        when(mapper.findByUserSource(1L, "analysis-1", "habit", "ref-1"))
                .thenReturn(Optional.empty());

        GrowthCard created = store.persistNewOrGet(
                1L, "habit", "front", "back", "analysis-1", "ref-1", "{\"evidence\":true}");

        ArgumentCaptor<GrowthCard> captor = ArgumentCaptor.forClass(GrowthCard.class);
        verify(mapper).insert(captor.capture());
        GrowthCard inserted = captor.getValue();

        assertNotNull(inserted.getCardId());
        assertEquals(1L, inserted.getUserId());
        assertEquals("habit", inserted.getType());
        assertEquals("unfamiliar", inserted.getStatus());
        assertEquals(LocalDate.now(), inserted.getNextDueAt());
        assertEquals("front", inserted.getFront());
        assertEquals("back", inserted.getBack());
        assertEquals("analysis-1", inserted.getSourceAnalysisId());
        assertEquals("ref-1", inserted.getSourceRef());
        assertEquals("{\"evidence\":true}", inserted.getEvidenceJson());
        assertEquals(inserted, created);
    }

    @Test
    void persistNewOrGet_shouldReturnExistingWithoutInsert() {
        GrowthCard existing = GrowthCard.builder()
                .id(10L)
                .cardId("existing-card-id")
                .userId(1L)
                .type("habit")
                .status("fuzzy")
                .nextDueAt(LocalDate.now().plusDays(3))
                .front("old front")
                .back("old back")
                .sourceAnalysisId("analysis-1")
                .sourceRef("ref-1")
                .build();
        when(mapper.findByUserSource(1L, "analysis-1", "habit", "ref-1"))
                .thenReturn(Optional.of(existing));

        GrowthCard result = store.persistNewOrGet(
                1L, "habit", "new front", "new back", "analysis-1", "ref-1", null);

        assertEquals(existing, result);
        verify(mapper, never()).insert(any());
    }

    @Test
    void persistNewOrGet_shouldReturnExistingOnDuplicateInsert() {
        GrowthCard existing = GrowthCard.builder()
                .id(10L)
                .cardId("existing-card-id")
                .userId(1L)
                .type("habit")
                .status("unfamiliar")
                .nextDueAt(LocalDate.now())
                .front("front")
                .back("back")
                .sourceAnalysisId("analysis-1")
                .sourceRef("ref-1")
                .build();
        when(mapper.findByUserSource(1L, "analysis-1", "habit", "ref-1"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(existing));
        doThrow(new DataIntegrityViolationException("duplicate key"))
                .when(mapper).insert(any());

        GrowthCard result = store.persistNewOrGet(
                1L, "habit", "front", "back", "analysis-1", "ref-1", null);

        assertEquals(existing, result);
        verify(mapper).insert(any());
        verify(mapper, times(2)).findByUserSource(1L, "analysis-1", "habit", "ref-1");
    }

    @Test
    void updateReview_shouldUpdateOwnedCard() {
        GrowthCard existing = GrowthCard.builder()
                .cardId("card-1")
                .userId(1L)
                .status("unfamiliar")
                .nextDueAt(LocalDate.now())
                .build();
        LocalDate nextDue = LocalDate.now().plusDays(3);
        when(mapper.findByCardIdAndUserId("card-1", 1L)).thenReturn(Optional.of(existing));

        GrowthCard updated = store.updateReview("card-1", 1L, "fuzzy", nextDue);

        verify(mapper).updateReview("card-1", 1L, "fuzzy", nextDue);
        assertEquals("fuzzy", updated.getStatus());
        assertEquals(nextDue, updated.getNextDueAt());
    }

    @Test
    void updateReview_shouldThrowWhenCardMissing() {
        when(mapper.findByCardIdAndUserId("missing", 1L)).thenReturn(Optional.empty());

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> store.updateReview("missing", 1L, "fuzzy", LocalDate.now()));

        assertEquals("成长卡不存在", ex.getMessage());
        verify(mapper, never()).updateReview(any(), any(), any(), any());
    }

    @Test
    void listDue_shouldDelegateToMapper() {
        LocalDate today = LocalDate.of(2026, 8, 2);
        GrowthCard due = GrowthCard.builder().cardId("due-1").build();
        when(mapper.findDueByUserId(1L, today)).thenReturn(java.util.List.of(due));

        assertEquals(1, store.listDue(1L, today).size());
        verify(mapper).findDueByUserId(eq(1L), eq(today));
    }
}
