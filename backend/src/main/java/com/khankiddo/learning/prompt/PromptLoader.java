package com.khankiddo.learning.prompt;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.FileCopyUtils;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * 加载 classpath 下按功能分目录的 prompt 模板（{@code templates/prompts/} 下各功能子目录），
 * 便于与 LangChain4j AiService 组合。
 */
@Component
public class PromptLoader {

    public String getSystemPromptConversationAnalysis() {
        return readPrompt("grammar-analysis/system.txt");
    }

    public String getSystemPromptConversationSeparation() {
        return readPrompt("conversation-separation/system.txt");
    }

    public String getSystemPromptEducationalSummary() {
        return readPrompt("educational-summary/system.txt");
    }

    public String getConversationAnalysisTemplate() {
        return readPrompt("grammar-analysis/user.txt");
    }

    public String getConversationSeparationTemplate() {
        return readPrompt("conversation-separation/user.txt");
    }

    public String getEducationalSummaryTemplate() {
        return readPrompt("educational-summary/user.txt");
    }

    public String getSystemPromptChineseExpressionReview() {
        return readPrompt("chinese-expression-review/system.txt");
    }

    public String getChineseExpressionReviewTemplate() {
        return readPrompt("chinese-expression-review/user.txt");
    }

    public String getSystemPromptGrowthCardMint() {
        return readPrompt("growth-card-mint/system.txt");
    }

    public String getGrowthCardMintTemplate() {
        return readPrompt("growth-card-mint/user.txt");
    }

    public String getPracticePromptTemplate() {
        return readPrompt("practice-recap/template.txt");
    }

    public String fillTemplate(String template, String placeholder, String value) {
        return template.replace("{" + placeholder + "}", value);
    }

    private String readPrompt(String relativePath) {
        ClassPathResource resource = new ClassPathResource("templates/prompts/" + relativePath);
        try (InputStream inputStream = resource.getInputStream()) {
            byte[] bytes = FileCopyUtils.copyToByteArray(inputStream);
            String content = new String(bytes, StandardCharsets.UTF_8);
            if (StringUtils.hasText(content)) {
                return content;
            }
            throw new IllegalStateException("提示词模板为空: " + relativePath);
        } catch (IOException e) {
            throw new IllegalStateException("读取提示词模板失败: " + relativePath, e);
        }
    }
}
