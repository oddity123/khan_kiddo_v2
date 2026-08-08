package com.khankiddo.learning.eval;

import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Stage2 黄金集比对内核（纯函数，不依赖 LLM / Spring 运行时）。
 */
public final class GoldenStatistics {

    public record InputUtterance(String id, String text) {
    }

    public record GoldError(String errorId, String primaryPointId, List<String> acceptablePointIds) {
        public GoldError {
            acceptablePointIds = acceptablePointIds == null ? List.of() : List.copyOf(acceptablePointIds);
        }
    }

    public record GoldUtterance(String id, boolean expectError, List<GoldError> errors) {
        public GoldUtterance {
            errors = errors == null ? List.of() : List.copyOf(errors);
        }
    }

    /** Stage2 单条错误：pointId + LLM 写出的 point 解释文案。 */
    public record ActualError(String pointId, String point) {
        public ActualError {
            pointId = pointId == null ? "" : pointId;
            point = point == null ? "" : point;
        }
    }

    /**
     * Stage2 检出句。指标只用 {@link #pointIds()}；{@code suggestion}/{@code point} 供报告明细。
     */
    public record ActualItem(String originalSentence, String suggestion, List<ActualError> errors) {
        public ActualItem {
            suggestion = suggestion == null ? "" : suggestion;
            errors = errors == null ? List.of() : List.copyOf(errors);
        }

        /** 测试与仅需 pointId 时的便捷构造。 */
        public ActualItem(String originalSentence, List<String> pointIds) {
            this(originalSentence, "", toErrors(pointIds));
        }

        public List<String> pointIds() {
            if (CollectionUtils.isEmpty(errors)) {
                return List.of();
            }
            List<String> ids = new ArrayList<>();
            for (ActualError e : errors) {
                if (e != null && StringUtils.hasText(e.pointId())) {
                    ids.add(e.pointId().trim());
                }
            }
            return ids;
        }

        private static List<ActualError> toErrors(List<String> pointIds) {
            if (CollectionUtils.isEmpty(pointIds)) {
                return List.of();
            }
            List<ActualError> out = new ArrayList<>();
            for (String pid : pointIds) {
                if (StringUtils.hasText(pid)) {
                    out.add(new ActualError(pid.trim(), ""));
                }
            }
            return out;
        }
    }

    public record CaseReport(
            int tp, int fp, int fn, int tn,
            double precision, double recall, double f1,
            int goldErrorCount,
            int leafHits,
            int familyHits,
            double leafAccuracy,
            double familyAccuracy,
            int surplusActualErrors,
            List<String> falsePositiveIds,
            List<String> falseNegativeIds,
            List<String> unalignedActual) {
        public CaseReport {
            falsePositiveIds = falsePositiveIds == null ? List.of() : List.copyOf(falsePositiveIds);
            falseNegativeIds = falseNegativeIds == null ? List.of() : List.copyOf(falseNegativeIds);
            unalignedActual = unalignedActual == null ? List.of() : List.copyOf(unalignedActual);
        }
    }

    private record MatchStats(int goldErrors, int leafHits, int familyHits, int surplus) {
    }

    private GoldenStatistics() {
    }

    public static String normalize(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        return text.trim().replaceAll("\\s+", " ");
    }

    public static CaseReport evaluate(
            List<InputUtterance> input,
            List<GoldUtterance> gold,
            List<ActualItem> actual,
            Function<String, String> pointIdToFamily) {
        Objects.requireNonNull(pointIdToFamily, "pointIdToFamily");

        Map<String, String> idByNorm = new LinkedHashMap<>();
        if (!CollectionUtils.isEmpty(input)) {
            for (InputUtterance u : input) {
                String norm = normalize(u.text());
                idByNorm.putIfAbsent(norm, u.id());
            }
        }

        Map<String, GoldUtterance> goldById = CollectionUtils.isEmpty(gold)
                ? Map.of()
                : gold.stream().collect(Collectors.toMap(
                GoldUtterance::id, g -> g, (a, b) -> a, LinkedHashMap::new));

        Map<String, ActualItem> actualById = new LinkedHashMap<>();
        List<String> unaligned = new ArrayList<>();
        if (!CollectionUtils.isEmpty(actual)) {
            for (ActualItem item : actual) {
                String id = idByNorm.get(normalize(item.originalSentence()));
                if (!StringUtils.hasText(id)) {
                    unaligned.add(item.originalSentence() == null ? "" : item.originalSentence());
                } else {
                    actualById.putIfAbsent(id, item);
                }
            }
        }

        int tp = 0;
        int fp = 0;
        int fn = 0;
        int tn = 0;
        List<String> fps = new ArrayList<>();
        List<String> fns = new ArrayList<>();
        int goldErrorCount = 0;
        int leafHits = 0;
        int familyHits = 0;
        int surplus = 0;

        for (Map.Entry<String, GoldUtterance> e : goldById.entrySet()) {
            String id = e.getKey();
            GoldUtterance g = e.getValue();
            ActualItem a = actualById.get(id);
            boolean hasActual = a != null && !CollectionUtils.isEmpty(a.pointIds());

            if (!g.expectError() && !hasActual) {
                tn++;
            } else if (!g.expectError() && hasActual) {
                fp++;
                fps.add(id);
            } else if (g.expectError() && !hasActual) {
                fn++;
                fns.add(id);
                // leafAcc/familyAcc 只在 TP 句上统计（与 README「双方都认为有错」一致）
            } else {
                tp++;
                MatchStats ms = matchErrors(g.errors(), a.pointIds(), pointIdToFamily);
                goldErrorCount += ms.goldErrors();
                leafHits += ms.leafHits();
                familyHits += ms.familyHits();
                surplus += ms.surplus();
            }
        }

        double precision = (tp + fp) == 0 ? 0.0 : (double) tp / (tp + fp);
        double recall = (tp + fn) == 0 ? 0.0 : (double) tp / (tp + fn);
        double f1 = (precision + recall) == 0 ? 0.0 : 2 * precision * recall / (precision + recall);
        double leafAcc = goldErrorCount == 0 ? 0.0 : (double) leafHits / goldErrorCount;
        double familyAcc = goldErrorCount == 0 ? 0.0 : (double) familyHits / goldErrorCount;

        return new CaseReport(
                tp, fp, fn, tn, precision, recall, f1,
                goldErrorCount, leafHits, familyHits, leafAcc, familyAcc, surplus,
                fps, fns, unaligned);
    }

    private static MatchStats matchErrors(
            List<GoldError> goldErrors,
            List<String> actualPointIds,
            Function<String, String> pointIdToFamily) {
        List<GoldError> golds = goldErrors == null ? List.of() : goldErrors;
        List<String> actuals = actualPointIds == null ? List.of() : new ArrayList<>(actualPointIds);
        boolean[] used = new boolean[actuals.size()];

        int leafHits = 0;
        for (GoldError g : golds) {
            Set<String> acceptable = acceptablePointIds(g);
            for (int i = 0; i < actuals.size(); i++) {
                if (used[i]) {
                    continue;
                }
                String pid = actuals.get(i);
                if (StringUtils.hasText(pid) && acceptable.contains(pid.trim())) {
                    used[i] = true;
                    leafHits++;
                    break;
                }
            }
        }

        boolean[] usedFamily = new boolean[actuals.size()];
        int familyHits = 0;
        for (GoldError g : golds) {
            Set<String> acceptableFamilies = new LinkedHashSet<>();
            for (String pid : acceptablePointIds(g)) {
                String fam = pointIdToFamily.apply(pid);
                if (StringUtils.hasText(fam)) {
                    acceptableFamilies.add(fam.trim());
                }
            }
            for (int i = 0; i < actuals.size(); i++) {
                if (usedFamily[i]) {
                    continue;
                }
                String pid = actuals.get(i);
                if (!StringUtils.hasText(pid)) {
                    continue;
                }
                String fam = pointIdToFamily.apply(pid.trim());
                if (StringUtils.hasText(fam) && acceptableFamilies.contains(fam.trim())) {
                    usedFamily[i] = true;
                    familyHits++;
                    break;
                }
            }
        }

        int surplus = 0;
        for (int i = 0; i < actuals.size(); i++) {
            if (!used[i] && StringUtils.hasText(actuals.get(i))) {
                surplus++;
            }
        }

        return new MatchStats(golds.size(), leafHits, familyHits, surplus);
    }

    private static Set<String> acceptablePointIds(GoldError g) {
        Set<String> acceptable = new LinkedHashSet<>();
        if (StringUtils.hasText(g.primaryPointId())) {
            acceptable.add(g.primaryPointId().trim());
        }
        if (!CollectionUtils.isEmpty(g.acceptablePointIds())) {
            for (String p : g.acceptablePointIds()) {
                if (StringUtils.hasText(p)) {
                    acceptable.add(p.trim());
                }
            }
        }
        return acceptable;
    }
}
