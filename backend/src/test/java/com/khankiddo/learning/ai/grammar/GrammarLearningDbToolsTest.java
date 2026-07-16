package com.khankiddo.learning.ai.grammar;

import com.khankiddo.learning.model.enums.ProblemType;
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
class GrammarLearningDbToolsTest {

    @Mock
    private GrammarLearningDbService dbService;

    @InjectMocks
    private GrammarLearningDbTools tools;

    @Test
    void stats_shouldMapEnumToStoredEnglishName() {
        when(dbService.buildStatsSummary(eq(7L), eq(List.of("Plural")), eq(7)))
                .thenReturn("ok-stats");

        String result = tools.getGrammarErrorStats(7L, List.of(ProblemType.PLURAL), 7);

        assertEquals("ok-stats", result);
        verify(dbService).buildStatsSummary(7L, List.of("Plural"), 7);
    }

    @Test
    void examples_shouldPassLimit() {
        when(dbService.buildErrorExamples(eq(7L), eq(List.of()), eq(30), eq(3)))
                .thenReturn("ok-examples");

        String result = tools.listGrammarErrorExamples(7L, null, 30, 3);

        assertEquals("ok-examples", result);
        verify(dbService).buildErrorExamples(7L, List.of(), 30, 3);
    }

    @Test
    void overview_shouldDelegate() {
        when(dbService.buildPracticeOverview(eq(7L), isNull())).thenReturn("ok-overview");

        assertEquals("ok-overview", tools.getGrammarPracticeOverview(7L, null));
        verify(dbService).buildPracticeOverview(7L, null);
    }

    @Test
    void everyTool_shouldInjectUserIdViaToolMemoryId() {
        List<Method> toolMethods = Arrays.stream(GrammarLearningDbTools.class.getDeclaredMethods())
                .filter(m -> m.isAnnotationPresent(Tool.class))
                .toList();

        assertEquals(3, toolMethods.size());
        for (Method method : toolMethods) {
            assertTrue(method.getParameters()[0].isAnnotationPresent(ToolMemoryId.class),
                    method.getName() + " 首参必须为 @ToolMemoryId userId");
        }
    }

    @Test
    void toEnglishNames_shouldMapEnums() {
        assertEquals(
                List.of("Tense", "Article"),
                GrammarLearningDbTools.toEnglishNames(List.of(ProblemType.TENSE, ProblemType.ARTICLE)));
        assertEquals(List.of(), GrammarLearningDbTools.toEnglishNames(null));
    }
}
