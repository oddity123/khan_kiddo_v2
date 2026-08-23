package com.khankiddo.learning.ai.grammar;

import com.khankiddo.learning.knowledge.KnowledgePointStatsSupport;
import com.khankiddo.learning.knowledge.PointDefinition;
import com.khankiddo.learning.knowledge.PointDictionary;
import com.khankiddo.learning.mapper.ConversationAnalysisItemMapper;
import com.khankiddo.learning.mapper.ConversationAnalysisMapper;
import com.khankiddo.learning.model.ConversationAnalysisItem;
import com.khankiddo.learning.model.PointIdCount;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 语法学习相关 MySQL 查询，供 LangChain4j DB Tools 使用。
 */
@Service
@RequiredArgsConstructor
public class GrammarLearningDbService {

    private final ConversationAnalysisItemMapper itemMapper;
    private final ConversationAnalysisMapper analysisMapper;
    private final GrammarStatsProperties properties;
    private final PointDictionary pointDictionary;

    public String buildStatsSummary(
            Long userId, List<String> pointIds, List<String> familyIds, Integer days) {
        Integer effectiveDays = normalizeDays(days);
        List<PointIdCount> rows = itemMapper.countPointIdsByUserIdAndDays(userId, effectiveDays);
        if (CollectionUtils.isEmpty(rows)) {
            return emptyStatsMessage(effectiveDays);
        }

        List<String> filterPointIds = KnowledgePointStatsSupport.mergePointFilters(
                pointDictionary, pointIds, familyIds);
        Set<String> filterSet = toFilterSet(filterPointIds);

        List<PointIdCount> filtered = rows.stream()
                .filter(row -> CollectionUtils.isEmpty(filterSet)
                        || filterSet.contains(row.getPointId()))
                .limit(properties.getDb().getStatsTopN())
                .toList();

        if (CollectionUtils.isEmpty(filtered)) {
            return "（指定知识点在" + timeScopeLabel(effectiveDays) + "暂无统计，可去掉过滤重试）";
        }

        StringBuilder builder = new StringBuilder();
        builder.append("时间范围：").append(timeScopeLabel(effectiveDays)).append('\n');
        for (PointIdCount row : filtered) {
            PointDefinition definition = pointDictionary.resolveOrFallback(row.getPointId());
            builder.append("- ")
                    .append(definition.titleZh())
                    .append(" (")
                    .append(definition.pointId())
                    .append(" / ")
                    .append(definition.familyId())
                    .append("): ")
                    .append(row.getCount())
                    .append(" 次\n");
        }
        return builder.toString().trim();
    }

    public String buildErrorExamples(
            Long userId, List<String> pointIds, List<String> familyIds, Integer days, Integer limit) {
        Integer effectiveDays = normalizeDays(days);
        int effectiveLimit = normalizeLimit(limit);
        List<String> filterPointIds = KnowledgePointStatsSupport.mergePointFilters(
                pointDictionary, pointIds, familyIds);
        List<String> types = CollectionUtils.isEmpty(filterPointIds) ? null : filterPointIds;
        List<ConversationAnalysisItem> items = itemMapper.findErrorExamplesByUserId(
                userId, types, effectiveDays, effectiveLimit);
        if (CollectionUtils.isEmpty(items)) {
            return "（" + timeScopeLabel(effectiveDays) + "暂无匹配的错句样例）";
        }

        StringBuilder builder = new StringBuilder();
        builder.append("时间范围：").append(timeScopeLabel(effectiveDays))
                .append("；返回 ").append(items.size()).append(" 条\n\n");
        int index = 1;
        for (ConversationAnalysisItem item : items) {
            PointDefinition definition = StringUtils.hasText(item.getPointId())
                    ? pointDictionary.resolveOrFallback(item.getPointId())
                    : null;
            if (definition == null) {
                continue;
            }
            String typeLabel = definition.titleZh();
            String meta = definition.pointId() + " / " + definition.familyId();
            builder.append(index++).append(". [").append(typeLabel)
                    .append(" / ").append(meta).append("]\n");
            builder.append("   原句：").append(nullToEmpty(item.getOriginalSentence())).append('\n');
            builder.append("   错误点：").append(nullToEmpty(item.getErrorPoint())).append('\n');
            builder.append("   建议：").append(nullToEmpty(item.getSuggestion())).append('\n');
            if (StringUtils.hasText(item.getAnalysisId())) {
                builder.append("   analysisId：").append(item.getAnalysisId()).append('\n');
            }
            builder.append('\n');
        }
        return builder.toString().trim();
    }

    public String buildPracticeOverview(Long userId, Integer days) {
        Integer effectiveDays = normalizeDays(days);
        long analysisCount = analysisMapper.countByUserIdAndStatusAndDays(userId, "success", effectiveDays);
        long errorSentenceCount = itemMapper.countDistinctErrorSentencesByUserIdAndDays(userId, effectiveDays);
        Map<String, Object> topPoint = itemMapper.getMostCommonPointIdByUserIdAndDays(userId, effectiveDays);

        StringBuilder builder = new StringBuilder();
        builder.append("时间范围：").append(timeScopeLabel(effectiveDays)).append('\n');
        builder.append("- 成功分析次数：").append(analysisCount).append('\n');
        builder.append("- 有错误的句子数：").append(errorSentenceCount).append('\n');
        if (ObjectUtils.isEmpty(topPoint) || topPoint.get("pointId") == null) {
            builder.append("- 最高频知识点：暂无");
        } else {
            String pointId = String.valueOf(topPoint.get("pointId"));
            Object count = topPoint.get("count");
            PointDefinition definition = pointDictionary.resolveOrFallback(pointId);
            builder.append("- 最高频知识点：")
                    .append(definition.titleZh())
                    .append(" (")
                    .append(pointId)
                    .append(" / ")
                    .append(definition.familyId())
                    .append(")，")
                    .append(count)
                    .append(" 次");
        }
        return builder.toString().trim();
    }

    Integer normalizeDays(Integer days) {
        if (days == null || days <= 0) {
            return null;
        }
        return Math.min(days, properties.getDb().getMaxDays());
    }

    int normalizeLimit(Integer limit) {
        GrammarStatsProperties.Db db = properties.getDb();
        if (limit == null || limit <= 0) {
            return db.getDefaultExampleLimit();
        }
        return Math.min(limit, db.getMaxExampleLimit());
    }

    private static Set<String> toFilterSet(List<String> filterPointIds) {
        if (CollectionUtils.isEmpty(filterPointIds)) {
            return Set.of();
        }
        return filterPointIds.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .collect(Collectors.toSet());
    }

    private static String timeScopeLabel(Integer days) {
        return days == null ? "全部历史" : "近 " + days + " 天";
    }

    private static String emptyStatsMessage(Integer days) {
        return "（" + timeScopeLabel(days) + "暂无历史错误统计）";
    }

    private static String nullToEmpty(String value) {
        return StringUtils.hasText(value) ? value : "";
    }
}
