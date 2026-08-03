package com.khankiddo.learning.growth;

import com.khankiddo.learning.exception.BadRequestException;
import com.khankiddo.learning.mapper.GrowthCardMapper;
import com.khankiddo.learning.model.GrowthCard;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class GrowthCardStore {

    private final GrowthCardMapper mapper;

    /**
     * 幂等：已存在则返回 existing，否则 insert 新卡（unfamiliar, nextDueAt=today）。
     */
    public GrowthCard persistNewOrGet(long userId, String type, String front, String back,
                                      String sourceAnalysisId, String sourceRef, String evidenceJson) {
        Optional<GrowthCard> existing = mapper.findByUserSource(userId, sourceAnalysisId, type, sourceRef);
        if (existing.isPresent()) {
            return existing.get();
        }
        LocalDate today = LocalDate.now();
        GrowthCard card = GrowthCard.builder()
                .cardId(UUID.randomUUID().toString())
                .userId(userId)
                .type(type)
                .status("unfamiliar")
                .nextDueAt(today)
                .front(front)
                .back(back)
                .sourceAnalysisId(sourceAnalysisId)
                .sourceRef(sourceRef)
                .evidenceJson(evidenceJson)
                .build();
        try {
            mapper.insert(card);
            return card;
        } catch (DataIntegrityViolationException ex) {
            return mapper.findByUserSource(userId, sourceAnalysisId, type, sourceRef)
                    .orElseThrow(() -> ex);
        }
    }

    public List<GrowthCard> listDue(long userId, LocalDate today) {
        return mapper.findDueByUserId(userId, today);
    }

    public Optional<GrowthCard> findOwned(String cardId, long userId) {
        return mapper.findByCardIdAndUserId(cardId, userId);
    }

    public GrowthCard updateReview(String cardId, long userId, String status, LocalDate nextDueAt) {
        GrowthCard card = mapper.findByCardIdAndUserId(cardId, userId)
                .orElseThrow(() -> new BadRequestException("成长卡不存在"));
        mapper.updateReview(cardId, userId, status, nextDueAt);
        card.setStatus(status);
        card.setNextDueAt(nextDueAt);
        return card;
    }

    public Optional<GrowthCard> findHabitByAnalysis(long userId, String analysisId) {
        return mapper.findHabitByAnalysis(userId, analysisId);
    }

    public List<GrowthCard> listByAnalysis(long userId, String analysisId) {
        return mapper.findByUserAndAnalysis(userId, analysisId);
    }
}
