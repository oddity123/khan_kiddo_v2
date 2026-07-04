package com.khankiddo.learning.rag.grammar;

import com.khankiddo.learning.config.condition.OnGrammarErrorRagCondition;
import com.khankiddo.learning.model.enums.ProblemType;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.regex.Pattern;

/**
 * 基于可解释规则的查询意图识别（第一版不做 LLM 分类）。
 */
@Component
@Conditional(OnGrammarErrorRagCondition.class)
public class GrammarErrorQueryIntentAnalyzer {

    private static final Pattern SUMMARY_PATTERN = Pattern.compile(
            "一般会犯|最常|高频|常见|最多|哪些错误|什么错误|犯什么|薄弱点|短板"
                    + "|犯的错误|错误是哪些|会犯哪些|有哪些错误|哪些.{0,8}错误|什么.{0,6}错误");
    private static final Pattern CHINGLISH_PATTERN = Pattern.compile(
            "中文表达|中文的表达|中式英语|中式表达|中文式|直译|chinglish|中国话|母语思维");
    private static final Pattern UNNATURAL_PATTERN = Pattern.compile("不自然|生硬|不地道|别扭");
    private static final Pattern ARTICLE_PATTERN = Pattern.compile("冠词|\\bthe\\b|\\ba/an\\b|定冠词|不定冠词");
    private static final Pattern TENSE_PATTERN = Pattern.compile("时态|过去式|现在完成|完成时|进行时|将来时");

    public GrammarErrorRetrievalIntent analyze(String query) {
        if (!StringUtils.hasText(query)) {
            return GrammarErrorRetrievalIntent.semanticDefault();
        }
        String normalized = query.trim().toLowerCase(Locale.ROOT);
        boolean includeStats = SUMMARY_PATTERN.matcher(normalized).find();

        if (CHINGLISH_PATTERN.matcher(normalized).find()) {
            return buildIntent(
                    GrammarErrorQueryKind.CHINGLISH,
                    List.of(ProblemType.CHINGLISH.getEnglishName()),
                    List.of(
                            ProblemType.UNNATURAL.getEnglishName(),
                            ProblemType.WORD_CHOICE.getEnglishName(),
                            ProblemType.COLLOCATION.getEnglishName(),
                            ProblemType.STRUCTURE.getEnglishName()),
                    List.of("中文", "中式", "直译", "chinglish", "direct translation", "chinese-influenced", "不地道"),
                    includeStats);
        }
        if (UNNATURAL_PATTERN.matcher(normalized).find()) {
            return buildIntent(
                    GrammarErrorQueryKind.UNNATURAL,
                    List.of(ProblemType.UNNATURAL.getEnglishName()),
                    List.of(
                            ProblemType.CHINGLISH.getEnglishName(),
                            ProblemType.WORD_CHOICE.getEnglishName(),
                            ProblemType.COLLOCATION.getEnglishName()),
                    List.of("不自然", "生硬", "不地道", "unnatural", "awkward"),
                    includeStats);
        }
        if (ARTICLE_PATTERN.matcher(normalized).find()) {
            return buildIntent(
                    GrammarErrorQueryKind.ARTICLE,
                    List.of(ProblemType.ARTICLE.getEnglishName()),
                    List.of(),
                    List.of("冠词", "article", "the", "a", "an"),
                    includeStats);
        }
        if (TENSE_PATTERN.matcher(normalized).find()) {
            return buildIntent(
                    GrammarErrorQueryKind.TENSE,
                    List.of(ProblemType.TENSE.getEnglishName()),
                    List.of(),
                    List.of("时态", "tense", "过去", "完成时"),
                    includeStats);
        }
        if (includeStats) {
            return buildIntent(
                    GrammarErrorQueryKind.GENERAL_SUMMARY,
                    List.of(),
                    List.of(),
                    List.of(),
                    true);
        }
        return GrammarErrorRetrievalIntent.semanticDefault();
    }

    private GrammarErrorRetrievalIntent buildIntent(
            GrammarErrorQueryKind kind,
            List<String> primaryTypes,
            List<String> secondaryTypes,
            List<String> keywords,
            boolean includeStats) {
        Set<String> expandedKeywords = new LinkedHashSet<>(keywords);
        for (String type : primaryTypes) {
            expandedKeywords.add(type.toLowerCase(Locale.ROOT));
            ProblemType problemType = ProblemType.fromEnglishName(type);
            if (problemType != null) {
                expandedKeywords.add(problemType.getChineseName());
            }
        }
        for (String type : secondaryTypes) {
            ProblemType problemType = ProblemType.fromEnglishName(type);
            if (problemType != null) {
                expandedKeywords.add(problemType.getChineseName());
            }
        }
        return new GrammarErrorRetrievalIntent(
                kind,
                List.copyOf(primaryTypes),
                List.copyOf(secondaryTypes),
                List.copyOf(new ArrayList<>(expandedKeywords)),
                includeStats);
    }
}
