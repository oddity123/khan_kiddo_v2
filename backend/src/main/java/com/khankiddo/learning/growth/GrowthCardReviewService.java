package com.khankiddo.learning.growth;

import com.khankiddo.learning.dto.conversation.ActionCardDto;
import com.khankiddo.learning.dto.growth.CollectGrowthCardRequest;
import com.khankiddo.learning.dto.growth.GrowthCardDto;
import com.khankiddo.learning.dto.growth.GrowthCardGradeRequest;
import com.khankiddo.learning.model.GrowthCard;
import com.khankiddo.learning.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class GrowthCardReviewService {

    private final GrowthCardStore store;
    private final GrowthCardMintGateway gateway;
    private final GrowthCardScheduler scheduler = new GrowthCardScheduler();

    public List<GrowthCardDto> listToday() {
        Long userId = SecurityUtils.requireUserId();
        return store.listDue(userId, LocalDate.now()).stream()
                .map(this::toDto)
                .toList();
    }

    public GrowthCardDto grade(String cardId, GrowthCardGradeRequest request) {
        Long userId = SecurityUtils.requireUserId();
        GrowthCardScheduler.ReviewResult result = scheduler.apply(request.getGrade(), LocalDate.now());
        GrowthCard card = store.updateReview(cardId, userId, result.status(), result.nextDueAt());
        return toDto(card);
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
        return toDto(card);
    }

    public void retryMint(String analysisId) {
        gateway.retryMint(SecurityUtils.requireUserId(), analysisId);
    }

    public Optional<GrowthCardDto> findHabitCardForAnalysis(String analysisId) {
        Long userId = SecurityUtils.requireUserId();
        return store.findHabitByAnalysis(userId, analysisId).map(this::toDto);
    }

    public String resolveHabitMintStatus(ActionCardDto topHabit, LocalDateTime createdAt, boolean habitCardPresent) {
        if (habitCardPresent) {
            return "ready";
        }
        if (topHabit == null) {
            return "none";
        }
        if (createdAt != null && createdAt.isAfter(LocalDateTime.now().minusSeconds(30))) {
            return "pending";
        }
        return "failed";
    }

    GrowthCardDto toDto(GrowthCard card) {
        return GrowthCardDto.builder()
                .cardId(card.getCardId())
                .type(card.getType())
                .status(card.getStatus())
                .nextDueAt(card.getNextDueAt())
                .front(card.getFront())
                .back(card.getBack())
                .sourceAnalysisId(card.getSourceAnalysisId())
                .build();
    }
}
