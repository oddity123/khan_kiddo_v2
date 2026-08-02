package com.khankiddo.learning.knowledge;

import com.khankiddo.learning.dto.conversation.ActionCardDto;
import com.khankiddo.learning.dto.conversation.ChineseExpressionDto;
import com.khankiddo.learning.dto.conversation.FamilyDistributionDto;
import com.khankiddo.learning.dto.conversation.PracticePromptDto;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 跨通道打分：语法家族 / 流利度策略 / 词汇缺口 / 中文夹杂竞争 Top1-3「本次最该改的说话习惯」。
 * 算法锁定见 docs/superpowers/specs/2026-08-01-conversation-analysis-action-cards-design.md §4。
 */
public class HabitCardScorer {

    private static final String CHINESE_POINT_ID = "CHINESE_CODE_SWITCH";
    private static final String CHINESE_INJECT_SEVERITY = "BASIC";
    private static final String HEADLINE_PREFIX = "本次最该改：";
    private static final int MIN_HIT_COUNT = 2;
    private static final int MAX_EXAMPLES = 5;
    private static final int MAX_ACTION_CARDS = 3;
    private static final double DEFAULT_RULE_FIXABILITY = 0.5;
    private static final double DEFAULT_SEVERITY_WEIGHT = 2.0;

    private final PointDictionary dictionary;

    public HabitCardScorer(PointDictionary dictionary) {
        this.dictionary = dictionary;
    }

    public HabitScoreResult score(HabitScoreInput input) {
        List<ResolvedHit> allHits = resolveHits(input);

        List<FamilyDistributionDto> familyDistribution = buildFamilyDistribution(allHits);
        List<ActionCardDto> actionCards = buildActionCards(groupCandidates(allHits));
        ActionCardDto topHabit = actionCards.isEmpty() ? null : actionCards.get(0);

        return new HabitScoreResult(topHabit, actionCards, familyDistribution);
    }

    private List<ResolvedHit> resolveHits(HabitScoreInput input) {
        List<ResolvedHit> resolved = new ArrayList<>();

        List<HabitScoreInput.ErrorHit> errorHits = input.errorHits();
        if (!CollectionUtils.isEmpty(errorHits)) {
            for (HabitScoreInput.ErrorHit hit : errorHits) {
                PointDefinition point = dictionary.resolveOrFallback(hit.pointId());
                String errorLevel = StringUtils.hasText(hit.errorLevel()) ? hit.errorLevel() : point.errorLevel();
                resolved.add(new ResolvedHit(point, hit.originalSentence(), hit.errorPoint(), hit.suggestion(), errorLevel));
            }
        }

        List<ChineseExpressionDto> chineseExpressions = input.chineseExpressions();
        if (!CollectionUtils.isEmpty(chineseExpressions)) {
            PointDefinition chinesePoint = dictionary.resolveOrFallback(CHINESE_POINT_ID);
            for (ChineseExpressionDto expression : chineseExpressions) {
                String focusPhrase = StringUtils.hasText(expression.getFocusPhrase())
                        ? expression.getFocusPhrase()
                        : expression.getOriginalSentence();
                resolved.add(new ResolvedHit(
                        chinesePoint,
                        expression.getOriginalSentence(),
                        focusPhrase,
                        expression.getSuggestion(),
                        CHINESE_INJECT_SEVERITY));
            }
        }

        return resolved;
    }

    private List<FamilyDistributionDto> buildFamilyDistribution(List<ResolvedHit> allHits) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (ResolvedHit hit : allHits) {
            counts.merge(hit.point().familyId(), 1, Integer::sum);
        }

        List<FamilyDistributionDto> distribution = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            FamilyDefinition family = dictionary.familiesById().get(entry.getKey());
            distribution.add(FamilyDistributionDto.builder()
                    .familyId(entry.getKey())
                    .titleZh(family != null ? family.titleZh() : null)
                    .channel(family != null ? family.channel() : null)
                    .count(entry.getValue())
                    .build());
        }
        return distribution;
    }

    private Map<String, List<ResolvedHit>> groupCandidates(List<ResolvedHit> allHits) {
        Map<String, List<ResolvedHit>> groups = new LinkedHashMap<>();
        for (ResolvedHit hit : allHits) {
            PointDefinition point = hit.point();
            if (point.cardPolicy() == CardPolicy.RARE) {
                continue;
            }
            groups.computeIfAbsent(habitKey(point), key -> new ArrayList<>()).add(hit);
        }
        return groups;
    }

    private String habitKey(PointDefinition point) {
        return switch (point.habitUnit()) {
            case FAMILY -> point.familyId();
            case LEAF -> point.pointId();
            case CHANNEL -> point.channel().name();
        };
    }

    private List<ActionCardDto> buildActionCards(Map<String, List<ResolvedHit>> groups) {
        List<ActionCardDto> candidates = new ArrayList<>();
        for (Map.Entry<String, List<ResolvedHit>> entry : groups.entrySet()) {
            if (entry.getValue().size() < MIN_HIT_COUNT) {
                continue;
            }
            candidates.add(buildCard(entry.getKey(), entry.getValue()));
        }

        candidates.sort(Comparator.comparingDouble(ActionCardDto::getScore).reversed());

        List<ActionCardDto> top = candidates.size() > MAX_ACTION_CARDS
                ? new ArrayList<>(candidates.subList(0, MAX_ACTION_CARDS))
                : candidates;

        for (int i = 0; i < top.size(); i++) {
            ActionCardDto card = top.get(i);
            card.setRank(i + 1);
            if (card.getRank() == 1) {
                card.setHeadlineZh(HEADLINE_PREFIX + card.getHeadlineZh());
            }
        }
        return top;
    }

    private ActionCardDto buildCard(String habitKey, List<ResolvedHit> hits) {
        Map<String, Double> scoreByPoint = new LinkedHashMap<>();
        Map<String, Integer> countByPoint = new LinkedHashMap<>();
        double totalScore = 0.0;

        for (ResolvedHit hit : hits) {
            PointDefinition point = hit.point();
            double contribution = severityWeight(hit.errorLevel()) * point.impactWeight() * fixabilityPrime(point);
            totalScore += contribution;
            scoreByPoint.merge(point.pointId(), contribution, Double::sum);
            countByPoint.merge(point.pointId(), 1, Integer::sum);
        }

        String topPointId = scoreByPoint.entrySet().stream()
                .max(Comparator.<Map.Entry<String, Double>>comparingDouble(Map.Entry::getValue)
                        .thenComparingInt(entry -> countByPoint.getOrDefault(entry.getKey(), 0)))
                .map(Map.Entry::getKey)
                .orElseThrow();
        PointDefinition topPoint = dictionary.require(topPointId);

        List<ActionCardDto.ExampleDto> examples = new ArrayList<>();
        for (ResolvedHit hit : hits) {
            if (examples.size() >= MAX_EXAMPLES) {
                break;
            }
            examples.add(ActionCardDto.ExampleDto.builder()
                    .originalSentence(hit.originalSentence())
                    .errorPoint(hit.errorPoint())
                    .suggestion(hit.suggestion())
                    .build());
        }

        Map<String, Integer> siblingPoints = null;
        if (topPoint.habitUnit() == HabitUnit.FAMILY) {
            siblingPoints = new LinkedHashMap<>();
            for (Map.Entry<String, Integer> entry : countByPoint.entrySet()) {
                if (!entry.getKey().equals(topPointId)) {
                    siblingPoints.put(entry.getKey(), entry.getValue());
                }
            }
        }

        ResolvedHit firstEvidence = hits.get(0);
        PracticePromptDto practicePrompt = PracticePromptDto.builder()
                .originalSentence(firstEvidence.originalSentence())
                .targetSentence(resolveTargetSentence(firstEvidence))
                .coachingZh(topPoint.actionHintZh())
                .build();

        return ActionCardDto.builder()
                .channel(topPoint.channel())
                .cardKind(topPoint.cardKind())
                .cardPolicy(topPoint.cardPolicy())
                .habitKey(habitKey)
                .pointId(topPointId)
                .headlineZh(topPoint.topTitleZh())
                .titleZh(topPoint.titleZh())
                .whyZh(topPoint.whyZh())
                .errorCount(hits.size())
                .score(totalScore)
                .examples(examples)
                .siblingPoints(siblingPoints)
                .actionHintZh(topPoint.actionHintZh())
                .practicePrompt(practicePrompt)
                .build();
    }

    private String resolveTargetSentence(ResolvedHit hit) {
        if (StringUtils.hasText(hit.suggestion())) {
            return hit.suggestion();
        }
        String errorPoint = hit.errorPoint();
        if (StringUtils.hasText(errorPoint) && errorPoint.contains("→")) {
            String[] parts = errorPoint.split("→", 2);
            return parts[1].trim();
        }
        return errorPoint;
    }

    private static double severityWeight(String errorLevel) {
        if (!StringUtils.hasText(errorLevel)) {
            return DEFAULT_SEVERITY_WEIGHT;
        }
        return switch (errorLevel.trim().toUpperCase()) {
            case "FATAL" -> 3.0;
            case "BASIC" -> 2.0;
            case "NATURAL" -> 1.5;
            case "STYLE" -> 1.0;
            default -> DEFAULT_SEVERITY_WEIGHT;
        };
    }

    private double fixabilityPrime(PointDefinition point) {
        if (point.channel() != PointChannel.RULE) {
            return 1.0;
        }
        if (point.fixability() != null) {
            return point.fixability();
        }
        FamilyDefinition family = dictionary.familiesById().get(point.familyId());
        if (family != null && family.fixability() != null) {
            return family.fixability();
        }
        return DEFAULT_RULE_FIXABILITY;
    }

    private record ResolvedHit(
            PointDefinition point,
            String originalSentence,
            String errorPoint,
            String suggestion,
            String errorLevel
    ) {
    }

    /**
     * 打分结果：{@code topHabit} 即 rank1（若无候选，则为 {@code null}，{@code actionCards} 为空）。
     */
    public record HabitScoreResult(
            ActionCardDto topHabit,
            List<ActionCardDto> actionCards,
            List<FamilyDistributionDto> familyDistribution
    ) {
    }
}
