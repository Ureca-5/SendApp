package com.mycom.myapp.sendapp.admin.invoice.dto;

import java.time.LocalDateTime;

public record BatchFailViewDTO(
        long failId,
        long attemptId,
        String categoryName,
        long billingHistoryId,
        String errorCode,
        String errorMessage,
        String errorMessageShort,
        LocalDateTime createdAt
) {
}
