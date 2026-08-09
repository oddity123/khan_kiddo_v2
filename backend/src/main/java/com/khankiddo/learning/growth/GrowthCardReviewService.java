package com.khankiddo.learning.growth;

import com.khankiddo.learning.dto.growth.CollectGrowthCardRequest;
import com.khankiddo.learning.dto.growth.GrowthCardDto;
import com.khankiddo.learning.dto.growth.GrowthCardEvidenceDto;
import com.khankiddo.learning.dto.growth.GrowthCardGradeRequest;
import com.khankiddo.learning.model.GrowthCard;
import com.khankiddo.learning.model.GrowthCardEvidence;
import com.khankiddo.learning.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class GrowthCardReviewService {

    private final GrowthCardStore store;
    private final GrowthCardMintGateway gateway;
    private final GrowthCardEvidenceHydrator evidenceHydrator;
    private final GrowthCardScheduler scheduler = new GrowthCardScheduler();

    public List<GrowthCardDto> listToday() {
        Long userId = SecurityUtils.requireUserId();
        return toDtosWithEvidence(store.listDue(userId, LocalDate.now()));
    }

    public List<GrowthCardDto> listRandom(int limit) {
        Long userId = SecurityUtils.requireUserId();
        int size = Math.min(Math.max(limit, 1), 20);
        return toDtosWithEvidence(store.listRandom(userId, size));
    }

    public GrowthCardDto grade(String cardId, GrowthCardGradeRequest request) {
        Long userId = SecurityUtils.requireUserId();
        GrowthCardScheduler.ReviewResult result = scheduler.apply(request.getGrade(), LocalDate.now());
        GrowthCard card = store.updateReview(cardId, userId, result.status(), result.nextDueAt());
        return toDto(card, store.listEvidence(card.getCardId()));
    }

    public void delete(String cardId) {
        store.deleteOwned(cardId, SecurityUtils.requireUserId());
    }

    public GrowthCardDto collect(CollectGrowthCardRequest request) {
        Long userId = SecurityUtils.requireUserId();
        String type = StringUtils.hasText(request.getType()) ? request.getType().trim() : "habit";
        GrowthCard card = store.persistNewOrGet(
                userId,
                type,
                request.getFront(),
                request.getBack(),
                request.getAnalysisId(),
                request.getSourceRef(),
                null);
        return toDto(card, store.listEvidence(card.getCardId()));
    }

    public void retryMint(String analysisId) {
        gateway.retryMint(SecurityUtils.requireUserId(), analysisId);
    }

    public GrowthCardDto mintHabit(String analysisId, String habitKey) {
        GrowthCard card = gateway.mintHabitByKey(SecurityUtils.requireUserId(), analysisId, habitKey);
        return toDto(card, store.listEvidence(card.getCardId()));
    }

    public Optional<GrowthCardDto> findHabitCardForAnalysis(String analysisId) {
        Long userId = SecurityUtils.requireUserId();
        return store.findHabitByAnalysis(userId, analysisId)
                .map(card -> toDto(card, store.listEvidence(card.getCardId())));
    }

    public List<GrowthCardDto> listByAnalysis(String analysisId) {
        Long userId = SecurityUtils.requireUserId();
        return toDtosWithEvidence(store.listByAnalysis(userId, analysisId));
    }

    /**
     * 按句追踪：某场分析某句关联到的成长卡（含证据）。
     */
    public List<GrowthCardDto> listBySentence(String analysisId, String sentenceId) {
        Long userId = SecurityUtils.requireUserId();
        if (!StringUtils.hasText(analysisId) || !StringUtils.hasText(sentenceId)) {
            return List.of();
        }
        List<GrowthCardEvidence> rows =
                store.listEvidenceBySentence(userId, analysisId.trim(), sentenceId.trim());
        if (CollectionUtils.isEmpty(rows)) {
            return List.of();
        }
        Set<String> cardIds = new LinkedHashSet<>();
        for (GrowthCardEvidence row : rows) {
            cardIds.add(row.getCardId());
        }
        List<GrowthCard> cards = new ArrayList<>();
        for (String cardId : cardIds) {
            store.findOwned(cardId, userId).ifPresent(cards::add);
        }
        return toDtosWithEvidence(cards);
    }

    /**
     * 习惯卡是否已有（手动制卡后为 ready）。不再自动铸 Top1，故无 pending/failed。
     */
    public String resolveHabitMintStatus(boolean habitCardPresent) {
        return habitCardPresent ? "ready" : "none";
    }

    private List<GrowthCardDto> toDtosWithEvidence(List<GrowthCard> cards) {
        if (CollectionUtils.isEmpty(cards)) {
            return List.of();
        }
        List<String> ids = cards.stream().map(GrowthCard::getCardId).toList();
        Map<String, List<GrowthCardEvidence>> existing;
        try {
            existing = store.listEvidenceByCardIds(ids);
        } catch (Exception ex) {
            log.warn("读取成长卡证据失败（表是否已建？）: {}", ex.getMessage());
            existing = Map.of();
        }
        Map<String, List<GrowthCardEvidence>> byCard = evidenceHydrator.hydrateMissing(cards, existing);
        List<GrowthCardDto> result = new ArrayList<>(cards.size());
        for (GrowthCard card : cards) {
            result.add(toDto(card, byCard.getOrDefault(card.getCardId(), List.of())));
        }
        return result;
    }

    GrowthCardDto toDto(GrowthCard card) {
        List<GrowthCardEvidence> rows;
        try {
            rows = store.listEvidence(card.getCardId());
        } catch (Exception ex) {
            log.warn("读取成长卡证据失败 cardId={}: {}", card.getCardId(), ex.getMessage());
            rows = List.of();
        }
        if (CollectionUtils.isEmpty(rows)) {
            Map<String, List<GrowthCardEvidence>> hydrated =
                    evidenceHydrator.hydrateMissing(List.of(card), Map.of());
            rows = hydrated.getOrDefault(card.getCardId(), List.of());
        }
        return toDto(card, rows);
    }

    private GrowthCardDto toDto(GrowthCard card, List<GrowthCardEvidence> evidenceRows) {
        return GrowthCardDto.builder()
                .cardId(card.getCardId())
                .type(card.getType())
                .status(card.getStatus())
                .nextDueAt(card.getNextDueAt())
                .front(card.getFront())
                .back(card.getBack())
                .sourceAnalysisId(card.getSourceAnalysisId())
                .sourceRef(card.getSourceRef())
                .createdAt(card.getCreatedAt())
                .evidence(toEvidenceDtos(evidenceRows))
                .build();
    }

    private static List<GrowthCardEvidenceDto> toEvidenceDtos(List<GrowthCardEvidence> rows) {
        if (CollectionUtils.isEmpty(rows)) {
            return List.of();
        }
        List<GrowthCardEvidenceDto> result = new ArrayList<>(rows.size());
        for (GrowthCardEvidence row : rows) {
            result.add(GrowthCardEvidenceDto.builder()
                    .sentenceId(row.getSentenceId())
                    .originalSentence(row.getOriginalSentence())
                    .suggestion(row.getSuggestion())
                    .sortOrder(row.getSortOrder() != null ? row.getSortOrder() : 0)
                    .build());
        }
        return result;
    }
}
