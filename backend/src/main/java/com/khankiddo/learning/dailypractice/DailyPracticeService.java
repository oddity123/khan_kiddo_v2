package com.khankiddo.learning.dailypractice;

import com.khankiddo.learning.config.WeaknessMemoryProperties;
import com.khankiddo.learning.dto.conversation.ActionCardDto;
import com.khankiddo.learning.dto.conversation.PracticePromptRequest;
import com.khankiddo.learning.dto.conversation.PracticePromptResponse;
import com.khankiddo.learning.dto.dailypractice.DailyPracticeMarkPracticedRequest;
import com.khankiddo.learning.dto.dailypractice.DailyPracticeMarkPracticedResponse;
import com.khankiddo.learning.dto.dailypractice.DailyPracticeRecommendRequest;
import com.khankiddo.learning.dto.dailypractice.DailyPracticeRecommendResponse;
import com.khankiddo.learning.exception.BadRequestException;
import com.khankiddo.learning.growth.GrowthCardAnalysisSupport;
import com.khankiddo.learning.growth.GrowthCardStore;
import com.khankiddo.learning.knowledge.HabitCardScorer;
import com.khankiddo.learning.mapper.ConversationAnalysisItemMapper;
import com.khankiddo.learning.mapper.ConversationAnalysisMapper;
import com.khankiddo.learning.memory.domain.MemoryEvidence;
import com.khankiddo.learning.memory.domain.PracticeSession;
import com.khankiddo.learning.memory.domain.WeaknessMemory;
import com.khankiddo.learning.memory.enums.PracticeType;
import com.khankiddo.learning.memory.mapper.WeaknessMemoryMapper;
import com.khankiddo.learning.memory.service.MemoryEvidenceService;
import com.khankiddo.learning.memory.service.MemoryPracticeService;
import com.khankiddo.learning.memory.service.MemoryPriorityService;
import com.khankiddo.learning.memory.service.MemoryService;
import com.khankiddo.learning.model.ConversationAnalysis;
import com.khankiddo.learning.model.ConversationAnalysisItem;
import com.khankiddo.learning.model.GrowthCard;
import com.khankiddo.learning.service.conversation.PracticePromptService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * 今日练习推荐与练习会话发起编排。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DailyPracticeService {

    static final String EMPTY_FALLBACK_PROMPT = """
            Please help me practice spoken English for a few minutes.
            Ask me one simple question about my day, then briefly correct any unnatural phrasing
            and ask a follow-up. Keep replies short.
            """.trim();

    private final WeaknessMemoryProperties properties;
    private final WeaknessMemoryMapper memoryMapper;
    private final ConversationAnalysisMapper analysisMapper;
    private final ConversationAnalysisItemMapper itemMapper;
    private final GrowthCardStore growthCardStore;
    private final GrowthCardAnalysisSupport analysisSupport;
    private final PracticePromptService practicePromptService;
    private final MemoryService memoryService;
    private final MemoryPriorityService priorityService;
    private final MemoryEvidenceService evidenceService;
    private final MemoryPracticeService practiceService;
    private final Clock clock;

    public DailyPracticeRecommendResponse recommend(Long userId, DailyPracticeRecommendRequest request) {
        Set<Long> excludeMemoryIds = toLongSet(
                request == null ? null : request.getExcludeMemoryIds());
        Set<String> excludeCardIds = toStringSet(
                request == null ? null : request.getExcludeCardIds());

        LocalDateTime now = LocalDateTime.now(clock);
        if (memoryMapper.countAllByUserId(userId) == 0) {
            seedRecentAnalysesIfNeeded(userId);
        }

        int goalLimit = Math.max(1, properties.getRecommendGoalLimit());
        List<WeaknessMemory> selectedMemories =
                priorityService.selectDailyTargets(userId, excludeMemoryIds, goalLimit, now);
        List<DailyPracticeRecommendResponse.GoalSummary> goals = toGoalSummaries(userId, selectedMemories, now);
        List<DailyPracticeRecommendResponse.VocabSummary> vocabulary =
                selectVocabulary(userId, excludeCardIds);

        if (CollectionUtils.isEmpty(goals) && CollectionUtils.isEmpty(vocabulary)
                && (!excludeMemoryIds.isEmpty() || !excludeCardIds.isEmpty())) {
            selectedMemories = priorityService.selectDailyTargets(userId, Set.of(), goalLimit, now);
            goals = toGoalSummaries(userId, selectedMemories, now);
            vocabulary = selectVocabulary(userId, Set.of());
        }

        if (CollectionUtils.isEmpty(goals) && CollectionUtils.isEmpty(vocabulary)) {
            return buildFromRecentAnalysisOrEmpty(userId, excludeCardIds);
        }

        return assembleResponse(goals, vocabulary);
    }

    @Transactional
    public DailyPracticeMarkPracticedResponse markPracticed(
            Long userId,
            DailyPracticeMarkPracticedRequest request) {
        if (ObjectUtils.isEmpty(request) || CollectionUtils.isEmpty(request.getMemoryIds())) {
            return DailyPracticeMarkPracticedResponse.builder().build();
        }
        PracticeType practiceType;
        try {
            practiceType = PracticeType.fromStringOrDefault(request.getPracticeType(), PracticeType.SCENARIO);
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("不支持的练习类型: " + request.getPracticeType());
        }
        List<PracticeSession> sessions = practiceService.startSessions(
                userId, request.getMemoryIds(), practiceType, request.getSourceConversationId());
        List<Long> sessionIds = sessions.stream()
                .map(PracticeSession::getId)
                .filter(ObjectUtils::isNotEmpty)
                .toList();
        return DailyPracticeMarkPracticedResponse.builder()
                .sessionIds(sessionIds)
                .build();
    }

    private void seedRecentAnalysesIfNeeded(Long userId) {
        if (!properties.isEnabled()) {
            return;
        }
        List<ConversationAnalysis> recent = analysisMapper.findByUserId(userId, 3, 0);
        if (CollectionUtils.isEmpty(recent)) {
            return;
        }
        for (ConversationAnalysis analysis : recent) {
            if (StringUtils.hasText(analysis.getAnalysisId())) {
                try {
                    memoryService.recordAnalysisHits(userId, analysis.getAnalysisId());
                } catch (Exception ex) {
                    log.warn("daily practice deterministic seed failed userId={} analysisId={}: {}",
                            userId, analysis.getAnalysisId(), ex.getMessage());
                }
            }
        }
    }

    private DailyPracticeRecommendResponse buildFromRecentAnalysisOrEmpty(
            Long userId,
            Set<String> excludeCardIds) {
        List<DailyPracticeRecommendResponse.VocabSummary> vocabulary =
                selectVocabulary(userId, excludeCardIds);
        List<DailyPracticeRecommendResponse.GoalSummary> goals = List.of();

        List<ConversationAnalysis> recent = analysisMapper.findByUserId(userId, 1, 0);
        if (!CollectionUtils.isEmpty(recent)) {
            ConversationAnalysis latest = recent.get(0);
            List<ConversationAnalysisItem> rows = itemMapper.findByAnalysisId(latest.getAnalysisId());
            HabitCardScorer.HabitScoreResult score = analysisSupport.score(
                    CollectionUtils.isEmpty(rows) ? List.of() : rows);
            if (!CollectionUtils.isEmpty(score.actionCards())) {
                goals = actionCardsToGoals(score.actionCards());
            }
        }

        if (CollectionUtils.isEmpty(goals) && CollectionUtils.isEmpty(vocabulary)) {
            return DailyPracticeRecommendResponse.builder()
                    .empty(true)
                    .summary(MemoryPriorityService.buildSummary(0, null, 0))
                    .prompt(EMPTY_FALLBACK_PROMPT)
                    .goals(List.of())
                    .vocabulary(List.of())
                    .build();
        }
        return assembleResponse(goals, vocabulary);
    }

    private List<DailyPracticeRecommendResponse.GoalSummary> toGoalSummaries(
            Long userId,
            List<WeaknessMemory> memories,
            LocalDateTime now) {
        if (CollectionUtils.isEmpty(memories)) {
            return List.of();
        }
        List<DailyPracticeRecommendResponse.GoalSummary> goals = new ArrayList<>(memories.size());
        Set<String> usedNormalizedOriginals = new HashSet<>();
        int poolSize = memories.size();
        for (WeaknessMemory memory : memories) {
            Optional<MemoryEvidence> evidenceOpt =
                    evidenceService.findDistinctHitOrEvidence(memory.getId(), usedNormalizedOriginals);
            String original = evidenceOpt.map(MemoryEvidence::getOriginalText).orElse(null);
            String rewrite = evidenceOpt.map(MemoryEvidence::getCorrectedText).orElse(null);
            if (!StringUtils.hasText(original) && StringUtils.hasText(memory.getPointId())) {
                List<ConversationAnalysisItem> rows = itemMapper.findErrorExamplesByUserId(
                        userId, List.of(memory.getPointId().trim()), null, 5, null);
                if (!CollectionUtils.isEmpty(rows)) {
                    ConversationAnalysisItem picked = rows.stream()
                            .filter(r -> StringUtils.hasText(r.getOriginalSentence())
                                    && !usedNormalizedOriginals.contains(
                                            r.getOriginalSentence().trim().toLowerCase(Locale.ROOT)))
                            .findFirst()
                            .orElse(rows.get(0));
                    original = picked.getOriginalSentence();
                    rewrite = picked.getSuggestion();
                }
            }
            if (StringUtils.hasText(original)) {
                usedNormalizedOriginals.add(original.trim().toLowerCase(Locale.ROOT));
            }
            boolean cooling = priorityService.isCoolingDown(memory, now);
            double score = priorityService.computePriorityScore(memory, now, cooling, poolSize);
            goals.add(DailyPracticeRecommendResponse.GoalSummary.builder()
                    .memoryId(memory.getId())
                    .title(memory.getTitle())
                    .pointId(memory.getPointId())
                    .familyId(memory.getFamilyId())
                    .memoryType(memory.getMemoryType())
                    .status(memory.getStatus())
                    .masteryLevel(memory.getMasteryLevel())
                    .priorityScore(score)
                    .evidenceOriginal(original)
                    .evidenceRewrite(rewrite)
                    .build());
        }
        return goals;
    }

    private List<DailyPracticeRecommendResponse.VocabSummary> selectVocabulary(
            Long userId,
            Set<String> excludeCardIds) {
        int limit = Math.max(1, properties.getRecommendVocabLimit());
        List<GrowthCard> due = growthCardStore.listDue(userId, LocalDate.now(clock));
        if (CollectionUtils.isEmpty(due)) {
            return List.of();
        }
        List<DailyPracticeRecommendResponse.VocabSummary> selected = new ArrayList<>();
        for (GrowthCard card : due) {
            if (selected.size() >= limit) {
                break;
            }
            if (!isVocabOrExpression(card.getType())) {
                continue;
            }
            if (StringUtils.hasText(card.getCardId()) && excludeCardIds.contains(card.getCardId())) {
                continue;
            }
            selected.add(DailyPracticeRecommendResponse.VocabSummary.builder()
                    .cardId(card.getCardId())
                    .type(card.getType())
                    .front(card.getFront())
                    .back(card.getBack())
                    .build());
        }
        return selected;
    }

    private DailyPracticeRecommendResponse assembleResponse(
            List<DailyPracticeRecommendResponse.GoalSummary> goals,
            List<DailyPracticeRecommendResponse.VocabSummary> vocabulary) {
        String firstTitle = CollectionUtils.isEmpty(goals) ? null : goals.get(0).getTitle();
        String summary = MemoryPriorityService.buildSummary(
                goals.size(), firstTitle, vocabulary.size());
        String prompt = buildPrompt(goals, vocabulary);
        return DailyPracticeRecommendResponse.builder()
                .empty(false)
                .summary(summary)
                .prompt(prompt)
                .goals(goals)
                .vocabulary(vocabulary)
                .build();
    }

    private String buildPrompt(
            List<DailyPracticeRecommendResponse.GoalSummary> goals,
            List<DailyPracticeRecommendResponse.VocabSummary> vocabulary) {
        if (CollectionUtils.isEmpty(goals) && CollectionUtils.isEmpty(vocabulary)) {
            return EMPTY_FALLBACK_PROMPT;
        }
        List<PracticePromptRequest.Goal> promptGoals = new ArrayList<>();
        int rank = 1;
        for (DailyPracticeRecommendResponse.GoalSummary goal : goals) {
            promptGoals.add(PracticePromptRequest.Goal.builder()
                    .rank(rank++)
                    .title(goal.getTitle())
                    .originalSentence(goal.getEvidenceOriginal())
                    .targetSentence(goal.getEvidenceRewrite())
                    .build());
        }
        List<PracticePromptRequest.Vocabulary> promptVocab = new ArrayList<>();
        for (DailyPracticeRecommendResponse.VocabSummary card : vocabulary) {
            promptVocab.add(PracticePromptRequest.Vocabulary.builder()
                    .front(card.getFront())
                    .back(card.getBack())
                    .build());
        }
        PracticePromptResponse assembled = practicePromptService.assemble(
                PracticePromptRequest.builder()
                        .goals(promptGoals)
                        .vocabulary(promptVocab)
                        .build());
        return assembled.getPrompt();
    }

    private static List<DailyPracticeRecommendResponse.GoalSummary> actionCardsToGoals(
            List<ActionCardDto> actionCards) {
        int limit = Math.min(2, actionCards.size());
        List<DailyPracticeRecommendResponse.GoalSummary> goals = new ArrayList<>();
        for (int i = 0; i < limit; i++) {
            ActionCardDto card = actionCards.get(i);
            String evidenceOriginal = null;
            String evidenceRewrite = null;
            if (!CollectionUtils.isEmpty(card.getExamples())) {
                ActionCardDto.ExampleDto ex = card.getExamples().get(0);
                evidenceOriginal = ex.getOriginalSentence();
                evidenceRewrite = ex.getSuggestion();
            }
            goals.add(DailyPracticeRecommendResponse.GoalSummary.builder()
                    .memoryId(null)
                    .title(StringUtils.hasText(card.getTitleZh()) ? card.getTitleZh() : card.getHeadlineZh())
                    .pointId(card.getPointId())
                    .evidenceOriginal(evidenceOriginal)
                    .evidenceRewrite(evidenceRewrite)
                    .build());
        }
        return goals;
    }

    private static boolean isVocabOrExpression(String type) {
        if (!StringUtils.hasText(type)) {
            return false;
        }
        String normalized = type.trim().toLowerCase(Locale.ROOT);
        return "vocab".equals(normalized) || "expression".equals(normalized);
    }

    private static Set<Long> toLongSet(List<Long> ids) {
        if (CollectionUtils.isEmpty(ids)) {
            return Set.of();
        }
        Set<Long> set = new HashSet<>();
        for (Long id : ids) {
            if (ObjectUtils.isNotEmpty(id)) {
                set.add(id);
            }
        }
        return set;
    }

    private static Set<String> toStringSet(List<String> ids) {
        if (CollectionUtils.isEmpty(ids)) {
            return Set.of();
        }
        Set<String> set = new HashSet<>();
        for (String id : ids) {
            if (StringUtils.hasText(id)) {
                set.add(id.trim());
            }
        }
        return set;
    }
}
