package com.khankiddo.learning;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PracticePromptControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void assemble_successWithoutAuth() throws Exception {
        mockMvc.perform(post("/api/conversation/practice-prompt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"goals":[{"rank":1,"title":"过去时","diagnosis":"用了现在时","coaching":"先定时间","originalSentence":"Yesterday I go.","targetSentence":"Yesterday I went."}]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.prompt").value(containsString("你是我的英语口语陪练")))
                .andExpect(jsonPath("$.prompt").value(containsString("1. 过去时")));
    }

    @Test
    void assemble_rejectsBothEmpty() throws Exception {
        mockMvc.perform(post("/api/conversation/practice-prompt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"goals":[],"vocabulary":[]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("请至少选择一项薄弱点或词汇"));
    }

    @Test
    void assemble_rejectsMoreThanThreeGoals() throws Exception {
        mockMvc.perform(post("/api/conversation/practice-prompt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"goals":[
                                  {"rank":1,"title":"a"},
                                  {"rank":2,"title":"b"},
                                  {"rank":3,"title":"c"},
                                  {"rank":1,"title":"d"}
                                ]}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void assemble_rejectsMoreThanThreeVocabulary() throws Exception {
        mockMvc.perform(post("/api/conversation/practice-prompt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"vocabulary":[
                                  {"front":"a","back":"a"},
                                  {"front":"b","back":"b"},
                                  {"front":"c","back":"c"},
                                  {"front":"d","back":"d"}
                                ]}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void assemble_rejectsRankOutOfRangeOrDuplicate() throws Exception {
        mockMvc.perform(post("/api/conversation/practice-prompt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"goals":[{"rank":4,"title":"越界"}]}
                                """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/conversation/practice-prompt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"goals":[{"rank":1,"title":"过去时"},{"rank":1,"title":"冠词"}]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("rank 不能重复"));
    }

    @Test
    void assemble_rejectsBlankOrOversizedFields() throws Exception {
        mockMvc.perform(post("/api/conversation/practice-prompt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"goals":[{"rank":1,"title":"  "}]}
                                """))
                .andExpect(status().isBadRequest());

        String longTitle = "过".repeat(201);
        mockMvc.perform(post("/api/conversation/practice-prompt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"goals\":[{\"rank\":1,\"title\":\"" + longTitle + "\"}]}"))
                .andExpect(status().isBadRequest());
    }
}
