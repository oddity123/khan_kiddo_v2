package com.khankiddo.learning.ai.grammar;

import com.khankiddo.learning.rag.grammar.GrammarErrorSemanticRetrieverConfig;
import com.khankiddo.learning.rag.grammar.GrammarRagQuerySupport;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolMemoryId;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 语义检索 Tool：按需在当前用户的历史错句向量库中检索（Agentic RAG）。
 * <p>
 * userId 经 {@link ToolMemoryId} 注入后放入 Query 的 chatMemoryId，检索按用户过滤；
 * 未配置 Qdrant 时 Retriever Bean 不存在，Tool 返回未启用提示。
 */
@Slf4j
@Component("grammarErrorSemanticSearchTools")
public class GrammarErrorSemanticSearchTools {

    private static final int MAX_RESULTS_LIMIT = 10;

    private final ObjectProvider<ContentRetriever> retrieverProvider;

    public GrammarErrorSemanticSearchTools(
            @Qualifier(GrammarErrorSemanticRetrieverConfig.GRAMMAR_ERROR_SEMANTIC_CONTENT_RETRIEVER)
            ObjectProvider<ContentRetriever> retrieverProvider) {
        this.retrieverProvider = retrieverProvider;
    }

    @Tool(
            name = "search_similar_grammar_errors",
            value = "按语义相似度检索当前用户的历史错句记录（原句、错误点、建议）。"
                    + "适合围绕某个说法/句子/主题找相关历史错误，如「我说 go to school 那类句子错过吗」。"
                    + "查询词请用简洁的英文或中文短语描述句子内容或语法点。"
    )
    public String searchSimilarGrammarErrors(
            @ToolMemoryId Long userId,
            @P(name = "query", description = "检索内容，如句子片段、主题或语法点描述")
            String query,
            @P(name = "maxResults", required = false,
                    description = "可选。返回条数，默认 8，最大 10")
            Integer maxResults) {
        ContentRetriever retriever = retrieverProvider.getIfAvailable();
        if (retriever == null) {
            return "语义检索未启用（未配置向量库），请改用其它工具查询数据库。";
        }
        if (!StringUtils.hasText(query)) {
            return "检索内容不能为空，请提供句子片段、主题或语法点描述。";
        }
        List<Content> contents = retriever.retrieve(
                GrammarRagQuerySupport.query(userId, query.trim(), normalizeMaxResults(maxResults)));
        return formatContents(contents);
    }

    static Integer normalizeMaxResults(Integer maxResults) {
        if (maxResults == null || maxResults <= 0) {
            return null;
        }
        return Math.min(maxResults, MAX_RESULTS_LIMIT);
    }

    static String formatContents(List<Content> contents) {
        if (CollectionUtils.isEmpty(contents)) {
            return "未检索到相关历史错句。可能尚未完成对话分析，或该主题暂无记录。";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < contents.size(); i++) {
            builder.append("【相关历史错句 ").append(i + 1).append("】\n")
                    .append(contents.get(i).textSegment().text()).append("\n\n");
        }
        return builder.toString().trim();
    }
}
