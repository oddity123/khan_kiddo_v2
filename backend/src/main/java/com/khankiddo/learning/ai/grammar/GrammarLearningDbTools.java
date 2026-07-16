package com.khankiddo.learning.ai.grammar;

import com.khankiddo.learning.model.enums.ProblemType;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolMemoryId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.List;

/**
 * 语法学习 DB Tools：统计 / 错句样例 / 练习概览。
 * <p>
 * userId 由 LangChain4j 从 AiService 的 {@code @MemoryId} 经 {@link ToolMemoryId} 注入，
 * 不暴露给模型，也不依赖 SecurityContext（流式工具执行在异步线程，无法读 ThreadLocal）。
 */
@Component("grammarLearningDbTools")
@RequiredArgsConstructor
public class GrammarLearningDbTools {

    private final GrammarLearningDbService dbService;

    @Tool(
            name = "get_grammar_error_stats",
            value = "查询当前用户语法错误类型分布（TopN）。"
                    + "适合「常犯什么错 / 错误分布 / 薄弱点」类问题。"
                    + "用户说「最近」时可传 days=7 或 30；可用 problemTypes 过滤类型。"
    )
    public String getGrammarErrorStats(
            @ToolMemoryId Long userId,
            @P(name = "problemTypes", required = false,
                    description = "可选。错误类型列表，如单复数选择 PLURAL、时态选择 TENSE；空表示全部")
            List<ProblemType> problemTypes,
            @P(name = "days", required = false,
                    description = "可选。仅统计近 N 天，如 7、30；空或 0 表示全部历史")
            Integer days) {
        return dbService.buildStatsSummary(userId, toEnglishNames(problemTypes), days);
    }

    @Tool(
            name = "list_grammar_error_examples",
            value = "查询当前用户的具体错句样例（原句、错误点、建议）。"
                    + "适合「时态有哪些典型例子 / 给我看看冠词错误」类问题。"
                    + "用户说「最近」时可传 days=7 或 30。"
    )
    public String listGrammarErrorExamples(
            @ToolMemoryId Long userId,
            @P(name = "problemTypes", required = false,
                    description = "可选。错误类型列表，如单复数选择 PLURAL、冠词选择 ARTICLE；空表示不限类型")
            List<ProblemType> problemTypes,
            @P(name = "days", required = false,
                    description = "可选。仅查近 N 天；空或 0 表示全部历史")
            Integer days,
            @P(name = "limit", required = false,
                    description = "可选。返回条数，默认 5，最大 10")
            Integer limit) {
        return dbService.buildErrorExamples(userId, toEnglishNames(problemTypes), days, limit);
    }

    @Tool(
            name = "get_grammar_practice_overview",
            value = "查询当前用户练习概览：成功分析次数、有错句子数、最高频错误类型。"
                    + "适合「最近学得怎么样 / 练了多少」类问题。"
                    + "用户说「最近」时可传 days=7 或 30。"
    )
    public String getGrammarPracticeOverview(
            @ToolMemoryId Long userId,
            @P(name = "days", required = false,
                    description = "可选。仅统计近 N 天，如 7、30；空或 0 表示全部历史")
            Integer days) {
        return dbService.buildPracticeOverview(userId, days);
    }

    static List<String> toEnglishNames(List<ProblemType> problemTypes) {
        if (CollectionUtils.isEmpty(problemTypes)) {
            return List.of();
        }
        return problemTypes.stream()
                .map(ProblemType::getEnglishName)
                .toList();
    }
}
