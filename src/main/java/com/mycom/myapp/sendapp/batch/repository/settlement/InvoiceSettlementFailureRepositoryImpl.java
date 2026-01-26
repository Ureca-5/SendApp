package com.mycom.myapp.sendapp.batch.repository.settlement;

import com.mycom.myapp.sendapp.batch.dto.MonthlyInvoiceBatchFailRowDto;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class InvoiceSettlementFailureRepositoryImpl implements InvoiceSettlementFailureRepository {
    private final JdbcTemplate jdbcTemplate;

    @Override
    public int[] batchInsert(List<MonthlyInvoiceBatchFailRowDto> rows) {
        if (rows == null || rows.isEmpty()) {
            return new int[0];
        }

        // 1) fail_id는 AUTO_INCREMENT이므로 제외
        final String sql = """
            INSERT INTO monthly_invoice_batch_fail
                (attempt_id, error_code, error_message, created_at, invoice_category_id, billing_history_id, invoice_id)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """;

        return jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {

            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                // 2) i번째 실패 row 바인딩
                MonthlyInvoiceBatchFailRowDto row = rows.get(i);

                ps.setLong(1, row.getAttemptId());
                ps.setString(2, row.getErrorCode());

                // 3) error_message는 nullable (text)
                if (row.getErrorMessage() == null) {
                    ps.setNull(3, java.sql.Types.LONGVARCHAR);
                } else {
                    ps.setString(3, row.getErrorMessage());
                }

                ps.setTimestamp(4, Timestamp.valueOf(row.getCreatedAt()));
                ps.setInt(5, row.getInvoiceCategoryId());
                ps.setLong(6, row.getBillingHistoryId());
                ps.setLong(7, row.getInvoiceId());
            }

            @Override
            public int getBatchSize() {
                return rows.size();
            }
        });
    }
}
