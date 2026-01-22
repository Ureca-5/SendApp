package com.mycom.myapp.sendapp.admin.delivery.dto;

import java.time.LocalDateTime;

/**
 * Status 탭(현재 스냅샷) 렌더링용 DTO.
 *
 * NOTE: 상태 값은 READY/SENT/FAILED 를 전제로 한다.
 */
public record SendingStatusRowDTO(
        long invoiceId,
        Integer billingYyyymm,
        Long usersId,
        String userName,
        String receiverMasked,
        String deliveryChannel,
        String status,
        int retryCount,
        Long totalAmount,
        LocalDateTime lastAttemptAt,
        LocalDateTime createdAt
) {}
