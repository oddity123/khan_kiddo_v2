package com.khankiddo.learning.analysistask;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.khankiddo.learning.dto.conversation.ConversationAnalysisProgress;
import com.khankiddo.learning.dto.conversation.ConversationAnalysisRequest;
import com.khankiddo.learning.dto.conversation.ConversationAnalysisResultDto;
import com.khankiddo.learning.service.conversation.ConversationAnalysisService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AnalysisTaskControllerTest {

    private static final String SAMPLE = """
            User: I go to school yesterday.

            Assistant: What happened at school?
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ConversationAnalysisService conversationAnalysisService;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM product_funnel_event");
        jdbcTemplate.update("DELETE FROM conversation_analysis_task");
        jdbcTemplate.update("DELETE FROM extension_credential");
        jdbcTemplate.update("DELETE FROM extension_connect_session");
        jdbcTemplate.update("DELETE FROM users");
    }

    @Test
    void createRequiresLogin() throws Exception {
        mockMvc.perform(post("/api/conversation/analysis-tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(SAMPLE, "chatgpt_history", "conv-1")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createQueuesThenSucceedsAndIsIdempotent() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        String analysisId = UUID.randomUUID().toString();
        doAnswer(invocation -> {
            started.countDown();
            assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
            @SuppressWarnings("unchecked")
            Consumer<ConversationAnalysisProgress> onProgress = invocation.getArgument(2);
            ConversationAnalysisResultDto result = ConversationAnalysisResultDto.builder()
                    .analysisId(analysisId)
                    .build();
            onProgress.accept(ConversationAnalysisProgress.complete(result));
            return result;
        }).when(conversationAnalysisService).analyzeAndPersist(
                any(ConversationAnalysisRequest.class), anyString(), ArgumentMatchers.any());

        String token = registerAndLogin("taskuser");
        MvcResult created = mockMvc.perform(post("/api/conversation/analysis-tasks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(SAMPLE, "chatgpt_history", "conv-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taskId").isString())
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andReturn();
        String taskId = objectMapper.readTree(created.getResponse().getContentAsString()).path("taskId").asText();

        mockMvc.perform(post("/api/conversation/analysis-tasks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(SAMPLE, "chatgpt_history", "conv-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taskId").value(taskId));

        assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
        awaitStatus(token, taskId, "RUNNING");
        release.countDown();
        awaitStatus(token, taskId, "SUCCEEDED");

        mockMvc.perform(get("/api/conversation/analysis-tasks/" + taskId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.analysisId").value(analysisId));
    }

    @Test
    void otherUserCannotReadTask() throws Exception {
        stubAnalyzeSuccess();

        String alice = registerAndLogin("alice_task");
        String bob = registerAndLogin("bob_task");
        MvcResult created = mockMvc.perform(post("/api/conversation/analysis-tasks")
                        .header("Authorization", "Bearer " + alice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(SAMPLE, "chatgpt_history", "conv-a")))
                .andExpect(status().isOk())
                .andReturn();
        String taskId = objectMapper.readTree(created.getResponse().getContentAsString()).path("taskId").asText();

        mockMvc.perform(get("/api/conversation/analysis-tasks/" + taskId)
                        .header("Authorization", "Bearer " + bob))
                .andExpect(status().isForbidden());
    }

    @Test
    void bySourceReturnsLatestAndCreateReusesOnlySameFingerprint() throws Exception {
        stubAnalyzeSuccess();

        String token = registerAndLogin("source_user");
        mockMvc.perform(get("/api/conversation/analysis-tasks/by-source")
                        .param("sourceType", "chatgpt_history")
                        .param("sourceConversationId", "conv-b")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.found").value(false));

        MvcResult first = mockMvc.perform(post("/api/conversation/analysis-tasks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(SAMPLE, "chatgpt_history", "conv-b")))
                .andExpect(status().isOk())
                .andReturn();
        String firstTaskId = objectMapper.readTree(first.getResponse().getContentAsString()).path("taskId").asText();

        mockMvc.perform(get("/api/conversation/analysis-tasks/by-source")
                        .param("sourceType", "chatgpt_history")
                        .param("sourceConversationId", "conv-b")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.found").value(true))
                .andExpect(jsonPath("$.task.taskId").value(firstTaskId));

        mockMvc.perform(post("/api/conversation/analysis-tasks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(SAMPLE, "chatgpt_history", "conv-b")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taskId").value(firstTaskId));

        String updated = SAMPLE + "\nUser: More turns later.\n";
        MvcResult second = mockMvc.perform(post("/api/conversation/analysis-tasks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(updated, "chatgpt_history", "conv-b")))
                .andExpect(status().isOk())
                .andReturn();
        String secondTaskId = objectMapper.readTree(second.getResponse().getContentAsString()).path("taskId").asText();
        assertThat(secondTaskId).isNotEqualTo(firstTaskId);

        mockMvc.perform(get("/api/conversation/analysis-tasks/by-source")
                        .param("sourceType", "chatgpt_history")
                        .param("sourceConversationId", "conv-b")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.found").value(true))
                .andExpect(jsonPath("$.task.taskId").value(secondTaskId));
    }

    @Test
    void sameFingerprintDifferentConversationCreatesSeparateTasks() throws Exception {
        stubAnalyzeSuccess();

        String token = registerAndLogin("multi_conv");
        MvcResult a = mockMvc.perform(post("/api/conversation/analysis-tasks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(SAMPLE, "chatgpt_history", "conv-x")))
                .andExpect(status().isOk())
                .andReturn();
        MvcResult b = mockMvc.perform(post("/api/conversation/analysis-tasks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(SAMPLE, "chatgpt_history", "conv-y")))
                .andExpect(status().isOk())
                .andReturn();
        String taskA = objectMapper.readTree(a.getResponse().getContentAsString()).path("taskId").asText();
        String taskB = objectMapper.readTree(b.getResponse().getContentAsString()).path("taskId").asText();
        assertThat(taskA).isNotEqualTo(taskB);
    }

    private void stubAnalyzeSuccess() {
        doAnswer(invocation -> {
            ConversationAnalysisResultDto result = ConversationAnalysisResultDto.builder()
                    .analysisId(UUID.randomUUID().toString())
                    .build();
            @SuppressWarnings("unchecked")
            Consumer<ConversationAnalysisProgress> onProgress = invocation.getArgument(2);
            onProgress.accept(ConversationAnalysisProgress.complete(result));
            return result;
        }).when(conversationAnalysisService).analyzeAndPersist(any(), anyString(), any());
    }

    private void awaitStatus(String token, String taskId, String expected) throws Exception {
        for (int i = 0; i < 50; i++) {
            MvcResult result = mockMvc.perform(get("/api/conversation/analysis-tasks/" + taskId)
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andReturn();
            String status = objectMapper.readTree(result.getResponse().getContentAsString()).path("status").asText();
            if (expected.equals(status)) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("status did not become " + expected);
    }

    private static String createBody(String content, String sourceType, String sourceConversationId) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.createObjectNode()
                .put("conversationContent", content)
                .put("sourceType", sourceType)
                .put("sourceConversationId", sourceConversationId)
                .toString();
    }

    private String registerAndLogin(String username) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username
                                + "\",\"password\":\"secret12\",\"email\":\"" + username + "@example.com\"}"))
                .andExpect(status().isOk());
        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"secret12\"}"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(login.getResponse().getContentAsString());
        return body.path("token").asText();
    }
}
