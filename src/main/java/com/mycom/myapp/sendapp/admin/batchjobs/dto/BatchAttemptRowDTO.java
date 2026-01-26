package com.mycom.myapp.sendapp.admin.batchjobs.dto;

import java.time.LocalDateTime;

public record BatchAttemptRowDTO(
        long attemptId,
        int targetYyyymm,
        String executionStatus,
        String executionType,
        LocalDateTime startedAt,
        LocalDateTime endedAt,
        Long durationMs,
        long targetCount,
        long successCount,
        long failCount,
        String hostName,
        String errorMessage
) {}
