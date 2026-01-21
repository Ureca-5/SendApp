package com.mycom.myapp.sendapp.admin.batchjobs.dto;

import java.time.LocalDateTime;

public record BatchAttemptRowDTO(
        Long attemptId,
        Integer targetYyyymm,
        String status,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        Integer targetUserCount,
        Integer processedUserCount,
        Integer failCount,
        String hostName,
        String errorMessage
) {}
