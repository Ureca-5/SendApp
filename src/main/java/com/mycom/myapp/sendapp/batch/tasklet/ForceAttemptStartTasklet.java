package com.mycom.myapp.sendapp.batch.tasklet;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.stereotype.Component;

/**
 * 중단 배치 재개 시, 이미 생성된 attempt_id/targetYyyymm을 JobExecutionContext에 주입만 하는 Tasklet.
 * - Guard를 우회하고 곧바로 Step1을 실행하기 위한 용도.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ForceAttemptStartTasklet implements Tasklet, StepExecutionListener {
    @Override
    public void beforeStep(StepExecution stepExecution) {
        // no-op
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        StepExecution stepExecution = contribution.getStepExecution();
        ExecutionContext jobCtx = stepExecution.getJobExecution().getExecutionContext();

        Long attemptId = getLongParam(stepExecution, "attemptId");
        Integer targetYyyymm = getIntParam(stepExecution, "targetYyyymm");

        if (attemptId == null || targetYyyymm == null) {
            throw new IllegalStateException("attemptId와 targetYyyymm JobParameter가 필요합니다.");
        }

        // 기존 정기 배치와 동일한 키로 저장하여 Writer/Processor가 활용하도록 한다.
        jobCtx.putLong(MonthlyInvoiceAttemptStartTasklet.CTX_KEY_ATTEMPT_ID, attemptId);
        jobCtx.putInt(MonthlyInvoiceAttemptStartTasklet.CTX_KEY_TARGET_YYYYMM, targetYyyymm);

        log.info("Force attempt context set. attemptId={}, targetYyyymm={}", attemptId, targetYyyymm);
        return RepeatStatus.FINISHED;
    }

    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        return ExitStatus.COMPLETED;
    }

    private Long getLongParam(StepExecution stepExecution, String key) {
        if (stepExecution.getJobParameters() == null) return null;
        var param = stepExecution.getJobParameters().getParameters().get(key);
        if (param == null) return null;
        Object value = param.getValue();
        if (value instanceof Long l) return l;
        if (value instanceof Integer i) return i.longValue();
        if (value instanceof String s) {
            try {
                return Long.parseLong(s);
            } catch (NumberFormatException ignore) {
                return null;
            }
        }
        return null;
    }

    private Integer getIntParam(StepExecution stepExecution, String key) {
        if (stepExecution.getJobParameters() == null) return null;
        var param = stepExecution.getJobParameters().getParameters().get(key);
        if (param == null) return null;
        Object value = param.getValue();
        if (value instanceof Integer i) return i;
        if (value instanceof Long l) return l.intValue();
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
