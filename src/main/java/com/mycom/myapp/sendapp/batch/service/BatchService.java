package com.mycom.myapp.sendapp.batch.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.stereotype.Service;

import com.mycom.myapp.sendapp.batch.repository.attempt.MonthlyInvoiceBatchAttemptDto;
import com.mycom.myapp.sendapp.batch.repository.attempt.MonthlyInvoiceBatchAttemptRepository;
import com.mycom.myapp.sendapp.batch.support.BatchClock;
import com.mycom.myapp.sendapp.batch.support.HostIdentifier;

import org.springframework.beans.factory.annotation.Qualifier;

import java.time.Duration;
import java.time.LocalDateTime;

@Service
@Slf4j
@RequiredArgsConstructor
public class BatchService {
    private final JobLauncher jobLauncher;
    @Qualifier("monthlyInvoiceRetrySettlementJob")
    private final Job monthlyInvoiceRetrySettlementJob;
    @Qualifier("monthlyInvoiceSettlementForceJob")
    private final Job monthlyInvoiceSettlementForceJob;

    private final MonthlyInvoiceBatchAttemptRepository attemptRepository;
    private final BatchClock batchClock;
    private final HostIdentifier hostIdentifier;

    /**
     * 정산 실패 재시도 배치를 즉시 실행합니다.
     * - 별도 파라미터 없이 execution_type=RETRY attempt가 생성됩니다.
     */
    public JobExecution runRetrySettlementBatch() {
        try {
            JobParameters params = new JobParametersBuilder()
                    .addLong("runId", System.currentTimeMillis()) // 재실행 시 중복 방지
                    .toJobParameters();

            JobExecution execution = jobLauncher.run(monthlyInvoiceRetrySettlementJob, params);
            log.info("Retry settlement batch started. executionId={}, status={}", execution.getId(), execution.getStatus());
            return execution;
        } catch (Exception e) {
            log.error("Failed to start retry settlement batch", e);
            throw new IllegalStateException("재시도 배치를 시작하지 못했습니다.", e);
        }
    }

    /**
     * 180분 이상 중단된 STARTED attempt를 INTERRUPTED로 마킹한 뒤 FORCE로 재시작합니다.
     * - 가장 오래된 STARTED(180분 이상)만 재개
     * - 180분 미만 STARTED가 존재하면 재개 불가
     * - target_count/success_count/fail_count를 중단 attempt 값으로 복사해 새 attempt 시작
     */
    public JobExecution resumeStalledSettlementBatch() {
        LocalDateTime now = batchClock.now();
        LocalDateTime cutoff = now.minusMinutes(180);

        // 180분 미만 STARTED가 있으면 재개하지 않음
        if (attemptRepository.existsStartedAfter(cutoff)) {
            throw new IllegalStateException("180분 미만 진행 중인 배치가 있어 재개할 수 없습니다.");
        }

        MonthlyInvoiceBatchAttemptDto stalled = attemptRepository.findOldestStartedBefore(cutoff)
                .orElseThrow(() -> new IllegalStateException("중단된 배치를 찾을 수 없습니다."));

        long durationMs = stalled.getStartedAt() == null ? 0L : Duration.between(stalled.getStartedAt(), now).toMillis();
        int interrupted = attemptRepository.markInterrupted(stalled.getAttemptId(), now, durationMs);
        if (interrupted == 0) {
            throw new IllegalStateException("중단 attempt 상태 갱신에 실패했습니다. attemptId=" + stalled.getAttemptId());
        }

        long newAttemptId = attemptRepository.insertForceStartedAttempt(
                stalled.getTargetYyyymm(),
                stalled.getTargetCount() == null ? 0L : stalled.getTargetCount(),
                stalled.getSuccessCount() == null ? 0L : stalled.getSuccessCount(),
                stalled.getFailCount() == null ? 0L : stalled.getFailCount(),
                hostIdentifier.get(),
                now
        );

        try {
            JobParameters params = new JobParametersBuilder()
                    .addString("targetYyyymm", String.valueOf(stalled.getTargetYyyymm()))
                    .addLong("attemptId", newAttemptId)
                    .addLong("runId", System.currentTimeMillis())
                    .toJobParameters();

            JobExecution execution = jobLauncher.run(monthlyInvoiceSettlementForceJob, params);
            log.info("Force settlement batch started. executionId={}, attemptId={}, status={}",
                    execution.getId(), newAttemptId, execution.getStatus());
            return execution;
        } catch (Exception e) {
            log.error("Failed to resume stalled settlement batch", e);
            throw new IllegalStateException("중단 배치 재개에 실패했습니다.", e);
        }
    }

}
