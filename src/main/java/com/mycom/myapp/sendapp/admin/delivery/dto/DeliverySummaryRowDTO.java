package com.mycom.myapp.sendapp.admin.delivery.dto;

import java.time.LocalDateTime;

/**
 * Summary 탭(월+채널 집계) 렌더링용 DTO.
 *
 * 기대 테이블(예시):
 *  delivery_summary(billing_yyyymm, delivery_channel, total_attempt_count, success_count, fail_count, success_rate, updated_at)
 */
public record DeliverySummaryRowDTO(
        int billingYyyymm,
        String deliveryChannel,
        long totalAttemptCount,
        long successCount,
        long failCount,
        Integer successRate,        // 퍼센트 정수(0~100) 또는 NULL
        LocalDateTime updatedAt
) {}
