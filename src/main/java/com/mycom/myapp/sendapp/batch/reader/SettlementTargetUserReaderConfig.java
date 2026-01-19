package com.mycom.myapp.sendapp.batch.reader;

import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.database.JdbcPagingItemReader;
import org.springframework.batch.item.database.builder.JdbcPagingItemReaderBuilder;
import org.springframework.batch.item.database.support.MySqlPagingQueryProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.util.Map;

/**
 * 정산 대상 유저(users_id)만 1건씩 읽는 Reader.
 *
 * 선정 기준(사용자 정의 그대로):
 * 1) monthly_invoice에 (billing_yyyymm, users_id)가 아직 없다.
 * 2) subscribe_billing_history 또는 micro_payment_billing_history 중 당월 데이터가 하나라도 있다.
 *
 * 반환: users_id (Long)
 */
@Configuration
@RequiredArgsConstructor
public class SettlementTargetUserReaderConfig {
    private final DataSource dataSource;
    private final int READER_PAGE_SIZE = 1000;
    private final int READER_FETCH_SIZE = 1000;

    @Bean
    @StepScope
    public JdbcPagingItemReader<Long> settlementTargetUserReader(
            @Value("#{jobParameters['targetYyyymm']}") Integer targetYyyymm
    ) {
        if (targetYyyymm == null) {
            throw new IllegalArgumentException("jobParameters['targetYyyymm'] is required (e.g., 202601).");
        }

        // MySQL 페이징 쿼리 프로바이더 구성
        MySqlPagingQueryProvider queryProvider = new MySqlPagingQueryProvider();

        queryProvider.setSelectClause("SELECT u.users_id");
        queryProvider.setFromClause("FROM users u");

        // 정산 대상 유저 조건:
        // 1) 해당 월 청구서 헤더가 없는 유저
        // 2) 해당 월 원천 데이터가 하나라도 존재
        //
        // EXISTS 내부 조건이 (users_id, billing_yyyymm) 인덱스를 타기 쉬움:
        // - subscribe_billing_history: INDX_subscribe_billing_history_users_yyyymm (users_id, billing_yyyymm)
        // - micro_payment_billing_history: INDX_micro_payment_billing_history_users_yyyymm (users_id, billing_yyyymm)
        //
        // monthly_invoice는 UK(billing_yyyymm, users_id) 또는 UK(users_id, billing_yyyymm)로 빠르게 존재 여부 확인 가능
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

        // 페이징 정렬 키(필수): users_id를 안정적 오름차순으로 읽는다.
        queryProvider.setSortKeys(Map.of("u.users_id", org.springframework.batch.item.database.Order.ASCENDING));

        return new JdbcPagingItemReaderBuilder<Long>()
                .name("settlementTargetUserReader")
                .dataSource(dataSource)
                .queryProvider(queryProvider)
                .parameterValues(Map.of("yyyymm", targetYyyymm))
                .rowMapper((rs, rowNum) -> rs.getLong(1)) // users_id 1컬럼
                .pageSize(READER_PAGE_SIZE)      // DB 페이징 단위
                .fetchSize(READER_FETCH_SIZE)     // JDBC 드라이버 힌트(옵션)
                .saveState(true)     // 재시작 시 페이지 상태 복원(ExecutionContext)
                .build();
    }
}
