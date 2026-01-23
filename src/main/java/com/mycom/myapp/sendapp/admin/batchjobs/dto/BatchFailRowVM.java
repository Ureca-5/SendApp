package com.mycom.myapp.sendapp.admin.batchjobs.dto;

import java.time.LocalDateTime;

public record BatchFailRowVM(
        long failId,
        long attemptId,
        int targetYyyymm,
        LocalDateTime createdAt,
        Integer invoiceCategoryId,
        Long billingHistoryId,
        String errorCode,
        String errorMessage
) {}
