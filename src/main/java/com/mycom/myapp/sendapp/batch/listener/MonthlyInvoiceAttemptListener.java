package com.mycom.myapp.sendapp.batch.listener;

import com.mycom.myapp.sendapp.batch.repository.attempt.ChunkSettlementResultDto;
import com.mycom.myapp.sendapp.batch.repository.attempt.MonthlyInvoiceBatchAttemptRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.listener.ChunkListenerSupport;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * attempt_id 기반으로 배치 결과와 청크 단위 성공 건수를 기록하는 리스너.
 *
 * - afterChunk: 이번 청크의 성공 건수(writeCount 증가분)를 누적 기록
 * - afterJob  : 최종 상태/소요 시간을 attempt 테이블에 반영
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MonthlyInvoiceAttemptListener extends ChunkListenerSupport implements JobExecutionListener {

    private static final String CTX_ATTEMPT_ID = "monthlyInvoiceAttemptId";
    private static final String CTX_LAST_WRITE_COUNT = "attemptListener.lastWriteCount";

    private final MonthlyInvoiceBatchAttemptRepository attemptRepository;

    @Override
    public void afterChunk(org.springframework.batch.core.scope.context.ChunkContext context) {
        StepExecution stepExecution = context.getStepContext().getStepExecution();
        Long attemptId = getAttemptId(stepExecution.getJobExecution());
        if (attemptId == null) {
            return; // Step0에서 attempt_id를 못 넣었으면 건너뜀
        }

        // writeCount는 Step 누적 값이므로, 직전 값과의 차이를 이번 청크 성공 건수로 본다.
        long currentWrite = stepExecution.getWriteCount();
        long lastWrite = stepExecution.getExecutionContext().getLong(CTX_LAST_WRITE_COUNT, 0L);
        long chunkSuccess = Math.max(0, currentWrite - lastWrite);

        // 다음 청크를 위해 현재 writeCount를 기억
        stepExecution.getExecutionContext().putLong(CTX_LAST_WRITE_COUNT, currentWrite);

        ChunkSettlementResultDto dto = new ChunkSettlementResultDto();
        dto.setSuccessCount(chunkSuccess);
        dto.setFailCount(0L); // 실패 건수 집계가 필요하면 Writer에서 ExecutionContext에 남긴 값을 읽어와 반영

        attemptRepository.applyChunkResult(attemptId, dto);
    }

    @Override
    public void afterJob(JobExecution jobExecution) {
        Long attemptId = getAttemptId(jobExecution);
        if (attemptId == null) {
            return;
        }

        LocalDateTime endedAt = jobExecution.getEndTime() != null
                ? jobExecution.getEndTime()
                : LocalDateTime.now();
        long durationMs = 0L;
        if (jobExecution.getStartTime() != null && jobExecution.getEndTime() != null) {
            durationMs = Duration.between(
                    jobExecution.getStartTime(),
                    jobExecution.getEndTime()
            ).toMillis();
        }

        if (jobExecution.getStatus() == BatchStatus.COMPLETED) {
            attemptRepository.markCompleted(attemptId, endedAt, durationMs);
        } else {
            attemptRepository.markFailed(attemptId, endedAt, durationMs);
        }
    }

    private Long getAttemptId(JobExecution jobExecution) {
        if (jobExecution == null) return null;
        if (jobExecution.getExecutionContext().containsKey(CTX_ATTEMPT_ID)) {
            return jobExecution.getExecutionContext().getLong(CTX_ATTEMPT_ID);
        }
        return null;
    }
}
