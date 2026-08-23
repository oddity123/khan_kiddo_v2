package com.khankiddo.learning.service.impl;

import com.khankiddo.learning.dto.admin.AdminPointDictionaryResponse;
import com.khankiddo.learning.knowledge.CardPolicy;
import com.khankiddo.learning.knowledge.FamilyDefinition;
import com.khankiddo.learning.knowledge.PointChannel;
import com.khankiddo.learning.knowledge.PointDefinition;
import com.khankiddo.learning.knowledge.PointDictionary;
import com.khankiddo.learning.knowledge.PointDiscriminator;
import com.khankiddo.learning.security.SecurityUtils;
import com.khankiddo.learning.service.AdminKnowledgeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AdminKnowledgeServiceImpl implements AdminKnowledgeService {

    private static final Map<PointChannel, String> CHANNEL_LABELS = Map.of(
            PointChannel.RULE, "语法规则",
            PointChannel.FLUENCY, "口语流利度",
            PointChannel.LEXICAL, "词汇缺口",
            PointChannel.CHINESE, "中文夹杂"
    );

    private final PointDictionary pointDictionary;

    @Override
    public AdminPointDictionaryResponse getPointDictionary() {
        SecurityUtils.requireAdmin();

        Map<String, Integer> pointCountByChannel = new LinkedHashMap<>();
        for (PointChannel channel : PointChannel.values()) {
            pointCountByChannel.put(channel.getJsonValue(), 0);
        }

        List<AdminPointDictionaryResponse.FamilyView> families = new ArrayList<>();
        for (FamilyDefinition family : pointDictionary.familiesById().values()) {
            List<AdminPointDictionaryResponse.PointView> points = pointDictionary.pointsById().values().stream()
                    .filter(point -> family.familyId().equals(point.familyId()))
                    .map(this::toPointView)
                    .toList();
            for (AdminPointDictionaryResponse.PointView point : points) {
                pointCountByChannel.merge(point.getChannel(), 1, Integer::sum);
            }
            families.add(AdminPointDictionaryResponse.FamilyView.builder()
                    .familyId(family.familyId())
                    .titleZh(family.titleZh())
                    .channel(family.channel().getJsonValue())
                    .fixability(family.fixability())
                    .otherPointId(family.otherPointId())
                    .impactWeight(family.impactWeight())
                    .habitUnit(family.habitUnit().name().toLowerCase())
                    .pointCount(points.size())
                    .points(points)
                    .build());
        }

        Map<PointChannel, int[]> channelStats = new EnumMap<>(PointChannel.class);
        for (PointChannel channel : PointChannel.values()) {
            channelStats.put(channel, new int[]{0, 0});
        }
        for (AdminPointDictionaryResponse.FamilyView family : families) {
            PointChannel channel = PointChannel.fromJson(family.getChannel());
            int[] stats = channelStats.get(channel);
            stats[0]++;
            stats[1] += family.getPointCount();
        }

        List<AdminPointDictionaryResponse.ChannelSummary> channels = new ArrayList<>();
        for (PointChannel channel : PointChannel.values()) {
            int[] stats = channelStats.get(channel);
            channels.add(AdminPointDictionaryResponse.ChannelSummary.builder()
                    .channel(channel.getJsonValue())
                    .labelZh(CHANNEL_LABELS.get(channel))
                    .familyCount(stats[0])
                    .pointCount(stats[1])
                    .build());
        }

        List<AdminPointDictionaryResponse.DiscriminatorRow> discriminators = pointDictionary.discriminators().stream()
                .map(row -> AdminPointDictionaryResponse.DiscriminatorRow.builder()
                        .id(row.id())
                        .rule(row.rule())
                        .build())
                .toList();

        return AdminPointDictionaryResponse.builder()
                .version(pointDictionary.version())
                .stats(AdminPointDictionaryResponse.Stats.builder()
                        .familyCount(families.size())
                        .pointCount(pointDictionary.allPointIds().size())
                        .pointCountByChannel(pointCountByChannel)
                        .build())
                .discriminators(discriminators)
                .channels(channels)
                .families(families)
                .build();
    }

    private AdminPointDictionaryResponse.PointView toPointView(PointDefinition point) {
        FamilyDefinition family = pointDictionary.familiesById().get(point.familyId());
        boolean familyOtherLeaf = family != null
                && StringUtils.hasText(family.otherPointId())
                && family.otherPointId().equals(point.pointId());
        boolean catchAllLeaf = isCatchAllLeaf(point, familyOtherLeaf);
        return AdminPointDictionaryResponse.PointView.builder()
                .pointId(point.pointId())
                .familyId(point.familyId())
                .channel(point.channel().getJsonValue())
                .cardKind(point.cardKind().getJsonValue())
                .cardPolicy(point.cardPolicy().getJsonValue())
                .habitUnit(point.habitUnit().name().toLowerCase())
                .impactWeight(point.impactWeight())
                .fixability(point.fixability())
                .errorLevel(point.errorLevel())
                .scoreProfile(point.scoreProfile())
                .titleZh(point.titleZh())
                .catchAllLeaf(catchAllLeaf)
                .globalFallback(PointDictionary.FALLBACK_POINT_ID.equals(point.pointId()))
                .familyOtherLeaf(familyOtherLeaf)
                .build();
    }

    private boolean isCatchAllLeaf(PointDefinition point, boolean familyOtherLeaf) {
        if ("WORD_FORM_POS".equals(point.pointId()) || point.pointId().endsWith("_OTHER")) {
            return true;
        }
        return familyOtherLeaf;
    }
}
