package com.khankiddo.learning.growth;

import com.khankiddo.learning.conversation.ErrorPointSpanSupport;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * MVP 启发式切分：优先解析 point 的 wrong→correction；否则对原句/suggestion 做 token 对齐切短。
 */
@Component
public class HeuristicFocusPhraseCutter implements FocusPhraseCutStrategy {

    private static final int MAX_FOCUS_TOKENS = 6;
    private static final Pattern TOKEN = Pattern.compile("[A-Za-z0-9]+(?:'[A-Za-z]+)?");

    @Override
    public Optional<FocusPhrasePair> cut(FocusPhraseCutRequest request) {
        if (request == null) {
            return Optional.empty();
        }
        String original = trimToEmpty(request.originalSentence());
        String suggestion = trimToEmpty(request.suggestion());

        Optional<FocusPhrasePair> fromPoint = fromPoint(request.point());
        if (fromPoint.isPresent()) {
            FocusPhrasePair pair = fromPoint.get();
            if (isUsablePair(pair, original, suggestion)) {
                return Optional.of(pair);
            }
        }

        if (!StringUtils.hasText(original) || !StringUtils.hasText(suggestion)) {
            return Optional.empty();
        }
        if (!FocusPhraseTextSupport.hasSubstantiveDiff(original, suggestion)) {
            return Optional.empty();
        }
        return fromTokenAlignment(original, suggestion);
    }

    private static Optional<FocusPhrasePair> fromPoint(String point) {
        ErrorPointSpanSupport.Span span = ErrorPointSpanSupport.parse(point);
        if (span == null) {
            return Optional.empty();
        }
        String wrong = span.wrong().trim();
        String correct = span.correct().trim();
        if (!StringUtils.hasText(wrong) || !StringUtils.hasText(correct)) {
            return Optional.empty();
        }
        if (FocusPhraseTextSupport.normalizeKey(wrong)
                .equals(FocusPhraseTextSupport.normalizeKey(correct))) {
            return Optional.empty();
        }
        return Optional.of(new FocusPhrasePair(wrong, correct));
    }

    private static Optional<FocusPhrasePair> fromTokenAlignment(String original, String suggestion) {
        List<String> origTokens = tokens(original);
        List<String> suggTokens = tokens(suggestion);
        if (origTokens.isEmpty() || suggTokens.isEmpty()) {
            return Optional.empty();
        }

        int prefix = 0;
        int maxPrefix = Math.min(origTokens.size(), suggTokens.size());
        while (prefix < maxPrefix
                && origTokens.get(prefix).equalsIgnoreCase(suggTokens.get(prefix))) {
            prefix++;
        }

        int suffix = 0;
        int maxSuffix = Math.min(origTokens.size() - prefix, suggTokens.size() - prefix);
        while (suffix < maxSuffix
                && origTokens.get(origTokens.size() - 1 - suffix)
                .equalsIgnoreCase(suggTokens.get(suggTokens.size() - 1 - suffix))) {
            suffix++;
        }

        List<String> wrongMid = origTokens.subList(prefix, origTokens.size() - suffix);
        List<String> naturalMid = suggTokens.subList(prefix, suggTokens.size() - suffix);
        if (wrongMid.isEmpty() && naturalMid.isEmpty()) {
            return Optional.empty();
        }
        if (wrongMid.size() > MAX_FOCUS_TOKENS || naturalMid.size() > MAX_FOCUS_TOKENS) {
            return Optional.empty();
        }
        // 对齐结果等于整句时放弃（避免整句 rewrite 落卡）
        if (wrongMid.size() == origTokens.size() || naturalMid.size() == suggTokens.size()) {
            return Optional.empty();
        }

        String focusWrong = join(wrongMid);
        String focusNatural = join(naturalMid);
        if (!StringUtils.hasText(focusWrong) && !StringUtils.hasText(focusNatural)) {
            return Optional.empty();
        }
        if (!StringUtils.hasText(focusWrong)) {
            focusWrong = focusNatural;
        }
        if (!StringUtils.hasText(focusNatural)) {
            focusNatural = focusWrong;
        }
        if (FocusPhraseTextSupport.normalizeKey(focusWrong)
                .equals(FocusPhraseTextSupport.normalizeKey(focusNatural))) {
            return Optional.empty();
        }
        return Optional.of(new FocusPhrasePair(focusWrong, focusNatural));
    }

    private static boolean isUsablePair(FocusPhrasePair pair, String original, String suggestion) {
        if (pair == null
                || !StringUtils.hasText(pair.focusWrong())
                || !StringUtils.hasText(pair.focusNatural())) {
            return false;
        }
        if (FocusPhraseTextSupport.normalizeKey(pair.focusWrong())
                .equals(FocusPhraseTextSupport.normalizeKey(pair.focusNatural()))) {
            return false;
        }
        List<String> wrongTokens = tokens(pair.focusWrong());
        List<String> naturalTokens = tokens(pair.focusNatural());
        if (wrongTokens.size() > MAX_FOCUS_TOKENS || naturalTokens.size() > MAX_FOCUS_TOKENS) {
            return false;
        }
        // point 优先路径：若 suggestion 存在则要求与原句有实质差异；无 suggestion 时仍可用 point
        if (StringUtils.hasText(suggestion) && StringUtils.hasText(original)
                && !FocusPhraseTextSupport.hasSubstantiveDiff(original, suggestion)
                && !FocusPhraseTextSupport.hasSubstantiveDiff(pair.focusWrong(), pair.focusNatural())) {
            return false;
        }
        return true;
    }

    private static List<String> tokens(String text) {
        List<String> result = new ArrayList<>();
        var matcher = TOKEN.matcher(text);
        while (matcher.find()) {
            result.add(matcher.group());
        }
        return result;
    }

    private static String join(List<String> parts) {
        if (CollectionUtils.isEmpty(parts)) {
            return "";
        }
        return String.join(" ", parts);
    }

    private static String trimToEmpty(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }
}
