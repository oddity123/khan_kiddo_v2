package com.khankiddo.learning.memory.controller;

import com.khankiddo.learning.memory.dto.MemoryDetailResponse;
import com.khankiddo.learning.memory.dto.MemoryItemResponse;
import com.khankiddo.learning.memory.dto.MemoryListResponse;
import com.khankiddo.learning.memory.dto.UpdateMemoryStatusRequest;
import com.khankiddo.learning.memory.service.MemoryService;
import com.khankiddo.learning.security.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/memories")
@RequiredArgsConstructor
public class MemoryController {

    private final MemoryService memoryService;

    @GetMapping
    public MemoryListResponse list(@RequestParam(required = false) List<String> status) {
        return memoryService.listMemories(SecurityUtils.requireUserId(), status);
    }

    @GetMapping("/{id}")
    public MemoryDetailResponse detail(@PathVariable Long id) {
        return memoryService.getMemoryDetail(SecurityUtils.requireUserId(), id);
    }

    @PatchMapping("/{id}/status")
    public MemoryItemResponse updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateMemoryStatusRequest request) {
        return memoryService.updateStatus(SecurityUtils.requireUserId(), id, request.getStatus());
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        memoryService.deleteMemory(SecurityUtils.requireUserId(), id);
    }
}
