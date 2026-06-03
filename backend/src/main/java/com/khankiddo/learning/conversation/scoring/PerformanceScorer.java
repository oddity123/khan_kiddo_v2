package com.khankiddo.learning.conversation.scoring;

/**
 * 口语表现评分器。实现类应基于配置权重做确定性计算，不依赖 LLM。
 */
public interface PerformanceScorer {

    PerformanceScoreResult score(PerformanceScoringInput input);
}
