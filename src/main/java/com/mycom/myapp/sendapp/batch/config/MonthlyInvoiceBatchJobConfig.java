package com.mycom.myapp.sendapp.batch.config;

import com.mycom.myapp.sendapp.batch.dto.MonthlyInvoiceRowDto;
import com.mycom.myapp.sendapp.batch.listener.MonthlyInvoiceAttemptListener;
import com.mycom.myapp.sendapp.batch.processor.InvoiceSettlementProcessor;
import com.mycom.myapp.sendapp.batch.tasklet.MonthlyInvoiceAttemptStartTasklet;
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

@Configuration
@RequiredArgsConstructor
public class MonthlyInvoiceBatchJobConfig {

    private static final int CHUNK_SIZE = 1000;

    // Spring Batch 핵심 인프라
    private final org.springframework.batch.core.repository.JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;

    // Step0
    private final MonthlyInvoiceAttemptStartTasklet monthlyInvoiceAttemptStartTasklet;

    // Listener
    private final MonthlyInvoiceAttemptListener monthlyInvoiceAttemptListener;

    // Step1 components
    // 이미 SettlementTargetUserReaderConfig에서 @Bean으로 제공한 reader를 주입받습니다.
    private final JdbcPagingItemReader<Long> settlementTargetUserIdReader;
    private final InvoiceSettlementProcessor invoiceSettlementProcessor;
    private final MonthlyInvoiceWriter monthlyInvoiceWriter;

    @Bean
    public Job monthlyInvoiceSettlementJob() {
        return new JobBuilder("monthlyInvoiceSettlementJob", jobRepository)
                .start(step0AttemptStart())
                .next(step1SettlementChunk())
                .listener(monthlyInvoiceAttemptListener)
                .build();
    }

    /**
     * Step0 (Tasklet)
     * - 배치 시작 가능 여부 검증
     * - attempt 레코드(STARTED) 생성
     * - attemptId를 JobExecutionContext에 저장 (monthlyInvoiceAttemptId)
     */
    @Bean
    public Step step0AttemptStart() {
        return new StepBuilder("step0AttemptStart", jobRepository)
                .tasklet(monthlyInvoiceAttemptStartTasklet, transactionManager)
                .build();
    }

    /**
     * Step1 (Chunk)
     * - Reader: usersId 1건씩 읽음
     * - Processor: usersId -> MonthlyInvoiceRowDto (DB 접근 없음)
     * - Writer: List<MonthlyInvoiceRowDto> (최대 1000건) 단위로 정산/저장 수행
     */
    @Bean
    public Step step1SettlementChunk() {
        return new StepBuilder("step1SettlementChunk", jobRepository)
                .<Long, MonthlyInvoiceRowDto>chunk(CHUNK_SIZE, transactionManager)
                .reader(settlementTargetUserIdReader)
                .processor(invoiceSettlementProcessor)
                .writer(monthlyInvoiceWriter)
                .listener(monthlyInvoiceAttemptListener)
                .build();
    }
}
