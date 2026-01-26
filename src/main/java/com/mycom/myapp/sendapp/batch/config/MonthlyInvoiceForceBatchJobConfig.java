package com.mycom.myapp.sendapp.batch.config;

import com.mycom.myapp.sendapp.batch.listener.MonthlyInvoiceAttemptListener;
import com.mycom.myapp.sendapp.batch.tasklet.ForceAttemptStartTasklet;
import com.mycom.myapp.sendapp.batch.writer.MonthlyInvoiceWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.database.JdbcPagingItemReader;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * 중단된 정산 배치를 강제 재개하는 Job 설정
 * - Step0: ForceAttemptStartTasklet (attemptId/targetYyyymm 컨텍스트 세팅)
 * - Step1: 기존 settlement chunk(step1SettlementChunk)와 동일한 로직 사용
 */
@Configuration
@RequiredArgsConstructor
public class MonthlyInvoiceForceBatchJobConfig {
    private final org.springframework.batch.core.repository.JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;

    private final ForceAttemptStartTasklet forceAttemptStartTasklet;
    private final MonthlyInvoiceAttemptListener monthlyInvoiceAttemptListener;

    // 기존 컴포넌트 재사용
    private final JdbcPagingItemReader<com.mycom.myapp.sendapp.batch.dto.UserBillingDayDto> settlementTargetUserIdReader;
    private final com.mycom.myapp.sendapp.batch.processor.InvoiceSettlementProcessor invoiceSettlementProcessor;
    private final MonthlyInvoiceWriter monthlyInvoiceWriter;
    private final com.mycom.myapp.sendapp.batch.support.BatchInvoiceProperties batchInvoiceProperties;

    @Bean
    public Job monthlyInvoiceSettlementForceJob() {
        return new JobBuilder("monthlyInvoiceSettlementForceJob", jobRepository)
                .start(forceStep0AttemptStart())
                .next(forceStep1SettlementChunk())
                .listener(monthlyInvoiceAttemptListener)
                .build();
    }

    @Bean
    public Step forceStep0AttemptStart() {
        return new StepBuilder("forceStep0AttemptStart", jobRepository)
                .tasklet(forceAttemptStartTasklet, transactionManager)
                .build();
    }

    @Bean
    public Step forceStep1SettlementChunk() {
        return new StepBuilder("forceStep1SettlementChunk", jobRepository)
                .<com.mycom.myapp.sendapp.batch.dto.UserBillingDayDto, com.mycom.myapp.sendapp.batch.dto.MonthlyInvoiceRowDto>chunk(batchInvoiceProperties.getChunkSize(), transactionManager)
                .reader(settlementTargetUserIdReader)
                .processor(invoiceSettlementProcessor)
                .writer(monthlyInvoiceWriter)
                .listener(monthlyInvoiceAttemptListener)
                .build();
    }
}
