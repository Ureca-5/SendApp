package com.mycom.myapp.sendapp.batch.repository.settlement;


import com.mycom.myapp.sendapp.batch.dto.SettlementStatusRowDto;
import com.mycom.myapp.sendapp.batch.enums.SettlementStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.*;

@Repository
@RequiredArgsConstructor
public class InvoiceSettlementStatusRepositoryImpl implements InvoiceSettlementStatusRepository {
    private final JdbcTemplate jdbcTemplate;

    @Override
    public int batchInsert(List<SettlementStatusRowDto> rows) {
        // [1] 방어 로직: 비어있으면 DB를 치지 않습니다.
        if (rows == null || rows.isEmpty()) {
            return 0;
        }

        // [2] SQL: invoice_id는 PK이므로 중복 insert면 예외가 납니다.
        //     - 현재 단계(초기 배치)에서는 "신규 청구서 헤더 insert 후 상태 insert"가 전제이므로 정상 흐름입니다.
        final String sql = """
            INSERT INTO settlement_status (
                invoice_id,
                status,
                last_attempt_at,
                created_at
            ) VALUES (?, ?, ?, ?)
            """;

        // [3] batchArgs 구성:
        //     - JdbcTemplate.batchUpdate(sql, List<Object[]>) 는 int[]를 반환합니다.  [oai_citation:1‡Home](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/dao/class-use/DataAccessException.html)
        List<Object[]> batchArgs = new ArrayList<>(rows.size());

        for (SettlementStatusRowDto row : rows) {
            // [3-1] 필수 값 검증 (실무적으로는 writer에서 보장하되, repository도 최소 방어)
            if (row == null) {
                continue;
            }
            if (row.getInvoiceId() == null) {
                throw new IllegalArgumentException("settlement_status.invoice_id is required.");
            }
            if (row.getStatus() == null) {
                throw new IllegalArgumentException("settlement_status.status is required.");
            }
            if (row.getLastAttemptAt() == null) {
                throw new IllegalArgumentException("settlement_status.lastAttemptAt is required.");
            }
            if (row.getCreatedAt() == null) {
                throw new IllegalArgumentException("settlement_status.createdAt is required.");
            }

            // [3-2] DB 저장값은 대문자 문자열
            batchArgs.add(new Object[]{
                    row.getInvoiceId(),
                    row.getStatus().name(),
                    Timestamp.valueOf(row.getLastAttemptAt()),
                    Timestamp.valueOf(row.getCreatedAt())
            });
        }

        if (batchArgs.isEmpty()) {
            return 0;
        }

        // [4] 배치 실행
        int[] updated = jdbcTemplate.batchUpdate(sql, batchArgs);

        // [5] 반환값 합산:
        //     - 각 원소는 "각 row의 update count" (일반적으로 insert 성공이면 1)
        int sum = 0;
        for (int u : updated) {
            // 드라이버에 따라 SUCCESS_NO_INFO(-2) 같은 값이 올 수도 있어,
            // 여기서는 0보다 큰 값만 합산(보수적으로 처리).
            if (u > 0) {
                sum += u;
            }
        }
        return sum;
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
