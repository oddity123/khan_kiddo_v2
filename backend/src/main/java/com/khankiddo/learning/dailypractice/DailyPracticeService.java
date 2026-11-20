package com.khankiddo.learning.dailypractice;

import com.khankiddo.learning.config.WeaknessMemoryProperties;
import com.khankiddo.learning.dto.conversation.ActionCardDto;
import com.khankiddo.learning.dto.conversation.PracticePromptRequest;
import com.khankiddo.learning.dto.conversation.PracticePromptResponse;
import com.khankiddo.learning.dto.dailypractice.DailyPracticeMarkPracticedRequest;
import com.khankiddo.learning.dto.dailypractice.DailyPracticeRecommendRequest;
import com.khankiddo.learning.dto.dailypractice.DailyPracticeRecommendResponse;
import com.khankiddo.learning.growth.GrowthCardAnalysisSupport;
import com.khankiddo.learning.growth.GrowthCardStore;
import com.khankiddo.learning.knowledge.HabitCardScorer;
import com.khankiddo.learning.mapper.ConversationAnalysisItemMapper;
import com.khankiddo.learning.mapper.ConversationAnalysisMapper;
import com.khankiddo.learning.mapper.UserWeaknessMemoryMapper;
import com.khankiddo.learning.model.ConversationAnalysis;
import com.khankiddo.learning.model.ConversationAnalysisItem;
import com.khankiddo.learning.model.GrowthCard;
import com.khankiddo.learning.model.UserWeaknessMemory;
import com.khankiddo.learning.service.conversation.PracticePromptService;
import com.khankiddo.learning.weaknessmemory.WeaknessMemoryConsolidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

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
    private final UserWeaknessMemoryMapper memoryMapper;
    private final ConversationAnalysisMapper analysisMapper;
    private final ConversationAnalysisItemMapper itemMapper;
    private final GrowthCardStore growthCardStore;
    private final GrowthCardAnalysisSupport analysisSupport;
    private final PracticePromptService practicePromptService;
    private final WeaknessMemoryConsolidator consolidator;
    private final WeaknessEvidenceSupport evidenceSupport;

    public DailyPracticeRecommendResponse recommend(Long userId, DailyPracticeRecommendRequest request) {
        Set<Long> excludeMemoryIds = toLongSet(
                request == null ? null : request.getExcludeMemoryIds());
        Set<String> excludeCardIds = toStringSet(
                request == null ? null : request.getExcludeCardIds());

        List<UserWeaknessMemory> active = memoryMapper.findActiveByUserId(userId);
        if (CollectionUtils.isEmpty(active)) {
            maybeTriggerColdStart(userId);
            return buildFromColdStartOrEmpty(userId, excludeCardIds);
        }

        List<DailyPracticeRecommendResponse.GoalSummary> goals =
                selectGoals(userId, active, excludeMemoryIds, LocalDateTime.now());
        List<DailyPracticeRecommendResponse.VocabSummary> vocabulary =
                selectVocabulary(userId, excludeCardIds);

        if (CollectionUtils.isEmpty(goals) && CollectionUtils.isEmpty(vocabulary)
                && (!excludeMemoryIds.isEmpty() || !excludeCardIds.isEmpty())) {
            // 换一组把池子排空时回退到不排除，避免弹窗空白
            goals = selectGoals(userId, active, Set.of(), LocalDateTime.now());
            vocabulary = selectVocabulary(userId, Set.of());
        }

        return assembleResponse(goals, vocabulary);
    }

    @Transactional
    public void markPracticed(Long userId, DailyPracticeMarkPracticedRequest request) {
        if (ObjectUtils.isEmpty(request) || CollectionUtils.isEmpty(request.getMemoryIds())) {
            return;
        }
        List<Long> ids = request.getMemoryIds().stream()
                .filter(ObjectUtils::isNotEmpty)
                .distinct()
                .toList();
        if (CollectionUtils.isEmpty(ids)) {
            return;
        }
        memoryMapper.markPracticed(userId, ids, LocalDateTime.now());
    }

    private DailyPracticeRecommendResponse buildFromColdStartOrEmpty(
            Long userId, Set<String> excludeCardIds) {
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
                    .summary(DailyPracticeRankingSupport.buildSummary(0, null, 0))
                    .prompt(EMPTY_FALLBACK_PROMPT)
                    .goals(List.of())
                    .vocabulary(List.of())
                    .build();
        }
        return assembleResponse(goals, vocabulary);
    }

    private void maybeTriggerColdStart(Long userId) {
        if (!properties.isEnabled()) {
            return;
        }
        List<ConversationAnalysis> recent = analysisMapper.findByUserId(userId, 1, 0);
        if (CollectionUtils.isEmpty(recent)) {
            return;
        }
        String analysisId = recent.get(0).getAnalysisId();
        if (!StringUtils.hasText(analysisId)) {
            return;
        }
        Thread.startVirtualThread(() -> {
            try {
                consolidator.consolidateAfterAnalysis(userId, analysisId);
            } catch (Exception ex) {
                log.warn("daily practice cold-start seed failed userId={} analysisId={}: {}",
                        userId, analysisId, ex.getMessage());
            }
        });
    }

    private List<DailyPracticeRecommendResponse.GoalSummary> selectGoals(
            Long userId,
            List<UserWeaknessMemory> active,
            Set<Long> excludeMemoryIds,
            LocalDateTime now) {
        int limit = Math.max(1, properties.getRecommendGoalLimit());
        int activeCount = active.size();
        List<ScoredMemory> scored = new ArrayList<>();
        for (UserWeaknessMemory memory : active) {
            if (memory.getId() != null && excludeMemoryIds.contains(memory.getId())) {
                continue;
            }
            long analysesSince = 0L;
            if (memory.getLastPracticedAt() != null) {
                analysesSince = analysisMapper.countByUserIdAndStatusCreatedAfter(
                        memory.getUserId(), "success", memory.getLastPracticedAt());
            }
            boolean cooling = DailyPracticeRankingSupport.isCoolingDown(
                    memory,
                    now,
                    properties.getCooldownHours(),
                    properties.getCooldownAnalyses(),
                    analysesSince);
            double score = DailyPracticeRankingSupport.priorityScore(
                    memory,
                    now,
                    properties.getDecayHalfLifeDays(),
                    cooling,
                    activeCount);
            scored.add(new ScoredMemory(memory, score));
        }
        scored.sort(Comparator.comparingDouble(ScoredMemory::score).reversed()
                .thenComparing(s -> s.memory().getId(), Comparator.nullsLast(Long::compareTo)));

        List<DailyPracticeRecommendResponse.GoalSummary> selected = new ArrayList<>();
        for (ScoredMemory item : scored) {
            if (selected.size() >= limit) {
                break;
            }
            selected.add(toGoalSummary(userId, item.memory()));
        }
        return selected;
    }

    private List<DailyPracticeRecommendResponse.VocabSummary> selectVocabulary(
            Long userId, Set<String> excludeCardIds) {
        int limit = Math.max(1, properties.getRecommendVocabLimit());
        List<GrowthCard> due = growthCardStore.listDue(userId, LocalDate.now());
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
        String summary = DailyPracticeRankingSupport.buildSummary(
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
                    .anchorHabitKey(card.getHabitKey())
                    .anchorPointId(card.getPointId())
                    .evidenceOriginal(evidenceOriginal)
                    .evidenceRewrite(evidenceRewrite)
                    .build());
        }
        return goals;
    }

    private DailyPracticeRecommendResponse.GoalSummary toGoalSummary(Long userId, UserWeaknessMemory memory) {
        WeaknessEvidenceSupport.EvidenceLine evidence = evidenceSupport.findLatestEvidence(userId, memory);
        return DailyPracticeRecommendResponse.GoalSummary.builder()
                .memoryId(memory.getId())
                .title(memory.getTitle())
                .anchorHabitKey(memory.getAnchorHabitKey())
                .anchorPointId(memory.getAnchorPointId())
                .evidenceOriginal(evidence.originalSentence())
                .evidenceRewrite(evidence.targetSentence())
                .build();
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

    private record ScoredMemory(UserWeaknessMemory memory, double score) {
    }
}
