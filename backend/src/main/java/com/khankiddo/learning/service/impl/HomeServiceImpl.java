package com.khankiddo.learning.service.impl;

import com.khankiddo.learning.dto.AnalysisDashboardStats;
import com.khankiddo.learning.dto.HomePageResponse;
import com.khankiddo.learning.dto.RecentSentenceView;
import com.khankiddo.learning.mapper.ConversationAnalysisItemMapper;
import com.khankiddo.learning.model.ConversationAnalysisItem;
import com.khankiddo.learning.model.enums.ErrorLevel;
import com.khankiddo.learning.model.enums.ProblemType;
import com.khankiddo.learning.service.HomeService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class HomeServiceImpl implements HomeService {

    private static final String DEFAULT_TITLE = "Khan Kiddo 英语学习助手";
    private static final String DEFAULT_DESCRIPTION = "让 AI 对话练习真正变得有效";

    private final ConversationAnalysisItemMapper conversationAnalysisItemMapper;

    @Override
    public HomePageResponse getHomePage(Long userId) {
        boolean authenticated = ObjectUtils.isNotEmpty(userId);
        return HomePageResponse.builder()
                .title(DEFAULT_TITLE)
                .description(DEFAULT_DESCRIPTION)
                .authenticated(authenticated)
                .analysisStats(authenticated ? getAnalysisDashboardStats(userId) : null)
                .build();
    }

    private AnalysisDashboardStats getAnalysisDashboardStats(Long userId) {
        long analyzedSentenceCount = conversationAnalysisItemMapper.countDistinctSentencesByUserId(userId);
        List<String> seriousProblemTypes = Arrays.stream(ProblemType.values())
                .filter(pt -> pt.getErrorLevel() == ErrorLevel.FATAL || pt.getErrorLevel() == ErrorLevel.BASIC)
                .map(ProblemType::getEnglishName)
                .collect(Collectors.toList());
        long seriousIssueCount = conversationAnalysisItemMapper.countByUserIdAndProblemTypes(userId, seriousProblemTypes);

        Map<String, Object> topType = conversationAnalysisItemMapper.getMostCommonProblemTypeByUserId(userId);
        String mostCommonErrorType = "—";
        if (topType != null && topType.get("problemType") != null) {
            mostCommonErrorType = ProblemType.translate(String.valueOf(topType.get("problemType")));
        }

        long recent7DaysSentenceCount =
                conversationAnalysisItemMapper.countDistinctSentencesInLast7DaysByUserId(userId);
        List<ConversationAnalysisItem> rawRecent =
                conversationAnalysisItemMapper.findRecentSentencesByUserId(userId, 3);

        return AnalysisDashboardStats.builder()
                .analyzedSentenceCount(analyzedSentenceCount)
                .seriousIssueCount(seriousIssueCount)
                .mostCommonErrorType(mostCommonErrorType)
                .recent7DaysSentenceCount(recent7DaysSentenceCount)
                .recentSentences(buildRecentSentenceViews(rawRecent))
                .build();
    }

    private List<RecentSentenceView> buildRecentSentenceViews(List<ConversationAnalysisItem> rawRecent) {
        if (CollectionUtils.isEmpty(rawRecent)) {
            return Collections.emptyList();
        }
        List<RecentSentenceView> result = new ArrayList<>();
        for (ConversationAnalysisItem first : rawRecent) {
            List<ConversationAnalysisItem> allForSentence = conversationAnalysisItemMapper
                    .findByAnalysisIdAndSentenceId(first.getAnalysisId(), first.getSentenceId());
            List<String> tags = allForSentence.stream()
                    .map(ConversationAnalysisItem::getProblemTypes)
                    .filter(StringUtils::hasText)
                    .distinct()
                    .map(ProblemType::translate)
                    .collect(Collectors.toList());
            result.add(RecentSentenceView.builder()
                    .originalSentence(first.getOriginalSentence())
                    .suggestion(first.getSuggestion())
                    .problemTypeTags(tags)
                    .createdAt(first.getCreatedAt())
                    .build());
        }
        return result;
    }
}
