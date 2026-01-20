package com.mycom.myapp.sendapp.batch.repository.settlement;


import com.mycom.myapp.sendapp.batch.dto.SettlementStatusRowDto;
import com.mycom.myapp.sendapp.batch.enums.SettlementStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.*;

@Repository
@RequiredArgsConstructor
public class InvoiceSettlementStatusRepositoryImpl implements InvoiceSettlementStatusRepository {
    private final JdbcTemplate jdbcTemplate;

    @Override
    public int[] batchInsert(List<SettlementStatusRowDto> rows) {
        if (rows == null || rows.isEmpty()) {
            return new int[0];
        }

        // 1) INSERT SQL 준비
        // - invoice_id는 PK/FK
        // - status는 enum.name()으로 대문자 저장
        final String sql = """
            INSERT INTO settlement_status (invoice_id, status, last_attempt_at, created_at)
            VALUES (?, ?, ?, ?)
            """;

        // 2) batchUpdate(PreparedStatementSetter 오버로드) 사용
        // - 이 오버로드는 반환 타입이 int[] 이므로 int[][] 이슈가 발생하지 않음
        return jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {

            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                // 3) i번째 row를 꺼내어 바인딩
                SettlementStatusRowDto row = rows.get(i);

                ps.setLong(1, row.getInvoiceId());
                ps.setString(2, row.getStatus().name()); // enum -> DB 문자열(대문자)

                ps.setTimestamp(3, Timestamp.valueOf(row.getLastAttemptAt()));
                ps.setTimestamp(4, Timestamp.valueOf(row.getCreatedAt()));
            }

            @Override
            public int getBatchSize() {
                // 4) 배치 크기 반환 (JdbcTemplate이 이 횟수만큼 setValues 호출)
                return rows.size();
            }
        });
    }

    @Override
    public Optional<SettlementStatusRowDto> findByInvoiceId(Long invoiceId) {
        if (invoiceId == null) {
            return Optional.empty();
        }

        final String sql = """
            SELECT
                invoice_id,
                status,
                last_attempt_at,
                created_at
            FROM settlement_status
            WHERE invoice_id = ?
            """;

        List<SettlementStatusRowDto> list = jdbcTemplate.query(
                sql,
                (rs, rowNum) -> SettlementStatusRowDto.builder()
                        .invoiceId(rs.getLong("invoice_id"))
                        .status(SettlementStatus.valueOf(rs.getString("status")))
                        .lastAttemptAt(rs.getTimestamp("last_attempt_at").toLocalDateTime())
                        .createdAt(rs.getTimestamp("created_at").toLocalDateTime())
                        .build(),
                invoiceId
        );

        if (list == null || list.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(list.get(0));
    }

    @Override
    public List<SettlementStatusRowDto> findByInvoiceIds(List<Long> invoiceIds) {
        if (invoiceIds == null || invoiceIds.isEmpty()) {
            return Collections.emptyList();
        }

        String inClause = String.join(",", invoiceIds.stream().map(x -> "?").toArray(String[]::new));

        final String sql = String.format("""
            SELECT
                invoice_id,
                status,
                last_attempt_at,
                created_at
            FROM settlement_status
            WHERE invoice_id IN (%s)
            """, inClause);

        Object[] args = invoiceIds.toArray(new Object[0]);

        return jdbcTemplate.query(
                sql,
                (rs, rowNum) -> SettlementStatusRowDto.builder()
                        .invoiceId(rs.getLong("invoice_id"))
                        .status(SettlementStatus.valueOf(rs.getString("status")))
                        .lastAttemptAt(rs.getTimestamp("last_attempt_at").toLocalDateTime())
                        .createdAt(rs.getTimestamp("created_at").toLocalDateTime())
                        .build(),
                args
        );
    }
}
