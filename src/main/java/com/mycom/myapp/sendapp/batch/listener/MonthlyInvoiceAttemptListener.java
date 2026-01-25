package com.mycom.myapp.sendapp.batch.listener;

import com.mycom.myapp.sendapp.batch.repository.attempt.ChunkSettlementResultDto;
import com.mycom.myapp.sendapp.batch.repository.attempt.MonthlyInvoiceBatchAttemptRepository;
import com.mycom.myapp.sendapp.batch.support.ChunkHeaderBuffer;
import com.mycom.myapp.sendapp.delivery.service.DeliveryLoaderService;
import net.ttddyy.dsproxy.QueryCount;
import net.ttddyy.dsproxy.QueryCountHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.listener.ChunkListenerSupport;
import org.springframework.batch.core.scope.context.ChunkContext;
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
    private final ChunkHeaderBuffer chunkHeaderBuffer;
    private final DeliveryLoaderService deliveryLoaderService;

    @Override
    public void afterChunk(ChunkContext context) {
        StepExecution stepExecution = context.getStepContext().getStepExecution();

        // Writer가 메모리 버퍼에 넣어둔 성공 헤더 리스트를 꺼내 DeliveryLoaderService로 전달
        var headers = chunkHeaderBuffer.poll(stepExecution.getId());
        if (headers != null && !headers.isEmpty()) {
            deliveryLoaderService.loadChunk(headers);
        }

        // 쿼리 카운트 로깅 후 초기화
        QueryCount qc = QueryCountHolder.get(Thread.currentThread().getName());
        if (qc != null) {
            log.info("Chunk query counts: select={} insert={} update={} delete={}",
                    qc.getSelect(), qc.getInsert(), qc.getUpdate(), qc.getDelete());
        }
        QueryCountHolder.clear();
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
