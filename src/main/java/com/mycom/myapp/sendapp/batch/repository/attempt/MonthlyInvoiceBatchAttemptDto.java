package com.mycom.myapp.sendapp.batch.repository.attempt;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 배치 실행 이력 조회용 DTO
 */
@Getter
@Builder
public class MonthlyInvoiceBatchAttemptDto {
    private final Long attemptId;
    private final Integer targetYyyymm;
    private final MonthlyInvoiceBatchExecutionStatus executionStatus;
    private final MonthlyInvoiceBatchExecutionType executionType;
    private final LocalDateTime startedAt;
    private final LocalDateTime endedAt;
    private final Long durationMs;
    private final Long successCount;
    private final Long failCount;
    private final String hostName;
}
