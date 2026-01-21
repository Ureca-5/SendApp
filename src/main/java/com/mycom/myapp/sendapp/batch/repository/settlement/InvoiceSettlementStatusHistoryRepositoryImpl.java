package com.mycom.myapp.sendapp.batch.repository.settlement;

import com.mycom.myapp.sendapp.batch.dto.SettlementStatusHistoryRowDto;
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
public class InvoiceSettlementStatusHistoryRepositoryImpl implements InvoiceSettlementStatusHistoryRepository {
    private final JdbcTemplate jdbcTemplate;

    private static final String INSERT_SQL = """
        INSERT INTO settlement_status_history
            (invoice_id, attempt_id, from_status, to_status, changed_at, reason_code)
        VALUES
            (?, ?, ?, ?, ?, ?)
        """;

    @Override
    public int[] batchInsert(List<SettlementStatusHistoryRowDto> rows) {
        if (rows == null || rows.isEmpty()) {
            return new int[0];
        }

        // 1) AUTO_INCREMENT인 settlement_status_history_id는 insert 대상에서 제외
        final String sql = """
            INSERT INTO settlement_status_history
                (invoice_id, attempt_id, from_status, to_status, changed_at, reason_code)
            VALUES (?, ?, ?, ?, ?, ?)
            """;

        return jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {

            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                // 2) i번째 이력 row 바인딩
                SettlementStatusHistoryRowDto row = rows.get(i);

                ps.setLong(1, row.getInvoiceId());
                ps.setLong(2, row.getAttemptId());
                ps.setString(3, row.getFromStatus().name()); // enum.name()
                ps.setString(4, row.getToStatus().name());   // enum.name()
                ps.setTimestamp(5, Timestamp.valueOf(row.getChangedAt()));

                // 3) reason_code는 nullable
                if (row.getReasonCode() == null) {
                    ps.setNull(6, java.sql.Types.VARCHAR);
                } else {
                    ps.setString(6, row.getReasonCode());
                }
            }

            @Override
            public int getBatchSize() {
                return rows.size();
            }
        });
    }

}
