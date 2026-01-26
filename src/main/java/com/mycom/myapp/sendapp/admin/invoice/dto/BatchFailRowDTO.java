package com.mycom.myapp.sendapp.admin.invoice.dto;

import java.time.LocalDateTime;

public record BatchFailRowDTO(
        long failId,
        long attemptId,
        int invoiceCategoryId,
        String invoiceCategoryName,
        long billingHistoryId,
        String errorCode,
        String errorMessage,
        LocalDateTime createdAt
) {
}
