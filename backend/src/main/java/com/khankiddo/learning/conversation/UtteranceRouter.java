package com.khankiddo.learning.conversation;

import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Stage1 分离后将用户句分流：
 * <ul>
 *   <li>纯中文 / 中文为主的内容表达 → 中文表达通道</li>
 *   <li>词汇求助（怎么说 / how to say …）→ 中文表达通道</li>
 *   <li>英文为主、仅夹少量汉字（code-switch）→ Stage2 语法分析</li>
 *   <li>纯英文 → Stage2</li>
 * </ul>
 */
@Component
public class UtteranceRouter {

    /**
     * 汉字占（汉字+拉丁字母）比例达到该阈值 → 中文通道；低于则视为英文句内夹杂，进 Stage2。
     */
    static final double HAN_RATIO_THRESHOLD = 0.35;

    private static final Pattern VOCAB_HELP = Pattern.compile(
            "怎么说|用英语怎么说|英文是什么|不知道怎么表达|"
                    + "how\\s+to\\s+say|"
                    + "what(?:'s|\\s+is)\\b.{0,40}?\\bin\\s+english",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.DOTALL);

    public boolean containsCjk(String text) {
        if (!StringUtils.hasText(text)) {
            return false;
        }
        return text.codePoints()
                .anyMatch(cp -> Character.UnicodeScript.of(cp) == Character.UnicodeScript.HAN);
    }

    /**
     * 是否应进中文表达通道（相对 Stage2）。
     */
    public boolean isChineseChannel(String text) {
        if (!containsCjk(text)) {
            return false;
        }
        if (isVocabHelp(text)) {
            return true;
        }
        return hanRatio(text) >= HAN_RATIO_THRESHOLD;
    }

    public RoutedUtterances route(List<String> userSentences) {
        if (CollectionUtils.isEmpty(userSentences)) {
            return new RoutedUtterances(List.of(), List.of());
        }
        List<String> englishSentences = new ArrayList<>();
        List<RoutedChineseSentence> chineseSentences = new ArrayList<>();
        for (int i = 0; i < userSentences.size(); i++) {
            String sentence = userSentences.get(i);
            if (isChineseChannel(sentence)) {
                chineseSentences.add(new RoutedChineseSentence(i, sentence));
            } else {
                englishSentences.add(sentence);
            }
        }
        return new RoutedUtterances(englishSentences, chineseSentences);
    }

    static boolean isVocabHelp(String text) {
        if (!StringUtils.hasText(text)) {
            return false;
        }
        return VOCAB_HELP.matcher(text.toLowerCase(Locale.ROOT)).find();
    }

    /**
     * 汉字数 / (汉字数 + 拉丁字母数)。无字母时若含汉字视为 1.0。
     */
    static double hanRatio(String text) {
        if (!StringUtils.hasText(text)) {
            return 0.0;
        }
        int han = 0;
        int latin = 0;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            Character.UnicodeScript script = Character.UnicodeScript.of(cp);
            if (script == Character.UnicodeScript.HAN) {
                han++;
            } else if (script == Character.UnicodeScript.LATIN) {
                latin++;
            }
        }
        int total = han + latin;
        if (total == 0) {
            return han > 0 ? 1.0 : 0.0;
        }
        return (double) han / total;
    }

    public record RoutedChineseSentence(int originalIndex, String sentence) {
    }

    public record RoutedUtterances(List<String> englishSentences, List<RoutedChineseSentence> chineseSentences) {

        public int chineseCount() {
            return chineseSentences.size();
        }

        public int englishCount() {
            return englishSentences.size();
        }
    }
}
