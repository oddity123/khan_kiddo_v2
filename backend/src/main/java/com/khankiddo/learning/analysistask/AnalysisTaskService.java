package com.khankiddo.learning.analysistask;

import com.khankiddo.learning.analysistask.dto.AnalysisTaskConfigResponse;
import com.khankiddo.learning.analysistask.dto.AnalysisTaskLookupResponse;
import com.khankiddo.learning.analysistask.dto.AnalysisTaskResponse;
import com.khankiddo.learning.analysistask.dto.CreateAnalysisTaskRequest;
import com.khankiddo.learning.config.AnalysisTaskProperties;
import com.khankiddo.learning.config.ConversationAnalysisProperties;
import com.khankiddo.learning.conversation.ConversationAnalyzeRateLimiter;
import com.khankiddo.learning.conversation.ConversationAnalysisErrorMessages;
import com.khankiddo.learning.dto.conversation.ConversationAnalysisRequest;
import com.khankiddo.learning.dto.conversation.ConversationAnalysisResultDto;
import com.khankiddo.learning.exception.BadRequestException;
import com.khankiddo.learning.exception.ForbiddenException;
import com.khankiddo.learning.extensionconnect.ExtensionConnectTokenSupport;
import com.khankiddo.learning.funnel.FunnelEventName;
import com.khankiddo.learning.funnel.FunnelEventService;
import com.khankiddo.learning.model.UserRole;
import com.khankiddo.learning.security.AuthenticatedUser;
import com.khankiddo.learning.security.SecurityUtils;
import com.khankiddo.learning.service.conversation.ConversationAnalysisService;
import com.khankiddo.learning.util.TextSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnalysisTaskService {

    private static final Set<String> ALLOWED_SOURCE_TYPES = Set.of(
            "chatgpt_history",
            "chatgpt_share"
    );

    private final AnalysisTaskProperties properties;
    private final ConversationAnalysisProperties analysisProperties;
    private final ConversationAnalysisTaskMapper taskMapper;
    private final ConversationAnalysisService conversationAnalysisService;
    private final ConversationAnalyzeRateLimiter analyzeRateLimiter;
    private final FunnelEventService funnelEventService;
    private final Clock clock;

    public AnalysisTaskConfigResponse config() {
        return AnalysisTaskConfigResponse.builder()
                .enabled(properties.isEnabled())
                .pollIntervalMs(properties.getPollIntervalMs())
                .pollTimeoutMs(properties.getPollTimeoutMs())
                .build();
    }

    public AnalysisTaskResponse create(CreateAnalysisTaskRequest request) {
        requireEnabled();
        AuthenticatedUser user = SecurityUtils.requireCurrentUser();
        analyzeRateLimiter.checkAllowed(user.id());

        String content = normalizeContent(request.getConversationContent());
        validateContentLength(content);
        String sourceType = normalizeSourceType(request.getSourceType());
        String sourceConversationId = TextSupport.trimToNull(request.getSourceConversationId());
        String modelId = TextSupport.trimToNull(request.getModelId());
        String fingerprint = ExtensionConnectTokenSupport.sha256Hex(content);

        Optional<ConversationAnalysisTask> reusable = StringUtils.hasText(sourceConversationId)
                ? taskMapper.findReusableBySourceAndFingerprint(
                        user.id(), sourceType, sourceConversationId, fingerprint)
                : taskMapper.findReusableByFingerprint(user.id(), fingerprint);
        if (reusable.isPresent()) {
            return toResponse(reusable.get());
        }

        LocalDateTime now = LocalDateTime.now(clock);
        ConversationAnalysisTask task = ConversationAnalysisTask.builder()
                .taskId(UUID.randomUUID().toString())
                .userId(user.id())
                .status(AnalysisTaskStatus.QUEUED)
                .conversationContent(content)
                .contentFingerprint(fingerprint)
                .sourceType(sourceType)
                .sourceConversationId(sourceConversationId)
                .modelId(modelId)
                .createdAt(now)
                .updatedAt(now)
                .build();
        // 先提交插入再调度，避免虚拟线程读不到未提交行
        taskMapper.insert(task);
        funnelEventService.recordInternal(user.id(), FunnelEventName.ext_analyze_submitted);
        scheduleExecution(task.getTaskId(), user);
        return toResponse(task);
    }

    public AnalysisTaskResponse getOwned(String taskId) {
        requireEnabled();
        Long userId = SecurityUtils.requireUserId();
        ConversationAnalysisTask task = taskMapper.findByTaskId(taskId)
                .orElseThrow(() -> new BadRequestException("分析任务不存在"));
        if (!userId.equals(task.getUserId())) {
            throw new ForbiddenException("无权查看该分析任务");
        }
        return toResponse(task);
    }

    /**
     * 按来源对话查询最新任务状态（含 FAILED）；无记录时 {@code found=false}。
     */
    public AnalysisTaskLookupResponse findLatestBySource(String sourceTypeRaw, String sourceConversationIdRaw) {
        requireEnabled();
        Long userId = SecurityUtils.requireUserId();
        String sourceType = normalizeSourceType(sourceTypeRaw);
        String sourceConversationId = TextSupport.trimToNull(sourceConversationIdRaw);
        if (!StringUtils.hasText(sourceConversationId)) {
            throw new BadRequestException("来源对话 id 不能为空");
        }
        return taskMapper.findLatestBySource(userId, sourceType, sourceConversationId)
                .map(task -> AnalysisTaskLookupResponse.builder()
                        .found(true)
                        .task(toResponse(task))
                        .build())
                .orElseGet(() -> AnalysisTaskLookupResponse.builder()
                        .found(false)
                        .build());
    }

    private void scheduleExecution(String taskId, AuthenticatedUser user) {
        Thread.startVirtualThread(() -> runTask(taskId, user));
    }

    private void runTask(String taskId, AuthenticatedUser user) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
        if (UserRole.ADMIN.equals(user.role())) {
            authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
        }
        context.setAuthentication(new UsernamePasswordAuthenticationToken(user, null, authorities));
        SecurityContextHolder.setContext(context);
        long startedMs = System.currentTimeMillis();
        try {
            ConversationAnalysisTask task = taskMapper.findByTaskId(taskId).orElse(null);
            if (ObjectUtils.isEmpty(task) || !AnalysisTaskStatus.QUEUED.equals(task.getStatus())) {
                return;
            }
            LocalDateTime now = LocalDateTime.now(clock);
            if (taskMapper.markRunning(taskId, now, AnalysisTaskStatus.QUEUED) != 1) {
                return;
            }
            String analysisId = UUID.randomUUID().toString();
            ConversationAnalysisRequest analysisRequest = new ConversationAnalysisRequest();
            analysisRequest.setConversationContent(task.getConversationContent());
            analysisRequest.setModelId(task.getModelId());
            ConversationAnalysisResultDto result = conversationAnalysisService.analyzeAndPersist(
                    analysisRequest, analysisId, progress -> {
                    });
            LocalDateTime finished = LocalDateTime.now(clock);
            String resultAnalysisId = StringUtils.hasText(result.getAnalysisId())
                    ? result.getAnalysisId()
                    : analysisId;
            taskMapper.markSucceeded(taskId, resultAnalysisId, finished, AnalysisTaskStatus.RUNNING);
            funnelEventService.recordInternal(user.id(), FunnelEventName.ext_analyze_succeeded);
        } catch (Exception ex) {
            log.error("分析任务失败 taskId={}", taskId, ex);
            String message = ConversationAnalysisErrorMessages.toUserMessage(ex);
            LocalDateTime finished = LocalDateTime.now(clock);
            taskMapper.markFailed(taskId, message, finished, AnalysisTaskStatus.RUNNING);
            // 若尚未 RUNNING（极少），再尝试从 QUEUED 标记失败
            taskMapper.markFailed(taskId, message, finished, AnalysisTaskStatus.QUEUED);
            funnelEventService.recordInternal(user.id(), FunnelEventName.ext_analyze_failed);
            long elapsed = System.currentTimeMillis() - startedMs;
            log.debug("analysis task failed elapsedMs={}", elapsed);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private void requireEnabled() {
        if (!properties.isEnabled()) {
            throw new ForbiddenException("异步分析任务暂未开放");
        }
    }

    private void validateContentLength(String content) {
        int len = content.length();
        if (len < analysisProperties.getMinContentLength()) {
            throw new BadRequestException("对话内容过短");
        }
        if (len > analysisProperties.getMaxContentLength()) {
            throw new BadRequestException("对话内容过长");
        }
    }

    private static String normalizeContent(String raw) {
        if (!StringUtils.hasText(raw)) {
            throw new BadRequestException("对话内容不能为空");
        }
        return raw.trim();
    }

    private static String normalizeSourceType(String raw) {
        if (!StringUtils.hasText(raw)) {
            throw new BadRequestException("来源类型不能为空");
        }
        String type = raw.trim().toLowerCase(Locale.ROOT);
        if (!ALLOWED_SOURCE_TYPES.contains(type)) {
            throw new BadRequestException("不支持的来源类型");
        }
        return type;
    }

    private static AnalysisTaskResponse toResponse(ConversationAnalysisTask task) {
        return AnalysisTaskResponse.builder()
                .taskId(task.getTaskId())
                .status(task.getStatus())
                .analysisId(task.getAnalysisId())
                .errorMessage(task.getErrorMessage())
                .sourceType(task.getSourceType())
                .sourceConversationId(task.getSourceConversationId())
                .createdAt(task.getCreatedAt())
                .startedAt(task.getStartedAt())
                .finishedAt(task.getFinishedAt())
                .build();
    }
}
