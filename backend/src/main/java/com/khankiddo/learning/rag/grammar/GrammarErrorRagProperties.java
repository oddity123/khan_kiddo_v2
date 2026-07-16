package com.khankiddo.learning.rag.grammar;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.grammar-error-rag")
public class GrammarErrorRagProperties {

    private boolean enabled = true;
    private Qdrant qdrant = new Qdrant();
    private int retrievalMaxResults = 8;
    private double retrievalMinScore = 0.55;

    @Data
    public static class Qdrant {
        private String host;
        private int port = 6334;
        private String apiKey;
        private String collectionName = "grammar_errors";
        private boolean useTls = false;
    }
}
