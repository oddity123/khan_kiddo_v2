package com.khankiddo.learning.growth;

import com.khankiddo.learning.model.GrowthCard;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolMemoryId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GrowthCardToolsTest {

    @Mock
    private GrowthCardStore store;

    @InjectMocks
    private GrowthCardTools tools;

    @Test
    void persistGrowthCard_shouldDelegateToStoreAndReturnOk() {
        GrowthCard card = GrowthCard.builder()
                .cardId("card-abc")
                .type("habit")
                .build();
        when(store.persistNewOrGet(
                eq(7L), eq("habit"), eq("front text"), eq("back text"),
                eq("analysis-1"), eq("habit:FAM_WORD_FORM"), isNull()))
                .thenReturn(card);

        String result = tools.persistGrowthCard(
                7L, "habit", "front text", "back text", "analysis-1", "habit:FAM_WORD_FORM");

        assertEquals("ok cardId=card-abc type=habit", result);
        verify(store).persistNewOrGet(
                7L, "habit", "front text", "back text", "analysis-1", "habit:FAM_WORD_FORM", null);
    }

    @Test
    void persistGrowthCard_shouldNormalizeType() {
        GrowthCard card = GrowthCard.builder()
                .cardId("card-habit")
                .type("habit")
                .build();
        when(store.persistNewOrGet(
                eq(1L), eq("habit"), eq("f"), eq("b"),
                eq("a"), eq("habit:FAM_WORD_FORM"), isNull()))
                .thenReturn(card);

        String result = tools.persistGrowthCard(1L, " HABIT ", "f", "b", "a", "habit:FAM_WORD_FORM");

        assertEquals("ok cardId=card-habit type=habit", result);
        verify(store).persistNewOrGet(1L, "habit", "f", "b", "a", "habit:FAM_WORD_FORM", null);
    }

    @Test
    void everyTool_shouldInjectUserIdViaToolMemoryId() {
        List<Method> toolMethods = Arrays.stream(GrowthCardTools.class.getDeclaredMethods())
                .filter(m -> m.isAnnotationPresent(Tool.class))
                .toList();

        assertEquals(1, toolMethods.size());
        for (Method method : toolMethods) {
            assertTrue(method.getParameters()[0].isAnnotationPresent(ToolMemoryId.class),
                    method.getName() + " 首参必须为 @ToolMemoryId userId");
        }
    }
}
