package com.khankiddo.learning.rag.grammar;

import com.khankiddo.learning.model.enums.ProblemType;
import com.khankiddo.learning.rag.core.RagMetadataKeys;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GrammarErrorHybridRankerTest {

    private GrammarErrorHybridRanker ranker;
    private GrammarErrorQueryIntentAnalyzer intentAnalyzer;

    @BeforeEach
    void setUp() {
        ranker = new GrammarErrorHybridRanker();
        intentAnalyzer = new GrammarErrorQueryIntentAnalyzer();
    }

    @Test
    void shouldPromoteChinglishMatchOverChinaTopicMatch() {
        GrammarErrorRetrievalIntent intent = intentAnalyzer.analyze("对于中文的表达, 我一般会犯什么错误呢?");
        List<EmbeddingMatch<TextSegment>> candidates = List.of(
                match(0.82, "Article,Unnatural",
                        "问题类型: 冠词错误 (Article)\n原句: Chinese stock market politics first"),
                match(0.78, "Collocation,Chinglish",
                        "问题类型: 搭配错误 (Collocation), 中式英语 (Chinglish)\n原句: take some plan and make it come true"));

        List<EmbeddingMatch<TextSegment>> ranked = ranker.rerank(candidates, intent, "对于中文的表达", 2);

        assertEquals(2, ranked.size());
        String topTypes = ranked.get(0).embedded().metadata().getString(RagMetadataKeys.PROBLEM_TYPES);
        assertTrue(topTypes.contains(ProblemType.CHINGLISH.getEnglishName()));
    }

    @Test
    void shouldRespectLimit() {
        GrammarErrorRetrievalIntent intent = GrammarErrorRetrievalIntent.semanticDefault();
        List<EmbeddingMatch<TextSegment>> candidates = List.of(
                match(0.9, "Tense", "时态错误"),
                match(0.8, "Article", "冠词错误"),
                match(0.7, "Structure", "句式错误"));

        List<EmbeddingMatch<TextSegment>> ranked = ranker.rerank(candidates, intent, "test", 1);

        assertEquals(1, ranked.size());
    }

    @Test
    void shouldResolveMatchLabels() {
        GrammarErrorRetrievalIntent intent = intentAnalyzer.analyze("中文表达中我一般犯的错误是哪些呢");
        EmbeddingMatch<TextSegment> primary = match(0.8, "Chinglish", "chinglish sample");
        EmbeddingMatch<TextSegment> secondary = match(0.7, "Unnatural", "unnatural sample");
        EmbeddingMatch<TextSegment> other = match(0.9, "Article", "article sample");

        assertEquals("主类型匹配", ranker.resolveMatchLabel(primary, intent));
        assertEquals("补充参考", ranker.resolveMatchLabel(secondary, intent));
        assertEquals("语义相关", ranker.resolveMatchLabel(other, intent));
    }

    private EmbeddingMatch<TextSegment> match(double score, String problemTypes, String text) {
        Metadata metadata = Metadata.from(Map.of(RagMetadataKeys.PROBLEM_TYPES, problemTypes));
        TextSegment segment = TextSegment.from(text, metadata);
        return new EmbeddingMatch<>(score, "id-" + score, null, segment);
    }
}
