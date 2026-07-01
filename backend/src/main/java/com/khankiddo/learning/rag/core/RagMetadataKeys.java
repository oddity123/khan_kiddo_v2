package com.khankiddo.learning.rag.core;

/**
 * RAG 向量 metadata 键名（各语料域共用 user_id 过滤）。
 */
public final class RagMetadataKeys {

    public static final String USER_ID = "user_id";
    public static final String ANALYSIS_ID = "analysis_id";
    public static final String SENTENCE_ID = "sentence_id";
    public static final String PROBLEM_TYPES = "problem_types";
    public static final String CREATED_AT = "created_at";

    private RagMetadataKeys() {
    }
}
