package com.mycom.myapp.sendapp.admin.invoice.dto;

import java.time.LocalDateTime;

public record BatchAttemptViewDTO(
        long attemptId,
        int targetYyyymm,
        String executionStatus,
        String executionType,
        long targetCount,
        long successCount,
        long failCount,
        String successRate,
        Long durationMs,
        String durationText,
        String hostName,
        LocalDateTime startedAt,
        LocalDateTime endedAt
) {
}
