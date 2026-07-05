package com.khankiddo.learning.eval;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * 漂移度量的纯计算内核（不依赖 LLM / Spring）：给定同一段对话多次运行的结果，
 * 量化「AI 分析漂移」。所有方法均为确定性纯函数，可独立单测。
 *
 * <p>核心思路：稳定性度量不需要标准答案，只需把同一输入跑 N 次，看结果晃不晃。
 * 关注三个层面：
 * <ul>
 *   <li>数量层：切分句数、错误总数的极差与变异系数；</li>
 *   <li>分数层：综合分与各维度分的极差与标准差（用户直接看到的数字）；</li>
 *   <li>结构层：错误类型分布的多重集 Jaccard 相似度、被标记句子的「翻转率」。</li>
 * </ul>
 */
public final class DriftStatistics {

    /** 综合分极差 <= 此值视为稳定 */
    public static final int SCORE_RANGE_STABLE = 3;
    /** 综合分极差 <= 此值视为中等漂移，超过则高漂移 */
    public static final int SCORE_RANGE_MODERATE = 7;

    /** 单次运行中与漂移相关的可比较快照。 */
    public record DriftSample(
            int totalSentences,
            int totalErrors,
            int performanceScore,
            Map<String, Integer> dimensionScores,
            Map<String, Integer> errorTypeCounts,
            Set<String> flaggedSentences) {

        public DriftSample {
            dimensionScores = dimensionScores == null ? Map.of() : Map.copyOf(dimensionScores);
            errorTypeCounts = errorTypeCounts == null ? Map.of() : Map.copyOf(errorTypeCounts);
            flaggedSentences = flaggedSentences == null ? Set.of() : Set.copyOf(flaggedSentences);
        }
    }

    /** 整数序列的基础统计。 */
    public record IntStat(int min, int max, double mean, double stdDev) {
        public int range() {
            return max - min;
        }

        /** 变异系数（stdDev / mean），mean 为 0 时返回 0。 */
        public double coefficientOfVariation() {
            return mean == 0 ? 0 : stdDev / mean;
        }
    }

    public record ConversationDriftReport(
            String conversationId,
            int runs,
            IntStat totalSentences,
            IntStat totalErrors,
            IntStat performanceScore,
            Map<String, IntStat> dimensionStats,
            double avgTypeDistributionJaccard,
            double sentenceFlagInstability,
            String verdict) {
    }

    public record CorpusDriftReport(
            List<ConversationDriftReport> perConversation,
            IntStat worstScoreRange,
            double avgScoreStdDev,
            double avgTypeDistributionJaccard,
            double avgSentenceFlagInstability,
            String overallVerdict) {
    }

    private DriftStatistics() {
    }

    public static IntStat intStat(List<Integer> values) {
        if (values == null || values.isEmpty()) {
            return new IntStat(0, 0, 0, 0);
        }
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        double sum = 0;
        for (int v : values) {
            min = Math.min(min, v);
            max = Math.max(max, v);
            sum += v;
        }
        double mean = sum / values.size();
        double variance = 0;
        for (int v : values) {
            variance += (v - mean) * (v - mean);
        }
        variance /= values.size();
        return new IntStat(min, max, mean, Math.sqrt(variance));
    }

    public static ConversationDriftReport analyzeConversation(String conversationId, List<DriftSample> samples) {
        if (samples == null || samples.isEmpty()) {
            return new ConversationDriftReport(conversationId, 0,
                    new IntStat(0, 0, 0, 0), new IntStat(0, 0, 0, 0), new IntStat(0, 0, 0, 0),
                    Map.of(), 1.0, 0.0, "NO_DATA");
        }

        List<Integer> sentences = new ArrayList<>();
        List<Integer> errors = new ArrayList<>();
        List<Integer> scores = new ArrayList<>();
        Set<String> dimensionKeys = new HashSet<>();
        for (DriftSample s : samples) {
            sentences.add(s.totalSentences());
            errors.add(s.totalErrors());
            scores.add(s.performanceScore());
            dimensionKeys.addAll(s.dimensionScores().keySet());
        }

        Map<String, IntStat> dimensionStats = new TreeMap<>();
        for (String key : dimensionKeys) {
            List<Integer> vals = new ArrayList<>();
            for (DriftSample s : samples) {
                vals.add(s.dimensionScores().getOrDefault(key, 0));
            }
            dimensionStats.put(key, intStat(vals));
        }

        IntStat scoreStat = intStat(scores);
        double jaccard = averagePairwiseMultisetJaccard(samples);
        double instability = sentenceFlagInstability(samples);
        String verdict = verdict(scoreStat.range(), jaccard, instability);

        return new ConversationDriftReport(conversationId, samples.size(),
                intStat(sentences), intStat(errors), scoreStat,
                dimensionStats, jaccard, instability, verdict);
    }

    public static CorpusDriftReport analyzeCorpus(Map<String, List<DriftSample>> byConversation) {
        List<ConversationDriftReport> reports = new ArrayList<>();
        List<Integer> scoreRanges = new ArrayList<>();
        double stdDevSum = 0;
        double jaccardSum = 0;
        double instabilitySum = 0;
        for (Map.Entry<String, List<DriftSample>> e : byConversation.entrySet()) {
            ConversationDriftReport r = analyzeConversation(e.getKey(), e.getValue());
            reports.add(r);
            scoreRanges.add(r.performanceScore().range());
            stdDevSum += r.performanceScore().stdDev();
            jaccardSum += r.avgTypeDistributionJaccard();
            instabilitySum += r.sentenceFlagInstability();
        }
        int n = Math.max(1, reports.size());
        IntStat worstScoreRange = intStat(scoreRanges);
        double avgStdDev = stdDevSum / n;
        double avgJaccard = jaccardSum / n;
        double avgInstability = instabilitySum / n;
        String overall = verdict(worstScoreRange.max(), avgJaccard, avgInstability);
        return new CorpusDriftReport(reports, worstScoreRange, avgStdDev, avgJaccard, avgInstability, overall);
    }

    /** 所有运行两两之间错误类型分布的多重集 Jaccard 相似度的平均值（1.0 = 完全一致）。 */
    static double averagePairwiseMultisetJaccard(List<DriftSample> samples) {
        if (samples.size() < 2) {
            return 1.0;
        }
        double sum = 0;
        int pairs = 0;
        for (int i = 0; i < samples.size(); i++) {
            for (int j = i + 1; j < samples.size(); j++) {
                sum += multisetJaccard(samples.get(i).errorTypeCounts(), samples.get(j).errorTypeCounts());
                pairs++;
            }
        }
        return pairs == 0 ? 1.0 : sum / pairs;
    }

    private static double multisetJaccard(Map<String, Integer> a, Map<String, Integer> b) {
        if (a.isEmpty() && b.isEmpty()) {
            return 1.0;
        }
        Set<String> keys = new HashSet<>();
        keys.addAll(a.keySet());
        keys.addAll(b.keySet());
        long intersection = 0;
        long union = 0;
        for (String k : keys) {
            int ca = a.getOrDefault(k, 0);
            int cb = b.getOrDefault(k, 0);
            intersection += Math.min(ca, cb);
            union += Math.max(ca, cb);
        }
        return union == 0 ? 1.0 : (double) intersection / union;
    }

    /**
     * 句子翻转率：跨 N 次运行，被标记过（有错）的句子中，只在部分运行里被标记
     * （而非全部）的比例。0 = 每句要么次次被标、要么从不被标（稳定）；越高越漂移。
     */
    static double sentenceFlagInstability(List<DriftSample> samples) {
        int runs = samples.size();
        if (runs < 2) {
            return 0.0;
        }
        Map<String, Integer> flagFrequency = new HashMap<>();
        for (DriftSample s : samples) {
            for (String sentence : s.flaggedSentences()) {
                flagFrequency.merge(sentence, 1, Integer::sum);
            }
        }
        if (flagFrequency.isEmpty()) {
            return 0.0;
        }
        int unstable = 0;
        for (int freq : flagFrequency.values()) {
            if (freq > 0 && freq < runs) {
                unstable++;
            }
        }
        return (double) unstable / flagFrequency.size();
    }

    private static String verdict(int scoreRange, double jaccard, double instability) {
        if (scoreRange <= SCORE_RANGE_STABLE && jaccard >= 0.8 && instability <= 0.15) {
            return "STABLE";
        }
        if (scoreRange <= SCORE_RANGE_MODERATE && jaccard >= 0.6 && instability <= 0.35) {
            return "MODERATE";
        }
        return "HIGH_DRIFT";
    }
}
