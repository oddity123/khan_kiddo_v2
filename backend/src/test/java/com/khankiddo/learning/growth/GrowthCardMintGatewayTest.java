package com.khankiddo.learning.growth;

import com.khankiddo.learning.conversation.EducationalSummaryParser;
import com.khankiddo.learning.dto.conversation.ActionCardDto;
import com.khankiddo.learning.dto.conversation.ChineseExpressionDto;
import com.khankiddo.learning.dto.conversation.EducationalSummaryDto;
import com.khankiddo.learning.exception.BadRequestException;
import com.khankiddo.learning.knowledge.HabitCardScorer;
import com.khankiddo.learning.mapper.ConversationAnalysisItemMapper;
import com.khankiddo.learning.mapper.ConversationAnalysisMapper;
import com.khankiddo.learning.model.ConversationAnalysis;
import com.khankiddo.learning.model.GrowthCard;
import com.khankiddo.learning.prompt.PromptLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GrowthCardMintGatewayTest {

    private static final long USER_ID = 7L;
    private static final String ANALYSIS_ID = "analysis-1";
    private static final String SYSTEM_PROMPT = "system-prompt";

    @Mock
    private ConversationAnalysisMapper analysisMapper;
    @Mock
    private ConversationAnalysisItemMapper itemMapper;
    @Mock
    private EducationalSummaryParser summaryParser;
    @Mock
    private GrowthCardAnalysisSupport analysisSupport;
    @Mock
    private GrowthCardMintContextBuilder contextBuilder;
    @Mock
    private GrowthCardMintAssistant assistant;
    @Mock
    private GrowthCardStore store;
    @Mock
    private PromptLoader promptLoader;

    private GrowthCardMintGateway gateway;

    @BeforeEach
    void setUp() {
        gateway = new GrowthCardMintGateway(
                analysisMapper, itemMapper, summaryParser, analysisSupport,
                contextBuilder, assistant, store, promptLoader);
        when(analysisMapper.findByAnalysisIdAndUserId(ANALYSIS_ID, USER_ID))
                .thenReturn(Optional.of(ConversationAnalysis.builder()
                        .analysisId(ANALYSIS_ID)
                        .educationalSummary("{}")
                        .build()));
        when(itemMapper.findByAnalysisId(ANALYSIS_ID)).thenReturn(List.of());
    }

    @Test
    void mintAfterAnalysis_shouldPersistVocabWhenNoTopHabit() {
        ChineseExpressionDto expression = ChineseExpressionDto.builder()
                .originalIndex(3)
                .focusPhrase("很有成就感")
                .originalSentence("我觉得很有成就感")
                .suggestion("I feel a strong sense of accomplishment.")
                .build();
        when(summaryParser.fromJson("{}")).thenReturn(EducationalSummaryDto.builder()
                .chineseExpressions(List.of(expression))
                .build());
        when(analysisSupport.score(any(), any()))
                .thenReturn(new HabitCardScorer.HabitScoreResult(null, List.of(), List.of()));

        gateway.mintAfterAnalysis(USER_ID, ANALYSIS_ID);

        verify(assistant, never()).generate(anyString(), anyString());
        verify(store).persistNewOrGet(
                USER_ID, "vocab", "很有成就感", "I feel a strong sense of accomplishment.",
                ANALYSIS_ID, "vocab:3", null);
    }

    @Test
    void mintAfterAnalysis_shouldNotInvokeAssistantWhenTopHabitAlreadyExists() {
        ActionCardDto topHabit = ActionCardDto.builder().rank(1).habitKey("FAM_TENSE").build();
        when(summaryParser.fromJson("{}")).thenReturn(EducationalSummaryDto.builder().build());
        when(analysisSupport.score(any(), any()))
                .thenReturn(new HabitCardScorer.HabitScoreResult(topHabit, List.of(topHabit), List.of()));
        when(store.findByUserSource(USER_ID, ANALYSIS_ID, "habit", "habit:FAM_TENSE"))
                .thenReturn(Optional.of(GrowthCard.builder().cardId("existing").build()));

        gateway.mintAfterAnalysis(USER_ID, ANALYSIS_ID);

        verify(assistant, never()).generate(anyString(), anyString());
        verify(store, never()).persistNewOrGet(
                anyLong(), eq("habit"), anyString(), anyString(), anyString(), anyString(), isNull());
    }

    @Test
    void mintAfterAnalysis_shouldGenerateThenPersistHabit() {
        ActionCardDto topHabit = ActionCardDto.builder().rank(1).habitKey("FAM_TENSE").build();
        when(summaryParser.fromJson("{}")).thenReturn(EducationalSummaryDto.builder().build());
        when(analysisSupport.score(any(), any()))
                .thenReturn(new HabitCardScorer.HabitScoreResult(topHabit, List.of(topHabit), List.of()));
        when(store.findByUserSource(USER_ID, ANALYSIS_ID, "habit", "habit:FAM_TENSE"))
                .thenReturn(Optional.empty());
        when(promptLoader.getSystemPromptGrowthCardMint()).thenReturn(SYSTEM_PROMPT);
        when(contextBuilder.build(topHabit)).thenReturn("mint brief");
        when(assistant.generate(SYSTEM_PROMPT, "mint brief")).thenReturn(GrowthCardDraft.builder()
                .front("何时用现在完成时？")
                .back("I've already finished it.")
                .build());
        when(store.persistNewOrGet(
                USER_ID, "habit", "何时用现在完成时？", "I've already finished it.",
                ANALYSIS_ID, "habit:FAM_TENSE", null))
                .thenReturn(GrowthCard.builder().cardId("new").build());

        gateway.mintAfterAnalysis(USER_ID, ANALYSIS_ID);

        verify(assistant, times(1)).generate(SYSTEM_PROMPT, "mint brief");
        verify(store).persistNewOrGet(
                USER_ID,
                "habit",
                "何时用现在完成时？",
                "I've already finished it.",
                ANALYSIS_ID,
                "habit:FAM_TENSE",
                null);
    }

    @Test
    void mintAfterAnalysis_shouldSkipPersistWhenDraftEmpty() {
        ActionCardDto topHabit = ActionCardDto.builder().rank(1).habitKey("FAM_TENSE").build();
        when(summaryParser.fromJson("{}")).thenReturn(EducationalSummaryDto.builder().build());
        when(analysisSupport.score(any(), any()))
                .thenReturn(new HabitCardScorer.HabitScoreResult(topHabit, List.of(topHabit), List.of()));
        when(store.findByUserSource(USER_ID, ANALYSIS_ID, "habit", "habit:FAM_TENSE"))
                .thenReturn(Optional.empty());
        when(promptLoader.getSystemPromptGrowthCardMint()).thenReturn(SYSTEM_PROMPT);
        when(contextBuilder.build(topHabit)).thenReturn("mint brief");
        when(assistant.generate(SYSTEM_PROMPT, "mint brief"))
                .thenReturn(GrowthCardDraft.builder().front("").back("").build());

        gateway.mintAfterAnalysis(USER_ID, ANALYSIS_ID);

        verify(store, never()).persistNewOrGet(
                anyLong(), eq("habit"), anyString(), anyString(), anyString(), anyString(), isNull());
    }

    @Test
    void mintHabitByKey_shouldGenerateAndPersistForTop2() {
        ActionCardDto top2 = ActionCardDto.builder().rank(2).habitKey("FAM_ARTICLE").titleZh("冠词").build();
        when(summaryParser.fromJson("{}")).thenReturn(EducationalSummaryDto.builder().build());
        when(analysisSupport.score(any(), any()))
                .thenReturn(new HabitCardScorer.HabitScoreResult(null, List.of(top2), List.of()));
        when(store.findByUserSource(USER_ID, ANALYSIS_ID, "habit", "habit:FAM_ARTICLE"))
                .thenReturn(Optional.empty());
        when(promptLoader.getSystemPromptGrowthCardMint()).thenReturn(SYSTEM_PROMPT);
        when(contextBuilder.build(top2)).thenReturn("top2 brief");
        when(assistant.generate(SYSTEM_PROMPT, "top2 brief")).thenReturn(GrowthCardDraft.builder()
                .front("何时加 a？")
                .back("I need a pen.")
                .build());
        GrowthCard saved = GrowthCard.builder().cardId("card-2").front("何时加 a？").build();
        when(store.persistNewOrGet(
                USER_ID, "habit", "何时加 a？", "I need a pen.",
                ANALYSIS_ID, "habit:FAM_ARTICLE", null))
                .thenReturn(saved);

        GrowthCard result = gateway.mintHabitByKey(USER_ID, ANALYSIS_ID, "FAM_ARTICLE");

        assertEquals("card-2", result.getCardId());
        verify(assistant).generate(SYSTEM_PROMPT, "top2 brief");
    }

    @Test
    void mintHabitByKey_shouldReturnExistingWithoutLlm() {
        ActionCardDto top2 = ActionCardDto.builder().rank(2).habitKey("FAM_ARTICLE").build();
        when(summaryParser.fromJson("{}")).thenReturn(EducationalSummaryDto.builder().build());
        when(analysisSupport.score(any(), any()))
                .thenReturn(new HabitCardScorer.HabitScoreResult(null, List.of(top2), List.of()));
        GrowthCard existing = GrowthCard.builder().cardId("exists").build();
        when(store.findByUserSource(USER_ID, ANALYSIS_ID, "habit", "habit:FAM_ARTICLE"))
                .thenReturn(Optional.of(existing));

        GrowthCard result = gateway.mintHabitByKey(USER_ID, ANALYSIS_ID, "FAM_ARTICLE");

        assertEquals("exists", result.getCardId());
        verify(assistant, never()).generate(anyString(), anyString());
    }

    @Test
    void mintHabitByKey_shouldRejectUnknownHabit() {
        when(summaryParser.fromJson("{}")).thenReturn(EducationalSummaryDto.builder().build());
        when(analysisSupport.score(any(), any()))
                .thenReturn(new HabitCardScorer.HabitScoreResult(null, List.of(), List.of()));

        assertThrows(BadRequestException.class,
                () -> gateway.mintHabitByKey(USER_ID, ANALYSIS_ID, "UNKNOWN"));
        verify(assistant, never()).generate(anyString(), anyString());
    }
}
