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
    private final Map<String, PointDefinition> pointsById;
    private final Map<String, FamilyDefinition> familiesById;
    private final List<String> allPointIds;

    private PointDictionary(
            String version,
            Map<String, PointDefinition> pointsById,
            Map<String, FamilyDefinition> familiesById) {
        this.version = version;
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
                familiesById.put(family.familyId(), family);
            }
        }

        Map<String, PointDefinition> pointsById = new LinkedHashMap<>();
        for (PointDefinition point : document.points()) {
            pointsById.put(point.pointId(), point);
        }

        if (!pointsById.containsKey(FALLBACK_POINT_ID)) {
            throw new IllegalStateException("知识点字典缺少兜底 pointId: " + FALLBACK_POINT_ID);
        }

        return new PointDictionary(document.version(), pointsById, familiesById);
    }

    public String version() {
        return version;
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
            List<FamilyDefinition> families,
            List<PointDefinition> points
    ) {
    }
}
