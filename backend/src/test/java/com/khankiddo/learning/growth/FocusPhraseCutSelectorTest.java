package com.khankiddo.learning.growth;

import com.khankiddo.learning.config.FocusPhraseCutProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FocusPhraseCutSelectorTest {

    @Mock
    private HeuristicFocusPhraseCutter heuristic;
    @Mock
    private LlmFocusPhraseCutter llm;

    @Test
    void cut_usesHeuristic_whenPointIdNotInList() {
        FocusPhraseCutProperties properties = new FocusPhraseCutProperties();
        properties.setLlmPointIds(List.of("FEEL_ED_ADJ"));
        FocusPhraseCutSelector selector = new FocusPhraseCutSelector(heuristic, llm, properties);
        FocusPhraseCutRequest request = new FocusPhraseCutRequest(
                "I need a paper.", null, "I need a document.", "LEXICAL_GAP");
        FocusPhrasePair expected = new FocusPhrasePair("paper", "document");
        when(heuristic.cut(request)).thenReturn(Optional.of(expected));

        Optional<FocusPhrasePair> result = selector.cut(request);

        assertTrue(result.isPresent());
        assertEquals(expected, result.get());
        verify(llm, never()).cut(any());
    }

    @Test
    void cut_prefersLlm_whenPointIdListed_fallsBackToHeuristic() {
        FocusPhraseCutProperties properties = new FocusPhraseCutProperties();
        properties.setLlmPointIds(List.of("FEEL_ED_ADJ"));
        FocusPhraseCutSelector selector = new FocusPhraseCutSelector(heuristic, llm, properties);
        FocusPhraseCutRequest request = new FocusPhraseCutRequest(
                "I'm so exciting.", "exciting → excited", "I'm so excited.", "FEEL_ED_ADJ");
        FocusPhrasePair expected = new FocusPhrasePair("exciting", "excited");
        when(llm.cut(request)).thenReturn(Optional.empty());
        when(heuristic.cut(request)).thenReturn(Optional.of(expected));

        Optional<FocusPhrasePair> result = selector.cut(request);

        assertTrue(result.isPresent());
        assertEquals(expected, result.get());
        verify(llm).cut(request);
        verify(heuristic).cut(request);
    }

    @Test
    void cut_emptyList_alwaysHeuristic() {
        FocusPhraseCutSelector selector =
                new FocusPhraseCutSelector(heuristic, llm, new FocusPhraseCutProperties());
        FocusPhraseCutRequest request = new FocusPhraseCutRequest(
                "a", null, "b", "ANY");
        when(heuristic.cut(request)).thenReturn(Optional.empty());

        assertTrue(selector.cut(request).isEmpty());
        verify(llm, never()).cut(any());
    }
}
