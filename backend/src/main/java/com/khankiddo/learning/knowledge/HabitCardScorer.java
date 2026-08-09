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
 * 算法锁定见 docs/achieved/conversation-analysis-action-cards/2026-08-01-design.md §4。
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
                resolved.add(new ResolvedHit(
                        point,
                        hit.sentenceId(),
                        hit.originalSentence(),
                        hit.errorPoint(),
                        hit.suggestion(),
                        errorLevel));
            }
        }

        List<ChineseExpressionDto> chineseExpressions = input.chineseExpressions();
        if (!CollectionUtils.isEmpty(chineseExpressions)) {
            PointDefinition chinesePoint = dictionary.resolveOrFallback(CHINESE_POINT_ID);
            for (ChineseExpressionDto expression : chineseExpressions) {
                // 词汇求助（有 focusPhrase）不参与习惯打分 / Top3；仅内容表达计入
                if (StringUtils.hasText(expression.getFocusPhrase())) {
                    continue;
                }
                String sentenceId = expression.getOriginalIndex() != null
                        ? String.valueOf(expression.getOriginalIndex())
                        : null;
                resolved.add(new ResolvedHit(
                        chinesePoint,
                        sentenceId,
                        expression.getOriginalSentence(),
                        expression.getOriginalSentence(),
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

        String tipPointId = pickHighestScoringPointId(scoreByPoint, countByPoint, true);
        PointDefinition tipPoint = dictionary.require(tipPointId);

        CardCopy copy = resolveCardCopy(tipPoint);

        List<ResolvedHit> orderedHits = orderHitsForDisplay(hits, tipPointId);
        List<ActionCardDto.ExampleDto> examples = new ArrayList<>();
        for (ResolvedHit hit : orderedHits) {
            if (examples.size() >= MAX_EXAMPLES) {
                break;
            }
            examples.add(ActionCardDto.ExampleDto.builder()
                    .sentenceId(hit.sentenceId())
                    .originalSentence(hit.originalSentence())
                    .errorPoint(hit.errorPoint())
                    .suggestion(hit.suggestion())
                    .build());
        }

        List<ActionCardDto.SiblingPointDto> siblingPoints = null;
        if (tipPoint.habitUnit() == HabitUnit.FAMILY) {
            siblingPoints = new ArrayList<>();
            for (Map.Entry<String, Integer> entry : countByPoint.entrySet()) {
                if (entry.getKey().equals(tipPointId)) {
                    continue;
                }
                PointDefinition sibling = dictionary.require(entry.getKey());
                siblingPoints.add(ActionCardDto.SiblingPointDto.builder()
                        .pointId(entry.getKey())
                        .titleZh(sibling.titleZh())
                        .errorCount(entry.getValue())
                        .build());
            }
        }

        ResolvedHit firstEvidence = orderedHits.get(0);
        PracticePromptDto practicePrompt = PracticePromptDto.builder()
                .originalSentence(firstEvidence.originalSentence())
                .targetSentence(resolveTargetSentence(firstEvidence))
                .coachingZh(tipPoint.actionHintZh())
                .build();

        return ActionCardDto.builder()
                .channel(tipPoint.channel())
                .cardKind(tipPoint.cardKind())
                .cardPolicy(tipPoint.cardPolicy())
                .habitKey(habitKey)
                .pointId(tipPointId)
                .headlineZh(copy.headlineZh())
                .titleZh(copy.titleZh())
                .whyZh(copy.whyZh())
                .errorCount(hits.size())
                .score(totalScore)
                .examples(examples)
                .siblingPoints(siblingPoints)
                .actionHintZh(tipPoint.actionHintZh())
                .practicePrompt(practicePrompt)
                .build();
    }

    /**
     * 选代表叶子：优先非兜底（非 {@code *_OTHER}、非家族 otherPointId、非 WORD_FORM_POS）；
     * 若全是兜底则退回分数最高者。
     */
    private String pickHighestScoringPointId(
            Map<String, Double> scoreByPoint,
            Map<String, Integer> countByPoint,
            boolean preferNonCatchAll) {
        Comparator<Map.Entry<String, Double>> byScoreThenCount =
                Comparator.<Map.Entry<String, Double>>comparingDouble(Map.Entry::getValue)
                        .thenComparingInt(entry -> countByPoint.getOrDefault(entry.getKey(), 0));

        if (preferNonCatchAll) {
            return scoreByPoint.entrySet().stream()
                    .filter(entry -> !isCatchAllLeaf(dictionary.require(entry.getKey())))
                    .max(byScoreThenCount)
                    .map(Map.Entry::getKey)
                    .orElseGet(() -> pickHighestScoringPointId(scoreByPoint, countByPoint, false));
        }
        return scoreByPoint.entrySet().stream()
                .max(byScoreThenCount)
                .map(Map.Entry::getKey)
                .orElseThrow();
    }

    private boolean isCatchAllLeaf(PointDefinition point) {
        if ("WORD_FORM_POS".equals(point.pointId()) || point.pointId().endsWith("_OTHER")) {
            return true;
        }
        FamilyDefinition family = dictionary.familiesById().get(point.familyId());
        return family != null
                && StringUtils.hasText(family.otherPointId())
                && family.otherPointId().equals(point.pointId());
    }

    /**
     * 家族卡：大标题始终用家族名（习惯粒度）；有可教细叶子时副文写「其中可先练」。
     * 非家族卡（leaf/channel）仍用代表点自身文案。
     */
    private CardCopy resolveCardCopy(PointDefinition tipPoint) {
        if (tipPoint.habitUnit() != HabitUnit.FAMILY) {
            return new CardCopy(tipPoint.topTitleZh(), tipPoint.titleZh(), tipPoint.whyZh());
        }
        FamilyDefinition family = dictionary.familiesById().get(tipPoint.familyId());
        if (family == null) {
            return new CardCopy(tipPoint.topTitleZh(), tipPoint.titleZh(), tipPoint.whyZh());
        }

        String familyHeadline = family.titleZh() + "容易用错";
        if (isCatchAllLeaf(tipPoint)) {
            return new CardCopy(familyHeadline, family.titleZh(), tipPoint.whyZh());
        }
        return new CardCopy(
                familyHeadline,
                family.titleZh(),
                "其中可先练：" + tipPoint.titleZh() + "。" + tipPoint.whyZh());
    }

    private static List<ResolvedHit> orderHitsForDisplay(List<ResolvedHit> hits, String tipPointId) {
        List<ResolvedHit> tipFirst = new ArrayList<>();
        List<ResolvedHit> rest = new ArrayList<>();
        for (ResolvedHit hit : hits) {
            if (tipPointId.equals(hit.point().pointId())) {
                tipFirst.add(hit);
            } else {
                rest.add(hit);
            }
        }
        tipFirst.addAll(rest);
        return tipFirst;
    }

    private record CardCopy(String headlineZh, String titleZh, String whyZh) {
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
            String sentenceId,
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
