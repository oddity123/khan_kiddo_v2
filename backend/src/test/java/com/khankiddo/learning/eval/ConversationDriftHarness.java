package com.khankiddo.learning.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.khankiddo.learning.config.ConversationAnalysisProperties;
import com.khankiddo.learning.conversation.ConversationAnalysisPipeline;
import com.khankiddo.learning.llm.LlmModelCatalog;
import com.khankiddo.learning.llm.ResolvedLlmModel;
import com.khankiddo.learning.dto.conversation.AnalysisErrorDto;
import com.khankiddo.learning.dto.conversation.AnalysisItemDto;
import com.khankiddo.learning.dto.conversation.ConversationAnalysisRequest;
import com.khankiddo.learning.dto.conversation.ConversationAnalysisResultDto;
import com.khankiddo.learning.dto.conversation.EducationalSummaryDto;
import com.khankiddo.learning.dto.conversation.PerformanceDimensionScoresDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 漂移回归运行器（opt-in，需真实 LLM）。默认不随 {@code mvn test} 运行。
 *
 * <p>运行方式见 {@code src/test/resources/eval/drift/README.md}。
 */
@SpringBootTest
@EnabledIfSystemProperty(named = "drift", matches = "true")
class ConversationDriftHarness {

    private static final Logger log = LoggerFactory.getLogger(ConversationDriftHarness.class);
    private static final Path CONVERSATIONS_DIR = Path.of("src/test/resources/eval/drift/conversations");
    private static final Path REPORT_DIR = Path.of("target/drift-report");

    @Autowired
    private ConversationAnalysisPipeline pipeline;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private LlmModelCatalog modelCatalog;

    @Autowired
    private ConversationAnalysisProperties conversationAnalysisProperties;

    /** Stage2/Stage3 模型上下文（写入报告头）。 */
    private record ModelContext(
            String modelId,
            String displayName,
            String apiModelName,
            String provider,
            double stage2Temperature,
            String stage1ModelName,
            double stage1Temperature) {
    }

    /** 单次运行的可对比快照（含明细，供报告逐次对照）。 */
    private record RunDetail(
            int runIndex,
            DriftStatistics.DriftSample sample,
            int flaggedSentenceCount,
            long processingTimeMs,
            String typeDistributionSummary) {
    }

    private record ConversationRuns(String conversationId, List<RunDetail> runs) {
    }

    @Test
    void measureDrift() throws IOException {
        int runs = Integer.getInteger("drift.runs", 5);
        String modelId = resolveDriftModelId();
        ModelContext model = resolveModelContext(modelId);
        log.info("[漂移] Stage2/3 模型：{} ({}) api={} provider={} temperature={}；Stage1 分离：{} temperature={}",
                model.modelId(), model.displayName(), model.apiModelName(), model.provider(),
                model.stage2Temperature(), model.stage1ModelName(), model.stage1Temperature());

        List<Path> conversations = loadConversationFiles();
        if (conversations.isEmpty()) {
            log.warn("未找到种子对话，跳过。请在 {} 放置 *.txt", CONVERSATIONS_DIR.toAbsolutePath());
            return;
        }

        Map<String, List<DriftStatistics.DriftSample>> corpus = new LinkedHashMap<>();
        List<ConversationRuns> allRuns = new ArrayList<>();
        for (Path file : conversations) {
            String id = file.getFileName().toString().replaceFirst("\\.txt$", "");
            String content = Files.readString(file, StandardCharsets.UTF_8).trim();
            List<RunDetail> runDetails = new ArrayList<>();
            List<DriftStatistics.DriftSample> samples = new ArrayList<>();
            for (int i = 1; i <= runs; i++) {
                log.info("[漂移] {} 第 {}/{} 次运行...", id, i, runs);
                try {
                    RunDetail detail = runOnceDetail(content, i, modelId);
                    runDetails.add(detail);
                    samples.add(detail.sample());
                    log.info("[漂移] {} 第 {} 次：综合分={} 句数={} 错误数={} 有错句={} 耗时={}ms 类型={}",
                            id, i,
                            detail.sample().performanceScore(),
                            detail.sample().totalSentences(),
                            detail.sample().totalErrors(),
                            detail.flaggedSentenceCount(),
                            detail.processingTimeMs(),
                            detail.typeDistributionSummary());
                } catch (RuntimeException ex) {
                    log.warn("[漂移] {} 第 {} 次运行失败：{}", id, i, ex.getMessage());
                }
            }
            if (!samples.isEmpty()) {
                corpus.put(id, samples);
                allRuns.add(new ConversationRuns(id, runDetails));
            }
        }

        DriftStatistics.CorpusDriftReport report = DriftStatistics.analyzeCorpus(corpus);
        String markdown = renderMarkdown(report, runs, allRuns, model);
        writeReport(markdown);
        log.info("\n{}", markdown);
    }

    /**
     * 可选：{@code -Ddrift.modelId=doubao-seed} 或 {@code qwen-plus}。
     * 未设置时使用 {@code app.llm.default-model-id}（当前默认 doubao-seed）。
     */
    private static String resolveDriftModelId() {
        String raw = System.getProperty("drift.modelId");
        return StringUtils.hasText(raw) ? raw.trim() : null;
    }

    private ModelContext resolveModelContext(String modelId) {
        ResolvedLlmModel resolved = modelCatalog.resolveOrDefault(modelId);
        return new ModelContext(
                resolved.getId(),
                resolved.getDisplayName(),
                resolved.getConfig().getModelName(),
                resolved.getProvider(),
                resolved.getConfig().getTemperature(),
                conversationAnalysisProperties.getSeparationModelName(),
                conversationAnalysisProperties.getSeparationTemperature());
    }

    private RunDetail runOnceDetail(String content, int runIndex, String modelId) {
        ConversationAnalysisRequest request = new ConversationAnalysisRequest();
        request.setConversationContent(content);
        if (StringUtils.hasText(modelId)) {
            request.setModelId(modelId);
        }
        ConversationAnalysisResultDto result = pipeline.run(
                request, "drift-" + System.nanoTime(), progress -> {
                });
        return toRunDetail(runIndex, result);
    }

    private RunDetail toRunDetail(int runIndex, ConversationAnalysisResultDto result) {
        Map<String, Object> results = result.getAnalysisResults() == null
                ? Map.of() : result.getAnalysisResults();

        int totalSentences = asInt(results.get("totalSentences"));
        int totalErrors = asInt(results.get("totalErrors"));

        List<AnalysisItemDto> items = convertItems(results.get("items"));
        Set<String> flagged = new TreeSet<>();
        Map<String, Integer> typeCounts = new TreeMap<>();
        for (AnalysisItemDto item : items) {
            if (CollectionUtils.isEmpty(item.getErrors())) {
                continue;
            }
            flagged.add(normalize(item.getOriginalSentence()));
            for (AnalysisErrorDto error : item.getErrors()) {
                if (StringUtils.hasText(error.getType())) {
                    typeCounts.merge(error.getType().trim(), 1, Integer::sum);
                }
            }
        }

        Map<String, Integer> dimensions = extractDimensions(results.get("educationalSummary"));
        int score = extractScore(results.get("educationalSummary"));
        long elapsed = result.getProcessingTimeMs() == null ? 0L : result.getProcessingTimeMs();

        DriftStatistics.DriftSample sample = new DriftStatistics.DriftSample(
                totalSentences, totalErrors, score, dimensions, typeCounts, flagged);
        String typeSummary = formatTypeSummary(typeCounts);
        return new RunDetail(runIndex, sample, flagged.size(), elapsed, typeSummary);
    }

    private static String formatTypeSummary(Map<String, Integer> typeCounts) {
        if (CollectionUtils.isEmpty(typeCounts)) {
            return "（无）";
        }
        return typeCounts.entrySet().stream()
                .map(e -> e.getKey() + "×" + e.getValue())
                .collect(Collectors.joining(", "));
    }

    private List<AnalysisItemDto> convertItems(Object rawItems) {
        if (rawItems == null) {
            return List.of();
        }
        return objectMapper.convertValue(rawItems,
                objectMapper.getTypeFactory().constructCollectionType(List.class, AnalysisItemDto.class));
    }

    private Map<String, Integer> extractDimensions(Object rawSummary) {
        EducationalSummaryDto summary = convertSummary(rawSummary);
        if (summary == null || summary.getReport() == null
                || summary.getReport().getOverallStats() == null
                || summary.getReport().getOverallStats().getDimensionScores() == null) {
            return Map.of();
        }
        PerformanceDimensionScoresDto d = summary.getReport().getOverallStats().getDimensionScores();
        Map<String, Integer> map = new LinkedHashMap<>();
        map.put("naturalness", nullSafe(d.getNaturalness()));
        map.put("accuracy", nullSafe(d.getAccuracy()));
        map.put("fluency", nullSafe(d.getFluency()));
        map.put("lexical", nullSafe(d.getLexical()));
        return map;
    }

    private int extractScore(Object rawSummary) {
        EducationalSummaryDto summary = convertSummary(rawSummary);
        if (summary == null || summary.getReport() == null
                || summary.getReport().getOverallStats() == null
                || summary.getReport().getOverallStats().getPerformanceScore() == null) {
            return 0;
        }
        return summary.getReport().getOverallStats().getPerformanceScore();
    }

    private EducationalSummaryDto convertSummary(Object rawSummary) {
        if (rawSummary == null) {
            return null;
        }
        if (rawSummary instanceof EducationalSummaryDto dto) {
            return dto;
        }
        return objectMapper.convertValue(rawSummary, EducationalSummaryDto.class);
    }

    private static int nullSafe(Integer v) {
        return v == null ? 0 : v;
    }

    private static int asInt(Object v) {
        return v instanceof Number n ? n.intValue() : 0;
    }

    private static String normalize(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "";
        }
        String lower = raw.toLowerCase(Locale.ROOT);
        StringBuilder sb = new StringBuilder(lower.length());
        boolean pendingSpace = false;
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                if (pendingSpace) {
                    sb.append(' ');
                    pendingSpace = false;
                }
                sb.append(c);
            } else if (sb.length() > 0) {
                pendingSpace = true;
            }
        }
        return sb.toString();
    }

    private List<Path> loadConversationFiles() throws IOException {
        if (!Files.isDirectory(CONVERSATIONS_DIR)) {
            return List.of();
        }
        try (Stream<Path> paths = Files.list(CONVERSATIONS_DIR)) {
            return paths.filter(p -> p.toString().endsWith(".txt")).sorted().toList();
        }
    }

    private void writeReport(String markdown) throws IOException {
        Files.createDirectories(REPORT_DIR);
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        Path out = REPORT_DIR.resolve("drift-" + stamp + ".md");
        Files.writeString(out, markdown, StandardCharsets.UTF_8);
        log.info("[漂移] 报告已写入 {}", out.toAbsolutePath());
    }

    private String renderMarkdown(
            DriftStatistics.CorpusDriftReport report,
            int runs,
            List<ConversationRuns> allRuns,
            ModelContext model) {
        StringBuilder sb = new StringBuilder();
        sb.append("# 分析漂移报告\n\n");
        sb.append("- Stage2/3 模型 ID：`").append(model.modelId()).append("`\n");
        sb.append("- Stage2/3 展示名：").append(model.displayName()).append("\n");
        sb.append("- Stage2/3 API 模型：`").append(model.apiModelName()).append("`\n");
        sb.append("- Provider：").append(model.provider()).append("\n");
        sb.append("- Stage2/3 temperature：").append(model.stage2Temperature()).append("\n");
        sb.append("- Stage1 分离模型：`").append(model.stage1ModelName()).append("`\n");
        sb.append("- Stage1 temperature：").append(model.stage1Temperature()).append("\n");
        sb.append("- 每段对话运行次数：").append(runs).append("\n");
        sb.append("- 语料数：").append(report.perConversation().size()).append("\n");
        sb.append("- 综合裁决：**").append(report.overallVerdict()).append("**\n");
        sb.append("- 最差综合分极差：").append(report.worstScoreRange().max()).append(" 分\n");
        sb.append(String.format("- 平均综合分标准差：%.2f%n", report.avgScoreStdDev()));
        sb.append(String.format("- 平均类型分布 Jaccard：%.3f（越接近 1 越稳）%n", report.avgTypeDistributionJaccard()));
        sb.append(String.format("- 平均句子翻转率：%.3f（越接近 0 越稳）%n%n", report.avgSentenceFlagInstability()));

        sb.append("## 汇总\n\n");
        sb.append("| 对话 | 裁决 | 分数极差 | 分数σ | 句数极差 | 错误数极差 | 类型Jaccard | 句子翻转率 |\n");
        sb.append("|---|---|---|---|---|---|---|---|\n");
        for (DriftStatistics.ConversationDriftReport c : report.perConversation()) {
            sb.append(String.format("| %s | %s | %d | %.2f | %d | %d | %.3f | %.3f |%n",
                    c.conversationId(), c.verdict(),
                    c.performanceScore().range(), c.performanceScore().stdDev(),
                    c.totalSentences().range(), c.totalErrors().range(),
                    c.avgTypeDistributionJaccard(), c.sentenceFlagInstability()));
        }

        sb.append("\n## 逐次运行明细\n\n");
        for (ConversationRuns conv : allRuns) {
            sb.append("### ").append(conv.conversationId()).append("\n\n");
            sb.append("| 次 | 综合分 | 自然 | 准确 | 流畅 | 词汇 | 句数 | 错误数 | 有错句 | 耗时(s) | 类型分布 |\n");
            sb.append("|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|\n");
            for (RunDetail run : conv.runs()) {
                DriftStatistics.DriftSample s = run.sample();
                Map<String, Integer> dims = s.dimensionScores();
                sb.append(String.format("| %d | %d | %d | %d | %d | %d | %d | %d | %d | %.1f | %s |%n",
                        run.runIndex(),
                        s.performanceScore(),
                        dims.getOrDefault("naturalness", 0),
                        dims.getOrDefault("accuracy", 0),
                        dims.getOrDefault("fluency", 0),
                        dims.getOrDefault("lexical", 0),
                        s.totalSentences(),
                        s.totalErrors(),
                        run.flaggedSentenceCount(),
                        run.processingTimeMs() / 1000.0,
                        run.typeDistributionSummary()));
            }
            sb.append('\n');
        }

        sb.append("> 裁决阈值：STABLE = 分数极差≤").append(DriftStatistics.SCORE_RANGE_STABLE)
                .append(" 且 Jaccard≥0.8 且 翻转率≤0.15；MODERATE = 分数极差≤")
                .append(DriftStatistics.SCORE_RANGE_MODERATE).append(" 且 Jaccard≥0.6 且 翻转率≤0.35；否则 HIGH_DRIFT。\n");
        return sb.toString();
    }
}
