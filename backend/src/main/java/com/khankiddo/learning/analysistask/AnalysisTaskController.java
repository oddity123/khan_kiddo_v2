package com.khankiddo.learning.analysistask;

import com.khankiddo.learning.analysistask.dto.AnalysisTaskConfigResponse;
import com.khankiddo.learning.analysistask.dto.AnalysisTaskResponse;
import com.khankiddo.learning.analysistask.dto.CreateAnalysisTaskRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/conversation/analysis-tasks")
@RequiredArgsConstructor
public class AnalysisTaskController {

    private final AnalysisTaskService service;

    @GetMapping("/config")
    public AnalysisTaskConfigResponse config() {
        return service.config();
    }

    @PostMapping
    public AnalysisTaskResponse create(@Valid @RequestBody CreateAnalysisTaskRequest request) {
        return service.create(request);
    }

    @GetMapping("/{taskId}")
    public AnalysisTaskResponse get(@PathVariable String taskId) {
        return service.getOwned(taskId);
    }
}
