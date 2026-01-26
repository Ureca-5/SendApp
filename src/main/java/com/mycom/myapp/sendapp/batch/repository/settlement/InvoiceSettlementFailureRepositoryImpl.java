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

    @Override
    public List<MonthlyInvoiceBatchFailRowDto> findByInvoiceIdsAndCategoryIds(List<Long> invoiceIds, List<Integer> categoryIds) {
        if (invoiceIds == null || invoiceIds.isEmpty() || categoryIds == null || categoryIds.isEmpty()) {
            return List.of();
        }

        String invoiceInClause = String.join(",", invoiceIds.stream().map(id -> "?").toArray(String[]::new));
        String categoryInClause = String.join(",", categoryIds.stream().map(id -> "?").toArray(String[]::new));

        String sql = String.format("""
            SELECT fail_id, attempt_id, error_code, error_message, created_at, invoice_id, invoice_category_id, billing_history_id
            FROM monthly_invoice_batch_fail
            WHERE invoice_id IN (%s)
              AND invoice_category_id IN (%s)
            """, invoiceInClause, categoryInClause);

        Object[] args = new Object[invoiceIds.size() + categoryIds.size()];
        int idx = 0;
        for (Long id : invoiceIds) {
            args[idx++] = id;
        }
        for (Integer id : categoryIds) {
            args[idx++] = id;
        }

        return jdbcTemplate.query(sql, (rs, rowNum) -> MonthlyInvoiceBatchFailRowDto.builder()
                .failId(rs.getLong("fail_id"))
                .attemptId(rs.getObject("attempt_id") == null ? null : rs.getLong("attempt_id"))
                .errorCode(rs.getString("error_code"))
                .errorMessage(rs.getString("error_message"))
                .createdAt(rs.getTimestamp("created_at") == null ? null : rs.getTimestamp("created_at").toLocalDateTime())
                .invoiceId(rs.getObject("invoice_id") == null ? null : rs.getLong("invoice_id"))
                .invoiceCategoryId(rs.getInt("invoice_category_id"))
                .billingHistoryId(rs.getLong("billing_history_id"))
                .build(), args);
    }

    @Override
    public List<MonthlyInvoiceBatchFailRowDto> findMicroByInvoiceIds(List<Long> invoiceIds, int microCategoryId, Long lastFailId, int limit) {
        if (invoiceIds == null || invoiceIds.isEmpty()) {
            return List.of();
        }
        long safeLastFailId = lastFailId == null ? 0L : lastFailId;
        int safeLimit = limit <= 0 ? 5000 : limit;

        String invoiceInClause = String.join(",", invoiceIds.stream().map(id -> "?").toArray(String[]::new));

        String sql = String.format("""
            SELECT fail_id, attempt_id, error_code, error_message, created_at, invoice_id, invoice_category_id, billing_history_id
            FROM monthly_invoice_batch_fail
            WHERE invoice_id IN (%s)
              AND invoice_category_id = ?
              AND fail_id > ?
            ORDER BY fail_id ASC
            LIMIT ?
            """, invoiceInClause);

        Object[] args = new Object[invoiceIds.size() + 3];
        int idx = 0;
        for (Long id : invoiceIds) {
            args[idx++] = id;
        }
        args[idx++] = microCategoryId;
        args[idx++] = safeLastFailId;
        args[idx] = safeLimit;

        return jdbcTemplate.query(sql, (rs, rowNum) -> MonthlyInvoiceBatchFailRowDto.builder()
                .failId(rs.getLong("fail_id"))
                .attemptId(rs.getObject("attempt_id") == null ? null : rs.getLong("attempt_id"))
                .errorCode(rs.getString("error_code"))
                .errorMessage(rs.getString("error_message"))
                .createdAt(rs.getTimestamp("created_at") == null ? null : rs.getTimestamp("created_at").toLocalDateTime())
                .invoiceId(rs.getObject("invoice_id") == null ? null : rs.getLong("invoice_id"))
                .invoiceCategoryId(rs.getInt("invoice_category_id"))
                .billingHistoryId(rs.getLong("billing_history_id"))
                .build(), args);
    }
}
