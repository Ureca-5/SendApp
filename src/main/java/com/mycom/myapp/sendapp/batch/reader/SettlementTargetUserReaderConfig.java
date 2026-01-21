package com.mycom.myapp.sendapp.batch.reader;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.database.JdbcPagingItemReader;
import org.springframework.batch.item.database.builder.JdbcPagingItemReaderBuilder;
import org.springframework.batch.item.database.support.MySqlPagingQueryProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.util.Map;
import com.mycom.myapp.sendapp.batch.dto.UserBillingDayDto;

/**
 * 역할: 조건을 만족하는 회원의 usersId 조회 <br>
 * 1. users_id > lastUserId (키셋 페이징) <br>
 * 2. 정산 당월 청구서가 아직 없는 경우 <br>
 * 3. 당월 정산해야할 구독 또는 단건 결제 원천 데이터가 하나라도 있는 경우
 */
@Slf4j
@Configuration
public class SettlementTargetUserReaderConfig {

    /**
     * “monthly_invoice가 아직 없고 + 당월(billing_yyyymm)에 원천 데이터가 존재하는 유저”를
     * users_id 오름차순으로 페이징 조회하여 1건씩 반환합니다.
     *
     * - chunkSize=1000 이면, 프레임워크가 read를 1000번 호출해 모은 뒤 processor/write를 실행합니다.
     * - JobParameter late-binding을 위해 @StepScope 사용.
     */
    @Bean
    @StepScope
    public JdbcPagingItemReader<UserBillingDayDto> settlementTargetUserIdReader(
            DataSource dataSource,
            @Value("#{jobParameters['targetYyyymm']}") Integer targetYyyymm
    ) {
        if (targetYyyymm == null) {
            throw new IllegalArgumentException("jobParameters['targetYyyymm'] is required.");
        }

        // MySQL PagingQueryProvider 구성
        MySqlPagingQueryProvider queryProvider = new MySqlPagingQueryProvider();
        queryProvider.setSelectClause("SELECT u.users_id, u.billing_day");
        queryProvider.setFromClause("FROM users u");

        // 원천 데이터 존재 + invoice 헤더 미존재
        // (users, monthly_invoice, subscribe_billing_history, micro_payment_billing_history 테이블명/컬럼명은 프로젝트 스키마에 맞춰 조정하세요)
        queryProvider.setWhereClause("""
            WHERE NOT EXISTS (
                SELECT 1
                FROM monthly_invoice mi
                WHERE mi.users_id = u.users_id
                  AND mi.billing_yyyymm = :yyyymm
            )
            AND (
                EXISTS (
                    SELECT 1
                    FROM subscribe_billing_history sbh
                    WHERE sbh.users_id = u.users_id
                      AND sbh.billing_yyyymm = :yyyymm
                )
                OR EXISTS (
                    SELECT 1
                    FROM micro_payment_billing_history mph
                    WHERE mph.users_id = u.users_id
                      AND mph.billing_yyyymm = :yyyymm
                )
            )
            """);

        // 페이징 안정성을 위한 정렬 키(필수)
        queryProvider.setSortKeys(Map.of("users_id", org.springframework.batch.item.database.Order.ASCENDING));

        int pageSize = 1000; // chunkSize와 동일하게 두는 편이 이해/운영이 쉽습니다(필수는 아님)
        int fetchSize = 1000;

        return new JdbcPagingItemReaderBuilder<UserBillingDayDto>()
                .name("settlementTargetUserIdReader")
                .dataSource(dataSource)
                .queryProvider(queryProvider)
                .parameterValues(Map.of("yyyymm", targetYyyymm))
                .pageSize(pageSize)
                .fetchSize(fetchSize)
                .rowMapper((rs, rowNum) -> UserBillingDayDto.builder()
                        .usersId(rs.getLong("users_id"))
                        .billingDay(rs.getObject("billing_day") == null ? null : rs.getInt("billing_day"))
                        .build())
                .saveState(true) // 재시작을 고려하면 true (JobRepository에 상태 저장)
                .build();
    }
}
