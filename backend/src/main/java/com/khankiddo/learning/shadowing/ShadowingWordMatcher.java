package com.khankiddo.learning.shadowing;

import com.khankiddo.learning.dto.shadowing.ShadowingScoreDto;
import com.khankiddo.learning.dto.shadowing.ShadowingWordDto;
import com.khankiddo.learning.exception.BadRequestException;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 跟读打分：目标句与识别文本归一化后按单词求 LCS，得分 = 命中计分词数 / 计分词数。
 * <p>
 * 不做词形还原：产品练的是语法，went 说成 go 应算错。
 */
public final class ShadowingWordMatcher {

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern WORD_SEPARATOR = Pattern.compile("[-‐‑‒–—―/]+");
    private static final Pattern NON_WORD_CHAR = Pattern.compile("[^a-z0-9']");
    private static final Pattern EDGE_APOSTROPHE = Pattern.compile("^'+|'+$");
    private static final Pattern DIGIT = Pattern.compile("\\d");

    private ShadowingWordMatcher() {
    }

    public static ShadowingScoreDto match(String target, String recognized, int passScore) {
        List<DisplayToken> tokens = tokenizeTarget(target);
        List<String> targetWords = new ArrayList<>();
        List<Integer> ownerOfWord = new ArrayList<>();
        for (int i = 0; i < tokens.size(); i++) {
            DisplayToken token = tokens.get(i);
            if (!token.scored()) {
                continue;
            }
            for (String word : token.words()) {
                targetWords.add(word);
                ownerOfWord.add(i);
            }
        }
        if (targetWords.isEmpty()) {
            throw new BadRequestException("该句没有可跟读的英文单词");
        }

        String recognizedText = StringUtils.hasText(recognized) ? recognized.trim() : "";
        boolean[] wordHit = lcsHits(targetWords, normalizeWords(recognizedText));

        int hitCount = 0;
        int[] tokenMisses = new int[tokens.size()];
        for (int w = 0; w < targetWords.size(); w++) {
            if (wordHit[w]) {
                hitCount++;
            } else {
                tokenMisses[ownerOfWord.get(w)]++;
            }
        }

        List<ShadowingWordDto> words = new ArrayList<>(tokens.size());
        for (int i = 0; i < tokens.size(); i++) {
            DisplayToken token = tokens.get(i);
            words.add(ShadowingWordDto.builder()
                    .text(token.text())
                    .scored(token.scored())
                    .hit(token.scored() && tokenMisses[i] == 0)
                    .build());
        }

        int score = Math.round(hitCount * 100f / targetWords.size());
        return ShadowingScoreDto.builder()
                .score(score)
                .passed(score >= passScore)
                .recognizedText(recognizedText)
                .words(words)
                .build();
    }

    private static List<DisplayToken> tokenizeTarget(String target) {
        List<DisplayToken> tokens = new ArrayList<>();
        if (!StringUtils.hasText(target)) {
            return tokens;
        }
        for (String raw : WHITESPACE.split(target.trim())) {
            List<String> words = normalizeWords(raw);
            boolean scored = !words.isEmpty() && !DIGIT.matcher(raw).find();
            tokens.add(new DisplayToken(raw, words, scored));
        }
        return tokens;
    }

    static List<String> normalizeWords(String text) {
        List<String> words = new ArrayList<>();
        if (!StringUtils.hasText(text)) {
            return words;
        }
        String lowered = text.toLowerCase(Locale.ROOT).replace('’', '\'').replace('‘', '\'');
        String separated = WORD_SEPARATOR.matcher(lowered).replaceAll(" ");
        for (String part : WHITESPACE.split(separated.trim())) {
            String cleaned = EDGE_APOSTROPHE.matcher(NON_WORD_CHAR.matcher(part).replaceAll("")).replaceAll("");
            if (StringUtils.hasText(cleaned)) {
                words.add(cleaned);
            }
        }
        return words;
    }

    /** 标记 target 中属于某个最长公共子序列的位置 */
    private static boolean[] lcsHits(List<String> target, List<String> spoken) {
        int n = target.size();
        int m = spoken.size();
        int[][] dp = new int[n + 1][m + 1];
        for (int i = n - 1; i >= 0; i--) {
            for (int j = m - 1; j >= 0; j--) {
                dp[i][j] = target.get(i).equals(spoken.get(j))
                        ? dp[i + 1][j + 1] + 1
                        : Math.max(dp[i + 1][j], dp[i][j + 1]);
            }
        }
        boolean[] hits = new boolean[n];
        int i = 0;
        int j = 0;
        while (i < n && j < m) {
            if (target.get(i).equals(spoken.get(j))) {
                hits[i] = true;
                i++;
                j++;
            } else if (dp[i + 1][j] >= dp[i][j + 1]) {
                i++;
            } else {
                j++;
            }
        }
        return hits;
    }

    private record DisplayToken(String text, List<String> words, boolean scored) {
    }
}
