package com.khankiddo.learning.memory.service;

import com.khankiddo.learning.extensionconnect.ExtensionConnectTokenSupport;
import com.khankiddo.learning.memory.domain.MemoryEvidence;
import com.khankiddo.learning.memory.enums.EvidenceType;
import com.khankiddo.learning.memory.mapper.MemoryEvidenceMapper;
import com.khankiddo.learning.util.TextSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * 记忆证据管理：幂等追加 HIT / PRACTICE_ERROR / TRANSFER_SUCCESS / PROMPTED_CORRECT。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemoryEvidenceService {

    private final MemoryEvidenceMapper evidenceMapper;
    private final Clock clock;

    public boolean recordEvidence(
            Long memoryId,
            Long userId,
            String analysisId,
            String sentenceId,
            EvidenceType type,
            String dedupKey,
            String originalText,
            String correctedText,
            BigDecimal confidence,
            LocalDateTime createdAt) {
        if (memoryId == null || userId == null || type == null || !StringUtils.hasText(dedupKey)) {
            return false;
        }
        String normalizedKey = dedupKey.trim();
        if (evidenceMapper.existsByMemoryIdAndDedupKey(memoryId, normalizedKey)) {
            return false;
        }
        MemoryEvidence row = MemoryEvidence.builder()
                .memoryId(memoryId)
                .userId(userId)
                .analysisId(TextSupport.trimToNull(analysisId))
                .sentenceId(TextSupport.trimToNull(sentenceId))
                .evidenceType(type.name())
                .dedupKey(normalizedKey)
                .originalText(TextSupport.trimToNull(originalText))
                .correctedText(TextSupport.trimToNull(correctedText))
                .confidence(confidence)
                .createdAt(createdAt != null ? createdAt : LocalDateTime.now(clock))
                .build();
        try {
            evidenceMapper.insert(row);
            return true;
        } catch (DataIntegrityViolationException ex) {
            log.debug("memory evidence dedup conflict memoryId={} key={}", memoryId, normalizedKey);
            return false;
        }
    }

    public static String buildHitDedupKey(String analysisId, Long sentenceId, String originalSentence) {
        String prefix = "HIT:" + (StringUtils.hasText(analysisId) ? analysisId.trim() : "none") + ":";
        if (sentenceId != null) {
            return prefix + "S" + sentenceId;
        }
        return prefix + sha256Prefix16(originalSentence);
    }

    public static String buildAttemptDedupKey(Long sessionId, int attemptNo) {
        return "ATTEMPT:" + sessionId + ":" + attemptNo;
    }

    public static String buildAssessmentDedupKey(EvidenceType type, String analysisId, String quote) {
        return type.name() + ":" + (StringUtils.hasText(analysisId) ? analysisId.trim() : "none")
                + ":" + sha256Prefix16(quote);
    }

    public Optional<MemoryEvidence> findLatestHitOrEvidence(Long memoryId) {
        if (memoryId == null) {
            return Optional.empty();
        }
        Optional<MemoryEvidence> latestHit = evidenceMapper.findLatestByMemoryIdAndType(
                memoryId, EvidenceType.HIT.name());
        if (latestHit.isPresent()) {
            return latestHit;
        }
        List<MemoryEvidence> recent = evidenceMapper.findByMemoryId(memoryId, 1);
        return CollectionUtils.isEmpty(recent) ? Optional.empty() : Optional.of(recent.get(0));
    }

    public Optional<MemoryEvidence> findDistinctHitOrEvidence(Long memoryId, Set<String> usedNormalizedOriginals) {
        if (memoryId == null) {
            return Optional.empty();
        }
        if (CollectionUtils.isEmpty(usedNormalizedOriginals)) {
            return findLatestHitOrEvidence(memoryId);
        }
        List<MemoryEvidence> candidates = evidenceMapper.findByMemoryId(memoryId, 10);
        if (CollectionUtils.isEmpty(candidates)) {
            return Optional.empty();
        }
        for (MemoryEvidence candidate : candidates) {
            if (EvidenceType.HIT.name().equals(candidate.getEvidenceType())
                    && isDistinctOriginal(candidate.getOriginalText(), usedNormalizedOriginals)) {
                return Optional.of(candidate);
            }
        }
        for (MemoryEvidence candidate : candidates) {
            if (isDistinctOriginal(candidate.getOriginalText(), usedNormalizedOriginals)) {
                return Optional.of(candidate);
            }
        }
        return findLatestHitOrEvidence(memoryId);
    }

    private static boolean isDistinctOriginal(String originalText, Set<String> usedNormalizedOriginals) {
        if (!StringUtils.hasText(originalText)) {
            return false;
        }
        return !usedNormalizedOriginals.contains(originalText.trim().toLowerCase(Locale.ROOT));
    }

    public List<Long> deleteByAnalysis(Long userId, String analysisId) {
        if (userId == null || !StringUtils.hasText(analysisId)) {
            return List.of();
        }
        String normalizedAnalysisId = analysisId.trim();
        List<Long> affected = evidenceMapper.findDistinctMemoryIdsByUserIdAndAnalysisId(userId, normalizedAnalysisId);
        evidenceMapper.deleteByUserIdAndAnalysisId(userId, normalizedAnalysisId);
        return CollectionUtils.isEmpty(affected) ? List.of() : affected;
    }

    private static String sha256Prefix16(String text) {
        String normalized = StringUtils.hasText(text) ? text.trim().toLowerCase() : "";
        return ExtensionConnectTokenSupport.sha256Hex(normalized).substring(0, 16);
    }
}
