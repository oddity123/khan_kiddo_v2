package com.khankiddo.learning.growth;

import com.khankiddo.learning.dto.conversation.ActionCardDto;
import com.khankiddo.learning.model.ConversationAnalysisItem;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.util.StringUtils;

import java.util.Objects;

/**
 * 成长卡 {@code sourceRef} 编解码与 habitKey 解析单点。
 * 约定：{@code vocab:{index}} / {@code habit:{habitKey}} / {@code expr:{sentenceId}} / {@code expr:h:{hash}}。
 */
public final class GrowthCardSourceRefs {

    public static final String PREFIX_VOCAB = "vocab:";
    public static final String PREFIX_HABIT = "habit:";
    public static final String PREFIX_EXPR = "expr:";
    public static final String PREFIX_EXPR_HASH = "expr:h:";

    private GrowthCardSourceRefs() {
    }

    /** habitKey 优先，否则回退 pointId。 */
    public static String resolveHabitKey(ActionCardDto habit) {
        if (ObjectUtils.isEmpty(habit)) {
            return null;
        }
        return StringUtils.hasText(habit.getHabitKey()) ? habit.getHabitKey() : habit.getPointId();
    }

    public static String vocab(Integer originalIndex) {
        return PREFIX_VOCAB + originalIndex;
    }

    public static String habit(ActionCardDto habit) {
        return PREFIX_HABIT + resolveHabitKey(habit);
    }

    public static String habit(String habitKey) {
        return PREFIX_HABIT + habitKey;
    }

    public static String expression(ConversationAnalysisItem item) {
        if (ObjectUtils.isEmpty(item)) {
            return null;
        }
        if (item.getSentenceId() != null) {
            return PREFIX_EXPR + item.getSentenceId();
        }
        String seed = StringUtils.hasText(item.getOriginalSentence())
                ? item.getOriginalSentence().trim()
                : item.getPointId();
        return PREFIX_EXPR_HASH + Integer.toHexString(Objects.hash(
                seed,
                item.getPointId(),
                item.getErrorPoint()));
    }

    public static boolean isVocab(String sourceRef) {
        return StringUtils.hasText(sourceRef) && sourceRef.startsWith(PREFIX_VOCAB);
    }

    public static boolean isHabit(String sourceRef) {
        return StringUtils.hasText(sourceRef) && sourceRef.startsWith(PREFIX_HABIT);
    }

    /** 任意 expression 前缀（含 hash）。 */
    public static boolean isExpr(String sourceRef) {
        return StringUtils.hasText(sourceRef) && sourceRef.startsWith(PREFIX_EXPR);
    }

    /** {@code expr:{sentenceId}}，不含 hash 形态。 */
    public static boolean isExprSentence(String sourceRef) {
        return isExpr(sourceRef) && !sourceRef.startsWith(PREFIX_EXPR_HASH);
    }

    public static String vocabIndexText(String sourceRef) {
        if (!isVocab(sourceRef)) {
            return null;
        }
        return sourceRef.substring(PREFIX_VOCAB.length()).trim();
    }

    public static String habitKeyOf(String sourceRef) {
        if (!isHabit(sourceRef)) {
            return null;
        }
        return sourceRef.substring(PREFIX_HABIT.length()).trim();
    }

    public static String sentenceIdOf(String sourceRef) {
        if (!isExprSentence(sourceRef)) {
            return null;
        }
        return sourceRef.substring(PREFIX_EXPR.length()).trim();
    }
}
