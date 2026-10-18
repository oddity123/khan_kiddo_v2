package com.khankiddo.learning.voice;

import com.khankiddo.learning.exception.BadRequestException;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 把已完成字幕收成分析页能吃的 {@code User:} / {@code Assistant:} 文本。
 * 不接收 ASR 半句；未知角色直接拒绝，避免把预览悄悄写进去。
 */
public final class VoicePracticeTranscript {

    public static final int MAX_CHARS = 100_000;

    private VoicePracticeTranscript() {
    }

    public static String format(List<VoicePracticeCaption> lines) {
        if (CollectionUtils.isEmpty(lines)) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        for (VoicePracticeCaption line : lines) {
            if (line == null || !StringUtils.hasText(line.text())) {
                continue;
            }
            String label = labelFor(line.role());
            String text = line.text().trim().replace('\r', ' ').replace('\n', ' ');
            if (!StringUtils.hasText(text)) {
                continue;
            }
            if (!out.isEmpty()) {
                out.append('\n');
            }
            out.append(label).append(": ").append(text);
            if (out.length() > MAX_CHARS) {
                throw new BadRequestException("字幕过长，无法保存");
            }
        }
        return out.toString();
    }

    private static String labelFor(String role) {
        if ("user".equals(role)) {
            return "User";
        }
        if ("assistant".equals(role)) {
            return "Assistant";
        }
        throw new BadRequestException("字幕角色只能是 user 或 assistant");
    }
}
