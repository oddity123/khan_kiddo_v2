package com.khankiddo.learning.errant;

import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 与 ERRANT {@code tokenise:false} 对齐的空白分词：按非空白块切开，再以单空格拼接送检。
 */
public final class ErrantTokenSupport {

    private static final Pattern TOKEN = Pattern.compile("\\S+");

    private ErrantTokenSupport() {
    }

    public static List<String> tokenize(String text) {
        if (!StringUtils.hasText(text)) {
            return List.of();
        }
        List<String> tokens = new ArrayList<>();
        Matcher matcher = TOKEN.matcher(text.trim());
        while (matcher.find()) {
            tokens.add(matcher.group());
        }
        return tokens;
    }

    public static String join(List<String> tokens) {
        if (tokens == null || tokens.isEmpty()) {
            return "";
        }
        return String.join(" ", tokens);
    }
}
