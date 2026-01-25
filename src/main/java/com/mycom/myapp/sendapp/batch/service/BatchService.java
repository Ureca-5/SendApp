package com.mycom.myapp.sendapp.batch.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class BatchService {
    private final JobLauncher jobLauncher;
    @Qualifier("monthlyInvoiceRetrySettlementJob")
    private final Job monthlyInvoiceRetrySettlementJob;

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

}
