package com.mycom.myapp.sendapp.batch.tasklet;

import com.mycom.myapp.sendapp.batch.repository.attempt.MonthlyInvoiceBatchExecutionStatus;
import com.mycom.myapp.sendapp.batch.repository.attempt.MonthlyInvoiceBatchExecutionType;
import com.mycom.myapp.sendapp.batch.support.BatchClock;
import com.mycom.myapp.sendapp.batch.support.HostIdentifier;
import com.mycom.myapp.sendapp.batch.tasklet.MonthlyInvoiceAttemptStartTasklet;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;

/**
 * 재시도 배치용 attempt 생성 Tasklet.
 * - settlement_status.status=FAILED 대상 전체를 정산 대상으로 삼아 target_count에 기록
 * - execution_type=RETRY 로 attempt 생성 후 attempt_id를 JobExecutionContext에 저장
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RetrySettlementAttemptStartTasklet implements Tasklet, StepExecutionListener {
    public static final int RETRY_TARGET_YYYYMM = 0; // 다월 대상인 재시도 배치는 0으로 기록

    private final JdbcTemplate jdbcTemplate;
    private final BatchClock batchClock;
    private final HostIdentifier hostIdentifier;

    @Override
    public void beforeStep(StepExecution stepExecution) {
        // no-op
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        StepExecution stepExecution = contribution.getStepExecution();
        ExecutionContext jobCtx = stepExecution.getJobExecution().getExecutionContext();

        // 이미 attempt_id가 있으면(재시작/중복 호출) 통과
        if (jobCtx.containsKey(MonthlyInvoiceAttemptStartTasklet.CTX_KEY_ATTEMPT_ID)) {
            return RepeatStatus.FINISHED;
        }

        // STARTED RETRY attempt 중복 실행 방지
        assertRetryStartable();

        long targetCount = countFailedSettlements();
        LocalDateTime now = batchClock.now();
        long attemptId = insertRetryAttempt(targetCount, now, hostIdentifier.get());

        jobCtx.putLong(MonthlyInvoiceAttemptStartTasklet.CTX_KEY_ATTEMPT_ID, attemptId);
        jobCtx.putInt(MonthlyInvoiceAttemptStartTasklet.CTX_KEY_TARGET_YYYYMM, RETRY_TARGET_YYYYMM);

        log.info("Retry attempt created. attemptId={}, targetCount={}", attemptId, targetCount);
        return RepeatStatus.FINISHED;
    }

    @Override
    public org.springframework.batch.core.ExitStatus afterStep(StepExecution stepExecution) {
        return org.springframework.batch.core.ExitStatus.COMPLETED;
    }

    private long countFailedSettlements() {
        String sql = "SELECT COUNT(*) FROM settlement_status WHERE status = 'FAILED'";
        Long count = jdbcTemplate.queryForObject(sql, Long.class);
        return count == null ? 0L : count;
    }

    private void assertRetryStartable() {
        String sql = """
            SELECT attempt_id
            FROM monthly_invoice_batch_attempt
            WHERE execution_type = ?
              AND execution_status = ?
            LIMIT 1
            FOR UPDATE
            """;
        Long existing = jdbcTemplate.query(sql,
                rs -> rs.next() ? rs.getLong(1) : null,
                MonthlyInvoiceBatchExecutionType.RETRY.name(),
                MonthlyInvoiceBatchExecutionStatus.STARTED.name());

        if (existing != null) {
            throw new IllegalStateException("이미 진행 중인 RETRY 배치가 있습니다. attemptId=" + existing);
        }
    }

    private long insertRetryAttempt(long targetCount, LocalDateTime startedAt, String hostName) {
        String sql = """
            INSERT INTO monthly_invoice_batch_attempt
                (target_yyyymm, execution_status, execution_type, started_at, ended_at, duration_ms,
                 success_count, fail_count, host_name, target_count)
            VALUES
                (?, ?, ?, ?, NULL, NULL,
                 0, 0, ?, ?)
            """;

        KeyHolder keyHolder = new GeneratedKeyHolder();

        int updated = jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setInt(1, RETRY_TARGET_YYYYMM);
            ps.setString(2, MonthlyInvoiceBatchExecutionStatus.STARTED.name());
            ps.setString(3, MonthlyInvoiceBatchExecutionType.RETRY.name());
            ps.setTimestamp(4, Timestamp.valueOf(startedAt));
            ps.setString(5, hostName);
            ps.setLong(6, targetCount);
            return ps;
        }, keyHolder);

        if (updated != 1) {
            throw new IllegalStateException("RETRY attempt insert 실패. updatedRows=" + updated);
        }

        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("RETRY attempt_id를 반환받지 못했습니다. AUTO_INCREMENT 설정을 확인해주세요.");
        }
        return key.longValue();
    }
}
