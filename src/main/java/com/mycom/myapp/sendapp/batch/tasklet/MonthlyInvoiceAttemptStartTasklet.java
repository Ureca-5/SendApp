package com.mycom.myapp.sendapp.batch.tasklet;

import com.mycom.myapp.sendapp.batch.guard.BatchStartGuard;
import com.mycom.myapp.sendapp.support.BatchClock;
import com.mycom.myapp.sendapp.support.HostIdentifier;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Step0(Tasklet)
 * - attempt 테이블에 target_yyyymm 기준 STARTED/COMPLETED 존재 여부 검사
 * - 없다면 STARTED attempt insert
 * - attempt_id를 JobExecutionContext에 저장(후속 Step에서 참조)
 *
 * 전제:
 * - 단일 서버 환경(락 테이블/lease 없음)
 * - "중복 실행 방지"는 attempt 테이블의 검사/제약으로 방어
 */
@Component
@RequiredArgsConstructor
public class MonthlyInvoiceAttemptStartTasklet implements Tasklet, StepExecutionListener {
    public static final String CTX_KEY_TARGET_YYYYMM = "targetYyyymm";
    public static final String CTX_KEY_ATTEMPT_ID = "monthlyInvoiceAttemptId";
    private final BatchStartGuard batchStartGuard;   // 현재는 AttemptOnlyBatchStartGuard
    private final BatchClock batchClock;             // 테스트 용이성(현재 시각 주입)
    private final HostIdentifier hostIdentifier;     // host/pid/instance 식별
    private int targetYyyymm;

    @Override
    public void beforeStep(StepExecution stepExecution) {
        // JobParameters에서 targetYyyymm을 받는 것이 정석이지만,
        // 우선 구현 난이도를 낮추기 위해 아래 우선순위로 가져옵니다.
        // 1) JobParameters: targetYyyymm
        // 2) ExecutionContext: targetYyyymm (재시작 시)
        // 값이 없으면 예외 처리
        Integer fromParams = getIntJobParam(stepExecution, "targetYyyymm");
        if (fromParams != null) {
            this.targetYyyymm = fromParams;
            stepExecution.getJobExecution().getExecutionContext().putInt(CTX_KEY_TARGET_YYYYMM, fromParams);
            return;
        }

        ExecutionContext jobCtx = stepExecution.getJobExecution().getExecutionContext();
        if (jobCtx.containsKey(CTX_KEY_TARGET_YYYYMM)) {
            this.targetYyyymm = jobCtx.getInt(CTX_KEY_TARGET_YYYYMM);
            return;
        }

        throw new IllegalStateException("JobParameters에 targetYyyymm(int)이 필요합니다. 예: --targetYyyymm=202601");
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        StepExecution stepExecution = contribution.getStepExecution();
        ExecutionContext jobCtx = stepExecution.getJobExecution().getExecutionContext();

        // 이미 attempt_id가 있으면(재시작/중복 호출) 그대로 통과
        if (jobCtx.containsKey(CTX_KEY_ATTEMPT_ID)) {
            return RepeatStatus.FINISHED;
        }

        LocalDateTime now = batchClock.now();
        String host = hostIdentifier.get();

        // 1) 배치 시작 가능 여부(STARTED/COMPLETED 존재 시 거부)
        batchStartGuard.assertStartable(targetYyyymm);

        // 2) STARTED attempt 생성
        long attemptId = batchStartGuard.createStartedAttempt(targetYyyymm, now, host);

        // 3) 후속 Step에서 attempt_id를 참조할 수 있도록 저장
        jobCtx.putLong(CTX_KEY_ATTEMPT_ID, attemptId);

        return RepeatStatus.FINISHED;
    }

    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        return ExitStatus.COMPLETED;
    }

    private Integer getIntJobParam(StepExecution stepExecution, String key) {
        if (stepExecution.getJobParameters() == null) return null;
        var param = stepExecution.getJobParameters().getParameters().get(key);
        if (param == null) return null;

        Object value = param.getValue();
        if (value instanceof Long l) return l.intValue();
        if (value instanceof Integer i) return i;
        if (value instanceof String s) {
            try {
                return Integer.parseInt(s);
            } catch (NumberFormatException ignore) {
                return null;
            }
        }
        return null;
    }
}
