package com.khankiddo.learning.conversation;

import com.khankiddo.learning.ai.conversation.model.GrammarAnalysisResult;
import com.khankiddo.learning.ai.conversation.model.GrammarErrorDto;
import com.khankiddo.learning.ai.conversation.model.GrammarSentenceItemDto;
import com.khankiddo.learning.config.ConversationAnalysisProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 语法分析结果的确定性校验层（不调用 LLM）。
 *
 * <p>在 LLM 抽取之后、下游展示/统计/总结之前，用纯规则剔除两类高频噪声：
 * <ol>
 *   <li><b>span 子串校验</b>：错误点里「原文片段 → 正确写法」的原文片段必须真实出现在
 *       {@code originalSentence} 中，否则视为凭空捏造 / 与原句不对应而剔除（治 suggestion 与
 *       errors 脱节、部分假阳性）。</li>
 *   <li><b>自我修正过滤</b>：口语里「先说错、紧接着自己改对」（如 "It have It has"）属于
 *       口语不流利，不是语法错误，予以剔除。</li>
 * </ol>
 * 所有比较均在归一化文本（小写、去标点、压缩空白，保留 CJK）上进行。行为可通过
 * {@link ConversationAnalysisProperties} 开关关闭以便回滚。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GrammarAnalysisSanitizer {

    /** 归一化后原文片段至少这么长才做子串校验，避免对过短片段误杀。 */
    private static final int MIN_PROBE_LENGTH = 2;

    private static final Pattern ELLIPSIS = Pattern.compile("\\.{2,}|…+");
    private static final char[] ARROWS = {'\u2192', '\u21d2'};

    private final ConversationAnalysisProperties properties;

    public GrammarAnalysisResult sanitize(GrammarAnalysisResult grammar) {
        if (!properties.isSanitizerEnabled()
                || grammar == null
                || CollectionUtils.isEmpty(grammar.getItems())) {
            return grammar;
        }

        List<GrammarSentenceItemDto> cleanedItems = new ArrayList<>();
        int droppedErrors = 0;
        int droppedItems = 0;

        for (GrammarSentenceItemDto item : grammar.getItems()) {
            if (item == null) {
                continue;
            }
            String normalizedOriginal = normalize(item.getOriginalSentence());
            List<GrammarErrorDto> kept = new ArrayList<>();
            if (!CollectionUtils.isEmpty(item.getErrors())) {
                for (GrammarErrorDto error : item.getErrors()) {
                    if (shouldKeep(error, normalizedOriginal)) {
                        kept.add(error);
                    } else {
                        droppedErrors++;
                    }
                }
            }
            if (kept.isEmpty()) {
                droppedItems++;
                continue;
            }
            item.setErrors(kept);
            cleanedItems.add(item);
        }

        if (droppedErrors > 0 || droppedItems > 0) {
            log.info("语法分析校验层：剔除疑似误报 {} 条、剔除清洗后无错句 {} 句", droppedErrors, droppedItems);
        }
        grammar.setItems(cleanedItems);
        return grammar;
    }

    private boolean shouldKeep(GrammarErrorDto error, String normalizedOriginal) {
        if (error == null || !StringUtils.hasText(error.getPoint())) {
            return false;
        }
        if (StringUtils.hasText(error.getType()) && "Chinese".equalsIgnoreCase(error.getType().trim())) {
            return false;
        }
        if (!StringUtils.hasText(normalizedOriginal)) {
            return true;
        }
        ParsedPoint parsed = parsePoint(error.getPoint());
        if (parsed == null) {
            return true;
        }

        String wrongProbe = normalize(leadingSegment(parsed.wrong()));
        if (properties.isSanitizerDropUnmatchedSpan()
                && wrongProbe.length() >= MIN_PROBE_LENGTH
                && !normalizedOriginal.contains(wrongProbe)) {
            return false;
        }

        if (properties.isSanitizerDropSelfCorrection()) {
            String correctProbe = normalize(leadingSegment(parsed.correct()));
            if (StringUtils.hasText(wrongProbe)
                    && StringUtils.hasText(correctProbe)
                    && !wrongProbe.equals(correctProbe)
                    && normalizedOriginal.contains(wrongProbe + " " + correctProbe)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 解析「原文片段 → 正确写法（中文原因）」：以箭头切分，正确写法截到中文/英文左括号前。
     */
    private static ParsedPoint parsePoint(String point) {
        int arrowIdx = -1;
        for (char arrow : ARROWS) {
            int idx = point.indexOf(arrow);
            if (idx >= 0 && (arrowIdx < 0 || idx < arrowIdx)) {
                arrowIdx = idx;
            }
        }
        if (arrowIdx < 0) {
            return null;
        }
        String wrong = point.substring(0, arrowIdx).trim();
        String rest = point.substring(arrowIdx + 1).trim();
        int cut = rest.length();
        for (char paren : new char[]{'\uff08', '('}) {
            int idx = rest.indexOf(paren);
            if (idx >= 0 && idx < cut) {
                cut = idx;
            }
        }
        String correct = rest.substring(0, cut).trim();
        return new ParsedPoint(wrong, correct);
    }

    /** 取省略号（... / …）之前的引导片段，便于对含省略的长片段做校验。 */
    private static String leadingSegment(String fragment) {
        if (!StringUtils.hasText(fragment)) {
            return "";
        }
        return ELLIPSIS.split(fragment, 2)[0].trim();
    }

    /** 归一化：小写、非字母数字/CJK 一律折叠为单个空格。 */
    private static String normalize(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "";
        }
        String lower = raw.toLowerCase(Locale.ROOT);
        StringBuilder sb = new StringBuilder(lower.length());
        boolean pendingSpace = false;
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                if (pendingSpace) {
                    sb.append(' ');
                    pendingSpace = false;
                }
                sb.append(c);
            } else if (sb.length() > 0) {
                pendingSpace = true;
            }
        }
        return sb.toString();
    }

    private record ParsedPoint(String wrong, String correct) {
    }
}
