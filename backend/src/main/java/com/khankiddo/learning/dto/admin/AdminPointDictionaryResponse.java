package com.khankiddo.learning.dto.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminPointDictionaryResponse {

    private String version;
    private Stats stats;
    private List<DiscriminatorRow> discriminators;
    private List<ChannelSummary> channels;
    private List<FamilyView> families;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Stats {
        private int familyCount;
        private int pointCount;
        private Map<String, Integer> pointCountByChannel;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DiscriminatorRow {
        private String id;
        private String rule;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChannelSummary {
        private String channel;
        private String labelZh;
        private int familyCount;
        private int pointCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FamilyView {
        private String familyId;
        private String titleZh;
        private String channel;
        private Double fixability;
        private String otherPointId;
        private double impactWeight;
        private String habitUnit;
        private int pointCount;
        private List<PointView> points;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PointView {
        private String pointId;
        private String familyId;
        private String channel;
        private String cardKind;
        private String cardPolicy;
        private String habitUnit;
        private double impactWeight;
        private Double fixability;
        private String errorLevel;
        private String scoreProfile;
        private String titleZh;
        private boolean catchAllLeaf;
        private boolean globalFallback;
        private boolean familyOtherLeaf;
    }
}
