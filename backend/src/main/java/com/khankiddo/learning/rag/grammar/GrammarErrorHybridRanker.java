package com.khankiddo.learning.rag.grammar;

import com.khankiddo.learning.config.condition.OnGrammarErrorRagCondition;
import com.khankiddo.learning.model.enums.ProblemType;
import com.khankiddo.learning.rag.core.RagMetadataKeys;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 混合重排：向量分 + 错误类型加权 + 关键词加权。
 */
@Component
@Conditional(OnGrammarErrorRagCondition.class)
public class GrammarErrorHybridRanker {

    private static final double PRIMARY_TYPE_BOOST = 0.35;
    private static final double SECONDARY_TYPE_BOOST = 0.12;
    private static final double KEYWORD_BOOST = 0.08;
    private static final double TOPIC_DRIFT_PENALTY = 0.10;

    public List<EmbeddingMatch<TextSegment>> rerank(
            List<EmbeddingMatch<TextSegment>> candidates,
            GrammarErrorRetrievalIntent intent,
            String query,
            int limit) {
        if (CollectionUtils.isEmpty(candidates)) {
            return List.of();
        }
        int effectiveLimit = Math.max(limit, 1);
        return candidates.stream()
                .map(match -> new ScoredMatch(match, computeScore(match, intent, query)))
                .sorted(Comparator.comparingDouble(ScoredMatch::score).reversed())
                .limit(effectiveLimit)
                .map(ScoredMatch::match)
                .collect(Collectors.toList());
    }

    double computeScore(EmbeddingMatch<TextSegment> match, GrammarErrorRetrievalIntent intent, String query) {
        double score = match.score() != null ? match.score() : 0.0;
        TextSegment segment = match.embedded();
        String text = segment != null ? segment.text() : "";
        String textLower = text.toLowerCase(Locale.ROOT);
        Set<String> problemTypes = extractProblemTypes(segment);

        for (String primary : intent.primaryTypes()) {
            if (problemTypes.contains(primary)) {
                score += PRIMARY_TYPE_BOOST;
            }
        }
        for (String secondary : intent.secondaryTypes()) {
            if (problemTypes.contains(secondary)) {
                score += SECONDARY_TYPE_BOOST;
            }
        }
        for (String keyword : intent.expandedKeywords()) {
            if (StringUtils.hasText(keyword) && containsKeyword(textLower, keyword)) {
                score += KEYWORD_BOOST;
            }
        }
        if (intent.kind() == GrammarErrorQueryKind.CHINGLISH && shouldPenalizeTopicDrift(problemTypes, textLower)) {
            score -= TOPIC_DRIFT_PENALTY;
        }
        if (StringUtils.hasText(query)) {
            String queryLower = query.toLowerCase(Locale.ROOT);
            if (textLower.contains(queryLower) && queryLower.length() >= 4) {
                score += 0.05;
            }
        }
        return score;
    }

    public boolean matchesPrimaryType(EmbeddingMatch<TextSegment> match, GrammarErrorRetrievalIntent intent) {
        if (CollectionUtils.isEmpty(intent.primaryTypes())) {
            return false;
        }
        Set<String> problemTypes = extractProblemTypes(match.embedded());
        return intent.primaryTypes().stream().anyMatch(problemTypes::contains);
    }

    public boolean matchesSecondaryType(EmbeddingMatch<TextSegment> match, GrammarErrorRetrievalIntent intent) {
        if (CollectionUtils.isEmpty(intent.secondaryTypes())) {
            return false;
        }
        Set<String> problemTypes = extractProblemTypes(match.embedded());
        return intent.secondaryTypes().stream().anyMatch(problemTypes::contains);
    }

    public String resolveMatchLabel(EmbeddingMatch<TextSegment> match, GrammarErrorRetrievalIntent intent) {
        if (matchesPrimaryType(match, intent)) {
            return "主类型匹配";
        }
        if (matchesSecondaryType(match, intent)) {
            return "补充参考";
        }
        return "语义相关";
    }

    private boolean shouldPenalizeTopicDrift(Set<String> problemTypes, String textLower) {
        boolean hasChinglishType = problemTypes.contains(ProblemType.CHINGLISH.getEnglishName());
        if (hasChinglishType) {
            return false;
        }
        boolean topicChina = textLower.contains("china") || textLower.contains("chinese");
        return topicChina && !textLower.contains("chinglish");
    }

    private boolean containsKeyword(String textLower, String keyword) {
        String normalized = keyword.toLowerCase(Locale.ROOT).trim();
        if (!StringUtils.hasText(normalized)) {
            return false;
        }
        if (normalized.length() <= 3 && normalized.matches("[a-z]+")) {
            return textLower.matches(".*\\b" + java.util.regex.Pattern.quote(normalized) + "\\b.*");
        }
        return textLower.contains(normalized);
    }

    private Set<String> extractProblemTypes(TextSegment segment) {
        if (segment == null || segment.metadata() == null) {
            return Set.of();
        }
        String raw = segment.metadata().getString(RagMetadataKeys.PROBLEM_TYPES);
        if (!StringUtils.hasText(raw)) {
            return Set.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .collect(Collectors.toCollection(HashSet::new));
    }

    private record ScoredMatch(EmbeddingMatch<TextSegment> match, double score) {
    }
}
