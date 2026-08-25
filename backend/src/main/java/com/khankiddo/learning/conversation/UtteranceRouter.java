package com.khankiddo.learning.conversation;

import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Stage1 分离后将用户句分流：
 * <ul>
 *   <li>含任意汉字 → 中文表达通道（词汇求助、中文叙述、英文句内夹杂汉字均同）</li>
 *   <li>纯英文 / 无汉字 → Stage2 语法分析</li>
 * </ul>
 */
@Component
public class UtteranceRouter {

    public boolean containsCjk(String text) {
        if (!StringUtils.hasText(text)) {
            return false;
        }
        return text.codePoints()
                .anyMatch(cp -> Character.UnicodeScript.of(cp) == Character.UnicodeScript.HAN);
    }

    /**
     * 是否应进中文表达通道（相对 Stage2）：含任意汉字即分流。
     */
    public boolean isChineseChannel(String text) {
        return containsCjk(text);
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
