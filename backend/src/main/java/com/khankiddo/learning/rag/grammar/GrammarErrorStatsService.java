package com.khankiddo.learning.rag.grammar;

import com.khankiddo.learning.config.condition.OnGrammarErrorRagCondition;
import com.khankiddo.learning.mapper.ConversationAnalysisItemMapper;
import com.khankiddo.learning.model.ProblemTypeCount;
import com.khankiddo.learning.model.enums.ProblemType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 用户历史语法错误类型统计，供「常见错误 / 总结类」问题使用。
 */
@Service
@Conditional(OnGrammarErrorRagCondition.class)
@RequiredArgsConstructor
public class GrammarErrorStatsService {

    private static final int TOP_N = 8;

    private final ConversationAnalysisItemMapper itemMapper;

    public String buildStatsSummary(Long userId, List<String> filterTypes) {
        List<ProblemTypeCount> rows = itemMapper.countProblemTypesByUserId(userId);
        if (CollectionUtils.isEmpty(rows)) {
            return "（暂无历史错误统计）";
        }
        Set<String> filterSet = filterTypes.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .collect(Collectors.toSet());

        List<ProblemTypeCount> filtered = rows.stream()
                .filter(row -> CollectionUtils.isEmpty(filterSet)
                        || filterSet.contains(row.getProblemType()))
                .limit(TOP_N)
                .toList();

        if (CollectionUtils.isEmpty(filtered)) {
            filtered = rows.stream().limit(TOP_N).toList();
        }

        StringBuilder builder = new StringBuilder();
        for (ProblemTypeCount row : filtered) {
            String label = ProblemType.translate(row.getProblemType());
            builder.append("- ")
                    .append(label)
                    .append(" (")
                    .append(row.getProblemType())
                    .append("): ")
                    .append(row.getCount())
                    .append(" 次\n");
        }
        return builder.toString().trim();
    }
}
