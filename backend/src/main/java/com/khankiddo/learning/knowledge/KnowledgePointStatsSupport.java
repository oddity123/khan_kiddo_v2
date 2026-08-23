package com.khankiddo.learning.knowledge;

import com.khankiddo.learning.dto.KnowledgeFamilyStat;
import com.khankiddo.learning.model.PointIdCount;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 将 {@code point_id} 计数聚合为语法 family 统计（服务层反查字典，不落库 family_id）。
 */
public final class KnowledgePointStatsSupport {

    private KnowledgePointStatsSupport() {
    }

    public static List<String> seriousPointIds(PointDictionary dictionary) {
        return dictionary.pointsById().values().stream()
                .filter(point -> {
                    String level = PointScoringSupport.errorLevel(point);
                    return "FATAL".equals(level) || "BASIC".equals(level);
                })
                .map(PointDefinition::pointId)
                .sorted()
                .toList();
    }

    public static List<String> expandFamilyIds(PointDictionary dictionary, List<String> familyIds) {
        if (CollectionUtils.isEmpty(familyIds)) {
            return List.of();
        }
        return dictionary.pointsById().values().stream()
                .filter(point -> familyIds.contains(point.familyId()))
                .map(PointDefinition::pointId)
                .distinct()
                .toList();
    }

    public static List<String> mergePointFilters(
            PointDictionary dictionary, List<String> pointIds, List<String> familyIds) {
        List<String> expandedFamilies = expandFamilyIds(dictionary, familyIds);
        if (CollectionUtils.isEmpty(pointIds) && CollectionUtils.isEmpty(expandedFamilies)) {
            return List.of();
        }
        if (CollectionUtils.isEmpty(pointIds)) {
            return expandedFamilies;
        }
        if (CollectionUtils.isEmpty(expandedFamilies)) {
            return pointIds.stream().filter(StringUtils::hasText).map(String::trim).distinct().toList();
        }
        return pointIds.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .filter(expandedFamilies::contains)
                .distinct()
                .toList();
    }

    public static List<KnowledgeFamilyStat> aggregateFamilies(
            List<PointIdCount> rows, PointDictionary dictionary, int limit) {
        if (CollectionUtils.isEmpty(rows)) {
            return List.of();
        }
        Map<String, Long> counts = new LinkedHashMap<>();
        for (PointIdCount row : rows) {
            if (row == null || !StringUtils.hasText(row.getPointId())) {
                continue;
            }
            PointDefinition definition = dictionary.resolveOrFallback(row.getPointId());
            long increment = row.getCount() == null ? 0L : row.getCount();
            counts.merge(definition.familyId(), increment, Long::sum);
        }
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed()
                        .thenComparing(Map.Entry.comparingByKey()))
                .limit(limit > 0 ? limit : Long.MAX_VALUE)
                .map(entry -> toFamilyStat(entry.getKey(), entry.getValue(), dictionary))
                .toList();
    }

    public static KnowledgeFamilyStat topFamily(List<PointIdCount> rows, PointDictionary dictionary) {
        List<KnowledgeFamilyStat> stats = aggregateFamilies(rows, dictionary, 1);
        return stats.isEmpty() ? null : stats.get(0);
    }

    public static String familyTitle(PointDictionary dictionary, String familyId) {
        if (!StringUtils.hasText(familyId)) {
            return "—";
        }
        FamilyDefinition family = dictionary.familiesById().get(familyId.trim());
        return family != null && StringUtils.hasText(family.titleZh()) ? family.titleZh() : familyId;
    }

    private static KnowledgeFamilyStat toFamilyStat(String familyId, long count, PointDictionary dictionary) {
        return KnowledgeFamilyStat.builder()
                .familyId(familyId)
                .label(familyTitle(dictionary, familyId))
                .count(count)
                .build();
    }
}
