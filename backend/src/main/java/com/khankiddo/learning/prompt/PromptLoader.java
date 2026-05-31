package com.khankiddo.learning.prompt;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.FileCopyUtils;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * 加载 classpath 下的 prompt 模板，便于与 LangChain4j AiService 组合。
 */
@Component
public class PromptLoader {

    public String getSystemPromptConversationAnalysis() {
        return readPrompt("system-prompt-conversation-analysis.txt");
    }

    public String getConversationAnalysisTemplate() {
        return readPrompt("conversation-analysis-prompt-template.txt");
    }

    public String getConversationSeparationTemplate() {
        return readPrompt("conversation-separation-prompt-template.txt");
    }

    public String getEducationalSummaryTemplate() {
        return readPrompt("educational-summary-prompt-template.txt");
    }

    public String fillTemplate(String template, String placeholder, String value) {
        return template.replace("{" + placeholder + "}", value);
    }

    private String readPrompt(String fileName) {
        ClassPathResource resource = new ClassPathResource("templates/prompts/" + fileName);
        try (InputStream inputStream = resource.getInputStream()) {
            byte[] bytes = FileCopyUtils.copyToByteArray(inputStream);
            String content = new String(bytes, StandardCharsets.UTF_8);
            if (StringUtils.hasText(content)) {
                return content;
            }
            throw new IllegalStateException("提示词模板为空: " + fileName);
        } catch (IOException e) {
            throw new IllegalStateException("读取提示词模板失败: " + fileName, e);
        }
    }
}
