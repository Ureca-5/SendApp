// src/main/java/com/mycom/myapp/sendapp/admin/dashboard/dto/DashboardBatchAttemptVM.java
package com.mycom.myapp.sendapp.admin.dashboard.dto;

public record DashboardBatchAttemptVM(
        long attemptId,
        int targetYyyymm,
        String executionStatus,
        String executionType,
        String startedAt,
        Long durationMs,
        Long successCount,
        Long failCount
) {}
