package com.khankiddo.learning.ai.grammar;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 语法学习助手（{@link GrammarStatsAssistant}）可调参数。
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.grammar-stats")
public class GrammarStatsProperties {

    private Db db = new Db();
    private ChatMemory chatMemory = new ChatMemory();

    @Data
    public static class Db {

        /** 错误类型统计返回的 TopN 条数 */
        private int statsTopN = 8;

        /** 错句样例默认返回条数（模型未传 limit 时） */
        private int defaultExampleLimit = 5;

        /** 错句样例单次返回上限，防止一次灌入过多上下文 */
        private int maxExampleLimit = 10;

        /** days 参数上限（天），超出则截断 */
        private int maxDays = 365;
    }

    @Data
    public static class ChatMemory {

        /** 单用户对话窗口最多保留的消息条数 */
        private int maxMessages = 20;

        /** 进程内最多缓存的活跃用户数，超出按 LRU 淘汰 */
        private int maxUsers = 1000;

        /** 用户闲置多久后淘汰其对话记忆（小时） */
        private int expireAfterAccessHours = 2;
    }
}
