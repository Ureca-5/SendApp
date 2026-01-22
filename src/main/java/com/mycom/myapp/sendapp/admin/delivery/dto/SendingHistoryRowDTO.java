package com.mycom.myapp.sendapp.admin.delivery.dto;

import java.time.LocalDateTime;

/**
 * History 탭(시도 로그) 렌더링용 DTO.
 */
public record SendingHistoryRowDTO(
        long deliveryHistoryId,
        long invoiceId,
        int attemptNo,
        String deliveryChannel,
        String receiverMasked,
        String status,
        String errorMessage,
        LocalDateTime requestedAt,
        LocalDateTime sentAt
) {}
