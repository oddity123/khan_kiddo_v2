package com.khankiddo.learning.growth;

import com.khankiddo.learning.conversation.EducationalSummaryParser;
import com.khankiddo.learning.dto.conversation.ActionCardDto;
import com.khankiddo.learning.dto.conversation.ChineseExpressionDto;
import com.khankiddo.learning.dto.conversation.EducationalSummaryDto;
import com.khankiddo.learning.knowledge.HabitCardScorer;
import com.khankiddo.learning.mapper.ConversationAnalysisItemMapper;
import com.khankiddo.learning.mapper.ConversationAnalysisMapper;
import com.khankiddo.learning.model.ConversationAnalysis;
import com.khankiddo.learning.model.ConversationAnalysisItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class GrowthCardMintGateway {

    private final ConversationAnalysisMapper analysisMapper;
    private final ConversationAnalysisItemMapper itemMapper;
    private final EducationalSummaryParser summaryParser;
    private final GrowthCardAnalysisSupport analysisSupport;
    private final GrowthCardMintContextBuilder contextBuilder;
    private final GrowthCardMintAssistant assistant;
    private final GrowthCardStore store;

    public void mintAfterAnalysis(Long userId, String analysisId) {
        Optional<ConversationAnalysis> analysisOpt =
                analysisMapper.findByAnalysisIdAndUserId(analysisId, userId);
        if (analysisOpt.isEmpty()) {
            log.warn("growth mint skipped: analysis not found analysisId={} userId={}", analysisId, userId);
            return;
        }

        ConversationAnalysis analysis = analysisOpt.get();
        List<ConversationAnalysisItem> rows = itemMapper.findByAnalysisId(analysisId);
        if (CollectionUtils.isEmpty(rows)) {
            rows = List.of();
        }

        EducationalSummaryDto summary = summaryParser.fromJson(analysis.getEducationalSummary());
        List<ChineseExpressionDto> chineseExpressions = ObjectUtils.isEmpty(summary)
                || CollectionUtils.isEmpty(summary.getChineseExpressions())
                ? Collections.emptyList()
                : summary.getChineseExpressions();

        HabitCardScorer.HabitScoreResult scoreResult = analysisSupport.score(rows, chineseExpressions);
        ActionCardDto topHabit = scoreResult.topHabit();

        if (topHabit != null && store.findHabitByAnalysis(userId, analysisId).isEmpty()) {
            String brief = contextBuilder.build(analysisId, topHabit);
            assistant.mintHabitCard(userId, brief);
            if (store.findHabitByAnalysis(userId, analysisId).isEmpty()) {
                log.warn("成长卡 habit 铸卡后仍不存在 analysisId={}，LLM 可能未调用 persist 工具", analysisId);
            }
        }

        for (ChineseExpressionDto expression : chineseExpressions) {
            persistVocabCard(userId, analysisId, expression);
        }
    }

    public void retryMint(Long userId, String analysisId) {
        mintAfterAnalysis(userId, analysisId);
    }

    private void persistVocabCard(Long userId, String analysisId, ChineseExpressionDto expression) {
        if (ObjectUtils.isEmpty(expression)) {
            return;
        }
        String front = StringUtils.hasText(expression.getFocusPhrase())
                ? expression.getFocusPhrase()
                : expression.getOriginalSentence();
        String back = expression.getSuggestion();
        if (!StringUtils.hasText(front) || !StringUtils.hasText(back)) {
            return;
        }
        store.persistNewOrGet(
                userId,
                "vocab",
                front,
                back,
                analysisId,
                "vocab:" + expression.getOriginalIndex(),
                null);
    }
}
