package com.khankiddo.learning.rag.grammar;

import com.khankiddo.learning.config.condition.OnGrammarErrorRagCondition;
import com.khankiddo.learning.dto.grammar.GrammarErrorSearchRequest;
import com.khankiddo.learning.dto.grammar.GrammarErrorSearchResponse;
import com.khankiddo.learning.exception.UnauthorizedException;
import com.khankiddo.learning.model.enums.ProblemType;
import com.khankiddo.learning.rag.core.QdrantEmbeddingStoreFactory;
import com.khankiddo.learning.rag.core.RagMetadataKeys;
import com.khankiddo.learning.rag.core.UserScopedVectorRetriever;
import com.khankiddo.learning.security.SecurityUtils;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Conditional(OnGrammarErrorRagCondition.class)
public class GrammarErrorSearchService {

    private static final Pattern ORIGINAL_SENTENCE = Pattern.compile("原句:\\s*(.+)");
    private static final Pattern SUGGESTION = Pattern.compile("建议:\\s*(.+)");
    private static final DateTimeFormatter ISO_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final UserScopedVectorRetriever vectorRetriever;
    private final GrammarErrorRagProperties properties;
    private final EmbeddingStore<TextSegment> embeddingStore;

    public GrammarErrorSearchService(
            UserScopedVectorRetriever vectorRetriever,
            GrammarErrorRagProperties properties,
            @Qualifier(QdrantEmbeddingStoreFactory.GRAMMAR_ERROR_EMBEDDING_STORE)
            EmbeddingStore<TextSegment> embeddingStore) {
        this.vectorRetriever = vectorRetriever;
        this.properties = properties;
        this.embeddingStore = embeddingStore;
    }

    public GrammarErrorSearchResponse search(GrammarErrorSearchRequest request) {
        Long userId = requireUserId();
        int limit = resolveLimit(request.getLimit());
        List<EmbeddingMatch<TextSegment>> matches = vectorRetriever.search(
                embeddingStore,
                userId,
                request.getQuery(),
                limit,
                properties.getRetrievalMinScore());
        List<GrammarErrorSearchResponse.Item> items = new ArrayList<>();
        for (EmbeddingMatch<TextSegment> match : matches) {
            GrammarErrorSearchResponse.Item item = toItem(match);
            if (matchesProblemTypes(item, request.getProblemTypes())) {
                items.add(item);
            }
        }
        return GrammarErrorSearchResponse.builder().items(items).build();
    }

    public List<EmbeddingMatch<TextSegment>> retrieveForChat(Long userId, String query) {
        return vectorRetriever.search(
                embeddingStore,
                userId,
                query,
                properties.getRetrievalMaxResults(),
                properties.getRetrievalMinScore());
    }

    private GrammarErrorSearchResponse.Item toItem(EmbeddingMatch<TextSegment> match) {
        TextSegment segment = match.embedded();
        String text = segment.text();
        String analysisId = segment.metadata().getString(RagMetadataKeys.ANALYSIS_ID);
        String sentenceIdRaw = segment.metadata().getString(RagMetadataKeys.SENTENCE_ID);
        Long sentenceId = StringUtils.hasText(sentenceIdRaw) ? Long.parseLong(sentenceIdRaw) : null;
        String problemTypesRaw = segment.metadata().getString(RagMetadataKeys.PROBLEM_TYPES);
        List<String> problemTypes = splitCsv(problemTypesRaw);
        String createdAtRaw = segment.metadata().getString(RagMetadataKeys.CREATED_AT);
        LocalDateTime createdAt = parseCreatedAt(createdAtRaw);
        return GrammarErrorSearchResponse.Item.builder()
                .analysisId(analysisId)
                .sentenceId(sentenceId)
                .originalSentence(extractLine(ORIGINAL_SENTENCE, text))
                .problemTypes(problemTypes)
                .errorPoints(extractErrorPoints(text))
                .suggestion(extractLine(SUGGESTION, text))
                .score(match.score())
                .createdAt(createdAt)
                .build();
    }

    private List<String> extractErrorPoints(String text) {
        for (String line : text.split("\n")) {
            if (line.startsWith("错误点:")) {
                String value = line.substring("错误点:".length()).trim();
                if (!StringUtils.hasText(value)) {
                    return List.of();
                }
                return Arrays.stream(value.split(";"))
                        .map(String::trim)
                        .filter(StringUtils::hasText)
                        .toList();
            }
        }
        return List.of();
    }

    private String extractLine(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return "";
    }

    private List<String> splitCsv(String raw) {
        if (!StringUtils.hasText(raw)) {
            return List.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
    }

    private LocalDateTime parseCreatedAt(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            return LocalDateTime.parse(raw, ISO_FORMAT);
        } catch (Exception ex) {
            return null;
        }
    }

    private boolean matchesProblemTypes(GrammarErrorSearchResponse.Item item, List<String> requestedTypes) {
        if (CollectionUtils.isEmpty(requestedTypes)) {
            return true;
        }
        if (CollectionUtils.isEmpty(item.getProblemTypes())) {
            return false;
        }
        Set<String> normalizedRequested = new LinkedHashSet<>();
        for (String type : requestedTypes) {
            if (!StringUtils.hasText(type)) {
                continue;
            }
            String trimmed = type.trim();
            normalizedRequested.add(trimmed);
            ProblemType problemType = ProblemType.fromEnglishName(trimmed);
            if (problemType != null) {
                normalizedRequested.add(problemType.getEnglishName());
                normalizedRequested.add(problemType.getChineseName());
            }
        }
        for (String existing : item.getProblemTypes()) {
            if (normalizedRequested.contains(existing)) {
                return true;
            }
            ProblemType problemType = ProblemType.fromEnglishName(existing);
            if (problemType != null && normalizedRequested.contains(problemType.getChineseName())) {
                return true;
            }
        }
        return false;
    }

    private int resolveLimit(Integer limit) {
        if (limit == null) {
            return properties.getRetrievalMaxResults();
        }
        return Math.min(Math.max(limit, 1), 30);
    }

    private Long requireUserId() {
        Long userId = SecurityUtils.getCurrentUserId();
        if (ObjectUtils.isEmpty(userId)) {
            throw new UnauthorizedException("未登录");
        }
        return userId;
    }
}
