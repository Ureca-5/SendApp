package com.mycom.myapp.sendapp.admin.delivery.dto;

import java.time.LocalDateTime;

public record SendingStatusRowDTO(
        long invoiceId,
        int billingYyyymm,
        long usersId,
        String userNameMasked,
        String phoneMasked,
        String status,
        String channel,
        int retryCount,
        LocalDateTime lastAttemptAt,
        LocalDateTime createdAt
) {}
