package com.khankiddo.learning.rag.core;

/**
 * 向量索引写入/删除扩展点。
 */
public interface RagIndexer {

    void index(Long userId, String documentKey, Object payload);

    void remove(Long userId, String documentKey);
}
