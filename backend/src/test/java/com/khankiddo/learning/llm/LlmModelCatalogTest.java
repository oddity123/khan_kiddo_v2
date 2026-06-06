package com.khankiddo.learning.llm;

import com.khankiddo.learning.config.LlmModelProperties;
import com.khankiddo.learning.dto.conversation.LlmModelOptionDto;
import com.khankiddo.learning.exception.BadRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LlmModelCatalogTest {

    private LlmModelCatalog catalog;

    @BeforeEach
    void setUp() {
        LlmModelProperties properties = new LlmModelProperties();
        properties.setDefaultModelId("doubao-seed");

        LlmModelProperties.ModelConfig doubao = new LlmModelProperties.ModelConfig();
        doubao.setId("doubao-seed");
        doubao.setDisplayName("Doubao Seed");
        doubao.setProvider("doubao");
        doubao.setBaseUrl("https://example.com/v3");
        doubao.setApiKey("test-key");
        doubao.setModelName("doubao-seed-1-8");
        doubao.setEnabled(true);

        LlmModelProperties.ModelConfig disabled = new LlmModelProperties.ModelConfig();
        disabled.setId("disabled-model");
        disabled.setDisplayName("Disabled");
        disabled.setProvider("test");
        disabled.setApiKey("key");
        disabled.setModelName("m");
        disabled.setEnabled(false);

        properties.setModels(List.of(doubao, disabled));
        catalog = new LlmModelCatalog(properties);
    }

    @Test
    void resolveOrDefault_usesDefaultWhenBlank() {
        ResolvedLlmModel model = catalog.resolveOrDefault(null);
        assertEquals("doubao-seed", model.getId());
        assertEquals("Doubao Seed", model.getDisplayName());
    }

    @Test
    void resolveRequired_rejectsUnknown() {
        assertThrows(BadRequestException.class, () -> catalog.resolveRequired("unknown"));
    }

    @Test
    void resolveRequired_rejectsDisabled() {
        assertThrows(BadRequestException.class, () -> catalog.resolveRequired("disabled-model"));
    }

    @Test
    void listEnabled_excludesDisabledAndMarksDefault() {
        List<LlmModelOptionDto> options = catalog.listEnabled();
        assertEquals(1, options.size());
        assertEquals("doubao-seed", options.get(0).getId());
        assertTrue(options.get(0).isDefaultModel());
    }
}
