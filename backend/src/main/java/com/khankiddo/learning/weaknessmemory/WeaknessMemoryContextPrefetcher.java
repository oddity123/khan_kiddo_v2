package com.khankiddo.learning.weaknessmemory;

import com.khankiddo.learning.ai.grammar.GrammarLearningDbService;
import com.khankiddo.learning.config.WeaknessMemoryProperties;
import com.khankiddo.learning.dto.conversation.ActionCardDto;
import com.khankiddo.learning.growth.GrowthCardAnalysisSupport;
import com.khankiddo.learning.knowledge.HabitCardScorer;
import com.khankiddo.learning.knowledge.KnowledgePointStatsSupport;
import com.khankiddo.learning.knowledge.PointDictionary;
import com.khankiddo.learning.mapper.ConversationAnalysisItemMapper;
import com.khankiddo.learning.mapper.ConversationAnalysisMapper;
import com.khankiddo.learning.mapper.UserWeaknessMemoryMapper;
import com.khankiddo.learning.model.ConversationAnalysis;
import com.khankiddo.learning.model.ConversationAnalysisItem;
import com.khankiddo.learning.model.UserWeaknessMemory;
import com.khankiddo.learning.rag.grammar.GrammarErrorSemanticRetrieverConfig;
import com.khankiddo.learning.rag.grammar.GrammarRagQuerySupport;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 确定性预查：ACTIVE 记忆 + 本场行动卡 + 每卡跨场 SQL/向量证据（排除本场）。
 */
@Slf4j
@Component
public class WeaknessMemoryContextPrefetcher {

    private final ConversationAnalysisMapper analysisMapper;
    private final ConversationAnalysisItemMapper itemMapper;
    private final UserWeaknessMemoryMapper memoryMapper;
    private final GrowthCardAnalysisSupport analysisSupport;
    private final GrammarLearningDbService dbService;
    private final PointDictionary pointDictionary;
    private final WeaknessMemoryProperties properties;
    private final ObjectProvider<ContentRetriever> retrieverProvider;

    public WeaknessMemoryContextPrefetcher(
            ConversationAnalysisMapper analysisMapper,
            ConversationAnalysisItemMapper itemMapper,
            UserWeaknessMemoryMapper memoryMapper,
            GrowthCardAnalysisSupport analysisSupport,
            GrammarLearningDbService dbService,
            PointDictionary pointDictionary,
            WeaknessMemoryProperties properties,
            @Qualifier(GrammarErrorSemanticRetrieverConfig.GRAMMAR_ERROR_SEMANTIC_CONTENT_RETRIEVER)
            ObjectProvider<ContentRetriever> retrieverProvider) {
        this.analysisMapper = analysisMapper;
        this.itemMapper = itemMapper;
        this.memoryMapper = memoryMapper;
        this.analysisSupport = analysisSupport;
        this.dbService = dbService;
        this.pointDictionary = pointDictionary;
        this.properties = properties;
        this.retrieverProvider = retrieverProvider;
    }

    public Optional<WeaknessMemoryPrefetchContext> prefetch(Long userId, String analysisId) {
        if (userId == null || !StringUtils.hasText(analysisId)) {
            return Optional.empty();
        }
        Optional<ConversationAnalysis> analysisOpt =
                analysisMapper.findByAnalysisIdAndUserId(analysisId, userId);
        if (analysisOpt.isEmpty()) {
            log.warn("weakness prefetch skipped: analysis not found analysisId={} userId={}",
                    analysisId, userId);
            return Optional.empty();
        }

        List<ConversationAnalysisItem> rows = itemMapper.findByAnalysisId(analysisId);
        HabitCardScorer.HabitScoreResult scoreResult = analysisSupport.score(
                CollectionUtils.isEmpty(rows) ? List.of() : rows);
        List<ActionCardDto> actionCards = scoreResult.actionCards();
        if (CollectionUtils.isEmpty(actionCards)) {
            return Optional.empty();
        }

        List<UserWeaknessMemory> active = memoryMapper.findActiveByUserId(userId);
        List<WeaknessMemoryPrefetchContext.CardEvidence> evidences = new ArrayList<>();
        for (ActionCardDto card : actionCards) {
            evidences.add(buildCardEvidence(userId, analysisId, card));
        }
        return Optional.of(new WeaknessMemoryPrefetchContext(
                userId, analysisId, active == null ? List.of() : active, actionCards, evidences));
    }

    private WeaknessMemoryPrefetchContext.CardEvidence buildCardEvidence(
            Long userId, String analysisId, ActionCardDto card) {
        List<String> pointIds = resolvePointFilters(card);
        List<String> familyIds = resolveFamilyFilters(card);
        Integer days = properties.getPrefetchHistoryDays();
        int exampleLimit = Math.max(1, properties.getPrefetchExampleLimit());

        String stats = dbService.buildStatsSummary(userId, pointIds, familyIds, days);
        List<ConversationAnalysisItem> historical = itemMapper.findErrorExamplesByUserId(
                userId,
                CollectionUtils.isEmpty(pointIds) ? null : pointIds,
                days,
                exampleLimit,
                analysisId);
        List<WeaknessMemoryPrefetchContext.ExampleLine> examples = historical.stream()
                .map(item -> new WeaknessMemoryPrefetchContext.ExampleLine(
                        item.getAnalysisId(),
                        item.getOriginalSentence(),
                        item.getSuggestion()))
                .toList();

        List<String> similar = retrieveSimilar(userId, analysisId, card);
        return new WeaknessMemoryPrefetchContext.CardEvidence(
                card.getHabitKey(),
                card.getPointId(),
                stats,
                examples,
                similar);
    }

    private List<String> retrieveSimilar(Long userId, String analysisId, ActionCardDto card) {
        ContentRetriever retriever = retrieverProvider.getIfAvailable();
        if (retriever == null || ObjectUtils.isEmpty(card) || CollectionUtils.isEmpty(card.getExamples())) {
            return List.of();
        }
        String queryText = card.getExamples().stream()
                .map(ActionCardDto.ExampleDto::getOriginalSentence)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse(null);
        if (!StringUtils.hasText(queryText)) {
            return List.of();
        }
        try {
            int max = Math.max(1, properties.getPrefetchSimilarLimit());
            List<Content> contents = retriever.retrieve(
                    GrammarRagQuerySupport.query(userId, queryText.trim(), max + 2));
            if (CollectionUtils.isEmpty(contents)) {
                return List.of();
            }
            List<String> snippets = new ArrayList<>();
            for (Content content : contents) {
                if (content == null || content.textSegment() == null) {
                    continue;
                }
                String text = content.textSegment().text();
                if (!StringUtils.hasText(text)) {
                    continue;
                }
                // 粗过滤：片段含本场 analysisId 则跳过，避免自命中
                if (text.contains(analysisId)) {
                    continue;
                }
                snippets.add(text.trim());
                if (snippets.size() >= max) {
                    break;
                }
            }
            return snippets;
        } catch (Exception ex) {
            log.warn("weakness similar retrieve failed analysisId={}: {}", analysisId, ex.getMessage());
            return List.of();
        }
    }

    private List<String> resolvePointFilters(ActionCardDto card) {
        if (ObjectUtils.isEmpty(card)) {
            return List.of();
        }
        String habitKey = card.getHabitKey();
        if (StringUtils.hasText(habitKey) && habitKey.startsWith("FAM_")) {
            return KnowledgePointStatsSupport.expandFamilyIds(pointDictionary, List.of(habitKey.trim()));
        }
        if (StringUtils.hasText(card.getPointId())) {
            return List.of(card.getPointId().trim());
        }
        if (StringUtils.hasText(habitKey)) {
            return List.of(habitKey.trim());
        }
        return List.of();
    }

    private static List<String> resolveFamilyFilters(ActionCardDto card) {
        if (ObjectUtils.isEmpty(card) || !StringUtils.hasText(card.getHabitKey())) {
            return List.of();
        }
        String habitKey = card.getHabitKey().trim();
        if (habitKey.startsWith("FAM_")) {
            return List.of(habitKey);
        }
        return List.of();
    }
}
