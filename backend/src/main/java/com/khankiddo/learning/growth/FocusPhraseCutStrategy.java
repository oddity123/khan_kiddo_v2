package com.khankiddo.learning.growth;

import java.util.Optional;

/**
 * 可替换的短表达切分策略：MVP=启发式，后续可换 LLM。
 */
public interface FocusPhraseCutStrategy {

    Optional<FocusPhrasePair> cut(FocusPhraseCutRequest request);
}
