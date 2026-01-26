package com.mycom.myapp.sendapp.batch.config;

import com.mycom.myapp.sendapp.batch.listener.MonthlyInvoiceAttemptListener;
import com.mycom.myapp.sendapp.batch.tasklet.RetrySettlementAttemptStartTasklet;
import com.mycom.myapp.sendapp.batch.writer.MonthlyInvoiceRetryWriter;
import com.mycom.myapp.sendapp.batch.support.BatchInvoiceProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.database.JdbcPagingItemReader;
import org.springframework.batch.item.database.builder.JdbcPagingItemReaderBuilder;
import org.springframework.batch.item.database.support.MySqlPagingQueryProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.util.Map;

/**
 * 정산 실패 재시도 배치 Job 설정
 * - Reader: settlement_status에서 FAILED invoice_id 조회
 * - Writer: monthly_invoice_batch_fail 기반으로 상세/합계 재반영
 */
@Configuration
@RequiredArgsConstructor
public class MonthlyInvoiceRetryBatchJobConfig {
    private final BatchInvoiceProperties batchInvoiceProperties;

    private final org.springframework.batch.core.repository.JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;

    private final RetrySettlementAttemptStartTasklet retrySettlementAttemptStartTasklet;
    private final MonthlyInvoiceAttemptListener monthlyInvoiceAttemptListener;
    private final MonthlyInvoiceRetryWriter monthlyInvoiceRetryWriter;

    @Bean
    public Job monthlyInvoiceRetrySettlementJob(JdbcPagingItemReader<Long> failedSettlementInvoiceIdReader) {
        return new JobBuilder("monthlyInvoiceRetrySettlementJob", jobRepository)
                .start(retryStep0AttemptStart())
                .next(retryStep1SettlementChunk(failedSettlementInvoiceIdReader))
                .listener(monthlyInvoiceAttemptListener)
                .build();
    }

    @Bean
    public Step retryStep0AttemptStart() {
        return new StepBuilder("retryStep0AttemptStart", jobRepository)
                .tasklet(retrySettlementAttemptStartTasklet, transactionManager)
                .build();
    }

    @Bean
    public Step retryStep1SettlementChunk(JdbcPagingItemReader<Long> failedSettlementInvoiceIdReader) {
        return new StepBuilder("retryStep1SettlementChunk", jobRepository)
                .<Long, Long>chunk(batchInvoiceProperties.getChunkSize(), transactionManager)
                .reader(failedSettlementInvoiceIdReader)
                .writer(monthlyInvoiceRetryWriter)
                .listener(monthlyInvoiceAttemptListener)
                .build();
    }

    @Bean
    public JdbcPagingItemReader<Long> failedSettlementInvoiceIdReader(DataSource dataSource) {
        MySqlPagingQueryProvider queryProvider = new MySqlPagingQueryProvider();
        queryProvider.setSelectClause("SELECT invoice_id");
        queryProvider.setFromClause("FROM settlement_status");
        queryProvider.setWhereClause("WHERE status = 'FAILED'");
        queryProvider.setSortKeys(Map.of("invoice_id", org.springframework.batch.item.database.Order.ASCENDING));

        int pageSize = batchInvoiceProperties.getReaderPageSize();

        return new JdbcPagingItemReaderBuilder<Long>()
                .name("failedSettlementInvoiceIdReader")
                .dataSource(dataSource)
                .queryProvider(queryProvider)
                .pageSize(pageSize)
                .fetchSize(pageSize)
                .rowMapper((rs, rowNum) -> rs.getLong("invoice_id"))
                .saveState(true)
                .build();
    }
}
