package com.mycom.myapp.sendapp.batch.repository.invoice;

import com.mycom.myapp.sendapp.batch.dto.MonthlyInvoiceDetailRowDto;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class MonthlyInvoiceDetailRepositoryImpl implements MonthlyInvoiceDetailRepository {
    private final JdbcTemplate jdbcTemplate;

    @Override
    public int[] batchInsert(List<MonthlyInvoiceDetailRowDto> details) {
        if (details == null || details.isEmpty()) {
            return new int[0];
        }

        String sql = """
            INSERT INTO monthly_invoice_detail (
                  invoice_id,
                  invoice_category_id,
                  billing_history_id,
                  service_name,
                  origin_amount,
                  discount_amount,
                  total_amount,
                  usage_start_date,
                  usage_end_date,
                  created_at,
                  expired_at
            ) VALUES (
                  ?,
                  ?,
                  ?,
                  ?,
                  ?,
                  ?,
                  ?,
                  ?,
                  ?,
                  ?,
                  ?
            )
            """;

        return jdbcTemplate.batchUpdate(sql, newBatchSetter(details));
    }

    @Override
    public int[] batchInsertIgnore(List<MonthlyInvoiceDetailRowDto> details) {
        if (details == null || details.isEmpty()) {
            return new int[0];
        }

        // MySQL 전용: 충돌 무시 (ROW 단위 성공/실패를 SQL이 알아서 처리)
        String sql = """
            INSERT IGNORE INTO monthly_invoice_detail (
                  invoice_id,
                  invoice_category_id,
                  billing_history_id,
                  service_name,
                  origin_amount,
                  discount_amount,
                  total_amount,
                  usage_start_date,
                  usage_end_date,
                  created_at,
                  expired_at
            ) VALUES (
                  ?,
                  ?,
                  ?,
                  ?,
                  ?,
                  ?,
                  ?,
                  ?,
                  ?,
                  ?,
                  ?
            )
            """;

        return jdbcTemplate.batchUpdate(sql, newBatchSetter(details));
    }

    /**
     * 역할(Why):
     * - JdbcTemplate.batchUpdate(...)는 "SQL 1개 + 파라미터 N세트"를 받아서 한 번에 배치 실행합니다.
     * - BatchPreparedStatementSetter는 "i번째 DTO → PreparedStatement의 ? 파라미터 채우기" 규칙을 제공하는 콜백입니다.
     * 절차(How):
     * 1) batchUpdate가 내부에서 i=0..N-1까지 반복 호출
     * 2) setValues(ps, i)에서 details[i]의 값을 SQL의 ? 순서대로 바인딩
     * 3) getBatchSize()로 배치 크기(N)를 알려줌
     **/
    private BatchPreparedStatementSetter newBatchSetter(List<MonthlyInvoiceDetailRowDto> details) {
        return new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                /*
                 * (1) i번째 row DTO를 가져옵니다.
                 * - batchUpdate가 i를 증가시키며 호출하므로,
                 *   여기서 "i번째 DTO를 SQL 파라미터로 변환"하면 됩니다.
                 */
                MonthlyInvoiceDetailRowDto d = details.get(i);

                /*
                 * (2) 필수 키/제약조건 구성 요소 사전 검증
                 * - invoice_id: FK (헤더가 먼저 insert되고, 그 결과로 채워져야 함)
                 * - invoice_category_id: UK 구성 요소
                 * - billing_history_id: UK 구성 요소(원천 데이터 식별자)
                 *
                 * 이 3개는 UK(invoice_id, invoice_category_id, billing_history_id)의 핵심이므로
                 * null이면 DB에 보내기 전에 즉시 실패시키는 것이 원인 추적에 유리합니다.
                 */
                if (d.getInvoiceId() == null) {
                    throw new IllegalArgumentException("invoiceId is required for monthly_invoice_detail insert.");
                }
                if (d.getInvoiceCategoryId() == null) {
                    throw new IllegalArgumentException("invoiceCategoryId is required for monthly_invoice_detail insert.");
                }
                if (d.getBillingHistoryId() == null) {
                    throw new IllegalArgumentException("billingHistoryId is required for monthly_invoice_detail insert.");
                }

                /*
                 * (3) SQL의 VALUES(?, ?, ?, ...) 순서와 동일하게 바인딩합니다.
                 *
                 * SQL:
                 * INSERT INTO monthly_invoice_detail (
                 *   invoice_id, invoice_category_id, billing_history_id, service_name,
                 *   origin_amount, discount_amount, total_amount,
                 *   usage_start_date, usage_end_date,
                 *   created_at, expired_at
                 * ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                 */

                ps.setLong(1, d.getInvoiceId());
                ps.setInt(2, d.getInvoiceCategoryId());
                ps.setLong(3, d.getBillingHistoryId());

                ps.setString(4, d.getServiceName());

                // 원 가격 혹은 총 가격이 null 이라면 예외 반환
                if(d.getOriginAmount() == null) {
                    throw new IllegalArgumentException("originAmount is required for monthly_invoice_detail insert.");
                }
                if(d.getTotalAmount() == null) {
                    throw new IllegalArgumentException("totalAmount is required for monthly_invoice_detail insert.");
                }
                ps.setLong(5, d.getOriginAmount());
                ps.setLong(6, defaultLong(d.getDiscountAmount()));
                ps.setLong(7, d.getTotalAmount());

                // DATE 컬럼 ↔ LocalDate
                // (JDBC 4.2+: setObject(LocalDate) 가능)
                ps.setObject(8, d.getUsageStartDate());
                ps.setObject(9, d.getUsageEndDate());

                // created_at: DATETIME(6) ↔ LocalDateTime
                LocalDateTime createdAt = d.getCreatedAt();
                if (createdAt == null) {
                    throw new IllegalArgumentException("createdAt is required for monthly_invoice_detail insert.");
                }
                ps.setObject(10, createdAt);

                // expired_at: DATE ↔ LocalDate
                ps.setObject(11, d.getExpiredAt());
            }

            @Override
            public int getBatchSize() {
                return details.size();
            }
        };
    }

    private long defaultLong(Long v) {
        return v != null ? v : 0L;
    }
}
