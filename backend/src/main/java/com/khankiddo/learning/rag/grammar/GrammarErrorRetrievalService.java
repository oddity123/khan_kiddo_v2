package com.khankiddo.learning.rag.grammar;

import com.khankiddo.learning.config.condition.OnGrammarErrorRagCondition;
import com.khankiddo.learning.model.enums.ProblemType;
import com.khankiddo.learning.rag.core.QdrantEmbeddingStoreFactory;
import com.khankiddo.learning.rag.core.UserScopedVectorRetriever;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Agent Router 检索入口：意图识别 -> 类型精确召回 + 语义粗召回 -> 混合重排 -> 可选统计摘要。
 */
@Service
@Conditional(OnGrammarErrorRagCondition.class)
public class GrammarErrorRetrievalService {

    private final UserScopedVectorRetriever vectorRetriever;
    private final GrammarErrorRagProperties properties;
    private final GrammarErrorQueryIntentAnalyzer intentAnalyzer;
    private final GrammarErrorHybridRanker hybridRanker;
    private final GrammarErrorStatsService statsService;
    private final EmbeddingStore<TextSegment> embeddingStore;

    public GrammarErrorRetrievalService(
            UserScopedVectorRetriever vectorRetriever,
            GrammarErrorRagProperties properties,
            GrammarErrorQueryIntentAnalyzer intentAnalyzer,
            GrammarErrorHybridRanker hybridRanker,
            GrammarErrorStatsService statsService,
            @Qualifier(QdrantEmbeddingStoreFactory.GRAMMAR_ERROR_EMBEDDING_STORE)
            EmbeddingStore<TextSegment> embeddingStore) {
        this.vectorRetriever = vectorRetriever;
        this.properties = properties;
        this.intentAnalyzer = intentAnalyzer;
        this.hybridRanker = hybridRanker;
        this.statsService = statsService;
        this.embeddingStore = embeddingStore;
    }

    public GrammarErrorRetrievalResult retrieveForChat(Long userId, String query) {
        GrammarErrorRetrievalIntent intent = intentAnalyzer.analyze(query);
        List<EmbeddingMatch<TextSegment>> matches = retrieveMatches(userId, query, intent);
        List<String> matchLabels = matches.stream()
                .map(match -> hybridRanker.resolveMatchLabel(match, intent))
                .toList();
        String statsSummary = null;
        if (intent.includeStats()) {
            statsSummary = statsService.buildStatsSummary(userId, intent.primaryTypes());
        }
        return new GrammarErrorRetrievalResult(
                intent,
                describeStrategy(intent),
                statsSummary,
                matches,
                matchLabels);
    }

    private List<EmbeddingMatch<TextSegment>> retrieveMatches(
            Long userId,
            String query,
            GrammarErrorRetrievalIntent intent) {
        int maxResults = properties.getRetrievalMaxResults();
        int poolSize = properties.getRetrievalCandidatePoolSize();
        double minScore = properties.getRetrievalMinScore();

        List<EmbeddingMatch<TextSegment>> semanticCandidates = vectorRetriever.search(
                embeddingStore, userId, query, poolSize, minScore);

        if (CollectionUtils.isEmpty(intent.primaryTypes())) {
            return hybridRanker.rerank(semanticCandidates, intent, query, maxResults);
        }
        return mergeTypedRetrieval(userId, query, intent, semanticCandidates, maxResults);
    }

    private List<EmbeddingMatch<TextSegment>> mergeTypedRetrieval(
            Long userId,
            String query,
            GrammarErrorRetrievalIntent intent,
            List<EmbeddingMatch<TextSegment>> semanticCandidates,
            int maxResults) {
        int maxSecondary = properties.getRetrievalMaxSecondaryResults();
        int poolSize = properties.getRetrievalCandidatePoolSize();

        List<EmbeddingMatch<TextSegment>> primaryPool = vectorRetriever.searchByProblemTypes(
                embeddingStore,
                userId,
                query,
                intent.primaryTypes(),
                poolSize,
                0.0);
        List<EmbeddingMatch<TextSegment>> primaryRanked = hybridRanker.rerank(
                primaryPool, intent, query, maxResults);

        Set<String> seenIds = new LinkedHashSet<>();
        List<EmbeddingMatch<TextSegment>> result = new ArrayList<>();

        for (EmbeddingMatch<TextSegment> match : primaryRanked) {
            if (seenIds.add(match.embeddingId())) {
                result.add(match);
            }
        }

        List<EmbeddingMatch<TextSegment>> secondaryCandidates = semanticCandidates.stream()
                .filter(match -> !seenIds.contains(match.embeddingId()))
                .filter(match -> hybridRanker.matchesSecondaryType(match, intent))
                .filter(match -> !hybridRanker.matchesPrimaryType(match, intent))
                .toList();
        List<EmbeddingMatch<TextSegment>> secondaryRanked = hybridRanker.rerank(
                secondaryCandidates, intent, query, maxSecondary);
        for (EmbeddingMatch<TextSegment> match : secondaryRanked) {
            if (seenIds.add(match.embeddingId())) {
                result.add(match);
            }
        }

        if (result.size() < maxResults) {
            List<EmbeddingMatch<TextSegment>> remainder = semanticCandidates.stream()
                    .filter(match -> !seenIds.contains(match.embeddingId()))
                    .toList();
            List<EmbeddingMatch<TextSegment>> filler = hybridRanker.rerank(
                    remainder, intent, query, maxResults - result.size());
            for (EmbeddingMatch<TextSegment> match : filler) {
                if (seenIds.add(match.embeddingId())) {
                    result.add(match);
                }
            }
        }

        return result.stream().limit(maxResults).toList();
    }

    private String describeStrategy(GrammarErrorRetrievalIntent intent) {
        String primary = formatTypes(intent.primaryTypes());
        String secondary = formatTypes(intent.secondaryTypes());
        return switch (intent.kind()) {
            case CHINGLISH -> "优先检索中式英语（Chinglish），其次表达生硬、用词不当、搭配与句式结构问题";
            case UNNATURAL -> "优先检索表达生硬（Unnatural），其次中式英语与用词搭配问题";
            case ARTICLE -> "优先检索冠词错误（Article）";
            case TENSE -> "优先检索时态错误（Tense）";
            case GENERAL_SUMMARY -> "结合历史错误类型统计与代表性错句样本";
            case SEMANTIC -> CollectionUtils.isEmpty(intent.primaryTypes())
                    ? "语义向量检索"
                    : "语义向量检索，优先类型：" + primary;
        } + (StringUtils.hasText(secondary) ? "；次要类型：" + secondary : "");
    }

    private String formatTypes(List<String> types) {
        if (CollectionUtils.isEmpty(types)) {
            return "";
        }
        return types.stream()
                .map(type -> {
                    ProblemType problemType = ProblemType.fromEnglishName(type);
                    return problemType != null
                            ? problemType.getChineseName() + " (" + type + ")"
                            : type;
                })
                .collect(Collectors.joining("、"));
    }
}
