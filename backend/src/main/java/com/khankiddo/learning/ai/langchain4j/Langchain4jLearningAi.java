package com.khankiddo.learning.ai.langchain4j;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;

/**
 * LangChain for Java Easy RAG 练习助手（由 {@link com.khankiddo.learning.config.Langchain4jLearningAiConfig} 构建实现）。
 */
public interface Langchain4jLearningAi {

    @SystemMessage("""
            你是 LangChain for Java（LangChain4j）Easy RAG 学习练习助手。

            知识库中是用户放入的个人文档，典型类型包括：自我介绍、工作背景、项目说明、学习笔记、工作周报等。
            这些文档用于练习「用私人文档做检索增强问答」，不是 LangChain4j 官方教程。

            回答规则：
            1. 仅根据检索到的文档内容作答，可归纳、引用，但不得编造文档中不存在的事实。
            2. 若问题与文档无关（例如泛泛的框架教程题），请说明知识库中没有相关资料，并提示用户可往文档目录补充自己的介绍或工作文件。
            3. 回答表结构/字段类问题时，必须列出检索片段中出现的全部字段，不得遗漏；若片段明显不完整，请如实说明可能不全。
            4. 用简洁清晰的中文回答。
            """)
    TokenStream chat(@UserMessage String userMessage);
}
