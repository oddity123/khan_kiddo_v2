package com.khankiddo.learning.eval;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.khankiddo.learning.ai.conversation.model.GrammarAnalysisResult;
import com.khankiddo.learning.ai.conversation.model.GrammarErrorDto;
import com.khankiddo.learning.ai.conversation.model.GrammarSentenceItemDto;
import com.khankiddo.learning.conversation.ConversationAnalysisPipeline;
import com.khankiddo.learning.knowledge.PointDictionary;
import com.khankiddo.learning.llm.LlmModelCatalog;
import com.khankiddo.learning.llm.ResolvedLlmModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
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
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Stage2 黄金集准确性运行器（opt-in，需真实 LLM）。默认不随 {@code mvn test} 运行。
 *
 * <p>运行方式见 {@code src/test/resources/eval/golden/README.md}。
 */
@SpringBootTest
@ActiveProfiles("test")
@EnabledIfSystemProperty(named = "golden", matches = "true")
class ConversationGoldenHarness {

    private static final Logger log = LoggerFactory.getLogger(ConversationGoldenHarness.class);
    private static final Path CASES_DIR = Path.of("src/test/resources/eval/golden/cases");
    private static final Path REPORT_DIR = Path.of("target/golden-report");

    @Autowired
    private ConversationAnalysisPipeline pipeline;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PointDictionary pointDictionary;

    @Autowired
    private LlmModelCatalog modelCatalog;

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record MetaDto(
            String id,
            String category,
            String title,
            String notes,
            GoldSourceDto goldSource) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GoldSourceDto(String model, String dated, String reviewedBy, String reviewStatus) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record InputDto(List<UtteranceDto> utterances) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record UtteranceDto(String id, String text) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GoldDto(int version, List<GoldUtteranceDto> utterances) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GoldUtteranceDto(String id, boolean expectError, List<GoldErrorDto> errors) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GoldErrorDto(
            String errorId,
            String primaryPointId,
            List<String> acceptablePointIds,
            String span,
            String note) {
    }

    private record CaseEval(
            String caseId,
            String category,
            GoldenStatistics.CaseReport report,
            long processingTimeMs,
            List<GoldenStatistics.InputUtterance> inputUtterances,
            List<GoldenStatistics.GoldUtterance> goldUtterances,
            List<GoldenStatistics.ActualItem> actualItems) {
    }

    @Test
    void evaluateGoldenSet() throws IOException {
        String modelId = resolveGoldenModelId();
        ResolvedLlmModel model = modelCatalog.resolveOrDefault(modelId);
        log.info("[黄金集] Stage2 模型：{} ({}) api={} provider={} temperature={}",
                model.getId(), model.getDisplayName(), model.getConfig().getModelName(),
                model.getProvider(), model.getConfig().getTemperature());

        List<Path> caseDirs = loadCaseDirs();
        if (caseDirs.isEmpty()) {
            log.warn("未找到黄金 case，跳过。请在 {} 放置 case 目录", CASES_DIR.toAbsolutePath());
            return;
        }

        List<CaseEval> evals = new ArrayList<>();
        for (Path caseDir : caseDirs) {
            String caseId = caseDir.getFileName().toString();
            log.info("[黄金集] 评测 {} ...", caseId);
            try {
                CaseEval eval = evaluateOne(caseDir, model.getId());
                evals.add(eval);
                GoldenStatistics.CaseReport r = eval.report();
                log.info("[黄金集] {} P={}/{} R={}/{} F1={} leafAcc={} familyAcc={} unaligned={} 耗时={}ms",
                        caseId,
                        r.tp(), r.tp() + r.fp(),
                        r.tp(), r.tp() + r.fn(),
                        String.format("%.3f", r.f1()),
                        String.format("%.3f", r.leafAccuracy()),
                        String.format("%.3f", r.familyAccuracy()),
                        r.unalignedActual().size(),
                        eval.processingTimeMs());
            } catch (RuntimeException | IOException ex) {
                log.warn("[黄金集] {} 失败：{}", caseId, ex.getMessage(), ex);
            }
        }

        String markdown = renderMarkdown(evals, model);
        writeReport(markdown);
        log.info("\n{}", markdown);
    }

    private CaseEval evaluateOne(Path caseDir, String modelId) throws IOException {
        MetaDto meta = objectMapper.readValue(caseDir.resolve("meta.json").toFile(), MetaDto.class);
        InputDto input = objectMapper.readValue(caseDir.resolve("input.json").toFile(), InputDto.class);
        GoldDto gold = objectMapper.readValue(caseDir.resolve("gold.json").toFile(), GoldDto.class);

        List<String> texts = CollectionUtils.isEmpty(input.utterances())
                ? List.of()
                : input.utterances().stream().map(UtteranceDto::text).toList();

        long start = System.currentTimeMillis();
        GrammarAnalysisResult result = pipeline.analyzeEnglishUtterances(texts, modelId, p -> {});
        long elapsed = System.currentTimeMillis() - start;

        List<GoldenStatistics.InputUtterance> inputUtterances = CollectionUtils.isEmpty(input.utterances())
                ? List.of()
                : input.utterances().stream()
                .map(u -> new GoldenStatistics.InputUtterance(u.id(), u.text()))
                .toList();

        List<GoldenStatistics.GoldUtterance> goldUtterances = CollectionUtils.isEmpty(gold.utterances())
                ? List.of()
                : gold.utterances().stream()
                .map(u -> new GoldenStatistics.GoldUtterance(
                        u.id(),
                        u.expectError(),
                        CollectionUtils.isEmpty(u.errors())
                                ? List.of()
                                : u.errors().stream()
                                .map(e -> new GoldenStatistics.GoldError(
                                        e.errorId(), e.primaryPointId(), e.acceptablePointIds()))
                                .toList()))
                .toList();

        List<GoldenStatistics.ActualItem> actualItems = toActualItems(result);

        GoldenStatistics.CaseReport report = GoldenStatistics.evaluate(
                inputUtterances,
                goldUtterances,
                actualItems,
                pid -> pointDictionary.resolveOrFallback(pid).familyId());

        String category = meta != null && StringUtils.hasText(meta.category()) ? meta.category() : "";
        String caseId = meta != null && StringUtils.hasText(meta.id())
                ? meta.id()
                : caseDir.getFileName().toString();
        return new CaseEval(caseId, category, report, elapsed, inputUtterances, goldUtterances, actualItems);
    }

    private List<GoldenStatistics.ActualItem> toActualItems(GrammarAnalysisResult result) {
        if (result == null || CollectionUtils.isEmpty(result.getItems())) {
            return List.of();
        }
        List<GoldenStatistics.ActualItem> items = new ArrayList<>();
        for (GrammarSentenceItemDto item : result.getItems()) {
            if (item == null || !StringUtils.hasText(item.getOriginalSentence())) {
                continue;
            }
            List<GoldenStatistics.ActualError> errors = new ArrayList<>();
            if (!CollectionUtils.isEmpty(item.getErrors())) {
                for (GrammarErrorDto error : item.getErrors()) {
                    if (error != null && StringUtils.hasText(error.getPointId())) {
                        errors.add(new GoldenStatistics.ActualError(
                                error.getPointId().trim(),
                                StringUtils.hasText(error.getPoint()) ? error.getPoint().trim() : ""));
                    }
                }
            }
            if (!errors.isEmpty()) {
                items.add(new GoldenStatistics.ActualItem(
                        item.getOriginalSentence(),
                        StringUtils.hasText(item.getSuggestion()) ? item.getSuggestion().trim() : "",
                        errors));
            }
        }
        return items;
    }

    private static String resolveGoldenModelId() {
        String raw = System.getProperty("golden.modelId");
        return StringUtils.hasText(raw) ? raw.trim() : null;
    }

    private List<Path> loadCaseDirs() throws IOException {
        if (!Files.isDirectory(CASES_DIR)) {
            return List.of();
        }
        try (Stream<Path> paths = Files.list(CASES_DIR)) {
            return paths.filter(Files::isDirectory).sorted().toList();
        }
    }

    private void writeReport(String markdown) throws IOException {
        Files.createDirectories(REPORT_DIR);
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        Path out = REPORT_DIR.resolve("golden-" + stamp + ".md");
        Files.writeString(out, markdown, StandardCharsets.UTF_8);
        log.info("[黄金集] 报告已写入 {}", out.toAbsolutePath());
    }

    private String renderMarkdown(List<CaseEval> evals, ResolvedLlmModel model) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Stage2 黄金集准确性报告\n\n");
        sb.append("- Stage2 模型 ID：`").append(model.getId()).append("`\n");
        sb.append("- Stage2 展示名：").append(model.getDisplayName()).append("\n");
        sb.append("- Stage2 API 模型：`").append(model.getConfig().getModelName()).append("`\n");
        sb.append("- Provider：").append(model.getProvider()).append("\n");
        sb.append("- temperature：").append(model.getConfig().getTemperature()).append("\n");
        sb.append("- case 数：").append(evals.size()).append("\n");
        sb.append("- 报告生成：`ConversationGoldenHarness.renderMarkdown`（程序生成，非手写）\n\n");

        sb.append("## 指标说明\n\n");
        sb.append("完整含义见 `src/test/resources/eval/golden/README.md`「各指标含义」。摘要：\n\n");
        sb.append("| 列名 | 含义 |\n");
        sb.append("|---|---|\n");
        sb.append("| P | 句级精确率 = TP/(TP+FP)，越高误报越少 |\n");
        sb.append("| R | 句级召回率 = TP/(TP+FN)，越高漏报越少 |\n");
        sb.append("| F1 | 句级 F1 = 2·P·R/(P+R) |\n");
        sb.append("| leafAcc | 叶子准确率：actual pointId 命中 gold acceptablePointIds 的比例 |\n");
        sb.append("| familyAcc | 家族准确率：映射 familyId 后再匹配 |\n");
        sb.append("| TP | gold 有错且模型标错 |\n");
        sb.append("| FP | gold 无错但模型标错（误报） |\n");
        sb.append("| FN | gold 有错但模型未标（漏报） |\n");
        sb.append("| TN | gold 无错且模型未标 |\n");
        sb.append("| surplus | 叶子匹配后多出来的 actual pointId 数 |\n");
        sb.append("| unaligned | 原文对不齐 input 的 Stage2 item 数 |\n");
        sb.append("| ms | 该 case Stage2 耗时（毫秒） |\n\n");

        sb.append("## 汇总\n\n");
        sb.append("| case | category | P | R | F1 | leafAcc | familyAcc | TP | FP | FN | TN | surplus | unaligned | ms |\n");
        sb.append("|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|\n");
        for (CaseEval e : evals) {
            GoldenStatistics.CaseReport r = e.report();
            sb.append("| `").append(e.caseId()).append("` | ")
                    .append(e.category()).append(" | ")
                    .append(fmt(r.precision())).append(" | ")
                    .append(fmt(r.recall())).append(" | ")
                    .append(fmt(r.f1())).append(" | ")
                    .append(fmt(r.leafAccuracy())).append(" | ")
                    .append(fmt(r.familyAccuracy())).append(" | ")
                    .append(r.tp()).append(" | ")
                    .append(r.fp()).append(" | ")
                    .append(r.fn()).append(" | ")
                    .append(r.tn()).append(" | ")
                    .append(r.surplusActualErrors()).append(" | ")
                    .append(r.unalignedActual().size()).append(" | ")
                    .append(e.processingTimeMs()).append(" |\n");
        }

        Map<String, List<CaseEval>> byCategory = evals.stream()
                .collect(Collectors.groupingBy(
                        e -> StringUtils.hasText(e.category()) ? e.category() : "(none)",
                        LinkedHashMap::new,
                        Collectors.toList()));
        sb.append("\n## 按 category\n\n");
        for (Map.Entry<String, List<CaseEval>> entry : byCategory.entrySet()) {
            List<CaseEval> group = entry.getValue();
            double avgF1 = group.stream().mapToDouble(e -> e.report().f1()).average().orElse(0);
            double avgLeaf = group.stream().mapToDouble(e -> e.report().leafAccuracy()).average().orElse(0);
            sb.append("- **").append(entry.getKey()).append("**：")
                    .append(group.size()).append(" case，avg F1=")
                    .append(fmt(avgF1)).append("，avg leafAcc=")
                    .append(fmt(avgLeaf)).append("\n");
        }

        sb.append("\n## 明细\n\n");
        for (CaseEval e : evals) {
            GoldenStatistics.CaseReport r = e.report();
            sb.append("### `").append(e.caseId()).append("`\n\n");
            sb.append("- FP：").append(r.falsePositiveIds()).append("\n");
            sb.append("- FN：").append(r.falseNegativeIds()).append("\n");
            sb.append("- unaligned：").append(r.unalignedActual()).append("\n\n");

            sb.append("#### gold 期望 pointId\n\n");
            sb.append("| id | expectError | primary / acceptable |\n");
            sb.append("|---|---|---|\n");
            if (CollectionUtils.isEmpty(e.goldUtterances())) {
                sb.append("| （无） | | |\n");
            } else {
                for (GoldenStatistics.GoldUtterance g : e.goldUtterances()) {
                    sb.append("| `").append(g.id()).append("` | ")
                            .append(g.expectError()).append(" | ");
                    if (!g.expectError() || CollectionUtils.isEmpty(g.errors())) {
                        sb.append("— |\n");
                    } else {
                        String cell = g.errors().stream()
                                .map(err -> "`" + err.primaryPointId() + "` "
                                        + err.acceptablePointIds())
                                .collect(Collectors.joining("<br>"));
                        sb.append(cell).append(" |\n");
                    }
                }
            }

            sb.append("\n#### Stage2 actual（pointId / point / suggestion）\n\n");
            Map<String, String> idByNorm = new LinkedHashMap<>();
            if (!CollectionUtils.isEmpty(e.inputUtterances())) {
                for (GoldenStatistics.InputUtterance u : e.inputUtterances()) {
                    idByNorm.putIfAbsent(GoldenStatistics.normalize(u.text()), u.id());
                }
            }
            if (CollectionUtils.isEmpty(e.actualItems())) {
                sb.append("（无检出）\n\n");
            } else {
                for (GoldenStatistics.ActualItem item : e.actualItems()) {
                    String aligned = idByNorm.getOrDefault(
                            GoldenStatistics.normalize(item.originalSentence()), "—");
                    sb.append("- **`").append(aligned).append("`** ")
                            .append(escapeCell(item.originalSentence())).append("\n");
                    sb.append("  - suggestion：")
                            .append(StringUtils.hasText(item.suggestion())
                                    ? escapeCell(item.suggestion())
                                    : "—")
                            .append("\n");
                    if (CollectionUtils.isEmpty(item.errors())) {
                        sb.append("  - errors：—\n");
                    } else {
                        sb.append("  - errors：\n");
                        for (GoldenStatistics.ActualError err : item.errors()) {
                            String fam = pointDictionary.resolveOrFallback(err.pointId()).familyId();
                            sb.append("    - `").append(err.pointId()).append("` / `")
                                    .append(fam).append("` — ")
                                    .append(StringUtils.hasText(err.point())
                                            ? escapeCell(err.point())
                                            : "—")
                                    .append("\n");
                        }
                    }
                }
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    private static String escapeCell(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "";
        }
        return raw.replace("|", "\\|").replace("\n", " ");
    }

    private static String fmt(double v) {
        return String.format("%.3f", v);
    }
}
