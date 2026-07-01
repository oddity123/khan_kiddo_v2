package com.khankiddo.learning.rag.core;

import dev.langchain4j.data.segment.TextSegment;

/**
 * 将业务数据构建为可嵌入的 {@link TextSegment}。
 *
 * @param <T> 业务输入类型
 */
public interface RagDocumentBuilder<T> {

    TextSegment build(T source);
}
