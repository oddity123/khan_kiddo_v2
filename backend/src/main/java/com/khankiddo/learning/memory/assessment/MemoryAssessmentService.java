package com.khankiddo.learning.memory.assessment;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.khankiddo.learning.config.WeaknessMemoryProperties;
import com.khankiddo.learning.mapper.ConversationAnalysisItemMapper;
import com.khankiddo.learning.mapper.ConversationAnalysisMapper;
import com.khankiddo.learning.memory.domain.PracticeAttempt;
import com.khankiddo.learning.memory.domain.PracticeSession;
import com.khankiddo.learning.memory.domain.WeaknessMemory;
import com.khankiddo.learning.memory.enums.EvidenceType;
import com.khankiddo.learning.memory.enums.PracticeSessionStatus;
import com.khankiddo.learning.memory.mapper.PracticeAttemptMapper;
import com.khankiddo.learning.memory.mapper.PracticeSessionMapper;
import com.khankiddo.learning.memory.mapper.WeaknessMemoryMapper;
import com.khankiddo.learning.memory.service.MemoryEvidenceService;
import com.khankiddo.learning.memory.service.MemoryMasteryService;
import com.khankiddo.learning.memory.service.MemoryPriorityService;
import com.khankiddo.learning.model.ConversationAnalysis;
import com.khankiddo.learning.model.ConversationAnalysisItem;
import com.khankiddo.learning.util.TextSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 分析完成后的练习回检与自然语境迁移评估服务：
 * 1) 优先匹配绑定该 sourceConversationId 的 IN_PROGRESS 练习会话；
 * 2) 补充高优 ACTIVE/PRACTICING 薄弱点检查跨语境正向迁移（TRANSFER_SUCCESS）；
 * 3) 调用单次结构化 LLM 评估，在 Java 中做引文落盘校验并更新 Attempt / Evidence / Mastery。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemoryAssessmentService {

    private final WeaknessMemoryProperties properties;
    private final ConversationAnalysisMapper analysisMapper;
    private final ConversationAnalysisItemMapper itemMapper;
    private final WeaknessMemoryMapper memoryMapper;
    private final PracticeSessionMapper sessionMapper;
    private final PracticeAttemptMapper attemptMapper;
    private final MemoryPriorityService priorityService;
    private final MemoryEvidenceService evidenceService;
    private final MemoryMasteryService masteryService;
    private final MemoryAssessmentLlmClient llmClient;
    private final ObjectMapper objectMapper;
    private final PlatformTransactionManager transactionManager;
    private final Clock clock;

    public void assessAfterAnalysis(Long userId, String analysisId, String sourceConversationId) {
        if (!properties.isEnabled() || !properties.isAssessmentEnabled()) {
            return;
        }
        if (userId == null || !StringUtils.hasText(analysisId)) {
            return;
        }
        String normalizedAnalysisId = analysisId.trim();
        if (!CollectionUtils.isEmpty(sessionMapper.findIdsByUserIdAndAnalysisId(userId, normalizedAnalysisId))) {
            log.debug("assessment already applied for analysisId={} userId={}, skipping", normalizedAnalysisId, userId);
            return;
        }
        Optional<ConversationAnalysis> analysisOpt =
                analysisMapper.findByAnalysisIdAndUserId(normalizedAnalysisId, userId);
        if (analysisOpt.isEmpty() || !StringUtils.hasText(analysisOpt.get().getConversationContent())) {
            return;
        }

        String rawContent = analysisOpt.get().getConversationContent();
        String cleanedTranscript = ConversationTranscriptSupport.extractCleanTranscript(
                rawContent, properties.getAssessmentMaxTranscriptChars());
        List<String> cleanUserUtterances = ConversationTranscriptSupport.extractCleanUserUtterances(rawContent);
        if (!StringUtils.hasText(cleanedTranscript) || cleanUserUtterances.isEmpty()) {
            return;
        }

        LocalDateTime now = LocalDateTime.now(clock);
        String cleanConvId = TextSupport.trimToNull(sourceConversationId);
        Map<Long, PracticeSession> sessionByMemoryId = resolveBoundSessions(userId, cleanConvId);

        int maxTargets = Math.max(1, properties.getAssessmentMaxTargets());
        Map<String, TargetSpec> targetsByPointId = buildTargets(
                userId, normalizedAnalysisId, sessionByMemoryId, maxTargets, now);
        if (targetsByPointId.isEmpty()) {
            return;
        }

        List<MemoryAssessmentLlmClient.AssessmentTarget> llmTargets = targetsByPointId.values().stream()
                .map(t -> new MemoryAssessmentLlmClient.AssessmentTarget(
                        t.memory(), t.session() != null, t.stage2Errors()))
                .toList();

        List<MemoryAssessmentResult.EvaluationDto> rawEvaluations = llmClient.evaluate(
                new MemoryAssessmentLlmClient.AssessmentPromptContext(
                        normalizedAnalysisId, llmTargets, cleanedTranscript));
        if (CollectionUtils.isEmpty(rawEvaluations)) {
            return;
        }

        Runnable applyWork = () -> applyEvaluations(
                userId,
                normalizedAnalysisId,
                cleanConvId,
                targetsByPointId,
                rawEvaluations,
                cleanUserUtterances,
                now);
        if (transactionManager != null) {
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> applyWork.run());
        } else {
            applyWork.run();
        }
    }

    /**
     * 仅匹配显式绑定当前 sourceConversationId 的 IN_PROGRESS 练习会话；
     * 关联缺失时不做时间窗口猜测，避免将无关自由对话误判为练习完成。
     */
    private Map<Long, PracticeSession> resolveBoundSessions(Long userId, String sourceConversationId) {
        if (!StringUtils.hasText(sourceConversationId)) {
            return Map.of();
        }
        List<PracticeSession> matched =
                sessionMapper.findInProgressByUserIdAndSourceConversation(userId, sourceConversationId);
        if (CollectionUtils.isEmpty(matched)) {
            return Map.of();
        }
        Map<Long, PracticeSession> byMemory = new LinkedHashMap<>();
        for (PracticeSession session : matched) {
            if (session.getMemoryId() != null) {
                byMemory.putIfAbsent(session.getMemoryId(), session);
            }
        }
        return byMemory;
    }

    private Map<String, TargetSpec> buildTargets(
            Long userId,
            String analysisId,
            Map<Long, PracticeSession> sessionByMemoryId,
            int maxTargets,
            LocalDateTime now) {
        List<ConversationAnalysisItem> allItems = itemMapper.findByAnalysisId(analysisId);
        Map<String, List<ConversationAnalysisItem>> errorsByPointId = new LinkedHashMap<>();
        if (!CollectionUtils.isEmpty(allItems)) {
            for (ConversationAnalysisItem item : allItems) {
                if (StringUtils.hasText(item.getPointId())) {
                    errorsByPointId.computeIfAbsent(item.getPointId().trim(), k -> new ArrayList<>()).add(item);
                }
            }
        }

        Map<String, TargetSpec> targets = new LinkedHashMap<>();
        Set<Long> includedMemoryIds = new LinkedHashSet<>();

        for (Map.Entry<Long, PracticeSession> entry : sessionByMemoryId.entrySet()) {
            if (targets.size() >= maxTargets) {
                break;
            }
            Optional<WeaknessMemory> memOpt = memoryMapper.findByIdAndUserId(entry.getKey(), userId);
            if (memOpt.isEmpty()) {
                continue;
            }
            WeaknessMemory memory = memOpt.get();
            includedMemoryIds.add(memory.getId());
            targets.put(memory.getPointId(), new TargetSpec(
                    memory,
                    entry.getValue(),
                    errorsByPointId.getOrDefault(memory.getPointId(), List.of())));
        }

        int remaining = maxTargets - targets.size();
        if (remaining > 0) {
            List<WeaknessMemory> extra = priorityService.selectDailyTargets(
                    userId, includedMemoryIds, remaining, now);
            for (WeaknessMemory memory : extra) {
                if (targets.size() >= maxTargets) {
                    break;
                }
                if (!targets.containsKey(memory.getPointId())) {
                    targets.put(memory.getPointId(), new TargetSpec(
                            memory,
                            null,
                            errorsByPointId.getOrDefault(memory.getPointId(), List.of())));
                }
            }
        }
        return targets;
    }

    private void applyEvaluations(
            Long userId,
            String analysisId,
            String sourceConversationId,
            Map<String, TargetSpec> targetsByPointId,
            List<MemoryAssessmentResult.EvaluationDto> rawEvaluations,
            List<String> cleanUserUtterances,
            LocalDateTime now) {
        Set<Long> updatedMemoryIds = new LinkedHashSet<>();

        for (MemoryAssessmentResult.EvaluationDto eval : rawEvaluations) {
            if (eval == null || !StringUtils.hasText(eval.getTargetPointId())) {
                continue;
            }
            TargetSpec target = targetsByPointId.get(eval.getTargetPointId().trim());
            if (target == null) {
                log.debug("assessment ignored unknown targetPointId={} analysisId={}",
                        eval.getTargetPointId(), analysisId);
                continue;
            }

            MemoryAssessmentOutcome outcome = MemoryAssessmentOutcome.fromStringOrNull(eval.getOutcome());
            if (outcome == null) {
                continue;
            }
            String quote = TextSupport.trimToNull(eval.getEvidenceQuote());
            if (outcome != MemoryAssessmentOutcome.NO_OPPORTUNITY
                    && !ConversationTranscriptSupport.isQuoteGroundedInUserSpeech(quote, cleanUserUtterances)) {
                log.warn("assessment downgraded ungrounded quote pointId={} analysisId={}",
                        target.memory().getPointId(), analysisId);
                outcome = MemoryAssessmentOutcome.NO_OPPORTUNITY;
                quote = null;
            }

            WeaknessMemory memory = target.memory();
            PracticeSession session = target.session();

            if (session != null) {
                if (outcome == MemoryAssessmentOutcome.NO_OPPORTUNITY) {
                    session.setStatus(PracticeSessionStatus.COMPLETED.name());
                    session.setAnalysisId(analysisId);
                    session.setCompletedAt(now);
                    sessionMapper.completeIfInProgress(session);
                    continue;
                }
                boolean mutated = applySessionOutcome(
                        userId, analysisId, sourceConversationId, memory, session, outcome, quote, eval, now);
                if (mutated) {
                    updatedMemoryIds.add(memory.getId());
                }
            } else {
                // 自由对话中仅沉淀正向迁移证据（错误已由 Step1 recordAnalysisHits 统一记为 HIT）
                if (outcome == MemoryAssessmentOutcome.TRANSFER_SUCCESS
                        || outcome == MemoryAssessmentOutcome.PROMPTED_CORRECT) {
                    EvidenceType evidenceType = outcome == MemoryAssessmentOutcome.TRANSFER_SUCCESS
                            ? EvidenceType.TRANSFER_SUCCESS
                            : EvidenceType.PROMPTED_CORRECT;
                    BigDecimal conf = scoreToConfidence(eval.getScore());
                    boolean inserted = evidenceService.recordEvidence(
                            memory.getId(),
                            userId,
                            analysisId,
                            null,
                            evidenceType,
                            MemoryEvidenceService.buildAssessmentDedupKey(evidenceType, analysisId, quote),
                            quote,
                            TextSupport.trimToNull(eval.getExpectedResponse()),
                            conf,
                            now);
                    if (inserted) {
                        updatedMemoryIds.add(memory.getId());
                    }
                }
            }
        }

        for (Long memoryId : updatedMemoryIds) {
            memoryMapper.findByIdAndUserId(memoryId, userId)
                    .ifPresent(memory -> masteryService.recalculateAndSave(memory, now));
        }
    }

    private boolean applySessionOutcome(
            Long userId,
            String analysisId,
            String sourceConversationId,
            WeaknessMemory memory,
            PracticeSession session,
            MemoryAssessmentOutcome outcome,
            String quote,
            MemoryAssessmentResult.EvaluationDto eval,
            LocalDateTime now) {
        boolean isCorrect = outcome == MemoryAssessmentOutcome.TRANSFER_SUCCESS
                || outcome == MemoryAssessmentOutcome.PROMPTED_CORRECT;
        boolean independent = outcome == MemoryAssessmentOutcome.TRANSFER_SUCCESS;
        boolean hintUsed = outcome == MemoryAssessmentOutcome.PROMPTED_CORRECT;
        BigDecimal score = clampScore(eval.getScore(), outcome);

        int total = (session.getTotalAttempts() == null ? 0 : session.getTotalAttempts()) + 1;
        int correct = (session.getCorrectAttempts() == null ? 0 : session.getCorrectAttempts()) + (isCorrect ? 1 : 0);
        session.setStatus(PracticeSessionStatus.COMPLETED.name());
        session.setAnalysisId(analysisId);
        if (!StringUtils.hasText(session.getSourceConversationId()) && StringUtils.hasText(sourceConversationId)) {
            session.setSourceConversationId(sourceConversationId);
        }
        session.setTotalAttempts(total);
        session.setCorrectAttempts(correct);
        session.setCompletedAt(now);
        // 原子抢占 IN_PROGRESS → COMPLETED，并发/重放时仅首次写入 Attempt 与 Evidence
        if (sessionMapper.completeIfInProgress(session) != 1) {
            log.debug("assessment skipped already-completed sessionId={} analysisId={}", session.getId(), analysisId);
            return false;
        }

        int nextNo = attemptMapper.maxAttemptNo(session.getId()) + 1;
        PracticeAttempt attempt = PracticeAttempt.builder()
                .sessionId(session.getId())
                .attemptNo(nextNo)
                .userResponse(quote)
                .expectedResponse(TextSupport.trimToNull(eval.getExpectedResponse()))
                .isCorrect(isCorrect)
                .independent(independent)
                .hintUsed(hintUsed)
                .score(score)
                .feedback(TextSupport.trimToNull(eval.getFeedback()))
                .evaluationJson(toJson(eval))
                .createdAt(now)
                .build();
        attemptMapper.insert(attempt);

        EvidenceType evidenceType = switch (outcome) {
            case TRANSFER_SUCCESS -> EvidenceType.TRANSFER_SUCCESS;
            case PROMPTED_CORRECT -> EvidenceType.PROMPTED_CORRECT;
            case PRACTICE_ERROR -> EvidenceType.PRACTICE_ERROR;
            default -> null;
        };
        if (evidenceType != null) {
            evidenceService.recordEvidence(
                    memory.getId(),
                    userId,
                    analysisId,
                    null,
                    evidenceType,
                    MemoryEvidenceService.buildAttemptDedupKey(session.getId(), nextNo),
                    quote,
                    TextSupport.trimToNull(eval.getExpectedResponse()),
                    scoreToConfidence(score.doubleValue()),
                    now);
        }
        return true;
    }

    private static BigDecimal clampScore(Double rawScore, MemoryAssessmentOutcome outcome) {
        double fallback = switch (outcome) {
            case TRANSFER_SUCCESS -> 90.0;
            case PROMPTED_CORRECT -> 70.0;
            case PRACTICE_ERROR -> 20.0;
            case NO_OPPORTUNITY -> 0.0;
        };
        double val = rawScore != null && Double.isFinite(rawScore) ? rawScore : fallback;
        val = Math.max(0.0, Math.min(100.0, val));
        return BigDecimal.valueOf(val).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal scoreToConfidence(Double score) {
        if (score == null || !Double.isFinite(score)) {
            return BigDecimal.valueOf(0.85).setScale(4, RoundingMode.HALF_UP);
        }
        double clamped = Math.max(0.0, Math.min(100.0, score)) / 100.0;
        return BigDecimal.valueOf(clamped).setScale(4, RoundingMode.HALF_UP);
    }

    private String toJson(MemoryAssessmentResult.EvaluationDto eval) {
        try {
            return objectMapper.writeValueAsString(eval);
        } catch (JsonProcessingException ex) {
            return null;
        }
    }

    private record TargetSpec(
            WeaknessMemory memory,
            PracticeSession session,
            List<ConversationAnalysisItem> stage2Errors
    ) {
    }
}
