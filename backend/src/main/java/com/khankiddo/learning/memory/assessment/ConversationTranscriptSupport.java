package com.khankiddo.learning.memory.assessment;

import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 对话实录预处理与引文落盘校验：
 * 1) 剔除插件注入的教练开场指令（# Spoken English Warm-up Coach / # Review Material）；
 * 2) 提取纯学习者话轮用于核验 LLM 输出的 evidenceQuote 是否真实出自学习者口中。
 */
public final class ConversationTranscriptSupport {

    private static final Pattern SPEAKER_LINE = Pattern.compile("^(User|Assistant):\\s*(.*)$");
    private static final Pattern NON_ALNUM = Pattern.compile("[^a-z0-9]+");

    private ConversationTranscriptSupport() {
    }

    public static List<Turn> parseTurns(String rawContent) {
        if (!StringUtils.hasText(rawContent)) {
            return List.of();
        }
        List<Turn> turns = new ArrayList<>();
        String currentSpeaker = null;
        StringBuilder currentBody = new StringBuilder();

        String[] lines = rawContent.replace("\r\n", "\n").split("\n", -1);
        for (String line : lines) {
            Matcher matcher = SPEAKER_LINE.matcher(line);
            if (matcher.matches()) {
                if (currentSpeaker != null) {
                    turns.add(new Turn(currentSpeaker, currentBody.toString().trim()));
                }
                currentSpeaker = matcher.group(1);
                currentBody = new StringBuilder(matcher.group(2));
            } else if (currentSpeaker != null) {
                currentBody.append('\n').append(line);
            }
        }
        if (currentSpeaker != null) {
            turns.add(new Turn(currentSpeaker, currentBody.toString().trim()));
        }

        if (turns.isEmpty() && StringUtils.hasText(rawContent)) {
            turns.add(new Turn("User", rawContent.trim()));
        }
        return turns;
    }

    public static boolean isInjectedCoachPromptTurn(Turn turn) {
        if (turn == null || !"User".equalsIgnoreCase(turn.speaker()) || !StringUtils.hasText(turn.text())) {
            return false;
        }
        String text = turn.text();
        if (text.contains("# Spoken English Warm-up Coach")) {
            return true;
        }
        return text.contains("# Review Material")
                && (text.contains("## Phase 1") || text.contains("## Mission"));
    }

    public static String extractCleanTranscript(String rawContent, int maxChars) {
        List<Turn> turns = parseTurns(rawContent);
        if (turns.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Turn turn : turns) {
            if (isInjectedCoachPromptTurn(turn) || !StringUtils.hasText(turn.text())) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append("\n\n");
            }
            sb.append(turn.speaker()).append(": ").append(turn.text());
        }
        String cleaned = sb.toString().trim();
        if (maxChars > 0 && cleaned.length() > maxChars) {
            return cleaned.substring(cleaned.length() - maxChars);
        }
        return cleaned;
    }

    public static List<String> extractCleanUserUtterances(String rawContent) {
        List<Turn> turns = parseTurns(rawContent);
        List<String> utterances = new ArrayList<>();
        for (Turn turn : turns) {
            if ("User".equalsIgnoreCase(turn.speaker())
                    && !isInjectedCoachPromptTurn(turn)
                    && StringUtils.hasText(turn.text())) {
                utterances.add(turn.text().trim());
            }
        }
        return utterances;
    }

    public static boolean isQuoteGroundedInUserSpeech(String quote, List<String> userUtterances) {
        if (!StringUtils.hasText(quote) || userUtterances == null || userUtterances.isEmpty()) {
            return false;
        }
        String normalizedQuote = normalizeForMatch(quote);
        if (normalizedQuote.length() < 3) {
            return false;
        }
        for (String utterance : userUtterances) {
            if (normalizeForMatch(utterance).contains(normalizedQuote)) {
                return true;
            }
        }
        return false;
    }

    static String normalizeForMatch(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "";
        }
        return NON_ALNUM.matcher(raw.toLowerCase(Locale.ROOT)).replaceAll(" ").trim();
    }

    public record Turn(String speaker, String text) {
    }
}
