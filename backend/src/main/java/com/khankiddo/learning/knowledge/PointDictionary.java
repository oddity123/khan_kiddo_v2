package com.khankiddo.learning.knowledge;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class PointDictionary {

    public static final String FALLBACK_POINT_ID = "STRUCTURE_OTHER";

    private final String version;
    private final List<PointDiscriminator> discriminators;
    private final Map<String, PointDefinition> pointsById;
    private final Map<String, FamilyDefinition> familiesById;
    private final List<String> allPointIds;

    private PointDictionary(
            String version,
            List<PointDiscriminator> discriminators,
            Map<String, PointDefinition> pointsById,
            Map<String, FamilyDefinition> familiesById) {
        this.version = version;
        this.discriminators = List.copyOf(discriminators);
        this.pointsById = Map.copyOf(pointsById);
        this.familiesById = Map.copyOf(familiesById);
        this.allPointIds = List.copyOf(pointsById.keySet());
    }

    public static PointDictionary loadFromClasspath(String classpathLocation) {
        if (!StringUtils.hasText(classpathLocation)) {
            throw new IllegalArgumentException("classpathLocation is required");
        }
        try {
            ClassPathResource resource = new ClassPathResource(classpathLocation);
            try (InputStream inputStream = resource.getInputStream()) {
                ObjectMapper objectMapper = new ObjectMapper();
                PointDictionaryDocument document =
                        objectMapper.readValue(inputStream, PointDictionaryDocument.class);
                return fromDocument(document);
            }
        } catch (Exception e) {
            throw new IllegalStateException("加载知识点字典失败: " + classpathLocation, e);
        }
    }

    private static PointDictionary fromDocument(PointDictionaryDocument document) {
        if (document == null || CollectionUtils.isEmpty(document.points())) {
            throw new IllegalStateException("知识点字典缺少 points");
        }

        Map<String, FamilyDefinition> familiesById = new LinkedHashMap<>();
        if (!CollectionUtils.isEmpty(document.families())) {
            for (FamilyDefinition family : document.families()) {
                if (family == null || !StringUtils.hasText(family.familyId())) {
                    throw new IllegalStateException("知识点字典存在空白 familyId");
                }
                String familyId = family.familyId().trim();
                if (familiesById.containsKey(familyId)) {
                    throw new IllegalStateException("知识点字典重复 familyId: " + familyId);
                }
                familiesById.put(familyId, family);
            }
        }

        Map<String, PointDefinition> pointsById = new LinkedHashMap<>();
        for (PointDefinition point : document.points()) {
            if (point == null || !StringUtils.hasText(point.pointId())) {
                throw new IllegalStateException("知识点字典存在空白 pointId");
            }
            String pointId = point.pointId().trim();
            if (pointsById.containsKey(pointId)) {
                throw new IllegalStateException("知识点字典重复 pointId: " + pointId);
            }
            if (!StringUtils.hasText(point.familyId()) || !familiesById.containsKey(point.familyId().trim())) {
                throw new IllegalStateException(
                        "知识点字典 pointId=" + pointId + " 引用了不存在的 familyId: " + point.familyId());
            }
            pointsById.put(pointId, point);
        }

        if (!pointsById.containsKey(FALLBACK_POINT_ID)) {
            throw new IllegalStateException("知识点字典缺少兜底 pointId: " + FALLBACK_POINT_ID);
        }

        for (FamilyDefinition family : familiesById.values()) {
            if (!StringUtils.hasText(family.otherPointId())) {
                throw new IllegalStateException("知识点字典 familyId=" + family.familyId() + " 缺少 otherPointId");
            }
            String otherPointId = family.otherPointId().trim();
            if (!pointsById.containsKey(otherPointId)) {
                throw new IllegalStateException(
                        "知识点字典 familyId=" + family.familyId()
                                + " 的 otherPointId=" + otherPointId + " 不存在于 points");
            }
        }

        return new PointDictionary(
                document.version(),
                document.discriminators() == null ? List.of() : document.discriminators(),
                pointsById,
                familiesById);
    }

    public String version() {
        return version;
    }

    public List<PointDiscriminator> discriminators() {
        return discriminators;
    }

    public PointDefinition require(String pointId) {
        if (!StringUtils.hasText(pointId)) {
            throw new IllegalArgumentException("pointId is required");
        }
        PointDefinition point = pointsById.get(pointId.trim());
        if (point == null) {
            throw new IllegalArgumentException("Unknown pointId: " + pointId);
        }
        return point;
    }

    public PointDefinition resolveOrFallback(String pointId) {
        if (!StringUtils.hasText(pointId)) {
            return require(FALLBACK_POINT_ID);
        }
        PointDefinition point = pointsById.get(pointId.trim());
        if (point != null) {
            return point;
        }
        return require(FALLBACK_POINT_ID);
    }

    public List<String> allPointIds() {
        return allPointIds;
    }

    public Map<String, PointDefinition> pointsById() {
        return Collections.unmodifiableMap(pointsById);
    }

    public Map<String, FamilyDefinition> familiesById() {
        return Collections.unmodifiableMap(familiesById);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record PointDictionaryDocument(
            String version,
            List<PointDiscriminator> discriminators,
            List<FamilyDefinition> families,
            List<PointDefinition> points
    ) {
    }
}
