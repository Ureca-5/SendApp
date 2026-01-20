package com.mycom.myapp.sendapp.admin.delivery.dto;

import java.time.LocalDateTime;

public record SendingHistoryRowDTO(
        long deliveryHistoryId,
        long invoiceId,
        int attemptNo,
        String channel,
        String receiverInfo,
        String status,
        String errorMessage,
        LocalDateTime requestedAt,
        LocalDateTime sentAt
) {}
