package com.khankiddo.learning.growth;

import com.khankiddo.learning.exception.BadRequestException;
import com.khankiddo.learning.mapper.GrowthCardEvidenceMapper;
import com.khankiddo.learning.mapper.GrowthCardMapper;
import com.khankiddo.learning.model.GrowthCard;
import com.khankiddo.learning.model.GrowthCardEvidence;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class GrowthCardStore {

    private final GrowthCardMapper mapper;
    private final GrowthCardEvidenceMapper evidenceMapper;

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

    /**
     * 写入证据行（卡内 track_key 去重，已存在则忽略）。
     */
    public void saveEvidence(List<GrowthCardEvidence> rows) {
        if (CollectionUtils.isEmpty(rows)) {
            return;
        }
        evidenceMapper.insertIgnoreBatch(rows);
    }

    public List<GrowthCardEvidence> listEvidence(String cardId) {
        return evidenceMapper.findByCardId(cardId);
    }

    public Map<String, List<GrowthCardEvidence>> listEvidenceByCardIds(List<String> cardIds) {
        if (CollectionUtils.isEmpty(cardIds)) {
            return Map.of();
        }
        List<GrowthCardEvidence> rows = evidenceMapper.findByCardIds(cardIds);
        Map<String, List<GrowthCardEvidence>> grouped = new LinkedHashMap<>();
        for (GrowthCardEvidence row : rows) {
            grouped.computeIfAbsent(row.getCardId(), ignored -> new ArrayList<>()).add(row);
        }
        return grouped;
    }

    /**
     * 按句追踪：用户在某场分析的某句关联了哪些证据行（可反查卡）。
     */
    public List<GrowthCardEvidence> listEvidenceBySentence(long userId, String analysisId, String sentenceId) {
        return evidenceMapper.findByUserAnalysisSentence(userId, analysisId, sentenceId);
    }

    public List<GrowthCard> listDue(long userId, LocalDate today) {
        return mapper.findDueByUserId(userId, today);
    }

    public List<GrowthCard> listRandom(long userId, int limit) {
        return mapper.findRandomByUserId(userId, limit);
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

    public void deleteOwned(String cardId, long userId) {
        int deleted = mapper.deleteByCardIdAndUserId(cardId, userId);
        if (deleted == 0) {
            throw new BadRequestException("成长卡不存在");
        }
        evidenceMapper.deleteByCardId(cardId);
    }

    public Optional<GrowthCard> findHabitByAnalysis(long userId, String analysisId) {
        return mapper.findHabitByAnalysis(userId, analysisId);
    }

    public Optional<GrowthCard> findByUserSource(long userId, String sourceAnalysisId,
                                                 String type, String sourceRef) {
        return mapper.findByUserSource(userId, sourceAnalysisId, type, sourceRef);
    }

    public List<GrowthCard> listByAnalysis(long userId, String analysisId) {
        return mapper.findByUserAndAnalysis(userId, analysisId);
    }
}
