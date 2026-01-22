package com.mycom.myapp.sendapp.batch.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Slf4j
@Component
@RequiredArgsConstructor
public class MonthlyInvoiceBatchScheduler {
    private final JobLauncher jobLauncher;
    private final Job monthlyInvoiceSettlementJob;
    private final JdbcTemplate jdbcTemplate; // 테스트 배치 시작 전, 대상 년월의 정산 관련 데이터 모두 제거

    /**
     * 정산 대상 년월 파라미터를 생성
     *
     * 예: 현재가 2026-02-01이라면 202601로 잡는다
     * → 지난달 데이터를 정산하는 것이 일반적인 패턴
     */
    private Integer makeTargetYyyymm() {
        LocalDate now = LocalDate.now();
        LocalDate prev = now.minusMonths(1);
        return prev.getYear() * 100 + prev.getMonthValue();
    }

    /**
     * 매월 1일 새벽 3시에 배치 실행
     * cron 설정: (초 분 시 일 월 요일)
     */
    @Scheduled(cron = "0 0 7 3 * *")
    public void scheduleMonthlyInvoiceBatch() {
        Integer targetYyyymm = makeTargetYyyymm();

        log.info("Scheduled Monthly Invoice Batch Start. targetYyyymm={}", targetYyyymm);

        JobParameters params = new JobParametersBuilder()
                .addString("targetYyyymm", targetYyyymm.toString())
                // runId를 넣으면 동일 파라미터로도 재실행 가능
                .addLong("runId", System.currentTimeMillis())
                .toJobParameters();

        try {
            JobExecution execution = jobLauncher.run(monthlyInvoiceSettlementJob, params);
            log.info("Batch executed with status={}", execution.getStatus());
        } catch (Exception e) {
            log.error("Failed to execute monthly invoice batch", e);
        }
    }

    /**
     * 테스트 원천 데이터(2025년 10월) 정산 배치 수행 메서드(테스트용)
     */
    public void testMonthlyInvoiceBatch(Integer targetYyyymm) {
        if(targetYyyymm == null || targetYyyymm <= 0) {
            throw new IllegalArgumentException("정산 배치 테스트할 년월 정보를 올바르게 입력해주세요.");
        }
        log.info("Scheduled Monthly Invoice Batch Start. targetYyyymm={}", targetYyyymm);

        // 테스트 배치 수행 전 대상 년월에 해당하는 정산 결과 관련 데이터를 모두 제거
        String[] preBatchSqlArray = {
                "set foreign_key_checks = 0",
                "delete from monthly_invoice_batch_attempt",
                "truncate monthly_invoice_batch_fail",
                "truncate settlement_status_history",
                "truncate settlement_status",
                "truncate monthly_invoice_detail",
                "truncate monthly_invoice",
                "set foreign_key_checks = 1"
        };
        jdbcTemplate.batchUpdate(preBatchSqlArray);

        JobParameters params = new JobParametersBuilder()
                .addString("targetYyyymm", targetYyyymm.toString())
                // runId를 넣으면 동일 파라미터로도 재실행 가능
                .addLong("runId", System.currentTimeMillis())
                .toJobParameters();

        try {
            JobExecution execution = jobLauncher.run(monthlyInvoiceSettlementJob, params);
            log.info("Batch executed with status={}", execution.getStatus());
        } catch (Exception e) {
            log.error("Failed to execute monthly invoice batch", e);
        }
    }
}
