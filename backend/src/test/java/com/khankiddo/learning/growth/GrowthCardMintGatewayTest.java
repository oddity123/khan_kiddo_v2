package com.khankiddo.learning.growth;

import com.khankiddo.learning.conversation.EducationalSummaryParser;
import com.khankiddo.learning.dto.conversation.ActionCardDto;
import com.khankiddo.learning.dto.conversation.ChineseExpressionDto;
import com.khankiddo.learning.dto.conversation.EducationalSummaryDto;
import com.khankiddo.learning.knowledge.HabitCardScorer;
import com.khankiddo.learning.mapper.ConversationAnalysisItemMapper;
import com.khankiddo.learning.mapper.ConversationAnalysisMapper;
import com.khankiddo.learning.model.ConversationAnalysis;
import com.khankiddo.learning.model.GrowthCard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GrowthCardMintGatewayTest {

    private static final long USER_ID = 7L;
    private static final String ANALYSIS_ID = "analysis-1";

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

    private GrowthCardMintGateway gateway;

    @BeforeEach
    void setUp() {
        gateway = new GrowthCardMintGateway(
                analysisMapper, itemMapper, summaryParser, analysisSupport, contextBuilder, assistant, store);
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

        verify(assistant, never()).mintHabitCard(anyLong(), anyString());
        verify(store).persistNewOrGet(
                USER_ID, "vocab", "很有成就感", "I feel a strong sense of accomplishment.",
                ANALYSIS_ID, "vocab:3", null);
    }

    @Test
    void mintAfterAnalysis_shouldNotInvokeAssistantWhenHabitAlreadyExists() {
        ActionCardDto topHabit = ActionCardDto.builder().habitKey("FAM_TENSE").build();
        when(summaryParser.fromJson("{}")).thenReturn(EducationalSummaryDto.builder().build());
        when(analysisSupport.score(any(), any()))
                .thenReturn(new HabitCardScorer.HabitScoreResult(topHabit, List.of(topHabit), List.of()));
        when(store.findHabitByAnalysis(USER_ID, ANALYSIS_ID))
                .thenReturn(Optional.of(GrowthCard.builder().cardId("existing").build()));

        gateway.mintAfterAnalysis(USER_ID, ANALYSIS_ID);

        verify(assistant, never()).mintHabitCard(anyLong(), anyString());
    }

    @Test
    void mintAfterAnalysis_shouldInvokeAssistantOnceForNewTopHabit() {
        ActionCardDto topHabit = ActionCardDto.builder().habitKey("FAM_TENSE").build();
        when(summaryParser.fromJson("{}")).thenReturn(EducationalSummaryDto.builder().build());
        when(analysisSupport.score(any(), any()))
                .thenReturn(new HabitCardScorer.HabitScoreResult(topHabit, List.of(topHabit), List.of()));
        when(store.findHabitByAnalysis(USER_ID, ANALYSIS_ID)).thenReturn(Optional.ofNullable(null));
        when(contextBuilder.build(ANALYSIS_ID, topHabit)).thenReturn("mint brief");

        gateway.mintAfterAnalysis(USER_ID, ANALYSIS_ID);

        verify(assistant, times(1)).mintHabitCard(USER_ID, "mint brief");
    }
}
