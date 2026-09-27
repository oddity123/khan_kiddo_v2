package com.khankiddo.learning.growth;

import com.khankiddo.learning.conversation.EducationalSummaryParser;
import com.khankiddo.learning.dto.conversation.ActionCardDto;
import com.khankiddo.learning.dto.conversation.ChineseExpressionDto;
import com.khankiddo.learning.dto.conversation.EducationalSummaryDto;
import com.khankiddo.learning.dto.conversation.ExpressionPhraseDto;
import com.khankiddo.learning.exception.BadRequestException;
import com.khankiddo.learning.knowledge.HabitCardScorer;
import com.khankiddo.learning.mapper.ConversationAnalysisItemMapper;
import com.khankiddo.learning.mapper.ConversationAnalysisMapper;
import com.khankiddo.learning.model.ConversationAnalysis;
import com.khankiddo.learning.model.ConversationAnalysisItem;
import com.khankiddo.learning.model.GrowthCard;
import com.khankiddo.learning.model.GrowthCardEvidence;
import com.khankiddo.learning.prompt.PromptLoader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
    private final PromptLoader promptLoader;
    private final NaturalExpressionCandidateFilter naturalExpressionFilter;

    public void mintAfterAnalysis(Long userId, String analysisId) {
        Optional<ConversationAnalysis> analysisOpt =
                analysisMapper.findByAnalysisIdAndUserId(analysisId, userId);
        if (analysisOpt.isEmpty()) {
            log.warn("growth mint skipped: analysis not found analysisId={} userId={}", analysisId, userId);
            return;
        }

        // 习惯卡不再自动铸 Top1，由用户在行动卡上手动「制卡」；此处自动沉淀 vocab + expression
        ConversationAnalysis analysis = analysisOpt.get();
        EducationalSummaryDto summary = summaryParser.fromJson(analysis.getEducationalSummary());
        for (ChineseExpressionDto expression : chineseFromSummary(summary)) {
            persistVocabCard(userId, analysisId, expression);
        }
        Map<String, ExpressionPhraseDto> precomputedByKey = expressionPhrasesByKey(summary);
        List<ConversationAnalysisItem> items = itemMapper.findByAnalysisId(analysisId);
        if (!CollectionUtils.isEmpty(items)) {
            for (ConversationAnalysisItem item : items) {
                persistExpressionCard(userId, analysisId, item, precomputedByKey);
            }
        }
    }

    public void retryMint(Long userId, String analysisId) {
        mintAfterAnalysis(userId, analysisId);
    }

    /**
     * 按 habitKey 对本场行动卡走 LLM 生成并落库。
     * 已存在同 sourceRef 则直接返回（仍补写缺失证据），不再调 LLM。
     */
    public GrowthCard mintHabitByKey(Long userId, String analysisId, String habitKey) {
        if (!StringUtils.hasText(habitKey)) {
            throw new BadRequestException("habitKey 不能为空");
        }
        ConversationAnalysis analysis = analysisMapper.findByAnalysisIdAndUserId(analysisId, userId)
                .orElseThrow(() -> new BadRequestException("分析记录不存在"));

        HabitCardScorer.HabitScoreResult scoreResult = scoreAnalysis(analysisId, analysis);
        ActionCardDto habit = findActionCard(scoreResult, habitKey.trim());
        if (ObjectUtils.isEmpty(habit)) {
            throw new BadRequestException("未找到对应的说话习惯");
        }

        String sourceRef = GrowthCardSourceRefs.habit(habit);
        Optional<GrowthCard> existing = store.findByUserSource(userId, analysisId, "habit", sourceRef);
        if (existing.isPresent()) {
            GrowthCard card = existing.get();
            store.saveEvidence(GrowthCardEvidenceSupport.fromHabitExamples(
                    userId, card.getCardId(), analysisId, habit));
            return card;
        }

        GrowthCard card = mintHabitCard(userId, analysisId, habit);
        if (ObjectUtils.isEmpty(card)) {
            throw new BadRequestException("成长卡生成失败，请稍后重试");
        }
        return card;
    }

    private HabitCardScorer.HabitScoreResult scoreAnalysis(String analysisId, ConversationAnalysis analysis) {
        List<ConversationAnalysisItem> rows = itemMapper.findByAnalysisId(analysisId);
        if (CollectionUtils.isEmpty(rows)) {
            rows = List.of();
        }
        return analysisSupport.score(rows);
    }

    private static List<ChineseExpressionDto> chineseFromSummary(EducationalSummaryDto summary) {
        if (ObjectUtils.isEmpty(summary) || CollectionUtils.isEmpty(summary.getChineseExpressions())) {
            return Collections.emptyList();
        }
        return summary.getChineseExpressions();
    }

    private static Map<String, ExpressionPhraseDto> expressionPhrasesByKey(EducationalSummaryDto summary) {
        if (ObjectUtils.isEmpty(summary) || CollectionUtils.isEmpty(summary.getExpressionPhrases())) {
            return Map.of();
        }
        Map<String, ExpressionPhraseDto> byKey = new HashMap<>();
        for (ExpressionPhraseDto phrase : summary.getExpressionPhrases()) {
            if (ObjectUtils.isEmpty(phrase) || phrase.getSentenceId() == null) {
                continue;
            }
            if (!StringUtils.hasText(phrase.getFront()) || !StringUtils.hasText(phrase.getBack())) {
                continue;
            }
            String key = expressionPhraseKey(phrase.getSentenceId(), phrase.getPointId());
            if (!StringUtils.hasText(key)) {
                continue;
            }
            // 同句同 point 保留首次
            byKey.putIfAbsent(key, phrase);
        }
        return byKey;
    }

    private static String expressionPhraseKey(Long sentenceId, String pointId) {
        if (sentenceId == null || !StringUtils.hasText(pointId)) {
            return null;
        }
        return sentenceId + "\0" + pointId.trim();
    }

    private static ActionCardDto findActionCard(HabitCardScorer.HabitScoreResult scoreResult, String habitKey) {
        if (ObjectUtils.isEmpty(scoreResult)) {
            return null;
        }
        if (!CollectionUtils.isEmpty(scoreResult.actionCards())) {
            for (ActionCardDto card : scoreResult.actionCards()) {
                if (matchesHabitKey(card, habitKey)) {
                    return card;
                }
            }
        }
        ActionCardDto top = scoreResult.topHabit();
        if (top != null && matchesHabitKey(top, habitKey)) {
            return top;
        }
        return null;
    }

    private static boolean matchesHabitKey(ActionCardDto card, String habitKey) {
        return Objects.equals(habitKey, GrowthCardSourceRefs.resolveHabitKey(card));
    }

    private GrowthCard mintHabitCard(Long userId, String analysisId, ActionCardDto habit) {
        GrowthCardDraft draft = assistant.generate(
                promptLoader.getSystemPromptGrowthCardMint(),
                contextBuilder.build(habit));
        if (ObjectUtils.isEmpty(draft)
                || !StringUtils.hasText(draft.getFront())
                || !StringUtils.hasText(draft.getBack())) {
            log.warn("成长卡 habit 生成结果无效 analysisId={} habitKey={}",
                    analysisId, GrowthCardSourceRefs.resolveHabitKey(habit));
            return null;
        }
        GrowthCard card = store.persistNewOrGet(
                userId,
                "habit",
                draft.getFront().trim(),
                draft.getBack().trim(),
                analysisId,
                GrowthCardSourceRefs.habit(habit),
                null);
        List<GrowthCardEvidence> evidence = GrowthCardEvidenceSupport.fromHabitExamples(
                userId, card.getCardId(), analysisId, habit);
        store.saveEvidence(evidence);
        return card;
    }

    private void persistVocabCard(Long userId, String analysisId, ChineseExpressionDto expression) {
        if (ObjectUtils.isEmpty(expression)) {
            return;
        }
        String front = StringUtils.hasText(expression.getFront())
                ? expression.getFront()
                : expression.getOriginalSentence();
        String back = expression.getBack();
        if (!StringUtils.hasText(front) || !StringUtils.hasText(back)) {
            return;
        }
        GrowthCard card = store.persistNewOrGet(
                userId,
                "vocab",
                front,
                back,
                analysisId,
                GrowthCardSourceRefs.vocab(expression.getOriginalIndex()),
                GrowthCardReasonEvidence.toEvidenceJson(expression.getReason()));
        store.saveEvidence(GrowthCardEvidenceSupport.fromChineseExpression(
                userId, card.getCardId(), analysisId, expression));
    }

    private void persistExpressionCard(
            Long userId,
            String analysisId,
            ConversationAnalysisItem item,
            Map<String, ExpressionPhraseDto> precomputedByKey) {
        if (ObjectUtils.isEmpty(item) || !naturalExpressionFilter.test(item)) {
            return;
        }
        String key = expressionPhraseKey(item.getSentenceId(), item.getPointId());
        ExpressionPhraseDto precomputed = StringUtils.hasText(key) ? precomputedByKey.get(key) : null;
        if (ObjectUtils.isEmpty(precomputed)
                || !StringUtils.hasText(precomputed.getFront())
                || !StringUtils.hasText(precomputed.getBack())) {
            // 仅铸造 Phrase Review 明确产出的条目；LLM 跳过或不对齐则不铸
            return;
        }
        String front = precomputed.getFront().trim();
        String back = precomputed.getBack().trim();
        String reason = precomputed.getReason();
        String sourceRef = GrowthCardSourceRefs.expression(item);
        GrowthCard card = store.persistNewOrGet(
                userId,
                "expression",
                front,
                back,
                analysisId,
                sourceRef,
                GrowthCardReasonEvidence.toEvidenceJson(reason));
        store.saveEvidence(GrowthCardEvidenceSupport.fromAnalysisItem(
                userId, card.getCardId(), analysisId, item));
    }
}
